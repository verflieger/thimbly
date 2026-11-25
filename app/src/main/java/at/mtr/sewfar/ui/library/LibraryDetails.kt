package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.ProjectRef
import at.mtr.sewfar.domain.MeasurementFields
import at.mtr.sewfar.domain.Measurements
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.parseCm
import at.mtr.sewfar.ui.common.DetailScaffold
import at.mtr.sewfar.ui.common.EditHeader
import at.mtr.sewfar.ui.common.EditableText
import at.mtr.sewfar.ui.common.LabeledTextField
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.MeasurementsView
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.TextDialog
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.common.formatCm
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.common.measurementLabel
import at.mtr.sewfar.ui.photos.PhotoStrip
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A loaded record: null until the first read, then [value] (null = deleted). */
data class Loaded<T>(val value: T?)

/** Shared behaviour of library detail pages: the record, the projects using it, delete with undo (SPEC 4, 5). */
abstract class RecordViewModel<T : Any>(protected val container: AppContainer) : ViewModel() {
    protected val app = container.appScope
    abstract val record: Flow<T?>
    abstract val projectsFlow: Flow<List<ProjectRef>>
    abstract suspend fun delete(): DeletionToken?

    val state: StateFlow<Loaded<T>?> by lazy { record.map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null) }
    val projects: StateFlow<List<ProjectRef>> by lazy { projectsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()) }

    protected fun write(block: suspend () -> Unit) {
        app.launch { block() }
    }

}

/**
 * Frame of a library detail page (SPEC 2): [view] shows the record read-only; its texts are edited in dialogs and
 * state changes (photos, type) apply at once. ✎ in the top bar renames ([nameOf], [onRename]). Records with a group of
 * values (measurements, setting values) also have a form over a draft of [fieldsOf]: [view] opens it with `openForm`,
 * [onSave] stores it. [startEditing] opens the form right away (a setting just created to be filled in).
 */
@Composable
fun <T : Any> RecordScreen(
    viewModel: RecordViewModel<T>,
    startEditing: Boolean,
    deletedText: String,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    titleOf: @Composable (T) -> String,
    nameOf: (T) -> String,
    nameLabel: String,
    onRename: (String) -> Unit,
    fieldsOf: (T) -> Map<String, String> = { emptyMap() },
    onSave: (Map<String, String>) -> Unit = {},
    edit: @Composable ColumnScope.(record: T, draft: Map<String, String>, set: (String, String) -> Unit) -> Unit = { _, _, _ -> },
    /** Extra check before Save is enabled. */
    valid: (Map<String, String>) -> Boolean = { true },
    view: @Composable ColumnScope.(record: T, openForm: () -> Unit) -> Unit,
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf<HashMap<String, String>?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var autoEditDone by rememberSaveable { mutableStateOf(false) }
    // The form opened by itself for a record just created: cancelling it leaves the page too.
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(loaded) { if (loaded != null && loaded?.value == null) onBack() }
    val record = loaded?.value ?: return
    if (startEditing && !autoEditDone) {
        autoEditDone = true
        autoOpened = true
        draft = HashMap(fieldsOf(record))
    }
    val current = draft
    // Fields that appear while editing (a setting's machine type changed) join the draft; typed values stay.
    val fresh = fieldsOf(record)
    if (current != null && !current.keys.containsAll(fresh.keys)) draft = HashMap(fresh + current)
    DetailScaffold(
        title = titleOf(record),
        onBack = onBack,
        onEdit = { renaming = true },
        onDelete = {
            scope.launch {
                val token = viewModel.delete()
                withContext(Dispatchers.Main) { onBack() }
                if (token != null) messages.showUndo(deletedText, token)
            }
        },
        editing = current != null,
        dirty = current != null && current != fieldsOf(record),
        canSave = current?.let(valid) ?: true,
        onSave = { current?.let(onSave); draft = null; autoOpened = false },
        onCancel = { draft = null; if (autoOpened) onBack() },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (current != null) {
                edit(record, current) { key, value -> draft = HashMap(current).apply { put(key, value) } }
            } else {
                view(record) { autoOpened = false; draft = HashMap(fieldsOf(record)) }
                if (projects.isNotEmpty()) {
                    Column {
                        SectionLabel(stringResource(R.string.used_in))
                        projects.forEach { p ->
                            ListItem(
                                headlineContent = { Text(p.name.ifBlank { stringResource(R.string.project_untitled) }) },
                                supportingContent = { Text(stringResource(p.status.label)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onOpenProject(p.id) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (renaming) {
        TextDialog(
            stringResource(R.string.action_rename), nameLabel, initial = nameOf(record),
            onSave = { onRename(it); renaming = false }, onDismiss = { renaming = false },
        )
    }
}

// ---- Pattern (SPEC 4.1) ----

class PatternViewModel(container: AppContainer, private val id: String) : RecordViewModel<PatternEntity>(container) {
    private val repo = container.patterns
    override val record = repo.observe(id)
    override val projectsFlow = repo.observeProjects(id)
    val photos: StateFlow<List<PhotoEntity>> = container.photos.observe(PhotoOwner.PATTERN, id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override suspend fun delete() = container.runInApp { repo.delete(id) }

    fun rename(name: String) = write { repo.edit(id) { it.copy(name = name) } }
    fun setDesigner(text: String) = write { repo.edit(id) { it.copy(designer = text) } }
    fun setNotes(text: String) = write { repo.edit(id) { it.copy(notes = text) } }

    suspend fun addPhotos(sources: List<() -> InputStream>): Int = container.runInApp {
        sources.count { runCatching { container.photos.add(PhotoOwner.PATTERN, id, it) }.isFailure }
    }

    suspend fun deletePhoto(photoId: String) = container.runInApp { container.photos.delete(photoId) }
}

@Composable
fun PatternScreen(viewModel: PatternViewModel, onBack: () -> Unit, onOpenProject: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    RecordScreen(
        viewModel, startEditing = false, stringResource(R.string.pattern_deleted), onBack, onOpenProject,
        titleOf = { it.name }, nameOf = { it.name }, nameLabel = stringResource(R.string.pattern_name), onRename = viewModel::rename,
    ) { pattern, _ ->
        EditableText(stringResource(R.string.pattern_designer), pattern.designer, "designer", singleLine = true, onSave = viewModel::setDesigner)
        EditableText(stringResource(R.string.pattern_notes), pattern.notes, "notes", onSave = viewModel::setNotes)
        PhotoStrip(photos, viewModel::addPhotos, viewModel::deletePhoto, onOpenPhoto)
    }
}

// ---- Person (SPEC 4.2) ----

/** A person with their newest measurements (SPEC 4.2). */
data class PersonDetail(val person: PersonEntity, val measurements: Measurements?)

class PersonViewModel(container: AppContainer, private val id: String) : RecordViewModel<PersonDetail>(container) {
    private val repo = container.persons
    override val record = combine(repo.observe(id), repo.observeMeasurements(id)) { p, m -> p?.let { PersonDetail(it, m) } }
    override val projectsFlow = repo.observeProjects(id)

    override suspend fun delete() = container.runInApp { repo.delete(id) }

    fun rename(name: String) = write { repo.edit(id) { it.copy(name = name) } }
    fun setNotes(text: String) = write { repo.edit(id) { it.copy(notes = text) } }

    /** Every `m:<key>` measurement of the form (blank = none; the form only saves valid numbers). */
    fun saveMeasurements(f: Map<String, String>) = write {
        repo.saveMeasurements(id, f.filterKeys { it.startsWith(MeasurePrefix) }.mapNotNull { (k, v) -> parseCm(v)?.let { k.removePrefix(MeasurePrefix) to it } }.toMap())
    }
}

private const val MeasurePrefix = "m:"

@Composable
fun PersonScreen(viewModel: PersonViewModel, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    RecordScreen(
        viewModel, startEditing = false, stringResource(R.string.person_deleted), onBack, onOpenProject,
        titleOf = { it.person.name }, nameOf = { it.person.name }, nameLabel = stringResource(R.string.person_name),
        onRename = viewModel::rename,
        fieldsOf = { d -> MeasurementFields.associate { "$MeasurePrefix$it" to (d.measurements?.valuesCm?.get(it)?.let(::formatCm) ?: "") } },
        onSave = viewModel::saveMeasurements,
        valid = { d -> d.values.all { it.isBlank() || parseCm(it) != null } },
        edit = { _, d, set ->
            SectionLabel(stringResource(R.string.measurements))
            // Two per row like machine settings: all values are short numbers.
            MeasurementFields.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        val text = d.getValue("$MeasurePrefix$key")
                        LabeledTextField(
                            text, { set("$MeasurePrefix$key", it) }, stringResource(measurementLabel(key)), Modifier.weight(1f),
                            fieldModifier = Modifier.testTag("measure-$key"), suffix = stringResource(R.string.unit_cm),
                            isError = text.isNotBlank() && parseCm(text) == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        },
    ) { detail, openForm ->
        val m = detail.measurements
        Column {
            EditHeader(stringResource(R.string.measurements), "measurements", openForm)
            if (m != null && m.valuesCm.isNotEmpty()) {
                MeasurementsView(stringResource(R.string.measured_on, m.measuredOn.format()), m, Modifier.clickable(onClick = openForm))
            } else {
                Text(
                    stringResource(R.string.not_measured), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = openForm),
                )
            }
        }
        EditableText(stringResource(R.string.person_notes), detail.person.notes, "notes", onSave = viewModel::setNotes)
    }
}
