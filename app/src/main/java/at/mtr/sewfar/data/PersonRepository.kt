package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.MeasurementEntity
import at.mtr.sewfar.data.db.MeasurementValueEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.ProjectRef
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.Measurements
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    // ---- Measurements (SPEC 4.2) ----

    /** The newest measuring of the person on or before [onOrBefore] (null = the newest); null if there is none. */
    fun observeMeasurements(personId: String, onOrBefore: LocalDate? = null): Flow<Measurements?> =
        dao.observeMeasurement(personId, onOrBefore).map { rows ->
            rows.firstOrNull()?.let { first ->
                Measurements(first.measuredOn, rows.mapNotNull { r -> r.key?.let { k -> r.valueCm?.let { k to it } } }.toMap())
            }
        }

    /**
     * Stores the person's current measurements ([valuesCm] by key; missing = none). Nothing happens if they equal the
     * newest measuring. A measuring from [on] (today) is corrected in place, otherwise a new one is added and the old
     * ones stay for projects finished earlier.
     */
    suspend fun saveMeasurements(personId: String, valuesCm: Map<String, Double>, on: LocalDate = time.today()) = db.withTransaction {
        val now = time.nowMillis()
        val newest = dao.newestMeasurement(personId)
        val old = newest?.let { m -> dao.measurementValuesAny(m.id).filter { it.deletedAt == null }.associate { it.key to it.valueCm } }.orEmpty()
        if (old == valuesCm) return@withTransaction
        if (newest != null && newest.measuredOn == on) {
            val rows = dao.measurementValuesAny(newest.id).associateBy { it.key }
            for (key in rows.keys + valuesCm.keys) {
                val row = rows[key]
                val value = valuesCm[key]
                when {
                    row == null -> dao.insertMeasurementValue(MeasurementValueEntity(Ids.random(), newest.id, key, value!!, createdAt = now, updatedAt = now))
                    value == null -> if (row.deletedAt == null) dao.updateMeasurementValue(row.copy(deletedAt = now, updatedAt = now))
                    row.valueCm != value || row.deletedAt != null -> dao.updateMeasurementValue(row.copy(valueCm = value, deletedAt = null, updatedAt = now))
                }
            }
            dao.updateMeasurement(newest.copy(updatedAt = now))
        } else {
            val id = Ids.random()
            dao.insertMeasurement(MeasurementEntity(id, personId, on, createdAt = now, updatedAt = now))
            valuesCm.forEach { (key, value) -> dao.insertMeasurementValue(MeasurementValueEntity(Ids.random(), id, key, value, createdAt = now, updatedAt = now)) }
        }
    }
}
