package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.ProjectRef
import at.mtr.sewfar.data.db.SetupEntity
import at.mtr.sewfar.data.db.SetupRow
import at.mtr.sewfar.data.db.SetupValueEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.MachineType
import kotlinx.coroutines.flow.Flow

/** Machines and their reusable settings ("setups", SPEC 5). */
class MachineRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.machineDao()

    fun observeAll(): Flow<List<MachineEntity>> = dao.observeAll()
    fun observe(id: String): Flow<MachineEntity?> = dao.observeOne(id)

    suspend fun create(name: String = "", type: MachineType = MachineType.SEWING_MACHINE): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(MachineEntity(id, name.trim(), type, createdAt = now, updatedAt = now))
        return id
    }

    suspend fun edit(id: String, change: (MachineEntity) -> MachineEntity) = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction
        val next = change(current)
        if (next != current) dao.update(next.copy(updatedAt = time.nowMillis()))
    }

    /** Deletes the machine with its settings; projects lose the links (undo restores everything). */
    suspend fun delete(id: String): DeletionToken? = deleter.delete("machine", id)

    suspend fun undo(token: DeletionToken) = deleter.undo(token)

    suspend fun discardIfEmpty(id: String) = db.withTransaction {
        val m = dao.get(id) ?: return@withTransaction
        val hasSetups = db.query("SELECT 1 FROM setup WHERE machineId = ? AND deletedAt IS NULL", arrayOf(id)).use { it.moveToFirst() }
        if (m.name.isBlank() && m.notes.isBlank() && !hasSetups) deleter.delete("machine", id)
    }

    // ---- Setups (SPEC 5.2) ----

    fun observeSetups(): Flow<List<SetupRow>> = dao.observeSetups()
    fun observeSetup(id: String): Flow<SetupEntity?> = dao.observeSetup(id)
    fun observeValues(setupId: String): Flow<List<SetupValueEntity>> = dao.observeValues(setupId)
    fun observeSetupProjects(setupId: String): Flow<List<ProjectRef>> = dao.observeProjects(setupId)

    suspend fun createSetup(machineId: String, title: String = ""): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertSetup(SetupEntity(id, machineId, title.trim(), createdAt = now, updatedAt = now))
        return id
    }

    suspend fun editSetup(id: String, change: (SetupEntity) -> SetupEntity) = db.withTransaction {
        val current = dao.getSetup(id) ?: return@withTransaction
        val next = change(current)
        if (next != current) dao.updateSetup(next.copy(updatedAt = time.nowMillis()))
    }

    /** Sets one value; blank removes it. A removed value comes back in its old row (SPEC 1). */
    suspend fun setValue(setupId: String, key: String, value: String) = db.withTransaction {
        val setup = dao.getSetup(setupId) ?: return@withTransaction
        val now = time.nowMillis()
        val text = value.trim()
        val existing = dao.getValueAny(setupId, key)
        when {
            existing == null -> if (text.isNotEmpty()) {
                dao.insertValue(SetupValueEntity(Ids.random(), setupId, key, text, createdAt = now, updatedAt = now))
            }
            text.isEmpty() -> if (existing.deletedAt == null) dao.updateValue(existing.copy(deletedAt = now, updatedAt = now))
            existing.value != text || existing.deletedAt != null ->
                dao.updateValue(existing.copy(value = text, deletedAt = null, updatedAt = now))
        }
        dao.updateSetup(setup.copy(updatedAt = now))
    }

    /** Drops a new setting left without title, notes or values (SPEC 5.2). */
    suspend fun discardSetupIfEmpty(id: String) = db.withTransaction {
        val setup = dao.getSetup(id) ?: return@withTransaction
        val hasValues = db.query("SELECT 1 FROM setup_value WHERE setupId = ? AND deletedAt IS NULL", arrayOf(id)).use { it.moveToFirst() }
        if (setup.title.isBlank() && setup.notes.isBlank() && !hasValues) deleter.delete("setup", id)
    }

    suspend fun deleteSetup(id: String): DeletionToken? = deleter.delete("setup", id)
}
