package at.mtr.sewfar.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.coverOf
import at.mtr.sewfar.domain.Durations
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.SearchText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the project list (SPEC 3.1). */
data class ProjectSummary(
    val id: String,
    val name: String,
    val status: ProjectStatus,
    val personName: String?,
    val patternNames: List<String>,
    /** Total time of finished entries; [runningSince] adds the running one. */
    val timeMillis: Long,
    val runningSince: Long?,
    val coverPhotoId: String?,
)

data class ProjectListState(
    val loaded: Boolean = false,
    val sections: Map<ProjectStatus, List<ProjectSummary>> = emptyMap(),
    val collapsed: Set<ProjectStatus> = emptySet(),
    val isEmpty: Boolean = true,
)

/** Sections in the order shown (SPEC 3.1). */
val SectionOrder = listOf(ProjectStatus.IN_PROGRESS, ProjectStatus.PLANNED, ProjectStatus.DONE)

class ProjectListViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    private val summaries = combine(
        container.projects.observeAll(),
        container.persons.observeAll(),
        container.projects.observeAllTime(),
        container.photos.observeAllOfType(PhotoOwner.PROJECT),
        container.projects.observeAllPatternNames(),
    ) { projects, persons, time, photos, patternNames ->
        val personById = persons.associateBy { it.id }
        val timeByProject = time.groupBy { it.projectId }
        val photosByProject = photos.groupBy { it.ownerId }
        projects.map { p ->
            val entries = timeByProject[p.id].orEmpty()
            ProjectSummary(
                id = p.id, name = p.name, status = p.status,
                personName = p.personId?.let(personById::get)?.name,
                patternNames = patternNames[p.id].orEmpty(),
                timeMillis = Durations.total(entries.filter { it.endedAt != null }.map { it.startedAt to it.endedAt }, 0),
                runningSince = entries.firstOrNull { it.endedAt == null }?.startedAt,
                coverPhotoId = coverOf(photosByProject[p.id].orEmpty(), p.coverPhotoId),
            )
        }
    }

    val state: StateFlow<ProjectListState> = combine(summaries, query, container.settings.collapsedSections) { all, q, collapsed ->
        val matching = all.filter { SearchText.matches(q, listOfNotNull(it.name, it.personName) + it.patternNames) }
        ProjectListState(
            loaded = true,
            // A running timer puts its project first.
            sections = matching.sortedByDescending { it.runningSince != null }.groupBy { it.status },
            collapsed = if (q.isBlank()) collapsed else emptySet(),
            isEmpty = all.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListState())

    fun setCollapsed(status: ProjectStatus, collapsed: Boolean) {
        container.appScope.launch { container.settings.setSectionCollapsed(status, collapsed) }
    }

    suspend fun create(): String = container.runInApp { container.projects.create() }

    fun startTimer(id: String) {
        container.appScope.launch { container.projects.startTimer(id) }
    }

    fun stopTimer(id: String) {
        container.appScope.launch { container.projects.stopTimer(id) }
    }
}
