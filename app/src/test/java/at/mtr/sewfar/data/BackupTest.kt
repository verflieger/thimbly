package at.mtr.sewfar.data

import at.mtr.sewfar.data.backup.AutoBackup
import at.mtr.sewfar.data.backup.BackupException
import at.mtr.sewfar.data.backup.BackupFolder
import at.mtr.sewfar.data.backup.BackupRepository
import at.mtr.sewfar.domain.PhotoOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** SPEC 8. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupTest {
    private lateinit var t: TestDb
    private lateinit var backup: BackupRepository
    private lateinit var base: File
    private val image = TestDb.jpeg(40, 30)

    @Before fun setUp() = runTest {
        t = TestDb()
        base = t.photoFiles.dir.parentFile!!.apply { mkdirs() }
        val cache = File(base, "cache").apply { mkdirs() }
        backup = BackupRepository(t.db, t.photoFiles, base, cache, t.time, "test")
    }

    @After fun tearDown() { t.close(); base.deleteRecursively() }

    private suspend fun exportBytes(): ByteArray = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

    /** backup.json of an export: compares complete table contents. */
    private fun tablesJson(zip: ByteArray): String = ZipInputStream(zip.inputStream()).use { z ->
        generateSequence { z.nextEntry }.first { it.name == "backup.json" }
        val text = z.readBytes().decodeToString()
        text.substringAfter("\"tables\":")
    }

    private suspend fun sampleData(): Pair<String, String> {
        val person = t.persons.create("Josi")
        val pattern = t.patterns.create("Raglan")
        val project = t.projects.create("Shirt")
        t.projects.edit(project) { it.copy(personId = person) }
        t.projects.addPattern(project, pattern)
        t.projects.addTime(project, t.time.day, 30)
        val gone = t.projects.observeTime(project).first().single().id
        t.projects.deleteTime(gone) // deleted: not part of the backup
        val photo = t.photos.add(PhotoOwner.PROJECT, project) { image.inputStream() }
        return project to photo
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val file = File(base, "custom-${entries.size}-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
        return file
    }

    @Test
    fun roundTripRestoresTablesAndPhotos() = runTest {
        val (project, photo) = sampleData()
        val zip = exportBytes()
        val before = tablesJson(zip)

        // Change everything after the export.
        t.projects.delete(project)
        t.patterns.create("Another")
        t.photoFiles.delete(photo)

        backup.import(backup.prepareImport(zip.inputStream()))

        assertEquals(before, tablesJson(exportBytes()))
        assertTrue(t.photoFiles.file(photo).readBytes().contentEquals(image) || t.photoFiles.file(photo).length() > 0)
        assertTrue(photoBytes(zip, photo).contentEquals(t.photoFiles.file(photo).readBytes()))
        assertNotNull(t.projects.get(project))
    }

    @Test
    fun importMovesWhatILearnedToTheNotes() = runTest {
        val id = t.projects.create("Skirt")
        t.projects.edit(id) { it.copy(notes = "Fits", learned = "Grade the seams") } // as in a backup before SPEC 3.4
        backup.import(backup.prepareImport(exportBytes().inputStream()))
        assertEquals("Fits\n\nGrade the seams", t.projects.get(id)!!.notes)
    }

    @Test
    fun exportHoldsLiveRowsAndTheDeletionLog() = runTest {
        val (project, _) = sampleData()
        val person = t.projects.get(project)!!.personId!!
        val purged = t.patterns.create("Old")
        t.patterns.delete(purged)
        t.time.advance(at.mtr.sewfar.data.db.SoftDeleter.UndoWindowMillis + 1_000)
        t.deleter.purge()
        t.persons.delete(person) // still inside the Undo window: left out, and the project no longer points at it

        val tables = Json.parseToJsonElement(tablesJson(exportBytes()).substringBeforeLast("}")).jsonObject
        assertEquals(0, tables.getValue("time_entry").jsonArray.size)
        assertEquals(0, tables.getValue("person").jsonArray.size)
        assertEquals(JsonNull, tables.getValue("project").jsonArray.single().jsonObject["personId"])
        // The pattern and the time entry deleted in sampleData, both past the Undo window.
        val logged = tables.getValue("deletion_log").jsonArray.map { it.jsonObject.getValue("rowId").jsonPrimitive.content }
        assertEquals(2, logged.size)
        assertTrue(purged in logged)

        backup.import(backup.prepareImport(exportBytes().inputStream()))
        assertEquals(2, t.count("deletion_log", live = false))
    }

    @Test
    fun importSkipsDeletedRowsOfOlderBackups() = runTest {
        val (project, photo) = sampleData()
        val zip = exportBytes()
        // An older backup: the pattern is marked deleted (its project link then has no parent).
        val root = ZipInputStream(zip.inputStream()).use { z -> generateSequence { z.nextEntry }.first { it.name == "backup.json" }; z.readBytes() }
            .decodeToString().let { Json.parseToJsonElement(it).jsonObject }
        val tables = root.getValue("tables").jsonObject
        val deletedPattern = JsonObject(tables.getValue("pattern").jsonArray.single().jsonObject + ("deletedAt" to JsonPrimitive(5L)))
        val older = JsonObject(root + ("tables" to JsonObject(tables + ("pattern" to JsonArray(listOf(deletedPattern))))))
        val file = zipOf("backup.json" to older.toString().toByteArray(), "photos/$photo.jpg" to image)

        backup.import(backup.prepareImport(file.inputStream()))

        assertNotNull(t.projects.get(project))
        assertEquals(0, t.count("pattern", live = false))
        assertEquals(0, t.count("project_pattern", live = false))
    }

    @Test
    fun importKeepsSafetyExportForUndo() = runTest {
        val (project, _) = sampleData()
        val withShirt = exportBytes()
        t.projects.delete(project)
        val withoutShirt = exportBytes()

        backup.import(backup.prepareImport(withShirt.inputStream()))
        assertNotNull(t.projects.get(project))
        assertTrue(backup.safetyExport.exists())

        backup.import(backup.prepareSafetyRestore())
        assertEquals(null, t.projects.get(project))
        assertEquals(tablesJson(withoutShirt), tablesJson(exportBytes()))

        // Undo works like a toggle: the data replaced by the undo is itself kept as the new safety export.
        backup.import(backup.prepareSafetyRestore())
        assertNotNull(t.projects.get(project))
    }

    private fun photoBytes(zip: ByteArray, id: String): ByteArray = ZipInputStream(zip.inputStream()).use { z ->
        generateSequence { z.nextEntry }.first { it.name == "photos/$id.jpg" }
        z.readBytes()
    }

    @Test
    fun backupWithMissingPhotoFileStillRestores() = runTest {
        val (project, photo) = sampleData()
        t.photoFiles.delete(photo) // a lost file must not make the backup unrestorable
        val zip = exportBytes()
        t.projects.delete(project)

        backup.import(backup.prepareImport(zip.inputStream()))

        assertNotNull(t.projects.get(project))
        assertEquals(listOf(photo), t.photos.observe(PhotoOwner.PROJECT, project).first().map { it.id })
        assertFalse(t.photoFiles.file(photo).exists())
    }

    /** The released format must stay importable (SPEC 8.1). Every table has a row in the fixture. */
    @Test
    fun formatOneFixtureImports() = runTest {
        val fixture = javaClass.classLoader!!.getResourceAsStream("backup/format1.zip")!!
        backup.import(backup.prepareImport(fixture))
        val project = t.projects.observeAll().first().single()
        assertEquals("Fixture shirt", project.name)
        assertEquals("122", project.size)
        assertEquals("Fixture pattern", t.projects.observePatterns(project.id).first().single().name)
        assertEquals("Fixture person", t.persons.observe(project.personId!!).first()?.name)
        assertEquals(1, t.projects.observeTime(project.id).first().size)
        val photos = t.photos.observe(PhotoOwner.PROJECT, project.id).first()
        assertEquals(1, photos.size)
        assertTrue(photos.all { t.photoFiles.file(it.id).exists() })
        val setup = t.projects.observeSetups(project.id).first().single()
        assertEquals("1.3", t.machines.observeValues(setup.setupId).first().single().value)
        assertEquals(mapOf("waist" to 74.5), t.persons.observeMeasurements(project.personId!!).first()!!.valuesCm)
        for (table in at.mtr.sewfar.data.db.SoftDeleter.AllTables) assertTrue(table, t.count(table) > 0)
        assertEquals("format 1 has no pinned pattern cover", null, t.patterns.observeAll().first().single().coverPhotoId)
    }

    /** Format 2 adds the pinned pattern cover (SPEC 8.1); the fixture pins the newer of two pattern photos. */
    @Test
    fun formatTwoFixtureKeepsPinnedPatternCover() = runTest {
        val fixture = javaClass.classLoader!!.getResourceAsStream("backup/format2.zip")!!
        backup.import(backup.prepareImport(fixture))
        val pattern = t.patterns.observeAll().first().single()
        val photos = t.photos.observe(PhotoOwner.PATTERN, pattern.id).first()
        assertEquals(2, photos.size)
        assertTrue(photos.all { t.photoFiles.file(it.id).exists() })
        assertEquals("60bddf07-e804-4122-b6c9-4e7cd824ea89", pattern.coverPhotoId)
        assertEquals(pattern.coverPhotoId, patternCovers(listOf(pattern), photos)[pattern.id])
    }

    @Test
    fun prototypeBackupIsRejected() = runTest {
        sampleData()
        val json = """{"format":1,"tables":{"project":[],"log_entry":[]}}"""
        assertRejected(BackupException.Reason.PROTOTYPE, zipOf("backup.json" to json.toByteArray()))
    }

    @Test
    fun failedCommitKeepsOldDataAndPhotos() = runTest {
        val (project, photo) = sampleData()
        val zip = exportBytes()
        // Break a foreign key: a pattern link pointing at a pattern that isn't in the backup.
        val json = ZipInputStream(zip.inputStream()).use { z -> generateSequence { z.nextEntry }.first { it.name == "backup.json" }; z.readBytes() }
            .decodeToString().replace("\"pattern\":[{", "\"pattern\":[], \"unused\":[{")
        val broken = zipOf("backup.json" to json.toByteArray(), "photos/$photo.jpg" to image)
        val error = runCatching { backup.import(backup.prepareImport(broken.inputStream())) }.exceptionOrNull()

        assertTrue("expected rollback, got $error", error is BackupException)
        assertNotNull(t.projects.get(project))
        assertTrue(t.photoFiles.file(photo).exists())
        assertFalse(File(base, "photos.new").exists())
        assertFalse(File(base, "photo-swap.marker").exists())
    }

    @Test
    fun crashMidSwapIsCompleted() = runTest {
        val (_, photo) = sampleData()
        // DB committed with these photos; "photos" was already moved to photos.old, photos.new not yet renamed.
        val photosNew = File(base, "photos.new").apply { mkdirs() }
        t.photoFiles.file(photo).copyTo(File(photosNew, "$photo.jpg"))
        t.photoFiles.dir.renameTo(File(base, "photos.old"))
        File(base, "photo-swap.marker").writeText(photo)

        backup.recoverPhotoSwap()

        assertTrue(t.photoFiles.file(photo).exists())
        assertFalse(File(base, "photos.old").exists())
        assertFalse(photosNew.exists())
    }

    @Test
    fun failedAutoBackupLeavesNoPartialFile() = runTest {
        val folder = object : BackupFolder {
            val files = mutableListOf<String>()
            override fun isAccessible() = true
            override fun list() = files.toList()
            override fun create(name: String): OutputStream {
                files += name
                return object : OutputStream() { override fun write(b: Int) = throw IOException("disk full") }
            }
            override fun delete(name: String) { files -= name }
        }
        val error = runCatching { AutoBackup(backup).run(folder, LocalDateTime.of(2026, 3, 1, 8, 0)) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(emptyList<String>(), folder.files)
    }

    private suspend fun assertRejected(reason: BackupException.Reason, file: File) {
        val projectsBefore = t.count("project")
        val error = runCatching { backup.prepareImport(file.inputStream()) }.exceptionOrNull()
        assertTrue("expected $reason, got $error", error is BackupException && error.reason == reason)
        assertEquals(projectsBefore, t.count("project"))
    }

    @Test
    fun rejectsBadZipsWithoutTouchingData() = runTest {
        sampleData()
        val good = exportBytes()
        val json = ZipInputStream(good.inputStream()).use { z -> generateSequence { z.nextEntry }.first { it.name == "backup.json" }; z.readBytes() }

        assertRejected(BackupException.Reason.NOT_A_BACKUP, File(base, "junk.zip").apply { writeText("not a zip at all") })
        assertRejected(BackupException.Reason.NOT_A_BACKUP, zipOf("other.txt" to "x".toByteArray()))
        // ZipOutputStream refuses duplicate names: write "backup.jsoX" and patch the name bytes (same length).
        val duplicate = zipOf("backup.json" to json, "backup.jsoX" to json).apply {
            writeBytes(String(readBytes(), Charsets.ISO_8859_1).replace("backup.jsoX", "backup.json").toByteArray(Charsets.ISO_8859_1))
        }
        assertRejected(BackupException.Reason.NOT_A_BACKUP, duplicate)
        val newer = json.decodeToString().replaceFirst("\"format\":${BackupRepository.FORMAT}", "\"format\":99").toByteArray()
        assertRejected(BackupException.Reason.NEWER_FORMAT, zipOf("backup.json" to newer))
    }

    @Test
    fun zipSlipEntriesAreIgnored() = runTest {
        sampleData()
        val json = ZipInputStream(exportBytes().inputStream()).use { z -> generateSequence { z.nextEntry }.first { it.name == "backup.json" }; z.readBytes() }
        val evil = zipOf("backup.json" to json, "../evil.jpg" to byteArrayOf(1), "photos/../../evil.jpg" to byteArrayOf(1), "photos/a/b.jpg" to byteArrayOf(1))

        backup.import(backup.prepareImport(evil.inputStream()))

        assertEquals(1, t.count("project"))
        val written = base.parentFile!!.walk().filter { it.name == "evil.jpg" || it.name == "b.jpg" }.toList()
        assertEquals(emptyList<File>(), written)
    }

    @Test
    fun interruptedSwapIsCompletedWhenImportCommitted() = runTest {
        val (_, photo) = sampleData()
        // Simulate a crash after the DB commit: imported photos still in photos.new, old folder in place.
        val photosNew = File(base, "photos.new").apply { mkdirs() }
        t.photoFiles.file(photo).copyTo(File(photosNew, "$photo.jpg"))
        t.photoFiles.delete(photo)
        File(base, "photo-swap.marker").writeText(photo)

        backup.recoverPhotoSwap()

        assertTrue(t.photoFiles.file(photo).exists())
        assertFalse(photosNew.exists())
        assertFalse(File(base, "photo-swap.marker").exists())
    }

    @Test
    fun interruptedSwapIsRolledBackWhenImportDidNotCommit() = runTest {
        val (_, photo) = sampleData()
        // Crash before the commit: photos.new holds another backup's photos, the DB still has the old data.
        val photosNew = File(base, "photos.new").apply { mkdirs() }
        File(photosNew, "someone-else.jpg").writeBytes(image)
        File(base, "photo-swap.marker").writeText("someone-else")

        backup.recoverPhotoSwap()

        assertTrue(t.photoFiles.file(photo).exists())
        assertFalse(photosNew.exists())
        assertEquals(1, t.photos.observe(PhotoOwner.PROJECT, with(t) { t.projects.observeAllOnce() }.single()).first().size)
    }

    private class FakeFolder(var accessible: Boolean = true, names: List<String> = emptyList()) : BackupFolder {
        val files = names.toMutableList()
        override fun isAccessible() = accessible
        override fun list() = files.toList()
        override fun create(name: String): OutputStream { files += name; return ByteArrayOutputStream() }
        override fun delete(name: String) { files -= name }
    }

    @Test
    fun autoBackupKeepsNewestFiveAndTouchesNothingElse() = runTest {
        val old = (1..6).map { "thimbly-auto-2026-01-0$it-120000.zip" }
        val folder = FakeFolder(names = old + listOf("holiday.jpg", "thimbly-backup-2026-01-01-120000.zip", "thimbly-auto-notes.txt"))

        AutoBackup(backup).run(folder, LocalDateTime.of(2026, 2, 1, 9, 30, 0))

        val autos = folder.files.filter { it.startsWith("thimbly-auto-2026") }.sorted()
        assertEquals(listOf("thimbly-auto-2026-01-03-120000.zip", "thimbly-auto-2026-01-04-120000.zip", "thimbly-auto-2026-01-05-120000.zip",
            "thimbly-auto-2026-01-06-120000.zip", "thimbly-auto-2026-02-01-093000.zip"), autos)
        assertTrue(folder.files.containsAll(listOf("holiday.jpg", "thimbly-backup-2026-01-01-120000.zip", "thimbly-auto-notes.txt")))
    }

    @Test
    fun autoBackupReportsUnavailableFolder() = runTest {
        val error = runCatching { AutoBackup(backup).run(FakeFolder(accessible = false), LocalDateTime.now()) }.exceptionOrNull()
        assertTrue(error is IOException && error.message == AutoBackup.ERROR_FOLDER)
    }
}
