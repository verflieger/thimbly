package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
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
import at.mtr.sewfar.ui.common.AutoSaveTextField
import at.mtr.sewfar.ui.common.DetailScaffold
import at.mtr.sewfar.ui.common.EditableName
import at.mtr.sewfar.ui.common.SerialWriter
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.NameField
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.photos.PhotoStrip
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A loaded record: null until the first read, then [value] (null = deleted). */
data class Loaded<T>(val value: T?)

/**
 * Shared behaviour of library detail screens: the record, its name (saved as typed), the projects using it, and
 * discarding a new record left empty (SPEC 3.1, 4).
 */
abstract class RecordViewModel<T : Any>(protected val container: AppContainer, private val isNew: Boolean) : ViewModel() {
    protected val app = container.appScope
    protected val writer = SerialWriter(app)
    abstract val record: Flow<T?>
    abstract val projectsFlow: Flow<List<ProjectRef>>
    protected abstract fun nameOf(record: T): String
    protected abstract suspend fun saveName(name: String)
    protected abstract suspend fun discardIfEmpty()
    abstract suspend fun delete(): DeletionToken?

    val name by lazy { EditableName(app, writer) { saveName(it.trim()) } }
    val state: StateFlow<Loaded<T>?> by lazy { record.map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null) }
    val projects: StateFlow<List<ProjectRef>> by lazy { projectsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()) }

    protected fun start() {
        viewModelScope.launch { record.filterNotNull().collect { name.load(nameOf(it)) } }
    }

    protected fun write(block: suspend () -> Unit) = writer.launch(block)

    protected suspend fun <R> run(block: suspend () -> R): R = writer.run(block)

    fun undo(token: DeletionToken) = write { container.projects.undo(token) }

    /** Saves the name; a new record left empty is discarded (SPEC 2). */
    override fun onCleared() {
        writer.launchLast {
            name.flushInLock()
            if (isNew) discardIfEmpty()
        }
    }
}

/** Frame of a library detail screen: name field, [content], "Used in" projects, delete with undo. */
@Composable
fun <T : Any> RecordScreen(
    viewModel: RecordViewModel<T>,
    isNew: Boolean,
    nameLabel: String,
    deletedText: String,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(loaded) { if (loaded != null && loaded?.value == null) onBack() }
    val record = loaded?.value ?: return
    DetailScaffold(
        onBack = onBack,
        onDelete = {
            scope.launch {
                val token = viewModel.delete()
                withContext(Dispatchers.Main) { onBack() }
                if (token != null) messages.showUndo(deletedText) { viewModel.undo(token) }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NameField(viewModel.name, nameLabel, focus = isNew)
            content(record)
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

// ---- Pattern (SPEC 4.1) ----

class PatternViewModel(container: AppContainer, private val id: String, isNew: Boolean) : RecordViewModel<PatternEntity>(container, isNew) {
    private val repo = container.patterns
    override val record = repo.observe(id)
    override val projectsFlow = repo.observeProjects(id)
    val photos: StateFlow<List<PhotoEntity>> = container.photos.observe(PhotoOwner.PATTERN, id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { start() }

    override fun nameOf(record: PatternEntity) = record.name
    override suspend fun saveName(name: String) = repo.edit(id) { it.copy(name = name) }
    override suspend fun discardIfEmpty() = repo.discardIfEmpty(id)
    override suspend fun delete() = run { repo.delete(id) }

    fun setDesigner(text: String) = write { repo.edit(id) { it.copy(designer = text.trim()) } }
    fun setNotes(text: String) = write { repo.edit(id) { it.copy(notes = text) } }

    suspend fun addPhotos(sources: List<() -> InputStream>): Int = run {
        sources.count { runCatching { container.photos.add(PhotoOwner.PATTERN, id, it) }.isFailure }
    }

    suspend fun deletePhoto(photoId: String) = run { container.photos.delete(photoId) }
}

@Composable
fun PatternScreen(viewModel: PatternViewModel, isNew: Boolean, onBack: () -> Unit, onOpenProject: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    RecordScreen(viewModel, isNew, stringResource(R.string.pattern_name), stringResource(R.string.pattern_deleted), onBack, onOpenProject) { pattern ->
        AutoSaveTextField(
            pattern.designer, viewModel::setDesigner, stringResource(R.string.pattern_designer), "designer-${pattern.id}",
            Modifier.fillMaxWidth(),
        )
        AutoSaveTextField(
            pattern.notes, viewModel::setNotes, stringResource(R.string.pattern_notes), "notes-${pattern.id}",
            Modifier.fillMaxWidth(), singleLine = false, minLines = 3,
        )
        PhotoStrip(photos, viewModel::addPhotos, viewModel::deletePhoto, viewModel::undo, onOpenPhoto)
    }
}

// ---- Person (SPEC 4.2) ----

class PersonViewModel(container: AppContainer, private val id: String, isNew: Boolean) : RecordViewModel<PersonEntity>(container, isNew) {
    private val repo = container.persons
    override val record = repo.observe(id)
    override val projectsFlow = repo.observeProjects(id)

    init { start() }

    override fun nameOf(record: PersonEntity) = record.name
    override suspend fun saveName(name: String) = repo.edit(id) { it.copy(name = name) }
    override suspend fun discardIfEmpty() = repo.discardIfEmpty(id)
    override suspend fun delete() = run { repo.delete(id) }

    fun setNotes(text: String) = write { repo.edit(id) { it.copy(notes = text) } }
}

@Composable
fun PersonScreen(viewModel: PersonViewModel, isNew: Boolean, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    RecordScreen(viewModel, isNew, stringResource(R.string.person_name), stringResource(R.string.person_deleted), onBack, onOpenProject) { person ->
        AutoSaveTextField(
            person.notes, viewModel::setNotes, stringResource(R.string.person_notes), "notes-${person.id}",
            Modifier.fillMaxWidth(), singleLine = false, minLines = 3,
        )
    }
}
