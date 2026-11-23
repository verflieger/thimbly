package at.mtr.sewfar.ui.machines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.SetupEntity
import at.mtr.sewfar.domain.SetupResult
import at.mtr.sewfar.domain.SetupTitles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetupDetailState(
    val loaded: Boolean = false,
    val setup: SetupEntity? = null,
    val machine: MachineEntity? = null,
    val fields: List<MachineFieldEntity> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val purposes: List<ListEntryRow> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
)

/** Setup detail (SPEC 6.3). Writes run in the app scope. */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupDetailViewModel(private val container: AppContainer, val setupId: String) : ViewModel() {
    private val setups = container.setups
    private val scope = container.appScope

    private val machineAndFields = setups.observeOne(setupId).map { it?.machineId }.distinctUntilChanged().flatMapLatest { machineId ->
        if (machineId == null) flowOf(null to emptyList())
        else combine(container.machines.observeOne(machineId), container.machines.observeFields(machineId)) { m, f -> m to f }
    }

    val state: StateFlow<SetupDetailState> = combine(
        setups.observeOne(setupId),
        machineAndFields,
        setups.observeValues(setupId),
        container.lists.observe(ListKind.PURPOSE),
        setups.observeProjectsUsing(setupId),
    ) { setup, (machine, fields), values, purposes, projects ->
        SetupDetailState(true, setup, machine, fields, values.associate { it.fieldId to it.value }, purposes, projects)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupDetailState())

    private fun edit(change: (SetupEntity) -> SetupEntity) { scope.launch { setups.edit(setupId, change) } }

    fun setTitle(text: String) = edit { it.copy(title = text) }

    /**
     * Purpose and fabric fill the default title "<purpose> – <fabric>" (SPEC 6.3) while the title is empty or still
     * the automatic one; a title the user typed is never changed. [purposeNames] resolves purpose IDs to names.
     */
    fun setPurpose(id: String?, purposeNames: Map<String, String>) = edit { s ->
        retitled(s, s.copy(purposeId = id), purposeNames)
    }

    fun setFabric(text: String, purposeNames: Map<String, String>) = edit { s ->
        retitled(s, s.copy(fabric = text.trim()), purposeNames)
    }

    private fun retitled(before: SetupEntity, after: SetupEntity, names: Map<String, String>): SetupEntity {
        val oldAuto = SetupTitles.auto(before.purposeId?.let(names::get), before.fabric)
        return if (before.title.isBlank() || before.title == oldAuto) after.copy(title = SetupTitles.auto(after.purposeId?.let(names::get), after.fabric)) else after
    }
    fun setResult(result: SetupResult?) = edit { it.copy(result = result) }
    fun setNotes(text: String) = edit { it.copy(notes = text) }
    fun setValue(fieldId: String, value: String) { scope.launch { setups.setValue(setupId, fieldId, value) } }

    suspend fun createPurpose(name: String): String = container.runInApp { container.lists.add(ListKind.PURPOSE, name) }

    suspend fun delete(): DeletionToken? = container.runInApp { setups.delete(setupId) }

    fun undo(token: DeletionToken) { scope.launch { setups.undo(token) } }
}
