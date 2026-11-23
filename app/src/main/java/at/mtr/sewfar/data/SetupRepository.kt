package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.SetupEntity
import at.mtr.sewfar.data.db.SetupRow
import at.mtr.sewfar.data.db.SetupUseEntity
import at.mtr.sewfar.data.db.SetupUseRow
import at.mtr.sewfar.data.db.SetupValueEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.SnapshotField
import at.mtr.sewfar.domain.Snapshots
import kotlinx.coroutines.flow.Flow

/** Name of a field as shown to the user (built-in translation or custom name), supplied by the UI. */
typealias FieldLabel = (MachineFieldEntity) -> String

/** Setups, their values, and their uses in projects with snapshots (SPEC 6.3, 6.4). */
class SetupRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.machineDao()

    fun observeAll(): Flow<List<SetupRow>> = dao.observeSetups()
    fun observeOne(id: String): Flow<SetupEntity?> = dao.observeSetup(id)
    suspend fun get(id: String): SetupEntity? = dao.getSetup(id)

    suspend fun create(machineId: String, title: String, purposeId: String? = null, fabric: String = ""): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertSetup(SetupEntity(id, machineId, purposeId, title.trim(), fabric.trim(), createdAt = now, updatedAt = now))
        return id
    }

    /** Atomic edit of the setup row; nothing if it is gone. */
    suspend fun edit(id: String, change: (SetupEntity) -> SetupEntity) = db.withTransaction {
        dao.getSetup(id)?.let { current -> val next = change(current); dao.updateSetup(next.copy(title = next.title.trim(), updatedAt = time.nowMillis())) }
    }

    /** Deletes the setup and its values; snapshots in projects stay (SPEC 6.4, 7). */
    suspend fun delete(id: String): DeletionToken? = deleter.delete("setup", id)

    suspend fun undo(token: DeletionToken) = deleter.undo(token)

    // ---- Values ----

    fun observeValues(setupId: String): Flow<List<SetupValueEntity>> = dao.observeValues(setupId)
    fun observeAllValues(): Flow<List<SetupValueEntity>> = dao.observeAllValues()
    suspend fun values(setupId: String): Map<String, String> = dao.values(setupId).associate { it.fieldId to it.value }

    /** Sets one value (stored as text, SPEC 6.2); re-setting revives the row (SPEC 1). Touches the setup. */
    suspend fun setValue(setupId: String, fieldId: String, value: String) = db.withTransaction {
        val setup = dao.getSetup(setupId) ?: return@withTransaction
        val now = time.nowMillis()
        val text = value.trim()
        val existing = dao.getValueAny(setupId, fieldId)
        when {
            existing == null -> dao.insertValue(SetupValueEntity(Ids.random(), setupId, fieldId, text, createdAt = now, updatedAt = now))
            existing.value != text || existing.deletedAt != null -> dao.updateValue(existing.copy(value = text, deletedAt = null, updatedAt = now))
            else -> return@withTransaction
        }
        dao.updateSetup(setup.copy(updatedAt = now))
    }

    // ---- Uses and snapshots (SPEC 6.4) ----

    fun observeUses(projectId: String): Flow<List<SetupUseRow>> = dao.observeUses(projectId)
    fun observeProjectsUsing(setupId: String): Flow<List<ProjectEntity>> = dao.observeProjectsUsing(setupId)

    /** Snapshot of the setup's visible fields with their labels and values now. */
    private suspend fun snapshotOf(setup: SetupEntity, label: FieldLabel): List<SnapshotField> {
        val values = values(setup.id)
        return dao.fields(setup.machineId).filter { !it.hidden }.map { SnapshotField(it.id, label(it), values[it.id].orEmpty()) }
    }

    /** Links a setup to a project or log entry, storing a snapshot (SPEC 6.4). Touches the project. */
    suspend fun link(setupId: String, projectId: String, logEntryId: String? = null, label: FieldLabel): String? = db.withTransaction {
        val setup = dao.getSetup(setupId) ?: return@withTransaction null
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertUse(
            SetupUseEntity(id, setupId, projectId, logEntryId, snapshotAt = now, snapshot = Snapshots.encode(snapshotOf(setup, label)), createdAt = now, updatedAt = now),
        )
        db.projectDao().touch(projectId, now)
        id
    }

    /** Edits snapshot values in place in one transaction and marks the use as tweaked (SPEC 6.4). */
    suspend fun tweak(useId: String, values: Map<String, String>) = db.withTransaction {
        val use = dao.getUse(useId) ?: return@withTransaction
        val fields = Snapshots.decode(use.snapshot)
        val updated = fields.map { field -> values[field.fieldId]?.let { field.copy(value = it.trim()) } ?: field }
        if (updated == fields) return@withTransaction
        val now = time.nowMillis()
        dao.updateUse(use.copy(snapshot = Snapshots.encode(updated), tweaked = true, updatedAt = now))
        db.projectDao().touch(use.projectId, now)
    }

    suspend fun tweak(useId: String, fieldId: String, value: String) = tweak(useId, mapOf(fieldId to value))

    /**
     * "Update setup with these values" (SPEC 6.4, tweaked snapshots only): writes the snapshot's values to the setup
     * for fields that still exist. The snapshot itself is kept as it is (fields since hidden or deleted stay in the
     * project's record); only `snapshotAt` is refreshed and `tweaked` cleared.
     */
    suspend fun writeBack(useId: String) = db.withTransaction {
        val use = dao.getUse(useId) ?: return@withTransaction
        val setup = dao.getSetup(use.setupId) ?: return@withTransaction
        val existing = dao.fields(setup.machineId).map { it.id }.toSet()
        Snapshots.decode(use.snapshot).filter { it.fieldId in existing }.forEach { setValue(setup.id, it.fieldId, it.value) }
        val now = time.nowMillis()
        dao.updateUse(use.copy(snapshotAt = now, tweaked = false, updatedAt = now))
        db.projectDao().touch(use.projectId, now)
    }

    /**
     * "Save as new setup" / "Save as setup" from a log entry (SPEC 6.4, 3.4): a new setup on the same machine with the
     * snapshot's values, purpose and fabric of the source (also if the source was deleted). The use is not changed.
     * Null if the machine no longer exists.
     */
    suspend fun createFromSnapshot(useId: String, title: String): String? = db.withTransaction {
        val use = dao.getUse(useId) ?: return@withTransaction null
        val source = dao.getSetupAny(use.setupId) ?: return@withTransaction null
        dao.getMachine(source.machineId) ?: return@withTransaction null // never create setups under a deleted machine
        val newId = create(source.machineId, title, source.purposeId, source.fabric)
        val existing = dao.fields(source.machineId).map { it.id }.toSet()
        Snapshots.decode(use.snapshot).filter { it.fieldId in existing }.forEach { setValue(newId, it.fieldId, it.value) }
        newId
    }

    /** Removes a use from the project (soft delete, undoable). */
    suspend fun unlink(useId: String): DeletionToken? = db.withTransaction {
        val use = dao.getUse(useId) ?: return@withTransaction null
        deleter.delete("setup_use", useId).also { db.projectDao().touch(use.projectId, time.nowMillis()) }
    }
}
