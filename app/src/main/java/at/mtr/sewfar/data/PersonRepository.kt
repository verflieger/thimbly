package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.MeasurementSessionEntity
import at.mtr.sewfar.data.db.MeasurementValueEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.AvatarColors
import at.mtr.sewfar.domain.Ids
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Persons and their measurements (SPEC 5). */
class PersonRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.personDao()

    fun observe(includeArchived: Boolean = false): Flow<List<PersonEntity>> = dao.observe(includeArchived)

    suspend fun get(id: String): PersonEntity? = dao.get(id)

    suspend fun create(name: String, notes: String = "", avatarColor: Int? = null): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(
            PersonEntity(
                id = id, name = name.trim(), avatarColor = avatarColor ?: AvatarColors.forId(id), notes = notes,
                createdAt = now, updatedAt = now,
            ),
        )
        return id
    }

    /** Saves edited fields (name, colour, notes). */
    suspend fun update(person: PersonEntity) = dao.update(person.copy(name = person.name.trim(), updatedAt = time.nowMillis()))

    suspend fun setArchived(id: String, archived: Boolean) {
        val person = dao.get(id) ?: return
        val now = time.nowMillis()
        dao.update(person.copy(archivedAt = if (archived) now else null, updatedAt = now))
    }

    /** SPEC 7: only a person no live project refers to can be deleted; measurements go with it. */
    suspend fun delete(id: String): DeleteResult {
        if (dao.isInUse(id)) return DeleteResult.InUse
        return deleter.delete("person", id)?.let(DeleteResult::Deleted) ?: DeleteResult.InUse
    }

    // ---- Measurements (SPEC 5.2) ----

    fun observeSessions(personId: String): Flow<List<MeasurementSessionEntity>> = dao.observeSessions(personId)

    suspend fun values(sessionId: String): List<MeasurementValueEntity> = dao.values(sessionId)

    /** Creates a session with values in millimetres per measurement type ID. */
    suspend fun createSession(personId: String, date: LocalDate, valuesMm: Map<String, Int>, note: String = ""): String =
        db.withTransaction {
            val id = Ids.random()
            val now = time.nowMillis()
            dao.insertSession(MeasurementSessionEntity(id, personId, date, note, createdAt = now, updatedAt = now))
            valuesMm.forEach { (typeId, mm) -> setValue(id, typeId, mm) }
            id
        }

    suspend fun updateSession(session: MeasurementSessionEntity) = dao.updateSession(session.copy(updatedAt = time.nowMillis()))

    /** Sets or (with null) removes one value. Re-entering a removed value revives its row (SPEC 1). */
    suspend fun setValue(sessionId: String, typeId: String, valueMm: Int?) = db.withTransaction {
        val now = time.nowMillis()
        val existing = dao.getValueAny(sessionId, typeId)
        when {
            valueMm == null -> if (existing != null && existing.deletedAt == null) {
                dao.updateValue(existing.copy(deletedAt = now, updatedAt = now))
            }
            existing == null -> dao.insertValue(
                MeasurementValueEntity(Ids.random(), sessionId, typeId, valueMm, createdAt = now, updatedAt = now),
            )
            existing.valueMm != valueMm || existing.deletedAt != null ->
                dao.updateValue(existing.copy(valueMm = valueMm, deletedAt = null, updatedAt = now))
        }
    }

    suspend fun deleteSession(sessionId: String): DeletionToken? = deleter.delete("measurement_session", sessionId)

    suspend fun undo(token: DeletionToken) = deleter.undo(token)
}
