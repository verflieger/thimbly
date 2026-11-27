package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import at.mtr.sewfar.ui.common.ChoiceDialog
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
        // A machine never changes kind: the type is picked when it is created (SPEC 5.1).
        ReadOnlyField(stringResource(R.string.type), stringResource(machine.type.label))
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
            // The type belongs to the machine (it decides the fields here) and is changed on the machine page.
            AssistChip(
                onClick = { onOpenMachine(machine.id) },
                label = { Text("${machine.name.ifBlank { stringResource(R.string.untitled) }} · ${stringResource(machine.type.label)}") },
                modifier = Modifier.testTag("setupMachine"),
            )
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

/** Asks for the type of a new machine; the type decides the setting fields and is fixed (SPEC 5.1). */
@Composable
fun MachineTypeDialog(onPick: (MachineType) -> Unit, onDismiss: () -> Unit) =
    ChoiceDialog(stringResource(R.string.machine_type_question), MachineType.entries, { it.label }, onPick, onDismiss)
