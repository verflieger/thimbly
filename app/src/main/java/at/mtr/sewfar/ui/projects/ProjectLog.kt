package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.LogEntryEntity
import at.mtr.sewfar.domain.LogKind
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.currentLocale
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.photos.PhotoImage
import at.mtr.sewfar.ui.photos.rememberPickPhotos
import at.mtr.sewfar.ui.photos.rememberTakePhoto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private const val DayMillis = 86_400_000L

/**
 * Progress log (SPEC 3.4): note entries with photos and status entries, newest first. Status entries can be deleted
 * but not edited.
 */
@Composable
internal fun ProjectLog(state: ProjectDetailState, viewModel: ProjectDetailViewModel, onOpenPhoto: (String) -> Unit) {
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.log_deleted)
    /** "" = new note, an ID = editing that note, null = closed. */
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(currentLocale)
    val zone = ZoneId.systemDefault()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.log_title))
        AssistChip(
            onClick = { editing = "" },
            label = { Text(stringResource(R.string.log_add_note)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        )
        if (state.log.isEmpty()) Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyMedium)
        state.log.forEach { entry ->
            val time = Instant.ofEpochMilli(entry.at).atZone(zone).format(formatter)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        Modifier.weight(1f).padding(12.dp).let { if (entry.kind == LogKind.NOTE) it.clickable { editing = entry.id } else it },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry.kind == LogKind.STATUS) {
                            val from = entry.fromStatus?.let { stringResource(it.label) }.orEmpty()
                            val to = entry.toStatus?.let { stringResource(it.label) }.orEmpty()
                            Text(stringResource(R.string.log_status_change, from, to), style = MaterialTheme.typography.bodyMedium)
                        } else if (entry.text.isNotBlank()) {
                            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { viewModel.deleteLogEntry(entry.id)?.let { token -> messages.showUndo(deletedText) { viewModel.undo(token) } } }
                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.log_delete)) }
                }
                if (entry.kind == LogKind.NOTE) EntryPhotos(entry, state, viewModel, onOpenPhoto)
            }
        }
    }

    editing?.let { id ->
        val entry = state.log.firstOrNull { it.id == id }
        NoteDialog(
            initialText = entry?.text.orEmpty(),
            initialAt = entry?.at ?: viewModel.now(),
            onSave = { text, at ->
                if (entry == null) viewModel.addNote(text, at) else viewModel.editNote(entry, text, at)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Photos attached to one note entry (tagged Progress), plus buttons to add more. */
@Composable
private fun EntryPhotos(entry: LogEntryEntity, state: ProjectDetailState, viewModel: ProjectDetailViewModel, onOpenPhoto: (String) -> Unit) {
    val messages = LocalMessages.current
    val failedText = stringResource(R.string.photo_import_failed)
    val add: suspend (List<() -> java.io.InputStream>) -> Unit = { sources ->
        if (viewModel.addPhotos(sources, PhotoTag.PROGRESS, logEntryId = entry.id).second > 0) messages.show(failedText)
    }
    val take = rememberTakePhoto { add(listOf(it)) }
    val pick = rememberPickPhotos(multiple = true) { add(it) }
    val photos = state.photos.filter { it.logEntryId == entry.id }
    val px = with(LocalDensity.current) { 144.dp.roundToPx() }
    Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(photos, key = { it.id }) { photo ->
                PhotoImage(photo.id, minEdgePx = px, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpenPhoto(photo.id) })
            }
        }
        IconButton(onClick = take) { Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.action_take_photo)) }
        IconButton(onClick = pick) { Icon(Icons.Outlined.PhotoLibrary, stringResource(R.string.action_pick_photos)) }
    }
}

/** Text plus date and time of a note (SPEC 3.4). One dialog at a time: the pickers replace it while open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDialog(initialText: String, initialAt: Long, onSave: (String, Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var at by rememberSaveable { mutableStateOf(initialAt) }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 none, 1 date, 2 time
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
    val locale = currentLocale

    when (picker) {
        1 -> {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateTime.toLocalDate().toEpochDay() * DayMillis)
            DatePickerDialog(
                onDismissRequest = { picker = 0 },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val day = java.time.LocalDate.ofEpochDay(millis / DayMillis)
                            at = dateTime.with(day).atZone(zone).toInstant().toEpochMilli()
                        }
                        picker = 0
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = 0 }) { Text(stringResource(R.string.action_cancel)) } },
            ) { DatePicker(pickerState) }
        }
        2 -> {
            val timeState = rememberTimePickerState(dateTime.hour, dateTime.minute)
            AlertDialog(
                onDismissRequest = { picker = 0 },
                text = { TimePicker(timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        at = dateTime.withHour(timeState.hour).withMinute(timeState.minute).atZone(zone).toInstant().toEpochMilli()
                        picker = 0
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = 0 }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (initialText.isEmpty()) R.string.log_add_note else R.string.log_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        text, { text = it }, label = { Text(stringResource(R.string.log_note)) }, minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("noteText"),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.session_date)) },
                        supportingContent = { Text(dateTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))) },
                        modifier = Modifier.clickable { picker = 1 },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.log_time)) },
                        supportingContent = { Text(dateTime.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))) },
                        modifier = Modifier.clickable { picker = 2 },
                    )
                }
            },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim(), at) }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
