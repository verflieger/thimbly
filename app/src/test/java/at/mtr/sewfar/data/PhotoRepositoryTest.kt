package at.mtr.sewfar.data

import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.PhotoOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }

    @After fun tearDown() = t.close()

    private suspend fun addPhoto(projectId: String) = t.photos.add(PhotoOwner.PROJECT, projectId) { image.inputStream() }

    private suspend fun photosOf(owner: PhotoOwner, id: String) = t.photos.observe(owner, id).first()

    private suspend fun cover(projectId: String) = coverOf(photosOf(PhotoOwner.PROJECT, projectId), t.projects.get(projectId)!!.coverPhotoId)

    @Test
    fun projectPhotosAreStoredInOrderAndTouchProject() = runTest {
        val projectId = t.projects.create("Dress")
        val before = t.projects.get(projectId)!!.lastActivityAt
        t.time.advance()

        val a = addPhoto(projectId)
        val b = addPhoto(projectId)

        assertEquals(listOf(a, b), photosOf(PhotoOwner.PROJECT, projectId).map { it.id })
        assertTrue(t.photoFiles.file(a).exists())
        assertTrue(t.projects.get(projectId)!!.lastActivityAt > before)
    }

    @Test
    fun coverIsNewestPhotoUntilPinned() = runTest {
        val projectId = t.projects.create("Dress")
        val first = addPhoto(projectId)
        assertEquals(first, cover(projectId))
        val newest = addPhoto(projectId)
        assertEquals(newest, cover(projectId))

        t.photos.setCover(projectId, first)
        assertEquals(first, cover(projectId))

        t.photos.delete(first)
        assertEquals("pinned photo deleted: automatic again", newest, cover(projectId))
    }

    @Test
    fun filesOfDeletedPhotosGoOnlyAfterUndoWindow() = runTest {
        val projectId = t.projects.create("Dress")
        val photo = addPhoto(projectId)
        val token = t.photos.delete(photo)!!

        t.purge()
        assertTrue("still inside the Undo window", t.photoFiles.file(photo).exists())
        t.deleter.undo(token)
        assertEquals(listOf(photo), photosOf(PhotoOwner.PROJECT, projectId).map { it.id })

        t.photos.delete(photo)
        t.time.advance(SoftDeleter.UndoWindowMillis + 1_000)
        t.purge()
        assertFalse(t.photoFiles.file(photo).exists())
    }

    @Test
    fun cascadeDeletedPhotoFilesArePurgedToo() = runTest {
        val projectId = t.projects.create("Dress")
        val photo = addPhoto(projectId)
        t.projects.delete(projectId)

        t.time.advance(SoftDeleter.UndoWindowMillis + 1_000)
        t.purge()

        assertFalse(t.photoFiles.file(photo).exists())
    }

    @Test
    fun oldOrphanFilesArePurgedFreshOnesKept() = runTest {
        t.photoFiles.write("orphan-old") { image.inputStream() }
        t.photoFiles.file("orphan-old").setLastModified(t.time.now - SoftDeleter.UndoWindowMillis - 10_000)
        t.photoFiles.write("orphan-fresh") { image.inputStream() }

        t.purge()

        assertFalse(t.photoFiles.file("orphan-old").exists())
        assertTrue("may belong to an import in progress", t.photoFiles.file("orphan-fresh").exists())
    }

    @Test
    fun failedImportStoresNothing() = runTest {
        val projectId = t.projects.create("Dress")
        val result = runCatching { t.photos.add(PhotoOwner.PROJECT, projectId) { "nope".byteInputStream() } }

        assertTrue(result.isFailure)
        assertEquals(0, photosOf(PhotoOwner.PROJECT, projectId).size)
        assertEquals(emptyMap<String, Long>(), t.photoFiles.stored())
    }
}
