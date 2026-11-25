package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.ProjectRef
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import kotlinx.coroutines.flow.Flow

/** People projects are sewn for (SPEC 4.2). A deleted person no longer shows on their projects. */
class PersonRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.personDao()

    fun observeAll(): Flow<List<PersonEntity>> = dao.observeAll()
    fun observe(id: String): Flow<PersonEntity?> = dao.observeOne(id)
    fun observeProjects(id: String): Flow<List<ProjectRef>> = dao.observeProjects(id)

    suspend fun create(name: String = ""): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(PersonEntity(id, name.trim(), createdAt = now, updatedAt = now))
        return id
    }

    suspend fun edit(id: String, change: (PersonEntity) -> PersonEntity) = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction
        val next = change(current)
        if (next != current) dao.update(next.copy(updatedAt = time.nowMillis()))
    }

    suspend fun delete(id: String): DeletionToken? = deleter.delete("person", id)

}
