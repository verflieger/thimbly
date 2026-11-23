package at.mtr.sewfar.ui.machines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.data.db.SetupRow
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.ui.photos.ImageSource
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MachineDetailState(
    val loaded: Boolean = false,
    val machine: MachineEntity? = null,
    val photoId: String? = null,
    val fields: List<MachineFieldEntity> = emptyList(),
    val setups: List<SetupRow> = emptyList(),
)

/** Machine detail (SPEC 6.1, 6.2). Writes run in the app scope. */
class MachineDetailViewModel(private val container: AppContainer, val machineId: String) : ViewModel() {
    private val machines = container.machines
    private val scope = container.appScope

    val state: StateFlow<MachineDetailState> = combine(
        machines.observeOne(machineId),
        container.photos.observe(PhotoOwner.MACHINE, machineId),
        machines.observeFields(machineId),
        container.setups.observeAll(),
    ) { machine, photos, fields, setups ->
        MachineDetailState(true, machine, photos.firstOrNull()?.id, fields, setups.filter { it.machineId == machineId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachineDetailState())

    private fun edit(change: (MachineEntity) -> MachineEntity) { scope.launch { machines.edit(machineId, change) } }

    fun rename(name: String) { if (name.isNotBlank()) edit { it.copy(name = name) } }
    fun setBrand(text: String) = edit { it.copy(brand = text.trim()) }
    fun setModel(text: String) = edit { it.copy(model = text.trim()) }
    fun setType(type: MachineType) = edit { it.copy(type = type) }
    fun setNotes(text: String) = edit { it.copy(notes = text) }
    fun setServiced(date: LocalDate?) = edit { it.copy(lastServicedOn = date) }
    fun setArchived(archived: Boolean) { scope.launch { machines.setArchived(machineId, archived) } }

    fun addField(name: String) { scope.launch { machines.addField(machineId, name) } }
    fun renameField(id: String, name: String) { scope.launch { machines.renameField(id, name) } }
    fun resetFieldName(id: String) { scope.launch { machines.resetFieldName(id) } }
    fun setFieldHidden(id: String, hidden: Boolean) { scope.launch { machines.setFieldHidden(id, hidden) } }
    fun reorderFields(ids: List<String>) { scope.launch { machines.reorderFields(ids) } }
    suspend fun deleteField(id: String): DeleteResult = container.runInApp { machines.deleteField(id) }

    suspend fun setPhoto(source: ImageSource): Boolean =
        container.runInApp { runCatching { container.photos.setEntityPhoto(PhotoOwner.MACHINE, machineId, source) }.isSuccess }

    suspend fun removePhoto(photoId: String): DeletionToken? = container.runInApp { container.photos.delete(photoId) }

    suspend fun delete(): DeleteResult = container.runInApp { machines.delete(machineId) }

    fun undo(token: DeletionToken) { scope.launch { machines.undo(token) } }
}
