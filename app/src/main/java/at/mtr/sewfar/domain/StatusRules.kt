package at.mtr.sewfar.domain

import java.time.LocalDate

/** Dates of a project as affected by a status change. */
data class ProjectDates(val startedOn: LocalDate?, val finishedOn: LocalDate?)

/** Automatic effects of a status change (SPEC 3.2). Dates the user set stay unless the rule clears them. */
object StatusRules {
    fun apply(from: ProjectStatus, to: ProjectStatus, dates: ProjectDates, today: LocalDate): ProjectDates {
        if (from == to) return dates
        var (started, finished) = dates
        if (to == ProjectStatus.IN_PROGRESS && started == null) started = today
        if (to == ProjectStatus.DONE && finished == null) finished = today
        if (from == ProjectStatus.DONE) finished = null
        return ProjectDates(started, finished)
    }

    /** The completion sheet opens when a project becomes Done (SPEC 3.5). */
    fun opensCompletionSheet(from: ProjectStatus, to: ProjectStatus) = to == ProjectStatus.DONE && from != ProjectStatus.DONE
}
