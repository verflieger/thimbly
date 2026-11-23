package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.photos.PhotoFiles
import at.mtr.sewfar.domain.CoverCandidate
import at.mtr.sewfar.domain.CoverRules
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.PhotoTag
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Photos of projects, patterns, persons and machines (SPEC 3.6). The image is processed and stored first, then the
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

    suspend fun forOwner(ownerType: PhotoOwner, ownerId: String): List<PhotoEntity> = dao.forOwner(ownerType, ownerId)

    /** Adds a project photo (tag required); [logEntryId] attaches it to a progress log entry (SPEC 3.4). */
    suspend fun addProjectPhoto(projectId: String, tag: PhotoTag, logEntryId: String? = null, open: () -> InputStream): String {
        val id = store(open)
        db.withTransaction {
            val now = time.nowMillis()
            dao.insert(
                PhotoEntity(
                    id, PhotoOwner.PROJECT, projectId, logEntryId, tag, dao.nextOrder(PhotoOwner.PROJECT, projectId),
                    createdAt = now, updatedAt = now,
                ),
            )
            db.projectDao().touch(projectId, now)
        }
        return id
    }

    /**
     * Sets the single photo of a pattern, person or machine. Photos are immutable (SPEC 1), so a replacement is a
     * new row and the old one is deleted.
     */
    suspend fun setEntityPhoto(ownerType: PhotoOwner, ownerId: String, open: () -> InputStream): String {
        require(ownerType != PhotoOwner.PROJECT) { "Project photos use addProjectPhoto" }
        val id = store(open)
        val replaced = db.withTransaction {
            val now = time.nowMillis()
            val old = dao.forOwner(ownerType, ownerId)
            old.forEach { deleter.delete("photo", it.id) }
            dao.insert(PhotoEntity(id, ownerType, ownerId, sortOrder = 0, createdAt = now, updatedAt = now))
            old.isNotEmpty()
        }
        if (replaced) scheduleCleanup()
        return id
    }

    /** Changes a project photo's tag (metadata only; the file stays). */
    suspend fun setTag(photoId: String, tag: PhotoTag) = db.withTransaction {
        val photo = dao.get(photoId) ?: return@withTransaction
        require(photo.ownerType == PhotoOwner.PROJECT) { "Only project photos have tags" }
        val now = time.nowMillis()
        dao.update(photo.copy(tag = tag, updatedAt = now))
        db.projectDao().touch(photo.ownerId, now)
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

    /** Pins a cover (SPEC 3.6); null returns to the automatic cover. */
    suspend fun setCover(projectId: String, photoId: String?) = db.withTransaction {
        val project = db.projectDao().get(projectId) ?: return@withTransaction
        val now = time.nowMillis()
        db.projectDao().update(project.copy(coverPhotoId = photoId, updatedAt = now, lastActivityAt = now))
    }

    /** The project's cover photo ID (pinned, else automatic), or null if it has no photos. */
    suspend fun coverOf(projectId: String): String? {
        val project = db.projectDao().get(projectId) ?: return null
        val photos = dao.forOwner(PhotoOwner.PROJECT, projectId).map { CoverCandidate(it.id, it.tag, it.sortOrder) }
        return CoverRules.cover(photos, project.coverPhotoId)
    }

    /**
     * Removes files of photos deleted longer than [UndoWindowMillis] ago (including photos deleted by a cascade) and
     * orphan files without any row (SPEC 7). Called on start and after deletions.
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
