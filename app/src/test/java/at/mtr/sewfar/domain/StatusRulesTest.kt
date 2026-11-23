package at.mtr.sewfar.domain

import at.mtr.sewfar.domain.ProjectStatus.DONE
import at.mtr.sewfar.domain.ProjectStatus.IDEA
import at.mtr.sewfar.domain.ProjectStatus.IN_PROGRESS
import at.mtr.sewfar.domain.ProjectStatus.PAUSED
import at.mtr.sewfar.domain.ProjectStatus.PLANNED
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC 3.2 */
class StatusRulesTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val earlier = LocalDate.of(2026, 10, 1)
    private val none = ProjectDates(null, null)

    @Test
    fun inProgressSetsStartOnlyIfEmpty() {
        assertEquals(ProjectDates(today, null), StatusRules.apply(PLANNED, IN_PROGRESS, none, today))
        assertEquals(ProjectDates(earlier, null), StatusRules.apply(PAUSED, IN_PROGRESS, ProjectDates(earlier, null), today))
    }

    @Test
    fun doneSetsFinishOnlyIfEmptyAndDoesNotTouchStart() {
        assertEquals(ProjectDates(null, today), StatusRules.apply(PLANNED, DONE, none, today))
        assertEquals(ProjectDates(earlier, earlier), StatusRules.apply(IN_PROGRESS, DONE, ProjectDates(earlier, earlier), today))
    }

    @Test
    fun leavingDoneClearsFinish() {
        assertEquals(ProjectDates(earlier, null), StatusRules.apply(DONE, PAUSED, ProjectDates(earlier, today), today))
        assertEquals(ProjectDates(earlier, null), StatusRules.apply(DONE, IN_PROGRESS, ProjectDates(earlier, today), today))
    }

    @Test
    fun sameStatusChangesNothing() {
        assertEquals(ProjectDates(null, today), StatusRules.apply(DONE, DONE, ProjectDates(null, today), today))
    }

    @Test
    fun completionSheetOpensOnlyWhenBecomingDone() {
        assertTrue(StatusRules.opensCompletionSheet(IDEA, DONE))
        assertFalse(StatusRules.opensCompletionSheet(DONE, DONE))
        assertFalse(StatusRules.opensCompletionSheet(DONE, PLANNED))
    }
}
