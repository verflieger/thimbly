package at.mtr.sewfar.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.LogEntryEntity
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.ProjectFabricEntity
import at.mtr.sewfar.data.db.ProjectLessonEntity
import at.mtr.sewfar.data.db.ProjectPatternEntity
import at.mtr.sewfar.domain.CoverCandidate
import at.mtr.sewfar.domain.CoverRules
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.ui.photos.ImageSource
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A linked pattern with the pattern's own data. */
data class LinkedPattern(val link: ProjectPatternEntity, val pattern: PatternEntity?)

/** The project a "Sew again" copy came from, for the "Last time" card (SPEC 3.7). */
data class SourceInfo(val project: ProjectEntity, val patterns: List<LinkedPattern>, val lessons: List<ProjectLessonEntity>)

data class ProjectDetailState(
    val loaded: Boolean = false,
    /** Null once loaded means the project doesn't exist (anymore). */
    val project: ProjectEntity? = null,
    val person: PersonEntity? = null,
    val personPhotoId: String? = null,
    val patterns: List<LinkedPattern> = emptyList(),
    val fabrics: List<ProjectFabricEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val coverPhotoId: String? = null,
    val log: List<LogEntryEntity> = emptyList(),
    val lessons: List<ProjectLessonEntity> = emptyList(),
    /** All lesson tags incl. hidden ones (hidden tags still show where set, SPEC 1). */
    val lessonTags: List<ListEntryRow> = emptyList(),
    val source: SourceInfo? = null,
)

/**
 * Project detail (SPEC 3.3–3.7). Writes run in the app scope so edits saved while leaving the screen are not lost.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModel(private val container: AppContainer, val projectId: String) : ViewModel() {
    private val projects = container.projects
    private val scope = container.appScope

    private val patternsById: Flow<Map<String, PatternEntity>> =
        container.patterns.observe(includeArchived = true).map { list -> list.associateBy { it.id } }

    private val related = combine(
        projects.observePatterns(projectId),
        projects.observeFabrics(projectId),
        container.photos.observe(PhotoOwner.PROJECT, projectId),
    ) { links, fabrics, photos -> Triple(links, fabrics, photos) }

    private val diary = combine(
        projects.observeLog(projectId),
        projects.observeLessons(projectId),
        container.lists.observe(ListKind.LESSON_TAG),
    ) { log, lessons, tags -> Triple(log, lessons, tags) }

    private val people = combine(
        container.persons.observe(includeArchived = true),
        container.photos.observeAllOfType(PhotoOwner.PERSON),
    ) { persons, photos -> persons to photos }

    private val source: Flow<SourceInfo?> = projects.observe(projectId)
        .map { it?.sourceProjectId }
        .distinctUntilChanged()
        .flatMapLatest { sourceId ->
            if (sourceId == null) {
                flowOf(null)
            } else {
                combine(projects.observe(sourceId), projects.observePatterns(sourceId), projects.observeLessons(sourceId), patternsById) {
                        project, links, lessons, byId ->
                    project?.let { SourceInfo(it, links.map { l -> LinkedPattern(l, byId[l.patternId]) }, lessons) }
                }
            }
        }

    private val core = combine(projects.observe(projectId), people, patternsById, related) { project, (persons, personPhotos), byId, rel ->
        Core(project, persons, personPhotos, byId, rel)
    }

    private data class Core(
        val project: ProjectEntity?,
        val persons: List<PersonEntity>,
        val personPhotos: List<PhotoEntity>,
        val patternsById: Map<String, PatternEntity>,
        val related: Triple<List<ProjectPatternEntity>, List<ProjectFabricEntity>, List<PhotoEntity>>,
    )

    val state: StateFlow<ProjectDetailState> = combine(core, diary, source) { c, (log, lessons, tags), src ->
        val (links, fabrics, photos) = c.related
        val project = c.project
        ProjectDetailState(
            loaded = true,
            project = project,
            person = project?.personId?.let { id -> c.persons.firstOrNull { it.id == id } },
            personPhotoId = project?.personId?.let { id -> c.personPhotos.firstOrNull { it.ownerId == id }?.id },
            patterns = links.map { LinkedPattern(it, c.patternsById[it.patternId]) },
            fabrics = fabrics,
            photos = photos,
            coverPhotoId = CoverRules.cover(photos.map { CoverCandidate(it.id, it.tag, it.sortOrder) }, project?.coverPhotoId),
            log = log,
            lessons = lessons,
            lessonTags = tags,
            source = src,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailState())

    /** Selectable (non-archived) persons and patterns for the pickers. */
    val persons: StateFlow<List<PersonEntity>> =
        container.persons.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val patterns: StateFlow<List<PatternEntity>> =
        container.patterns.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Atomic per-field edit (SPEC 3.3); see [at.mtr.sewfar.data.ProjectRepository.edit]. */
    private fun edit(change: (ProjectEntity) -> ProjectEntity) {
        scope.launch { projects.edit(projectId, change) }
    }

    fun rename(name: String) { if (name.isNotBlank()) edit { it.copy(name = name) } }
    fun setType(type: ProjectType) = edit { it.copy(type = type) }
    fun setPerson(personId: String?) = edit { it.copy(personId = personId) }
    fun setSize(size: String) = edit { it.copy(size = size) }
    fun setNotes(notes: String) = edit { it.copy(notes = notes) }
    fun setStarted(date: LocalDate?) = edit { it.copy(startedOn = date) }
    fun setFinished(date: LocalDate?) = edit { it.copy(finishedOn = date) }
    fun setRating(rating: Int?) = edit { it.copy(rating = rating) }
    fun setCompletionNotes(text: String) = edit { it.copy(completionNotes = text) }

    /** Changes the status (SPEC 3.2); true if the completion sheet should open (SPEC 3.5). */
    suspend fun setStatus(status: ProjectStatus): Boolean =
        container.runInApp { projects.setStatus(projectId, status).opensCompletionSheet }

    fun addPattern(patternId: String) { scope.launch { projects.addPattern(projectId, patternId) } }
    fun setPatternSize(linkId: String, size: String) { scope.launch { projects.setPatternSize(linkId, size) } }
    suspend fun removePattern(linkId: String): DeletionToken? = container.runInApp { projects.removePattern(linkId) }

    fun addFabric() { scope.launch { projects.addFabric(projectId, "") } }
    fun editFabric(fabricId: String, change: (ProjectFabricEntity) -> ProjectFabricEntity) { scope.launch { projects.editFabric(fabricId, change) } }
    suspend fun removeFabric(fabricId: String): DeletionToken? = container.runInApp { projects.removeFabric(fabricId) }

    fun setLesson(projectPatternId: String?, tagId: String, set: Boolean) {
        scope.launch { projects.setLesson(projectId, projectPatternId, tagId, set) }
    }

    suspend fun createPerson(name: String): String = container.runInApp { container.persons.create(name) }
    suspend fun createPattern(name: String): String = container.runInApp { container.patterns.create(name) }

    // ---- Progress log (SPEC 3.4) ----

    /** Default time of a new note (the app clock, replaceable in tests). */
    fun now(): Long = container.time.nowMillis()

    fun addNote(text: String, at: Long) { scope.launch { projects.addNote(projectId, text, at) } }
    fun editNote(entry: LogEntryEntity, text: String, at: Long) { scope.launch { projects.updateNote(entry.copy(text = text, at = at)) } }
    suspend fun deleteLogEntry(entryId: String): DeletionToken? = container.runInApp { projects.deleteLogEntry(entryId) }

    // ---- Photos (SPEC 3.6) ----

    /** Tag for photos added from the detail screen, by status: the user can change it afterwards. */
    fun defaultPhotoTag(): PhotoTag = when (state.value.project?.status) {
        ProjectStatus.IN_PROGRESS, ProjectStatus.PAUSED -> PhotoTag.PROGRESS
        ProjectStatus.DONE -> PhotoTag.FINISHED
        else -> PhotoTag.INSPIRATION
    }

    /** Adds photos with [tag] (default by status), optionally to a log entry; returns the IDs of stored photos and how many failed. */
    suspend fun addPhotos(sources: List<ImageSource>, tag: PhotoTag = defaultPhotoTag(), logEntryId: String? = null): Pair<List<String>, Int> =
        container.runInApp {
            val results = sources.map { source -> runCatching { container.photos.addProjectPhoto(projectId, tag, logEntryId, open = source) } }
            results.mapNotNull { it.getOrNull() } to results.count { it.isFailure }
        }

    fun setPhotoTag(photoId: String, tag: PhotoTag) { scope.launch { container.photos.setTag(photoId, tag) } }
    fun setCover(photoId: String?) { scope.launch { container.photos.setCover(projectId, photoId) } }
    suspend fun deletePhoto(photoId: String): DeletionToken? = container.runInApp { container.photos.delete(photoId) }

    // ---- Project ----

    /** "Sew again" (SPEC 3.7); returns the new project's ID. */
    suspend fun sewAgain(): String? = container.runInApp { projects.sewAgain(projectId) }

    suspend fun delete(): DeletionToken? = container.runInApp { projects.delete(projectId) }

    /** Undo for any deletion made from this screen; runs after the screen may be gone. */
    fun undo(token: DeletionToken) { scope.launch { container.projects.undo(token) } }
}
