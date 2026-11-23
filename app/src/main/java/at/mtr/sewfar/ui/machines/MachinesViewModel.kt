package at.mtr.sewfar.ui.machines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.SetupRow
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.SearchText
import at.mtr.sewfar.domain.SetupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SetupFilter(val machineId: String? = null, val purposeId: String? = null, val result: SetupResult? = null)

data class MachinesState(
    val loaded: Boolean = false,
    val machines: List<MachineEntity> = emptyList(),
    val machinePhotos: Map<String, String> = emptyMap(),
    val setups: List<SetupRow> = emptyList(),
    val anySetups: Boolean = false,
    val purposes: List<ListEntryRow> = emptyList(),
    val query: String = "",
    val filter: SetupFilter = SetupFilter(),
)

/** Machines tab (SPEC 6.3): machine row, setups with search (title, fabric, notes) and filters. */
class MachinesViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SetupFilter())

    private val inputs = combine(query, filter) { q, f -> q to f }

    val state: StateFlow<MachinesState> = combine(
        container.machines.observe(),
        container.photos.observeAllOfType(PhotoOwner.MACHINE),
        container.setups.observeAll(),
        container.lists.observe(ListKind.PURPOSE),
        inputs,
    ) { machines, photos, setups, purposes, (q, f) ->
        val shown = setups.filter { s ->
            (f.machineId == null || s.machineId == f.machineId) && (f.purposeId == null || s.purposeId == f.purposeId) &&
                (f.result == null || s.result == f.result) && SearchText.matches(q, listOf(s.title, s.fabric, s.notes))
        }
        MachinesState(true, machines, photos.associateBy({ it.ownerId }, { it.id }), shown, setups.isNotEmpty(), purposes, q, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachinesState())

    fun setQuery(text: String) { query.value = text }
    fun setFilter(value: SetupFilter) { filter.value = value }

    suspend fun createMachine(name: String, type: MachineType): String = container.runInApp { container.machines.create(name, type) }
    suspend fun createSetup(machineId: String, title: String): String = container.runInApp { container.setups.create(machineId, title) }
}
