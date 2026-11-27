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
    val tags: List<String> = emptyList(),
    /** Everything search looks at (SPEC 3.1). */
    val searchFields: List<String> = emptyList(),
)

data class ProjectListState(
    val loaded: Boolean = false,
    val sections: Map<ProjectStatus, List<ProjectSummary>> = emptyMap(),
    val collapsed: Set<ProjectStatus> = emptySet(),
    val isEmpty: Boolean = true,
    /** Shown while the search field is empty (SPEC 3.1): tags and people of the projects, a tap searches for one. */
    val suggestedTags: List<String> = emptyList(),
    val suggestedPeople: List<String> = emptyList(),
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
        combine(container.projects.observeAllPatternNames(), container.projects.observeAllTags()) { p, t -> p to t },
    ) { projects, persons, time, photos, (patternNames, tags) ->
        val personById = persons.associateBy { it.id }
        val timeByProject = time.groupBy { it.projectId }
        val photosByProject = photos.groupBy { it.ownerId }
        projects.map { p ->
            val entries = timeByProject[p.id].orEmpty()
            val personName = p.personId?.let(personById::get)?.name
            val projectTags = tags[p.id].orEmpty()
            ProjectSummary(
                id = p.id, name = p.name, status = p.status,
                personName = personName,
                patternNames = patternNames[p.id].orEmpty(),
                timeMillis = Durations.total(entries.filter { it.endedAt != null }.map { it.startedAt to it.endedAt }, 0),
                runningSince = entries.firstOrNull { it.endedAt == null }?.startedAt,
                coverPhotoId = coverOf(photosByProject[p.id].orEmpty(), p.coverPhotoId),
                tags = projectTags,
                searchFields = listOfNotNull(p.name, personName, p.size, p.fabric, p.notes) + patternNames[p.id].orEmpty() + projectTags,
            )
        }
    }

    val state: StateFlow<ProjectListState> = combine(summaries, query, container.settings.collapsedSections) { all, q, collapsed ->
        val matching = all.filter { SearchText.matches(q, it.searchFields) }
        ProjectListState(
            loaded = true,
            // A running timer puts its project first.
            sections = matching.sortedByDescending { it.runningSince != null }.groupBy { it.status },
            collapsed = if (q.isBlank()) collapsed else emptySet(),
            isEmpty = all.isEmpty(),
            suggestedTags = all.flatMap { it.tags }.distinctBy { SearchText.fold(it) }.sortedBy { SearchText.fold(it) },
            suggestedPeople = all.mapNotNull { it.personName?.takeIf(String::isNotBlank) }.distinct().sortedBy { SearchText.fold(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListState())

    fun setCollapsed(status: ProjectStatus, collapsed: Boolean) {
        container.appScope.launch { container.settings.setSectionCollapsed(status, collapsed) }
    }

    suspend fun create(name: String): String = container.runInApp { container.projects.create(name) }

    fun startTimer(id: String) {
        container.appScope.launch { container.projects.startTimer(id) }
    }

    fun stopTimer(id: String) {
        container.appScope.launch { container.projects.stopTimer(id) }
    }
}
