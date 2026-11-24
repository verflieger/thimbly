package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.ProjectRef
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import kotlinx.coroutines.flow.Flow

/** Patterns (SPEC 4.1). Deleting one also removes it from its projects (with undo). */
class PatternRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.patternDao()

    fun observeAll(): Flow<List<PatternEntity>> = dao.observeAll()
    fun observe(id: String): Flow<PatternEntity?> = dao.observeOne(id)
    fun observeProjects(id: String): Flow<List<ProjectRef>> = dao.observeProjects(id)

    suspend fun create(name: String = ""): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(PatternEntity(id, name.trim(), createdAt = now, updatedAt = now))
        return id
    }

    suspend fun edit(id: String, change: (PatternEntity) -> PatternEntity) = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction
        val next = change(current)
        if (next != current) dao.update(next.copy(updatedAt = time.nowMillis()))
    }

    suspend fun delete(id: String): DeletionToken? = deleter.delete("pattern", id)

    suspend fun undo(token: DeletionToken) = deleter.undo(token)
}
