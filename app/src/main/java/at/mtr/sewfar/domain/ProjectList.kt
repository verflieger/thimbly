package at.mtr.sewfar.domain

import java.time.LocalDate

/** One linked pattern as shown on a project card. */
data class PatternRef(val id: String, val name: String, val designer: String, val sizeSewn: String)

/** Everything the project list needs about one project (SPEC 3.1). */
data class ProjectSummary(
    val id: String,
    val name: String,
    val type: ProjectType,
    val status: ProjectStatus,
    val personId: String?,
    val personName: String?,
    val personColor: Int?,
    val patterns: List<PatternRef>,
    val size: String,
    val notes: String,
    val fabrics: List<String>,
    val startedOn: LocalDate?,
    val finishedOn: LocalDate?,
    val createdOn: LocalDate,
    val lastActivityAt: Long,
    val coverPhotoId: String?,
) {
    /** SPEC 3.1: finish year for Done, else start year, else creation year. */
    val year: Int
        get() = (if (status == ProjectStatus.DONE) finishedOn else null)?.year ?: startedOn?.year ?: createdOn.year
}

data class ProjectFilter(
    val personId: String? = null,
    val patternId: String? = null,
    val type: ProjectType? = null,
    val year: Int? = null,
) {
    val isActive: Boolean get() = personId != null || patternId != null || type != null || year != null
}

data class ProjectSection(val status: ProjectStatus, val projects: List<ProjectSummary>)

object ProjectList {
    /** Section order of the list: active work first (SPEC 3.1). Pickers use [ProjectStatus] declaration order. */
    val SectionOrder = listOf(
        ProjectStatus.IN_PROGRESS, ProjectStatus.PLANNED, ProjectStatus.PAUSED, ProjectStatus.IDEA, ProjectStatus.DONE,
    )

    fun filter(projects: List<ProjectSummary>, query: String, filter: ProjectFilter): List<ProjectSummary> =
        projects.filter { p ->
            (filter.personId == null || p.personId == filter.personId) &&
                (filter.patternId == null || p.patterns.any { it.id == filter.patternId }) &&
                (filter.type == null || p.type == filter.type) &&
                (filter.year == null || p.year == filter.year) &&
                SearchText.matches(
                    query,
                    listOfNotNull(p.name, p.notes, p.personName) + p.fabrics +
                        p.patterns.flatMap { listOf(it.name, it.designer) },
                )
        }

    /** Non-empty sections in [SectionOrder], each sorted by last activity, newest first. */
    fun sections(projects: List<ProjectSummary>): List<ProjectSection> {
        val byStatus = projects.groupBy { it.status }
        return SectionOrder.mapNotNull { status ->
            byStatus[status]?.sortedByDescending { it.lastActivityAt }?.let { ProjectSection(status, it) }
        }
    }

    /** Gallery view: finished projects, newest finish first (SPEC 3.1). */
    fun gallery(projects: List<ProjectSummary>): List<ProjectSummary> =
        projects.filter { it.status == ProjectStatus.DONE }
            .sortedWith(compareByDescending<ProjectSummary> { it.finishedOn }.thenByDescending { it.lastActivityAt })

    /** Years offered by the year filter, newest first. */
    fun years(projects: List<ProjectSummary>): List<Int> = projects.map { it.year }.distinct().sortedDescending()
}
