package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.photos.PhotoFiles
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.PhotoOwner
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Photos of projects and patterns (SPEC 3.5). The image is processed and stored first, then the
 * row is inserted; a crash in between leaves only an orphan file, which [purgeFiles] removes.
 *
 * @param cleanupScope runs the delayed file cleanup after a deletion (outlives screens).
 */
class PhotoRepository(
    private val db: SewFarDatabase,
    private val deleter: SoftDeleter,
    private val files: PhotoFiles,
    private val time: TimeSource,
    private val cleanupScope: CoroutineScope,
) {
    private val dao = db.photoDao()

    fun observe(ownerType: PhotoOwner, ownerId: String): Flow<List<PhotoEntity>> = dao.observeForOwner(ownerType, ownerId)

    fun observeAllOfType(ownerType: PhotoOwner): Flow<List<PhotoEntity>> = dao.observeAllOfType(ownerType)

    /** Adds a photo to a project or pattern (SPEC 3.5). */
    suspend fun add(ownerType: PhotoOwner, ownerId: String, open: () -> InputStream): String {
        val id = store(open)
        db.withTransaction {
            val now = time.nowMillis()
            dao.insert(PhotoEntity(id, ownerType, ownerId, dao.nextOrder(ownerType, ownerId), createdAt = now, updatedAt = now))
            if (ownerType == PhotoOwner.PROJECT) db.projectDao().touch(ownerId, now)
        }
        return id
    }

    /** Deletes a photo; its file goes once the Undo window has passed. */
    suspend fun delete(photoId: String): DeletionToken? {
        val photo = dao.get(photoId) ?: return null
        val token = db.withTransaction {
            deleter.delete("photo", photoId).also {
                if (photo.ownerType == PhotoOwner.PROJECT) db.projectDao().touch(photo.ownerId, time.nowMillis())
            }
        }
        scheduleCleanup()
        return token
    }

    suspend fun undo(token: DeletionToken) = deleter.undo(token)

    /** Pins a cover (SPEC 3.5); null returns to the automatic cover. */
    suspend fun setCover(projectId: String, photoId: String?) = db.withTransaction {
        val project = db.projectDao().get(projectId) ?: return@withTransaction
        val now = time.nowMillis()
        db.projectDao().update(project.copy(coverPhotoId = photoId, updatedAt = now, lastActivityAt = now))
    }

    /**
     * Removes files of photos deleted longer than [UndoWindowMillis] ago (including photos deleted by a cascade) and
     * orphan files without any row (SPEC 6). Called on start and after deletions.
     */
    suspend fun purgeFiles() = withContext(Dispatchers.IO) {
        val cutoff = time.nowMillis() - UndoWindowMillis
        dao.deletedBefore(cutoff).forEach(files::delete)
        val known = dao.allIds().toSet()
        files.stored().forEach { (id, modified) -> if (id !in known && modified < cutoff) files.delete(id) }
    }

    private fun scheduleCleanup() {
        cleanupScope.launch {
            delay(UndoWindowMillis + 1_000)
            purgeFiles()
        }
    }

    private suspend fun store(open: () -> InputStream): String = withContext(Dispatchers.IO) {
        Ids.random().also { id -> files.write(id, open) }
    }

    companion object {
        /** Longer than any Undo snackbar, so a restored photo still has its file. */
        const val UndoWindowMillis = 60_000L
    }
}

/** The cover of a project (SPEC 3.5): the pinned photo if it still exists, else the newest one. */
fun coverOf(photos: List<PhotoEntity>, pinnedId: String?): String? =
    photos.firstOrNull { it.id == pinnedId }?.id ?: photos.maxByOrNull { it.sortOrder }?.id
