package at.mtr.sewfar.ui.machines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.ui.common.AutoSaveTextField
import at.mtr.sewfar.ui.common.EntityPhoto
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.NameDialog
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.projects.SectionLabel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DayMillis = 86_400_000L

/** Machine detail (SPEC 6.1, 6.2): data, photo, setting fields, the machine's setups. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MachineDetailScreen(viewModel: MachineDetailViewModel, onBack: () -> Unit, onOpenSetup: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.machine_deleted)
    val inUseText = stringResource(R.string.machine_in_use)
    val photoDeletedText = stringResource(R.string.photo_deleted)
    var menuOpen by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    LaunchedEffect(state.loaded, state.machine) { if (state.loaded && state.machine == null) onBack() }
    val machine = state.machine ?: return
    val archived = machine.archivedAt != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(machine.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
                            leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                            onClick = { menuOpen = false; viewModel.setArchived(!archived) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.machine_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    when (val result = viewModel.delete()) {
                                        is DeleteResult.Deleted -> {
                                            withContext(Dispatchers.Main) { onBack() }
                                            messages.showUndo(deletedText) { viewModel.undo(result.token) }
                                        }
                                        else -> messages.show(inUseText)
                                    }
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (archived) Text(stringResource(R.string.archived_label), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntityPhoto(state.photoId, onSet = viewModel::setPhoto, onOpen = onOpenPhoto, onRemove = {
                    state.photoId?.let { id -> scope.launch { viewModel.removePhoto(id)?.let { messages.showUndo(photoDeletedText) { viewModel.undo(it) } } } }
                })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSaveTextField(machine.name, viewModel::rename, stringResource(R.string.machine_name), "mname-${machine.id}",
                        isValid = { it.isNotBlank() }, modifier = Modifier.fillMaxWidth())
                    AutoSaveTextField(machine.brand, viewModel::setBrand, stringResource(R.string.machine_brand), "brand-${machine.id}", modifier = Modifier.fillMaxWidth())
                }
            }
            AutoSaveTextField(machine.model, viewModel::setModel, stringResource(R.string.machine_model), "model-${machine.id}", modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.machine_type))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MachineType.entries.forEach { type ->
                        FilterChip(selected = machine.type == type, onClick = { viewModel.setType(type) }, label = { Text(stringResource(type.label)) })
                    }
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.machine_serviced)) },
                supportingContent = { Text(machine.lastServicedOn?.format() ?: stringResource(R.string.project_date_none)) },
                modifier = Modifier.clickable { pickingDate = true },
            )
            AutoSaveTextField(machine.notes, viewModel::setNotes, stringResource(R.string.machine_notes), "mnotes-${machine.id}",
                singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            FieldsEditor(state.fields, viewModel)
            HorizontalDivider()
            Column {
                SectionLabel(stringResource(R.string.machine_setups))
                state.setups.forEach { setup ->
                    ListItem(headlineContent = { Text(setup.title) }, supportingContent = { Text(setup.fabric) }, modifier = Modifier.clickable { onOpenSetup(setup.id) })
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (machine.lastServicedOn ?: LocalDate.now()).toEpochDay() * DayMillis)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setServiced(LocalDate.ofEpochDay(it / DayMillis)) }
                    pickingDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                Row {
                    if (machine.lastServicedOn != null) TextButton(onClick = { viewModel.setServiced(null); pickingDate = false }) { Text(stringResource(R.string.action_clear_date)) }
                    TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        ) { DatePicker(pickerState) }
    }
}

/** Setting fields of the machine (SPEC 6.2): rename, reset name, hide/show, reorder, add, delete (custom and unused). */
@Composable
private fun FieldsEditor(fields: List<MachineFieldEntity>, viewModel: MachineDetailViewModel) {
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.field_deleted)
    val inUseText = stringResource(R.string.field_in_use)
    val builtInText = stringResource(R.string.field_builtin)
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MachineFieldEntity?>(null) }

    fun move(index: Int, delta: Int) {
        val ids = fields.map { it.id }.toMutableList()
        val target = index + delta
        if (target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        viewModel.reorderFields(ids)
    }

    Column {
        SectionLabel(stringResource(R.string.machine_fields))
        fields.forEachIndexed { index, field ->
            var menu by remember(field.id) { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(field.displayName(), color = if (field.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) },
                supportingContent = if (field.hidden) ({ Text(stringResource(R.string.list_hidden)) }) else null,
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; renaming = field })
                            if (field.builtinKey != null && field.customName != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_reset_name)) }, onClick = { menu = false; viewModel.resetFieldName(field.id) })
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(if (field.hidden) R.string.action_show else R.string.action_hide)) },
                                onClick = { menu = false; viewModel.setFieldHidden(field.id, !field.hidden) },
                            )
                            if (index > 0) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; move(index, -1) })
                            if (index < fields.lastIndex) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; move(index, 1) })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        when (val result = viewModel.deleteField(field.id)) {
                                            is DeleteResult.Deleted -> messages.showUndo(deletedText) { viewModel.undo(result.token) }
                                            DeleteResult.BuiltIn -> messages.show(builtInText)
                                            DeleteResult.InUse -> messages.show(inUseText)
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
        AssistChip(
            onClick = { adding = true },
            label = { Text(stringResource(R.string.machine_add_field)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        )
    }
    if (adding) {
        NameDialog(
            title = stringResource(R.string.machine_add_field), label = stringResource(R.string.list_entry_name),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { viewModel.addField(it); adding = false }, onDismiss = { adding = false },
        )
    }
    renaming?.let { field ->
        val shown = field.displayName()
        NameDialog(
            title = stringResource(R.string.action_rename), label = stringResource(R.string.list_entry_name),
            confirmLabel = stringResource(R.string.action_save), initial = shown,
            onConfirm = { name -> if (name != shown) viewModel.renameField(field.id, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}
