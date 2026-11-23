package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Replay
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.domain.Amounts
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.ui.common.AutoSaveTextField
import at.mtr.sewfar.ui.common.Avatar
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.PickerItem
import at.mtr.sewfar.ui.common.currentLocale
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.common.icon
import at.mtr.sewfar.ui.common.label
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DayMillis = 86_400_000L

/** Project detail, edited in place (SPEC 3.3). Lessons, log and sew again follow in M1.6. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenCompletion: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.project_deleted)
    var menuOpen by remember { mutableStateOf(false) }

    // Deleted elsewhere (or undo of creation): nothing to show.
    LaunchedEffect(state.loaded, state.project) { if (state.loaded && state.project == null) onBack() }
    val project = state.project ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sew_again)) },
                            leadingIcon = { Icon(Icons.Outlined.Replay, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { viewModel.sewAgain()?.let { id -> withContext(Dispatchers.Main) { onOpenProject(id) } } }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.project_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val token = viewModel.delete()
                                    withContext(Dispatchers.Main) { onBack() }
                                    if (token != null) messages.showUndo(deletedText) { viewModel.undo(token) }
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AutoSaveTextField(
                savedValue = project.name, onSave = viewModel::rename,
                label = stringResource(R.string.project_name), key = "name-${project.id}",
                isValid = { it.isNotBlank() }, modifier = Modifier.fillMaxWidth().testTag("projectName"),
            )
            val source = state.source
            if (source != null && project.status != ProjectStatus.DONE) {
                LastTimeCard(source, state.lessonTags, onOpenProject)
            }
            StatusAndType(project, { status ->
                scope.launch { if (viewModel.setStatus(status)) withContext(Dispatchers.Main) { onOpenCompletion() } }
            }, viewModel::setType)
            PersonRow(state, viewModel)
            ProjectPhotos(state, viewModel, onOpenPhoto)
            HorizontalDivider()
            Patterns(state, viewModel)
            HorizontalDivider()
            Fabrics(state, viewModel)
            HorizontalDivider()
            AutoSaveTextField(
                savedValue = project.notes, onSave = viewModel::setNotes,
                label = stringResource(R.string.project_notes), key = "notes-${project.id}",
                singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            Dates(project, viewModel::setStarted, viewModel::setFinished)
            // SPEC 3.3: shown when Done or when there is already something to show.
            if (project.status == ProjectStatus.DONE || state.lessons.isNotEmpty() || project.rating != null || project.completionNotes.isNotBlank()) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.project_lessons))
                    LessonTagRows(state.patterns, state.lessonTags, state.lessons.keys(), viewModel::setLesson)
                    RatingBar(project.rating, viewModel::setRating)
                    AutoSaveTextField(
                        savedValue = project.completionNotes, onSave = viewModel::setCompletionNotes,
                        label = stringResource(R.string.completion_notes), key = "cnotes-${project.id}",
                        singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HorizontalDivider()
            ProjectLog(state, viewModel, onOpenPhoto)
            Spacer(Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusAndType(project: ProjectEntity, onStatus: (ProjectStatus) -> Unit, onType: (ProjectType) -> Unit) {
    var statusOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.project_status))
        Column {
            AssistChip(
                onClick = { statusOpen = true },
                label = { Text(stringResource(project.status.label)) },
                leadingIcon = { Icon(project.status.icon, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("statusChip"),
            )
            DropdownMenu(statusOpen, onDismissRequest = { statusOpen = false }) {
                ProjectStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(stringResource(status.label)) },
                        leadingIcon = { Icon(status.icon, null) },
                        onClick = { statusOpen = false; onStatus(status) },
                    )
                }
            }
        }
        SectionLabel(stringResource(R.string.project_type))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectType.entries.forEach { type ->
                FilterChip(
                    selected = project.type == type, onClick = { onType(type) },
                    label = { Text(stringResource(type.label)) },
                    leadingIcon = { Icon(type.icon, null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun PersonRow(state: ProjectDetailState, viewModel: ProjectDetailViewModel) {
    val persons by viewModel.persons.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val person = state.person
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.project_person))
        AssistChip(
            onClick = { picking = true },
            label = {
                Text(
                    when {
                        person == null -> stringResource(R.string.project_no_person)
                        person.archivedAt != null -> stringResource(R.string.project_archived_suffix, person.name)
                        else -> person.name
                    },
                )
            },
            leadingIcon = {
                if (person != null) Avatar(person.name, person.avatarColor, size = 18.dp, photoId = state.personPhotoId)
                else Icon(Icons.Outlined.PersonOutline, null, Modifier.size(18.dp))
            },
        )
    }
    if (picking) {
        EntityPicker(
            title = stringResource(R.string.picker_choose_person),
            items = persons.map { PickerItem(it.id, it.name, avatarColor = it.avatarColor) },
            noneLabel = stringResource(R.string.project_no_person),
            onPick = { viewModel.setPerson(it); picking = false },
            onCreate = { name -> scope.launch { viewModel.setPerson(viewModel.createPerson(name)); picking = false } },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Patterns(state: ProjectDetailState, viewModel: ProjectDetailViewModel) {
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val removedText = stringResource(R.string.action_remove)
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val project = state.project ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.project_patterns))
        state.patterns.forEach { (link, pattern) ->
            val name = pattern?.name.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pattern?.archivedAt != null) stringResource(R.string.project_archived_suffix, name) else name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!pattern?.designer.isNullOrBlank()) {
                        Text(pattern!!.designer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AutoSaveTextField(
                    savedValue = link.sizeSewn, onSave = { viewModel.setPatternSize(link.id, it) },
                    label = stringResource(R.string.project_size_sewn), key = "size-${link.id}",
                    modifier = Modifier.width(140.dp),
                )
                IconButton(onClick = {
                    scope.launch {
                        viewModel.removePattern(link.id)?.let { token -> messages.showUndo("$removedText: $name") { viewModel.undo(token) } }
                    }
                }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove)) }
            }
        }
        if (state.patterns.isEmpty()) {
            AutoSaveTextField(
                savedValue = project.size, onSave = viewModel::setSize,
                label = stringResource(R.string.project_size), key = "projectSize-${project.id}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AssistChip(
            onClick = { picking = true },
            label = { Text(stringResource(R.string.project_add_pattern)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        )
    }
    if (picking) {
        val linked = state.patterns.map { it.link.patternId }.toSet()
        EntityPicker(
            title = stringResource(R.string.picker_choose_pattern),
            items = patterns.filter { it.id !in linked }.map { PickerItem(it.id, it.name, it.designer) },
            onPick = { id -> id?.let(viewModel::addPattern); picking = false },
            onCreate = { name -> scope.launch { viewModel.addPattern(viewModel.createPattern(name)); picking = false } },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Fabrics(state: ProjectDetailState, viewModel: ProjectDetailViewModel) {
    val messages = LocalMessages.current
    val removedText = stringResource(R.string.action_remove)
    val invalidText = stringResource(R.string.project_fabric_amount_invalid)
    val locale = currentLocale
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.project_fabrics))
        state.fabrics.forEach { fabric ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoSaveTextField(
                    savedValue = fabric.description, onSave = { text -> viewModel.editFabric(fabric.id) { it.copy(description = text) } },
                    label = stringResource(R.string.project_fabric_description), key = "fabric-${fabric.id}",
                    modifier = Modifier.weight(1f),
                )
                AutoSaveTextField(
                    savedValue = fabric.amountCm?.let { Amounts.formatMeters(it, locale) }.orEmpty(),
                    onSave = { text ->
                        val amount = (Amounts.parseMeters(text) as? Amounts.ParseResult.Cm)?.cm
                        viewModel.editFabric(fabric.id) { it.copy(amountCm = amount) }
                    },
                    label = stringResource(R.string.project_fabric_amount), key = "amount-${fabric.id}",
                    isValid = { Amounts.parseMeters(it) != Amounts.ParseResult.Invalid }, errorText = invalidText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(112.dp),
                )
                IconButton(onClick = {
                    scope.launch {
                        viewModel.removeFabric(fabric.id)?.let { token ->
                            messages.showUndo("$removedText: ${fabric.description}".trimEnd(':', ' ')) { viewModel.undo(token) }
                        }
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove)) }
            }
        }
        AssistChip(
            onClick = viewModel::addFabric,
            label = { Text(stringResource(R.string.project_add_fabric)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun Dates(project: ProjectEntity, onStarted: (LocalDate?) -> Unit, onFinished: (LocalDate?) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.project_dates))
        DateRow(stringResource(R.string.project_started), project.startedOn, onStarted)
        DateRow(stringResource(R.string.project_finished), project.finishedOn, onFinished)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(date?.format() ?: stringResource(R.string.project_date_none)) },
        modifier = Modifier.clickable { picking = true },
    )
    if (picking) {
        // DatePicker works in UTC millis.
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).toEpochDay() * DayMillis)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / DayMillis)) }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                Row {
                    if (date != null) {
                        TextButton(onClick = { onChange(null); picking = false }) { Text(stringResource(R.string.action_clear_date)) }
                    }
                    TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        ) { DatePicker(pickerState) }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
