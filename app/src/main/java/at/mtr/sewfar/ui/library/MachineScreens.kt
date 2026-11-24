package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.ProjectRef
import at.mtr.sewfar.data.db.SetupEntity
import at.mtr.sewfar.data.db.SetupRow
import at.mtr.sewfar.domain.MachineTemplates
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.ui.common.AutoSaveTextField
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.fieldLabel
import at.mtr.sewfar.ui.common.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Machine (SPEC 5.1) ----

class MachineViewModel(container: AppContainer, private val id: String, isNew: Boolean) : RecordViewModel<MachineEntity>(container, isNew) {
    private val repo = container.machines
    override val record = repo.observe(id)
    override val projectsFlow = emptyFlow<List<ProjectRef>>()
    val setups: StateFlow<List<SetupRow>> = repo.observeSetups().map { all -> all.filter { it.machineId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { start() }

    override fun nameOf(record: MachineEntity) = record.name
    override suspend fun saveName(name: String) = repo.edit(id) { it.copy(name = name) }
    override suspend fun discardIfEmpty() = repo.discardIfEmpty(id)
    override suspend fun delete() = run { repo.delete(id) }

    fun setType(type: MachineType) = write { repo.edit(id) { it.copy(type = type) } }
    fun setNotes(text: String) = write { repo.edit(id) { it.copy(notes = text) } }
    suspend fun createSetup(): String = run { repo.createSetup(id) }
}

@Composable
fun MachineScreen(viewModel: MachineViewModel, isNew: Boolean, onBack: () -> Unit, onOpenSetup: (String, Boolean) -> Unit) {
    val setups by viewModel.setups.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    RecordScreen(viewModel, isNew, stringResource(R.string.machine_name), stringResource(R.string.machine_deleted), onBack, onOpenProject = {}) { machine ->
        MachineTypeChip(machine.type, viewModel::setType)
        Column {
            SectionLabel(stringResource(R.string.machine_setups))
            setups.forEach { setup ->
                ListItem(
                    headlineContent = { Text(setup.title.ifBlank { stringResource(R.string.setup_untitled) }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpenSetup(setup.id, false) },
                )
            }
            AssistChip(
                onClick = { scope.launch { val sid = viewModel.createSetup(); withContext(Dispatchers.Main) { onOpenSetup(sid, true) } } },
                label = { Text(stringResource(R.string.setup_new)) },
                leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("newSetup"),
            )
        }
        AutoSaveTextField(
            machine.notes, viewModel::setNotes, stringResource(R.string.machine_notes), "notes-${machine.id}",
            Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
        )
    }
}

// ---- Setup (SPEC 5.2) ----

data class SetupDetail(val setup: SetupEntity, val machine: MachineEntity?, val values: Map<String, String>)

class SetupViewModel(container: AppContainer, private val id: String, isNew: Boolean) : RecordViewModel<SetupDetail>(container, isNew) {
    private val repo = container.machines
    override val record = combine(repo.observeSetup(id), repo.observeAll(), repo.observeValues(id)) { setup, machines, values ->
        setup?.let { s -> SetupDetail(s, machines.firstOrNull { it.id == s.machineId }, values.associate { it.key to it.value }) }
    }
    override val projectsFlow = repo.observeSetupProjects(id)

    init { start() }

    override fun nameOf(record: SetupDetail) = record.setup.title
    override suspend fun saveName(name: String) = repo.editSetup(id) { it.copy(title = name) }
    override suspend fun discardIfEmpty() = repo.discardSetupIfEmpty(id)
    override suspend fun delete() = run { repo.deleteSetup(id) }

    fun setValue(key: String, value: String) = write { repo.setValue(id, key, value) }

    /** Changes the machine's type, e.g. right after creating it from a project (SPEC 5.2). */
    fun setMachineType(machineId: String, type: MachineType) = write { repo.edit(machineId) { it.copy(type = type) } }
    fun setNotes(text: String) = write { repo.editSetup(id) { it.copy(notes = text) } }
}

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    isNew: Boolean,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenMachine: (String) -> Unit,
) {
    RecordScreen(viewModel, isNew, stringResource(R.string.setup_title), stringResource(R.string.setup_deleted), onBack, onOpenProject) { detail ->
        val machine = detail.machine
        if (machine != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onOpenMachine(machine.id) }, label = { Text(machine.name.ifBlank { stringResource(R.string.untitled) }) })
                MachineTypeChip(machine.type) { viewModel.setMachineType(machine.id, it) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Two per row: most values are short numbers.
                MachineTemplates.of(machine.type).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            AutoSaveTextField(
                                detail.values[key].orEmpty(), { viewModel.setValue(key, it) }, stringResource(fieldLabel(key)),
                                "value-${detail.setup.id}-$key", Modifier.weight(1f).testTag("field-$key"),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        AutoSaveTextField(
            detail.setup.notes, viewModel::setNotes, stringResource(R.string.setup_notes), "notes-${detail.setup.id}",
            Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
        )
    }
}

/** The machine type with a menu to change it; the type decides which setting fields are shown. */
@Composable
private fun MachineTypeChip(type: MachineType, onChange: (MachineType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(type.label)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
            modifier = Modifier.testTag("machineType"),
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            MachineType.entries.forEach { t ->
                DropdownMenuItem(text = { Text(stringResource(t.label)) }, onClick = { open = false; onChange(t) })
            }
        }
    }
}
