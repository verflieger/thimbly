package at.mtr.sewfar.data

import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.PhotoOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    fun finePhotosSettingReachesTheStoredFile() = runTest {
        val fine = PhotoRepository(t.db, t.deleter, t.photoFiles, t.time, t.projects) { at.mtr.sewfar.data.photos.ImageProcessor.FineEdge }
        val id = fine.add(PhotoOwner.PROJECT, t.projects.create("Coat")) { TestDb.jpeg(5000, 2500).inputStream() }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(t.photoFiles.file(id).path, bounds)
        assertEquals(4096, bounds.outWidth)
    }

    @Test
    fun projectPhotosAreStoredInOrderAndTouchProject() = runTest {
        val projectId = t.projects.create("Dress")
        val before = t.projects.get(projectId)!!.lastActivityAt
        t.time.advance()

        val a = addPhoto(projectId)
        val b = addPhoto(projectId)

        assertEquals(listOf(b, a), photosOf(PhotoOwner.PROJECT, projectId).map { it.id })
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

        val before = t.projects.get(projectId)!!.lastActivityAt
        t.time.now += 1_000
        t.photos.setCover(PhotoOwner.PROJECT, projectId, first)
        assertEquals(first, cover(projectId))
        assertTrue("pinning counts as activity", t.projects.get(projectId)!!.lastActivityAt > before)

        t.photos.delete(first)
        assertEquals("pinned photo deleted: automatic again", newest, cover(projectId))
    }

    @Test
    fun patternCoverIsOldestPhotoUntilPinned() = runTest {
        val raglan = t.patterns.create("Raglan")
        val dress = t.patterns.create("Dress")
        val add = { id: String -> suspend { t.photos.add(PhotoOwner.PATTERN, id) { image.inputStream() } } }
        val envelope = add(raglan)()
        val finished = add(raglan)()
        val dressPhoto = add(dress)()
        val noPhoto = t.patterns.create("Skirt")
        val covers = suspend { patternCovers(t.patterns.observeAll().first(), t.photos.observeAllOfType(PhotoOwner.PATTERN).first()) }
        assertEquals(mapOf(raglan to envelope, dress to dressPhoto), covers())
        assertEquals(null, covers()[noPhoto])

        t.photos.setCover(PhotoOwner.PATTERN, raglan, finished)
        assertEquals(finished, covers()[raglan])

        t.photos.delete(finished)
        assertEquals("pinned photo deleted: oldest again", envelope, covers()[raglan])
    }

    @Test
    fun patternGalleryShowsLivePhotosOfLinkedProjectsNewestFirst() = runTest {
        val pattern = t.patterns.create("Raglan")
        val dress = t.projects.create("Dress")
        val shirt = t.projects.create("Shirt")
        val unlinked = t.projects.create("Coat")
        t.projects.addPattern(dress, pattern)
        t.projects.addPattern(shirt, pattern)
        val dressPhoto = addPhoto(dress)
        t.time.advance()
        val shirtPhoto = addPhoto(shirt)
        t.time.advance()
        val deletedPhoto = addPhoto(shirt)
        t.photos.delete(deletedPhoto)
        addPhoto(unlinked)
        t.photos.add(PhotoOwner.PATTERN, pattern) { image.inputStream() } // the pattern's own photo is not a project photo

        val gallery = { runBlocking { t.patterns.observeProjectPhotos(pattern).first() } }
        assertEquals(listOf(shirtPhoto to "Shirt", dressPhoto to "Dress"), gallery().map { it.photoId to it.projectName })

        t.projects.delete(shirt)
        assertEquals(listOf(dressPhoto), gallery().map { it.photoId })
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
