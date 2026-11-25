package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import at.mtr.sewfar.ui.common.EditHeader
import at.mtr.sewfar.ui.common.EditableText
import at.mtr.sewfar.ui.common.FormField
import at.mtr.sewfar.ui.common.ReadOnlyField
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.TextDialog
import at.mtr.sewfar.ui.common.fieldLabel
import at.mtr.sewfar.ui.common.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Machine (SPEC 5.1) ----

class MachineViewModel(container: AppContainer, private val id: String) : RecordViewModel<MachineEntity>(container) {
    private val repo = container.machines
    override val record = repo.observe(id)
    override val projectsFlow = emptyFlow<List<ProjectRef>>()
    val setups: StateFlow<List<SetupRow>> = repo.observeSetups().map { all -> all.filter { it.machineId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override suspend fun delete() = container.runInApp { repo.delete(id) }

    fun rename(name: String) = write { repo.edit(id) { it.copy(name = name) } }
    fun setNotes(text: String) = write { repo.edit(id) { it.copy(notes = text) } }
    fun setType(type: MachineType) = write { repo.edit(id) { it.copy(type = type) } }
    suspend fun createSetup(title: String): String = container.runInApp { repo.createSetup(id, title) }
}

@Composable
fun MachineScreen(viewModel: MachineViewModel, onBack: () -> Unit, onOpenSetup: (String, Boolean) -> Unit) {
    val setups by viewModel.setups.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var naming by rememberSaveable { mutableStateOf(false) }
    RecordScreen(
        viewModel, startEditing = false, stringResource(R.string.machine_deleted), onBack, onOpenProject = {},
        titleOf = { it.name }, nameOf = { it.name }, nameLabel = stringResource(R.string.machine_name), onRename = viewModel::rename,
    ) { machine, _ ->
        MachineTypeChip(machine.type, viewModel::setType)
        EditableText(stringResource(R.string.machine_notes), machine.notes, "notes", onSave = viewModel::setNotes)
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
                onClick = { naming = true },
                label = { Text(stringResource(R.string.setup_new)) },
                leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("newSetup"),
            )
        }
    }
    if (naming) {
        TextDialog(
            stringResource(R.string.setup_new), stringResource(R.string.setup_title),
            onSave = { title ->
                naming = false
                scope.launch { val id = viewModel.createSetup(title); withContext(Dispatchers.Main) { onOpenSetup(id, true) } }
            },
            onDismiss = { naming = false },
        )
    }
}

// ---- Setting (SPEC 5.2) ----

data class SetupDetail(val setup: SetupEntity, val machine: MachineEntity?, val values: Map<String, String>)

class SetupViewModel(container: AppContainer, private val id: String) : RecordViewModel<SetupDetail>(container) {
    private val repo = container.machines
    override val record = combine(repo.observeSetup(id), repo.observeAll(), repo.observeValues(id)) { setup, machines, values ->
        setup?.let { s -> SetupDetail(s, machines.firstOrNull { it.id == s.machineId }, values.associate { it.key to it.value }) }
    }
    override val projectsFlow = repo.observeSetupProjects(id)

    override suspend fun delete() = container.runInApp { repo.deleteSetup(id) }

    fun rename(title: String) = write { repo.editSetup(id) { it.copy(title = title) } }
    fun setNotes(text: String) = write { repo.editSetup(id) { it.copy(notes = text) } }

    /** Stores the form: every `v:<key>` value (a blank value is removed). */
    fun saveValues(f: Map<String, String>) = write {
        f.filterKeys { it.startsWith(ValuePrefix) }.forEach { (k, v) -> repo.setValue(id, k.removePrefix(ValuePrefix), v) }
    }

    /** Changes the machine's type, e.g. right after creating it from a project (SPEC 5.2). */
    fun setMachineType(machineId: String, type: MachineType) = write { repo.edit(machineId) { it.copy(type = type) } }
}

private const val ValuePrefix = "v:"

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    startEditing: Boolean,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenMachine: (String) -> Unit,
) {
    RecordScreen(
        viewModel, startEditing, stringResource(R.string.setup_deleted), onBack, onOpenProject,
        titleOf = { it.setup.title.ifBlank { stringResource(R.string.setup_untitled) } },
        nameOf = { it.setup.title }, nameLabel = stringResource(R.string.setup_title), onRename = viewModel::rename,
        fieldsOf = { d ->
            d.machine?.let { MachineTemplates.of(it.type) }.orEmpty().associate { "$ValuePrefix$it" to d.values[it].orEmpty() }
        },
        onSave = viewModel::saveValues,
        edit = { detail, d, set ->
            // The type decides the fields, so it can be fixed here too (e.g. a machine just created from a project).
            detail.machine?.let { m -> MachineTypeChip(m.type) { viewModel.setMachineType(m.id, it) } }
            // The fields of the machine's type, two per row: most values are short numbers.
            detail.machine?.let { MachineTemplates.of(it.type) }.orEmpty().filter { "$ValuePrefix$it" in d }
                .chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            FormField(
                                d.getValue("$ValuePrefix$key"), { set("$ValuePrefix$key", it) }, stringResource(fieldLabel(key)),
                                Modifier.weight(1f), tag = "field-$key",
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
        },
    ) { detail, openForm ->
        val machine = detail.machine
        if (machine != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onOpenMachine(machine.id) }, label = { Text(machine.name.ifBlank { stringResource(R.string.untitled) }) })
                MachineTypeChip(machine.type) { viewModel.setMachineType(machine.id, it) }
            }
            val keys = MachineTemplates.of(machine.type)
            if (keys.isNotEmpty()) {
                Column(Modifier.clickable(onClick = openForm), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditHeader(stringResource(R.string.setup_values), "values", openForm)
                    val filled = keys.filter { !detail.values[it].isNullOrBlank() }
                    if (filled.isEmpty()) Text(stringResource(R.string.setup_no_values), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    filled.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                Column(Modifier.weight(1f)) { ReadOnlyField(stringResource(fieldLabel(key)), detail.values.getValue(key)) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        EditableText(stringResource(R.string.setup_notes), detail.setup.notes, "notes", onSave = viewModel::setNotes)
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
