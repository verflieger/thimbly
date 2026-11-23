package at.mtr.sewfar.data

import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PatternYardageEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import kotlinx.coroutines.flow.Flow

/** Pattern library (SPEC 4). */
class PatternRepository(db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.patternDao()

    fun observe(includeArchived: Boolean = false): Flow<List<PatternEntity>> = dao.observe(includeArchived)

    suspend fun get(id: String): PatternEntity? = dao.get(id)

    suspend fun create(name: String, designer: String = "", categoryId: String? = null): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(PatternEntity(id, name.trim(), designer.trim(), categoryId, createdAt = now, updatedAt = now))
        return id
    }

    /** Saves edited fields. */
    suspend fun update(pattern: PatternEntity) = dao.update(pattern.copy(name = pattern.name.trim(), updatedAt = time.nowMillis()))

    suspend fun setArchived(id: String, archived: Boolean) {
        val pattern = dao.get(id) ?: return
        val now = time.nowMillis()
        dao.update(pattern.copy(archivedAt = if (archived) now else null, updatedAt = now))
    }

    /** SPEC 7: only a pattern no live project uses can be deleted; its yardage rows and photo go with it. */
    suspend fun delete(id: String): DeleteResult {
        if (dao.isInUse(id)) return DeleteResult.InUse
        return deleter.delete("pattern", id)?.let(DeleteResult::Deleted) ?: DeleteResult.InUse
    }

    suspend fun isInUse(id: String): Boolean = dao.isInUse(id)

    // ---- Yardage table (SPEC 4.2) ----

    fun observeYardage(patternId: String): Flow<List<PatternYardageEntity>> = dao.observeYardage(patternId)

    suspend fun addYardage(patternId: String, size: String, amountCm: Int, fabricWidthCm: Int? = null, note: String = ""): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertYardage(
            PatternYardageEntity(
                id, patternId, size.trim(), amountCm, fabricWidthCm, note, dao.nextYardageOrder(patternId),
                createdAt = now, updatedAt = now,
            ),
        )
        return id
    }

    suspend fun updateYardage(row: PatternYardageEntity) = dao.updateYardage(row.copy(updatedAt = time.nowMillis()))

    suspend fun deleteYardage(id: String): DeletionToken? = deleter.delete("pattern_yardage", id)

    suspend fun undo(token: DeletionToken) = deleter.undo(token)
}
