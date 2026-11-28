package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import at.mtr.sewfar.data.ProjectDetailState
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.Durations
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.ui.common.DatePickDialog
import at.mtr.sewfar.ui.common.LabeledTextField
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.common.formatDuration
import at.mtr.sewfar.ui.common.rememberNow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


/** Time spent (SPEC 3.3): total, start/stop, add time by hand, the entries (tap one to correct it). */
@Composable
internal fun TimeSection(state: ProjectDetailState, viewModel: ProjectDetailViewModel, onRemove: (String) -> Unit) {
    val running = state.running
    val now = rememberNow(running != null)
    val total = Durations.total(state.time.map { it.startedAt to it.endedAt }, now)
    var showEntries by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        SectionLabel(stringResource(R.string.project_time))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDuration(total),
                style = MaterialTheme.typography.headlineSmall,
                color = if (running != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("timeTotal"),
            )
            IconButton(onClick = { adding = true }, modifier = Modifier.testTag("addTime")) {
                Icon(Icons.Outlined.Add, stringResource(R.string.time_add))
            }
            Spacer(Modifier.width(4.dp))
            // A finished project gets no new timer (SPEC 3.2); time can still be added by hand.
            if (running != null || state.project?.status != ProjectStatus.DONE) {
                Button(onClick = { if (running != null) viewModel.stopTimer() else viewModel.startTimer() }, modifier = Modifier.testTag("timer")) {
                    Icon(if (running != null) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                    Text(stringResource(if (running != null) R.string.timer_stop else R.string.timer_start), Modifier.padding(start = 8.dp))
                }
            }
        }
        if (state.time.isNotEmpty()) {
            TextButton(onClick = { showEntries = !showEntries }, modifier = Modifier.padding(start = 0.dp)) {
                Text(stringResource(if (showEntries) R.string.time_hide_entries else R.string.time_show_entries, state.time.size))
            }
        }
        if (showEntries) {
            val zone = ZoneId.systemDefault()
            state.time.forEach { entry ->
                // A finished entry opens the time dialog to be corrected; the running one has nothing to correct yet.
                Row(
                    Modifier.clickable(enabled = entry.endedAt != null) { editing = entry.id }.testTag("timeEntry-${entry.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Instant.ofEpochMilli(entry.startedAt).atZone(zone).toLocalDate().format(), Modifier.weight(1f))
                    Text(
                        if (entry.endedAt == null) stringResource(R.string.timer_running)
                        else formatDuration(entry.endedAt - entry.startedAt),
                    )
                    // A running entry can't be removed (SPEC 3.3): stop the timer first.
                    if (entry.endedAt != null) {
                        IconButton(onClick = { onRemove(entry.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove)) }
                    }
                }
            }
        }
    }
    if (adding) {
        TimeDialog(
            stringResource(R.string.time_add), stringResource(R.string.action_add),
            onSave = { date, minutes -> viewModel.addTime(date, minutes); adding = false }, onDismiss = { adding = false },
        )
    }
    state.time.firstOrNull { it.id == editing && it.endedAt != null }?.let { entry ->
        TimeDialog(
            stringResource(R.string.time_edit), stringResource(R.string.action_save),
            initialMinutes = ((entry.endedAt!! - entry.startedAt) / 60_000L).toInt(),
            initialDate = Instant.ofEpochMilli(entry.startedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
            onSave = { date, minutes -> viewModel.editTime(entry.id, date, minutes); editing = null }, onDismiss = { editing = null },
        )
    }
}

/**
 * Duration and day of time worked (SPEC 3.3): adding by hand, correcting an entry, or checking a long timer.
 * [initialMinutes] pre-fills the duration as `h:mm`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(
    title: String,
    confirmLabel: String,
    onSave: (LocalDate, Int) -> Unit,
    onDismiss: () -> Unit,
    initialMinutes: Int? = null,
    initialDate: LocalDate = LocalDate.now(),
) {
    var text by rememberSaveable { mutableStateOf(initialMinutes?.let { "%d:%02d".format(it / 60, it % 60) }.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var pickingDate by remember { mutableStateOf(false) }
    val minutes = Durations.parseMinutes(text)
    if (pickingDate) {
        DatePickDialog(date, onPick = { date = it; pickingDate = false }, onDismiss = { pickingDate = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledTextField(
                    text, { text = it }, stringResource(R.string.time_duration),
                    fieldModifier = Modifier.testTag("duration"),
                    placeholder = "1:30",
                    supportingText = stringResource(R.string.time_duration_hint),
                    isError = text.isNotBlank() && minutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedButton(onClick = { pickingDate = true }) { Text(date.format()) }
            }
        },
        confirmButton = {
            TextButton(enabled = minutes != null, onClick = { minutes?.let { onSave(date, it) } }, modifier = Modifier.testTag("timeSave")) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
