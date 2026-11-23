package at.mtr.sewfar.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.domain.AvatarColors
import at.mtr.sewfar.domain.Measured
import at.mtr.sewfar.domain.Measurements
import at.mtr.sewfar.ui.common.AutoSaveTextField
import at.mtr.sewfar.ui.common.EntityPhoto
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.currentLocale
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.common.namesById
import at.mtr.sewfar.ui.projects.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DayMillis = 86_400_000L

/** Person detail (SPEC 5): name, colour, photo, notes, measurements with history, projects. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale
    val deletedText = stringResource(R.string.person_deleted)
    val inUseText = stringResource(R.string.person_in_use)
    val sessionDeletedText = stringResource(R.string.session_deleted)
    val photoDeletedText = stringResource(R.string.photo_deleted)
    var menuOpen by remember { mutableStateOf(false) }
    /** Session being edited: "" = new, an ID = existing, null = dialog closed. */
    var editingSession by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.loaded, state.person) { if (state.loaded && state.person == null) onBack() }
    val person = state.person ?: return
    val archived = person.archivedAt != null
    val typeNames = state.types.namesById(ListKind.MEASUREMENT_TYPE)
    val measured = state.measurements.map { Measured(it.typeId, it.valueMm, it.date) }
    val latest = Measurements.latest(measured)
    val history = Measurements.history(measured)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person.name) },
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
                            text = { Text(stringResource(R.string.person_delete)) },
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
                    AutoSaveTextField(person.name, viewModel::rename, stringResource(R.string.person_name), "person-${person.id}",
                        isValid = { it.isNotBlank() }, modifier = Modifier.fillMaxWidth())
                    SectionLabel(stringResource(R.string.person_color))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AvatarColors.palette.forEach { color ->
                            val selected = person.avatarColor == color
                            Box(
                                Modifier.size(28.dp).background(Color(color), CircleShape)
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { viewModel.setColor(color) },
                            )
                        }
                    }
                }
            }
            AutoSaveTextField(person.notes, viewModel::setNotes, stringResource(R.string.person_notes), "pnotes-${person.id}",
                singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(R.string.person_measurements))
                if (latest.isEmpty()) Text(stringResource(R.string.person_no_measurements), style = MaterialTheme.typography.bodyMedium)
                // Latest value per type, in the list order (SPEC 5.2).
                state.types.filter { it.id in latest }.forEach { type ->
                    val value = latest.getValue(type.id)
                    val older = history[type.id].orEmpty().drop(1)
                    ListItem(
                        headlineContent = { Text(typeNames.getValue(type.id)) },
                        supportingContent = {
                            Text(
                                (listOf(value) + older).joinToString("  ·  ") {
                                    "${Measurements.formatCm(it.valueMm, locale)} cm (${it.date.format(dateFormat)})"
                                },
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = { editingSession = "" },
                    label = { Text(stringResource(R.string.person_add_measurements)) },
                    leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
                )
            }
            if (state.sessions.isNotEmpty()) {
                Column {
                    SectionLabel(stringResource(R.string.person_history))
                    state.sessions.forEach { session ->
                        val count = state.measurements.count { it.sessionId == session.id }
                        ListItem(
                            headlineContent = { Text(session.date.format()) },
                            supportingContent = { Text(listOf("$count×", session.note).filter { it.isNotBlank() }.joinToString(" · ")) },
                            trailingContent = {
                                IconButton(onClick = {
                                    scope.launch {
                                        viewModel.deleteSession(session.id)?.let { token -> messages.showUndo(sessionDeletedText) { viewModel.undo(token) } }
                                    }
                                }) { Icon(Icons.Outlined.Delete, stringResource(R.string.session_delete)) }
                            },
                            modifier = Modifier.clickable { editingSession = session.id },
                        )
                    }
                }
            }
            HorizontalDivider()
            Column {
                SectionLabel(stringResource(R.string.person_projects))
                if (state.projects.isEmpty()) Text(stringResource(R.string.person_no_projects), style = MaterialTheme.typography.bodyMedium)
                state.projects.forEach { project ->
                    ListItem(
                        headlineContent = { Text(project.name) },
                        supportingContent = { Text(stringResource(project.status.label)) },
                        modifier = Modifier.clickable { onOpenProject(project.id) },
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    editingSession?.let { sessionId ->
        val session = state.sessions.firstOrNull { it.id == sessionId }
        MeasurementDialog(
            types = state.types,
            typeNames = typeNames,
            initialDate = session?.date ?: LocalDate.now(),
            initialNote = session?.note.orEmpty(),
            initialValues = state.measurements.filter { it.sessionId == sessionId }.associate { it.typeId to it.valueMm },
            onSave = { date, note, values ->
                viewModel.saveSession(session?.id, date, note, values)
                editingSession = null
            },
            onDismiss = { editingSession = null },
        )
    }
}

/**
 * Enter or edit one measuring session (SPEC 5.2): date, note and a value in cm for each visible type
 * (hidden types are shown too if this session already has a value for them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementDialog(
    types: List<ListEntryRow>,
    typeNames: Map<String, String>,
    initialDate: LocalDate,
    initialNote: String,
    initialValues: Map<String, Int>,
    onSave: (LocalDate, String, Map<String, Int?>) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale
    // Saveable: typed values survive rotation. Dates as epoch days, values as a plain map.
    var epochDay by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var texts by rememberSaveable { mutableStateOf(HashMap(initialValues.mapValues { (_, v) -> Measurements.formatCm(v, locale) })) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val shown = types.filter { !it.hidden || it.id in initialValues }
    val invalid = texts.any { (_, text) -> text.isNotBlank() && Measurements.parseCm(text) == null }

    if (!pickingDate) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.session_date)) },
                    supportingContent = { Text(date.format()) },
                    modifier = Modifier.clickable { pickingDate = true },
                )
                shown.forEach { type ->
                    val text = texts[type.id].orEmpty()
                    OutlinedTextField(
                        value = text, onValueChange = { new -> texts = HashMap(texts).apply { put(type.id, new) } },
                        label = { Text(typeNames.getValue(type.id)) },
                        suffix = { Text("cm") },
                        isError = text.isNotBlank() && Measurements.parseCm(text) == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.session_note)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !invalid, onClick = {
                val values = (initialValues.keys + texts.keys).associateWith { id -> texts[id]?.let(Measurements::parseCm) }
                onSave(date, note.trim(), values)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * DayMillis)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { epochDay = it / DayMillis }
                    pickingDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(pickerState) }
    }
}
