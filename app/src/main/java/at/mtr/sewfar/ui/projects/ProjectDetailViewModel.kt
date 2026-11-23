package at.mtr.sewfar.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.ProjectFabricEntity
import at.mtr.sewfar.data.db.ProjectPatternEntity
import at.mtr.sewfar.domain.CoverCandidate
import at.mtr.sewfar.domain.CoverRules
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.ui.photos.ImageSource
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A linked pattern with the pattern's own data. */
data class LinkedPattern(val link: ProjectPatternEntity, val pattern: PatternEntity?)

data class ProjectDetailState(
    val loaded: Boolean = false,
    /** Null once loaded means the project doesn't exist (anymore). */
    val project: ProjectEntity? = null,
    val person: PersonEntity? = null,
    val patterns: List<LinkedPattern> = emptyList(),
    val fabrics: List<ProjectFabricEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val coverPhotoId: String? = null,
)

/**
 * Project detail (SPEC 3.3). Writes run in the app scope so edits saved while leaving the screen are not lost.
 */
class ProjectDetailViewModel(private val container: AppContainer, val projectId: String) : ViewModel() {
    private val projects = container.projects
    private val scope = container.appScope

    private val related = combine(
        projects.observePatterns(projectId),
        projects.observeFabrics(projectId),
        container.photos.observe(PhotoOwner.PROJECT, projectId),
    ) { links, fabrics, photos -> Triple(links, fabrics, photos) }

    val state: StateFlow<ProjectDetailState> = combine(
        projects.observe(projectId),
        container.persons.observe(includeArchived = true),
        container.patterns.observe(includeArchived = true),
        related,
    ) { project, persons, patterns, (links, fabrics, photos) ->
        val patternById = patterns.associateBy { it.id }
        ProjectDetailState(
            loaded = true,
            project = project,
            person = project?.personId?.let { id -> persons.firstOrNull { it.id == id } },
            patterns = links.map { LinkedPattern(it, patternById[it.patternId]) },
            fabrics = fabrics,
            photos = photos,
            coverPhotoId = CoverRules.cover(photos.map { CoverCandidate(it.id, it.tag, it.sortOrder) }, project?.coverPhotoId),
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

    fun setStatus(status: ProjectStatus) {
        scope.launch { projects.setStatus(projectId, status) }
    }

    fun addPattern(patternId: String) { scope.launch { projects.addPattern(projectId, patternId) } }
    fun setPatternSize(linkId: String, size: String) { scope.launch { projects.setPatternSize(linkId, size) } }
    suspend fun removePattern(linkId: String): DeletionToken? = projects.removePattern(linkId)

    fun addFabric() { scope.launch { projects.addFabric(projectId, "") } }
    fun editFabric(fabricId: String, change: (ProjectFabricEntity) -> ProjectFabricEntity) { scope.launch { projects.editFabric(fabricId, change) } }
    suspend fun removeFabric(fabricId: String): DeletionToken? = projects.removeFabric(fabricId)

    suspend fun createPerson(name: String): String = container.runInApp { container.persons.create(name) }
    suspend fun createPattern(name: String): String = container.runInApp { container.patterns.create(name) }

    /** Tag for photos added from the detail screen, by status: the user can change it afterwards. */
    fun defaultPhotoTag(): PhotoTag = when (state.value.project?.status) {
        ProjectStatus.IN_PROGRESS, ProjectStatus.PAUSED -> PhotoTag.PROGRESS
        ProjectStatus.DONE -> PhotoTag.FINISHED
        else -> PhotoTag.INSPIRATION
    }

    /** Adds photos; returns how many failed. */
    suspend fun addPhotos(sources: List<ImageSource>): Int {
        val tag = defaultPhotoTag()
        return container.runInApp {
            sources.count { source -> runCatching { container.photos.addProjectPhoto(projectId, tag, open = source) }.isFailure }
        }
    }

    fun setPhotoTag(photoId: String, tag: PhotoTag) { scope.launch { container.photos.setTag(photoId, tag) } }
    fun setCover(photoId: String?) { scope.launch { container.photos.setCover(projectId, photoId) } }
    suspend fun deletePhoto(photoId: String): DeletionToken? = container.photos.delete(photoId)

    suspend fun delete(): DeletionToken? = container.runInApp { projects.delete(projectId) }

    /** Undo for any deletion made from this screen; runs after the screen may be gone. */
    fun undo(token: DeletionToken) { scope.launch { container.projects.undo(token) } }
}
