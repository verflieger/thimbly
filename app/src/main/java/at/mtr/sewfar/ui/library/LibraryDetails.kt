package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.res.stringResource
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
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.ui.common.DetailScaffold
import at.mtr.sewfar.ui.common.FormField
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.ReadOnlyField
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.photos.PhotoStrip
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun undo(token: DeletionToken) = write { container.projects.undo(token) }
}

/**
 * Frame of a library detail page (SPEC 2): [view] shows the record read-only (plus things that change at once, like
 * photos); ✎ opens [edit], a form over a draft of [fieldsOf] that [onSave] stores. [startEditing] opens the form
 * right away (a record just created to be filled in).
 */
@Composable
fun <T : Any> RecordScreen(
    viewModel: RecordViewModel<T>,
    startEditing: Boolean,
    deletedText: String,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    titleOf: @Composable (T) -> String,
    fieldsOf: (T) -> Map<String, String>,
    onSave: (Map<String, String>) -> Unit,
    edit: @Composable ColumnScope.(record: T, draft: Map<String, String>, set: (String, String) -> Unit) -> Unit,
    view: @Composable ColumnScope.(T) -> Unit,
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf<HashMap<String, String>?>(null) }
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
        onEdit = { autoOpened = false; draft = HashMap(fieldsOf(record)) },
        onDelete = {
            scope.launch {
                val token = viewModel.delete()
                withContext(Dispatchers.Main) { onBack() }
                if (token != null) messages.showUndo(deletedText) { viewModel.undo(token) }
            }
        },
        editing = current != null,
        dirty = current != null && current != fieldsOf(record),
        canSave = current?.get("name")?.isNotBlank() ?: true,
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
                view(record)
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
}

// ---- Pattern (SPEC 4.1) ----

class PatternViewModel(container: AppContainer, private val id: String) : RecordViewModel<PatternEntity>(container) {
    private val repo = container.patterns
    override val record = repo.observe(id)
    override val projectsFlow = repo.observeProjects(id)
    val photos: StateFlow<List<PhotoEntity>> = container.photos.observe(PhotoOwner.PATTERN, id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override suspend fun delete() = container.runInApp { repo.delete(id) }

    fun save(f: Map<String, String>) = write {
        repo.edit(id) { it.copy(name = f.getValue("name").trim(), designer = f.getValue("designer").trim(), notes = f.getValue("notes").trim()) }
    }

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
        titleOf = { it.name },
        fieldsOf = { mapOf("name" to it.name, "designer" to it.designer, "notes" to it.notes) },
        onSave = viewModel::save,
        edit = { _, d, set ->
            FormField(d.getValue("name"), { set("name", it) }, stringResource(R.string.pattern_name))
            FormField(d.getValue("designer"), { set("designer", it) }, stringResource(R.string.pattern_designer), tag = "field-designer")
            FormField(d.getValue("notes"), { set("notes", it) }, stringResource(R.string.pattern_notes), singleLine = false)
        },
    ) { pattern ->
        ReadOnlyField(stringResource(R.string.pattern_designer), pattern.designer)
        ReadOnlyField(stringResource(R.string.pattern_notes), pattern.notes)
        PhotoStrip(photos, viewModel::addPhotos, viewModel::deletePhoto, viewModel::undo, onOpenPhoto)
    }
}

// ---- Person (SPEC 4.2) ----

class PersonViewModel(container: AppContainer, private val id: String) : RecordViewModel<PersonEntity>(container) {
    private val repo = container.persons
    override val record = repo.observe(id)
    override val projectsFlow = repo.observeProjects(id)

    override suspend fun delete() = container.runInApp { repo.delete(id) }

    fun save(f: Map<String, String>) = write { repo.edit(id) { it.copy(name = f.getValue("name").trim(), notes = f.getValue("notes").trim()) } }
}

@Composable
fun PersonScreen(viewModel: PersonViewModel, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    RecordScreen(
        viewModel, startEditing = false, stringResource(R.string.person_deleted), onBack, onOpenProject,
        titleOf = { it.name },
        fieldsOf = { mapOf("name" to it.name, "notes" to it.notes) },
        onSave = viewModel::save,
        edit = { _, d, set ->
            FormField(d.getValue("name"), { set("name", it) }, stringResource(R.string.person_name))
            FormField(d.getValue("notes"), { set("notes", it) }, stringResource(R.string.person_notes), singleLine = false)
        },
    ) { person ->
        ReadOnlyField(stringResource(R.string.person_notes), person.notes)
    }
}
