package at.mtr.sewfar.data

import at.mtr.sewfar.data.backup.AutoBackup
import at.mtr.sewfar.data.backup.BackupException
import at.mtr.sewfar.data.backup.BackupFolder
import at.mtr.sewfar.data.backup.BackupRepository
import at.mtr.sewfar.data.seed.Seeder
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.PhotoTag
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
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

/** SPEC 9 and the backup tests of SPEC 12. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupTest {
    private lateinit var t: TestDb
    private lateinit var backup: BackupRepository
    private lateinit var base: File
    private val image = TestDb.jpeg(40, 30)

    @Before fun setUp() = runTest {
        t = TestDb()
        t.seed()
        base = t.photoFiles.dir.parentFile!!.apply { mkdirs() }
        val cache = File(base, "cache").apply { mkdirs() }
        backup = BackupRepository(t.db, t.photoFiles, base, cache, t.time, "test", afterImport = { Seeder(t.db, t.time).seed() })
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
        val height = t.lists.entries(ListKind.MEASUREMENT_TYPE).first().id
        t.persons.createSession(person, t.time.day, mapOf(height to 1040))
        val pattern = t.patterns.create("Raglan")
        val project = t.projects.create("Shirt", personId = person, patternIds = listOf(pattern))
        val gone = t.projects.addFabric(project, "Old fabric")
        t.projects.removeFabric(gone) // tombstones are part of the backup
        val photo = t.photos.addProjectPhoto(project, PhotoTag.FINISHED) { image.inputStream() }
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
        assertEquals(1, t.count("project_fabric", "projectId = '$project'", live = false) - t.count("project_fabric", "projectId = '$project'"))
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
        assertEquals(listOf(photo), t.photos.forOwner(PhotoOwner.PROJECT, project).map { it.id })
        assertFalse(t.photoFiles.file(photo).exists())
    }

    @Test
    fun formatOneFixtureImports() = runTest {
        val fixture = javaClass.classLoader!!.getResourceAsStream("backup/format1.zip")!!
        backup.import(backup.prepareImport(fixture))
        val project = t.projects.observeAll().first().single()
        assertEquals("Fixture shirt", project.name)
        assertEquals("Fixture pattern", t.patterns.get(t.projects.patterns(project.id).single().patternId)?.name)
        assertEquals("Fixture person", t.persons.get(project.personId!!)?.name)
        assertEquals(1, t.projects.log(project.id).size)
        val photos = t.photos.forOwner(PhotoOwner.PROJECT, project.id)
        assertEquals(2, photos.size)
        assertTrue(photos.all { t.photoFiles.file(it.id).exists() })
        assertEquals(1, photos.count { it.logEntryId != null })
        // Every table of format 1 has rows in the fixture; spot-check what the core tables don't cover.
        assertEquals(1, t.count("measurement_value"))
        assertEquals(1, t.count("pattern_yardage"))
        assertEquals(1, t.count("project_fabric"))
        assertEquals(1, t.count("project_fabric", live = false) - 1)
        assertEquals(1, t.projects.lessons(project.id).size)
        assertEquals(1, t.count("machine"))
        assertEquals(10, t.count("machine_field"))
        assertEquals("1.3", SetupRepository(t.db, t.deleter, t.time).observeAll().first().single().let { setup ->
            SetupRepository(t.db, t.deleter, t.time).values(setup.id).values.single()
        })
        val use = SetupRepository(t.db, t.deleter, t.time).observeUses(project.id).first().single()
        assertEquals("1.3", at.mtr.sewfar.domain.Snapshots.decode(use.snapshot).first { it.value.isNotEmpty() }.value)
        assertEquals(1, t.photos.forOwner(PhotoOwner.PATTERN, project.let { t.projects.patterns(it.id).single().patternId }).size)
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
        assertRejected(BackupException.Reason.UNSAFE_PATH, zipOf("backup.json" to json, "../evil.jpg" to byteArrayOf(1)))
        assertRejected(BackupException.Reason.UNSAFE_PATH, zipOf("backup.json" to json, "photos/../../evil.jpg" to byteArrayOf(1)))
        val newer = json.decodeToString().replaceFirst("\"format\":1", "\"format\":99").toByteArray()
        assertRejected(BackupException.Reason.NEWER_FORMAT, zipOf("backup.json" to newer))
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
        assertEquals(1, t.photos.forOwner(PhotoOwner.PROJECT, with(t) { t.projects.observeAllOnce() }.single()).size)
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
        val old = (1..6).map { "sewfar-auto-2026-01-0$it-120000.zip" }
        val folder = FakeFolder(names = old + listOf("holiday.jpg", "sewfar-backup-2026-01-01-120000.zip", "sewfar-auto-notes.txt"))

        AutoBackup(backup).run(folder, LocalDateTime.of(2026, 2, 1, 9, 30, 0))

        val autos = folder.files.filter { it.startsWith("sewfar-auto-2026") }.sorted()
        assertEquals(listOf("sewfar-auto-2026-01-03-120000.zip", "sewfar-auto-2026-01-04-120000.zip", "sewfar-auto-2026-01-05-120000.zip",
            "sewfar-auto-2026-01-06-120000.zip", "sewfar-auto-2026-02-01-093000.zip"), autos)
        assertTrue(folder.files.containsAll(listOf("holiday.jpg", "sewfar-backup-2026-01-01-120000.zip", "sewfar-auto-notes.txt")))
    }

    @Test
    fun autoBackupReportsUnavailableFolder() = runTest {
        val error = runCatching { AutoBackup(backup).run(FakeFolder(accessible = false), LocalDateTime.now()) }.exceptionOrNull()
        assertTrue(error is IOException && error.message == AutoBackup.FOLDER_UNAVAILABLE)
    }
}
