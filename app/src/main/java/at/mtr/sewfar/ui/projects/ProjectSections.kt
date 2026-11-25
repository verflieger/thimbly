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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.Durations
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.ui.common.LabeledTextField
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.common.formatDuration
import at.mtr.sewfar.ui.common.rememberNow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DayMillis = 86_400_000L

/** Time spent (SPEC 3.3): total, start/stop, add time by hand, the entries. */
@Composable
internal fun TimeSection(state: ProjectDetailState, viewModel: ProjectDetailViewModel, onRemove: (String) -> Unit) {
    val running = state.running
    val now = rememberNow(running != null)
    val total = Durations.total(state.time.map { it.startedAt to it.endedAt }, now)
    var showEntries by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
    if (adding) AddTimeDialog(onAdd = { date, minutes -> viewModel.addTime(date, minutes); adding = false }, onDismiss = { adding = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimeDialog(onAdd: (LocalDate, Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var pickingDate by remember { mutableStateOf(false) }
    val minutes = Durations.parseMinutes(text)
    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * DayMillis)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / DayMillis) }
                    pickingDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(pickerState) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.time_add)) },
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
            TextButton(enabled = minutes != null, onClick = { minutes?.let { onAdd(date, it) } }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
