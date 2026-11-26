package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.photos.ImageProcessor
import at.mtr.sewfar.data.photos.PhotoFiles
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.PhotoOwner
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Photos of projects and patterns (SPEC 3.5). The image is processed and stored first, then the
 * row is inserted; a crash in between leaves only an orphan file, which [purgeFiles] removes. A deleted photo's
 * row is removed by the purge after the Undo window (SoftDeleter.purge), which makes its file an orphan too.
 */
class PhotoRepository(
    private val db: SewFarDatabase,
    private val deleter: SoftDeleter,
    private val files: PhotoFiles,
    private val time: TimeSource,
    /** Long edge for new photos (SPEC 3.5): normal or fine, from the settings. */
    private val maxEdge: suspend () -> Int = { ImageProcessor.MaxEdge },
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

    /** Deletes a photo; row and file go once the Undo window has passed. */
    suspend fun delete(photoId: String): DeletionToken? {
        val photo = dao.get(photoId) ?: return null
        return db.withTransaction {
            deleter.delete("photo", photoId).also {
                if (photo.ownerType == PhotoOwner.PROJECT) db.projectDao().touch(photo.ownerId, time.nowMillis())
            }
        }
    }


    /** Pins the cover of a project or pattern (SPEC 3.5); null returns to the automatic cover. */
    suspend fun setCover(ownerType: PhotoOwner, ownerId: String, photoId: String?) = db.withTransaction {
        val now = time.nowMillis()
        when (ownerType) {
            PhotoOwner.PROJECT -> db.projectDao().get(ownerId)
                ?.let { db.projectDao().update(it.copy(coverPhotoId = photoId, updatedAt = now, lastActivityAt = now)) }
            PhotoOwner.PATTERN -> db.patternDao().get(ownerId)
                ?.let { db.patternDao().update(it.copy(coverPhotoId = photoId, updatedAt = now)) }
        }
    }

    /**
     * Removes photo files without a row (SPEC 3.5): photos purged after the Undo window, and files a crash left before
     * their row was inserted. Files younger than the Undo window stay, as their row may still be on its way.
     */
    suspend fun purgeFiles() = withContext(Dispatchers.IO) {
        val cutoff = time.nowMillis() - SoftDeleter.UndoWindowMillis
        val known = dao.allIds().toSet()
        files.stored().forEach { (id, modified) -> if (id !in known && modified < cutoff) files.delete(id) }
    }

    private suspend fun store(open: () -> InputStream): String {
        val edge = maxEdge()
        return withContext(Dispatchers.IO) { Ids.random().also { id -> files.write(id, edge, open) } }
    }
}

/** The cover of a project (SPEC 3.5): the pinned photo if it still exists, else the newest one. */
fun coverOf(photos: List<PhotoEntity>, pinnedId: String?): String? =
    photos.firstOrNull { it.id == pinnedId }?.id ?: photos.maxByOrNull { it.sortOrder }?.id

/**
 * Pattern ID → cover photo ID (SPEC 3.5): the pinned photo if it still exists, else the oldest one (usually the
 * envelope, taken first). Patterns without photos have no entry.
 */
fun patternCovers(patterns: List<PatternEntity>, photos: List<PhotoEntity>): Map<String, String> {
    val byPattern = photos.groupBy { it.ownerId }
    return patterns.mapNotNull { p ->
        val own = byPattern[p.id] ?: return@mapNotNull null
        p.id to (own.firstOrNull { it.id == p.coverPhotoId } ?: own.minBy { it.sortOrder }).id
    }.toMap()
}
