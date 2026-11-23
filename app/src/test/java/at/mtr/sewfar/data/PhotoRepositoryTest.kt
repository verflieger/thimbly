package at.mtr.sewfar.data

import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.PhotoTag
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoRepositoryTest {
    private lateinit var t: TestDb
    private val image = TestDb.jpeg(120, 80)

    @Before fun setUp() = runTest {
        t = TestDb()
        // Real time: orphan cleanup compares against file modification times.
        t.time.now = System.currentTimeMillis()
        t.seed()
    }

    @After fun tearDown() = t.close()

    private suspend fun addPhoto(projectId: String, tag: PhotoTag) = t.photos.addProjectPhoto(projectId, tag) { image.inputStream() }

    @Test
    fun projectPhotosAreStoredInOrderAndTouchProject() = runTest {
        val projectId = t.projects.create("Dress")
        val before = t.projects.get(projectId)!!.lastActivityAt
        t.time.advance()

        val a = addPhoto(projectId, PhotoTag.INSPIRATION)
        val b = addPhoto(projectId, PhotoTag.PROGRESS)

        assertEquals(listOf(a, b), t.photos.forOwner(PhotoOwner.PROJECT, projectId).map { it.id })
        assertTrue(t.photoFiles.file(a).exists())
        assertTrue(t.projects.get(projectId)!!.lastActivityAt > before)
    }

    @Test
    fun coverIsAutomaticUntilPinned() = runTest {
        val projectId = t.projects.create("Dress")
        val inspiration = addPhoto(projectId, PhotoTag.INSPIRATION)
        assertEquals(inspiration, t.photos.coverOf(projectId))

        val finished = addPhoto(projectId, PhotoTag.FINISHED)
        assertEquals(finished, t.photos.coverOf(projectId))

        t.photos.setCover(projectId, inspiration)
        assertEquals(inspiration, t.photos.coverOf(projectId))

        t.photos.delete(inspiration)
        assertEquals("pinned photo deleted: automatic again", finished, t.photos.coverOf(projectId))
    }

    @Test
    fun entityPhotoIsReplacedNotEdited() = runTest {
        val patternId = t.patterns.create("Raglan")
        val first = t.photos.setEntityPhoto(PhotoOwner.PATTERN, patternId) { image.inputStream() }
        val second = t.photos.setEntityPhoto(PhotoOwner.PATTERN, patternId) { image.inputStream() }

        assertEquals(listOf(second), t.photos.forOwner(PhotoOwner.PATTERN, patternId).map { it.id })
        assertNotNull(t.deletedAt("photo", first))
    }

    @Test
    fun filesOfDeletedPhotosGoOnlyAfterUndoWindow() = runTest {
        val projectId = t.projects.create("Dress")
        val photo = addPhoto(projectId, PhotoTag.PROGRESS)
        val token = t.photos.delete(photo)!!

        t.photos.purgeFiles()
        assertTrue("still inside the Undo window", t.photoFiles.file(photo).exists())
        t.photos.undo(token)
        assertEquals(listOf(photo), t.photos.forOwner(PhotoOwner.PROJECT, projectId).map { it.id })

        t.photos.delete(photo)
        t.time.advance(PhotoRepository.UndoWindowMillis + 1_000)
        t.photos.purgeFiles()
        assertFalse(t.photoFiles.file(photo).exists())
    }

    @Test
    fun cascadeDeletedPhotoFilesArePurgedToo() = runTest {
        val projectId = t.projects.create("Dress")
        val photo = addPhoto(projectId, PhotoTag.FINISHED)
        t.projects.delete(projectId)

        t.time.advance(PhotoRepository.UndoWindowMillis + 1_000)
        t.photos.purgeFiles()

        assertFalse(t.photoFiles.file(photo).exists())
    }

    @Test
    fun oldOrphanFilesArePurgedFreshOnesKept() = runTest {
        t.photoFiles.write("orphan-old") { image.inputStream() }
        t.photoFiles.file("orphan-old").setLastModified(t.time.now - PhotoRepository.UndoWindowMillis - 10_000)
        t.photoFiles.write("orphan-fresh") { image.inputStream() }

        t.photos.purgeFiles()

        assertFalse(t.photoFiles.file("orphan-old").exists())
        assertTrue("may belong to an import in progress", t.photoFiles.file("orphan-fresh").exists())
    }

    @Test
    fun failedImportStoresNothing() = runTest {
        val projectId = t.projects.create("Dress")
        val result = runCatching { t.photos.addProjectPhoto(projectId, PhotoTag.PROGRESS) { "nope".byteInputStream() } }

        assertTrue(result.isFailure)
        assertEquals(0, t.photos.forOwner(PhotoOwner.PROJECT, projectId).size)
        assertEquals(emptyMap<String, Long>(), t.photoFiles.stored())
    }

    @Test
    fun logEntryPhotoBelongsToEntryAndGoesWithIt() = runTest {
        val projectId = t.projects.create("Dress")
        val entry = t.projects.addNote(projectId, "hemmed")
        val photo = t.photos.addProjectPhoto(projectId, PhotoTag.PROGRESS, logEntryId = entry) { image.inputStream() }

        val row = t.photos.forOwner(PhotoOwner.PROJECT, projectId).single()
        assertEquals(entry, row.logEntryId)
        assertEquals(PhotoTag.PROGRESS, row.tag)

        t.projects.deleteLogEntry(entry)
        assertEquals(0, t.photos.forOwner(PhotoOwner.PROJECT, projectId).size)
        assertNotNull(t.deletedAt("photo", photo))
    }
}
