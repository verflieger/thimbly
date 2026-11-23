package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.data.FieldLabel
import at.mtr.sewfar.data.db.SetupUseRow
import at.mtr.sewfar.domain.CurrentField
import at.mtr.sewfar.domain.FieldChange
import at.mtr.sewfar.domain.SnapshotDiff
import at.mtr.sewfar.domain.Snapshots
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.NameDialog
import at.mtr.sewfar.ui.common.PickerItem
import at.mtr.sewfar.ui.machines.FieldLabels
import kotlinx.coroutines.launch

/** Field names in the current language, for snapshots and diffs. */
@Composable
internal fun rememberFieldLabel(): FieldLabel {
    val resources = LocalContext.current.resources
    return remember(resources) { { field -> FieldLabels.resolve(field) { resources.getString(it) } } }
}

/** Diff of one use against its setup now (SPEC 6.4); null if the setup was deleted (snapshot only). */
internal fun SetupUsesData.changes(use: SetupUseRow, label: FieldLabel): List<FieldChange>? {
    if (use.setupDeleted) return null
    val machineId = setups.firstOrNull { it.id == use.setupId }?.machineId ?: return null
    val current = fields.filter { it.machineId == machineId }.map { CurrentField(it.id, label(it), it.deletedAt == null && !it.hidden) }
    val values = values.filter { it.setupId == use.setupId }.associate { it.fieldId to it.value }
    return SnapshotDiff.diff(Snapshots.decode(use.snapshot), current, values)
}

/**
 * "Used setups" of the project (logEntryId = null) or of one log entry (SPEC 6.4): snapshot values, "Setup changed
 * since" with the diff, adjust values, update the setup, save as new setup, remove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupUsesSection(
    data: SetupUsesData,
    viewModel: ProjectDetailViewModel,
    logEntryId: String?,
    onOpenSetup: (String) -> Unit,
    showTitle: Boolean = true,
) {
    val failedText = stringResource(R.string.setup_save_failed)
    val archivedSuffix = stringResource(R.string.archived_label)
    val label = rememberFieldLabel()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val removedText = stringResource(R.string.uses_removed)
    val savedText = stringResource(R.string.setup_saved)
    var picking by rememberSaveable { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf<String?>(null) }
    var savingAsNew by rememberSaveable { mutableStateOf<String?>(null) }
    val uses = data.uses.filter { it.logEntryId == logEntryId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) SectionLabel(stringResource(R.string.uses_title))
        uses.forEach { use ->
            val snapshot = Snapshots.decode(use.snapshot)
            val changes = data.changes(use, label)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        use.setupTitle?.let { at.mtr.sewfar.domain.SetupTitles.shown(it, use.machineName) } ?: stringResource(R.string.uses_setup_deleted),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = if (!use.setupDeleted) Modifier.clickable { onOpenSetup(use.setupId) } else Modifier,
                    )
                    use.machineName?.let { name ->
                        Text(if (use.machineArchived) "$name · $archivedSuffix" else name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        snapshot.filter { it.value.isNotBlank() }.joinToString("  ·  ") { "${it.label}: ${it.value}" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (use.tweaked) Text(stringResource(R.string.uses_tweaked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    if (!changes.isNullOrEmpty()) {
                        Text(stringResource(R.string.uses_changed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                        changes.forEach { change ->
                            val dash = stringResource(R.string.value_empty)
                            Text(
                                when (change.kind) {
                                    FieldChange.Kind.CHANGED -> stringResource(R.string.diff_changed, change.label, change.old.orEmpty().ifEmpty { dash }, change.new.orEmpty().ifEmpty { dash })
                                    FieldChange.Kind.REMOVED -> stringResource(R.string.diff_removed, change.label)
                                    FieldChange.Kind.ADDED -> stringResource(R.string.diff_added, change.label, change.new.orEmpty().ifEmpty { dash })
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { adjusting = use.id }) { Text(stringResource(R.string.uses_edit)) }
                        // Only an adjusted snapshot can update the setup (SPEC 6.4): otherwise it would overwrite newer values.
                        if (use.tweaked && !use.setupDeleted) {
                            TextButton(onClick = { viewModel.writeBack(use.id) }) { Text(stringResource(R.string.uses_update_setup)) }
                        }
                        if (use.tweaked || use.setupDeleted) {
                            TextButton(onClick = { savingAsNew = use.id }) { Text(stringResource(R.string.uses_save_new)) }
                        }
                        TextButton(onClick = {
                            scope.launch { viewModel.unlinkSetup(use.id)?.let { token -> messages.showUndo(removedText) { viewModel.undo(token) } } }
                        }) { Text(stringResource(R.string.uses_remove)) }
                    }
                }
            }
        }
        AssistChip(
            onClick = { picking = true },
            label = { Text(stringResource(R.string.uses_add)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
            modifier = Modifier.testTag(if (logEntryId == null) "useSetup" else "useSetup-$logEntryId"),
        )
    }

    if (picking) {
        EntityPicker(
            title = stringResource(R.string.uses_choose),
            // Setups of archived machines are not offered (SPEC 7).
            items = data.setups.filter { !it.machineArchived }.map { PickerItem(it.id, at.mtr.sewfar.domain.SetupTitles.shown(it.title, it.machineName), it.machineName) },
            onPick = { id -> id?.let { viewModel.linkSetup(it, logEntryId, label) }; picking = false },
            // A setup needs a machine and values: it is created on the Machines tab (SPEC 6.3).
            onCreate = null,
            onDismiss = { picking = false },
        )
    }
    adjusting?.let { useId ->
        val use = data.uses.firstOrNull { it.id == useId }
        if (use == null) {
            adjusting = null
        } else {
            AdjustDialog(use, onSave = { values -> if (values.isNotEmpty()) viewModel.tweak(use.id, values); adjusting = null },
                onDismiss = { adjusting = null })
        }
    }
    savingAsNew?.let { useId ->
        val use = data.uses.firstOrNull { it.id == useId }
        NameDialog(
            title = stringResource(R.string.uses_save_new), label = stringResource(R.string.setup_title),
            confirmLabel = stringResource(R.string.action_save), initial = use?.setupTitle.orEmpty(),
            onConfirm = { title ->
                savingAsNew = null
                scope.launch {
                    val id = viewModel.createSetupFromSnapshot(useId, title)
                    if (id == null) messages.show(failedText)
                    else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { messages.show(savedText); onOpenSetup(id) }
                }
            },
            onDismiss = { savingAsNew = null },
        )
    }
}

/** Edit the snapshot's values for this project (SPEC 6.4 "tweak"); only changed values are saved. */
@Composable
private fun AdjustDialog(use: SetupUseRow, onSave: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val snapshot = remember(use.snapshot) { Snapshots.decode(use.snapshot) }
    var values by rememberSaveable(use.id) { mutableStateOf(HashMap(snapshot.associate { it.fieldId to it.value })) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.uses_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.forEach { field ->
                    OutlinedTextField(
                        values[field.fieldId].orEmpty(), { new -> values = HashMap(values).apply { put(field.fieldId, new) } },
                        label = { Text(field.label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(snapshot.filter { values[it.fieldId].orEmpty().trim() != it.value }.associate { it.fieldId to values[it.fieldId].orEmpty() }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
