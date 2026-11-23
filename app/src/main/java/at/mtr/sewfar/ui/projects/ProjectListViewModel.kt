package at.mtr.sewfar.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ProjectSummaries
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.domain.ProjectFilter
import at.mtr.sewfar.domain.ProjectList
import at.mtr.sewfar.domain.ProjectSection
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectSummary
import at.mtr.sewfar.domain.ProjectType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectListState(
    val loaded: Boolean = false,
    /** True if there are no projects at all (as opposed to none matching). */
    val empty: Boolean = true,
    val sections: List<ProjectSection> = emptyList(),
    val gallery: List<ProjectSummary> = emptyList(),
    val collapsed: Set<ProjectStatus> = emptySet(),
    val query: String = "",
    val filter: ProjectFilter = ProjectFilter(),
    val years: List<Int> = emptyList(),
    /** Persons and patterns used by at least one project, for the filters. */
    val filterPersons: List<Pair<String, String>> = emptyList(),
    val filterPatterns: List<Pair<String, String>> = emptyList(),
)

/** Project list (SPEC 3.1) and the data the new-project dialog needs. */
class ProjectListViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ProjectFilter())
    val galleryMode = MutableStateFlow(false)

    private val summaries = ProjectSummaries.observe(container.projects, container.persons, container.patterns, container.photos)

    val state: StateFlow<ProjectListState> =
        combine(summaries, query, filter, container.settings.collapsedSections) { all, q, f, collapsed ->
            val shown = ProjectList.filter(all, q, f)
            ProjectListState(
                loaded = true,
                empty = all.isEmpty(),
                sections = ProjectList.sections(shown),
                gallery = ProjectList.gallery(shown),
                collapsed = collapsed,
                query = q,
                filter = f,
                years = ProjectList.years(all),
                filterPersons = all.mapNotNull { p -> p.personId?.let { it to p.personName.orEmpty() } }
                    .distinct().sortedBy { it.second.lowercase() },
                filterPatterns = all.flatMap { p -> p.patterns.map { it.id to it.name } }
                    .distinct().sortedBy { it.second.lowercase() },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListState())

    /** Selectable (non-archived) persons and patterns for the pickers. */
    val persons: StateFlow<List<PersonEntity>> =
        container.persons.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val patterns: StateFlow<List<PatternEntity>> =
        container.patterns.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) { query.value = text }
    fun setFilter(newFilter: ProjectFilter) { filter.value = newFilter }

    fun toggleSection(status: ProjectStatus, collapsed: Boolean) {
        container.appScope.launch { container.settings.setSectionCollapsed(status, collapsed) }
    }

    suspend fun createPerson(name: String): String = container.persons.create(name)
    suspend fun createPattern(name: String): String = container.patterns.create(name)

    suspend fun createProject(name: String, type: ProjectType, personId: String?, patternIds: List<String>): String =
        container.projects.create(name, type, personId, patternIds)
}
