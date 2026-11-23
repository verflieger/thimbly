package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.MachineTemplates
import at.mtr.sewfar.domain.MachineType
import kotlinx.coroutines.flow.Flow

/** Machines and their setting fields (SPEC 6.1, 6.2). */
class MachineRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.machineDao()

    fun observe(includeArchived: Boolean = false): Flow<List<MachineEntity>> = dao.observeMachines(includeArchived)
    fun observeOne(id: String): Flow<MachineEntity?> = dao.observeMachine(id)
    suspend fun get(id: String): MachineEntity? = dao.getMachine(id)

    /** Creates a machine with the field list of its type's template (SPEC 6.2). */
    suspend fun create(name: String, type: MachineType): String = db.withTransaction {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertMachine(MachineEntity(id, name.trim(), type = type, createdAt = now, updatedAt = now))
        MachineTemplates.fields.getValue(type).forEachIndexed { index, key ->
            dao.insertField(MachineFieldEntity(Ids.random(), id, builtinKey = key, sortOrder = index, createdAt = now, updatedAt = now))
        }
        id
    }

    /** Atomic edit; changing the type does not change existing fields (SPEC 6.2). */
    suspend fun edit(id: String, change: (MachineEntity) -> MachineEntity) = db.withTransaction {
        dao.getMachine(id)?.let { current -> val next = change(current); dao.updateMachine(next.copy(name = next.name.trim(), updatedAt = time.nowMillis())) }
    }

    suspend fun setArchived(id: String, archived: Boolean) = edit(id) { it.copy(archivedAt = if (archived) time.nowMillis() else null) }

    /** SPEC 7: a machine whose setups are used by live projects can only be archived. */
    suspend fun delete(id: String): DeleteResult = db.withTransaction {
        // Check and delete in one transaction, so a link committed in between can't be cascaded away.
        if (dao.isMachineInUse(id)) return@withTransaction DeleteResult.InUse
        deleter.delete("machine", id)?.let(DeleteResult::Deleted) ?: DeleteResult.InUse
    }

    suspend fun undo(token: DeletionToken) = deleter.undo(token)

    // ---- Fields (SPEC 6.2) ----

    fun observeFields(machineId: String): Flow<List<MachineFieldEntity>> = dao.observeFields(machineId)
    suspend fun fields(machineId: String): List<MachineFieldEntity> = dao.fields(machineId)
    fun observeAllFieldsAny(): Flow<List<MachineFieldEntity>> = dao.observeAllFieldsAny()

    suspend fun addField(machineId: String, name: String): String = db.withTransaction {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertField(MachineFieldEntity(id, machineId, customName = name.trim(), sortOrder = dao.nextFieldOrder(machineId), createdAt = now, updatedAt = now))
        id
    }

    private suspend fun editField(id: String, change: (MachineFieldEntity) -> MachineFieldEntity) = db.withTransaction {
        dao.getField(id)?.let { dao.updateField(change(it).copy(updatedAt = time.nowMillis())) }
    }

    suspend fun renameField(id: String, name: String) = editField(id) { it.copy(customName = name.trim()) }
    suspend fun resetFieldName(id: String) = editField(id) { if (it.builtinKey != null) it.copy(customName = null) else it }
    suspend fun setFieldHidden(id: String, hidden: Boolean) = editField(id) { it.copy(hidden = hidden) }

    suspend fun reorderFields(orderedIds: List<String>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> editField(id) { if (it.sortOrder == index) it else it.copy(sortOrder = index) } }
    }

    /** Built-in fields and fields with values in live setups can only be hidden (SPEC 7). */
    suspend fun deleteField(id: String): DeleteResult {
        val field = dao.getField(id) ?: return DeleteResult.InUse
        if (field.builtinKey != null) return DeleteResult.BuiltIn
        if (dao.isFieldInUse(id)) return DeleteResult.InUse
        return deleter.delete("machine_field", id)?.let(DeleteResult::Deleted) ?: DeleteResult.InUse
    }
}
