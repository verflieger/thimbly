package at.mtr.sewfar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SPEC 3.6 */
class CoverRulesTest {
    private val inspiration = CoverCandidate("a", PhotoTag.INSPIRATION, 0)
    private val progress = CoverCandidate("b", PhotoTag.PROGRESS, 1)
    private val finished1 = CoverCandidate("c", PhotoTag.FINISHED, 2)
    private val finished2 = CoverCandidate("d", PhotoTag.FINISHED, 3)

    @Test fun firstFinishedWins() = assertEquals("c", CoverRules.cover(listOf(finished2, inspiration, finished1, progress), null))

    @Test fun otherwiseFirstAnyTag() = assertEquals("a", CoverRules.cover(listOf(progress, inspiration), null))

    @Test fun pinnedWins() = assertEquals("b", CoverRules.cover(listOf(inspiration, progress, finished1), "b"))

    @Test fun deletedPinFallsBackToAutomatic() = assertEquals("c", CoverRules.cover(listOf(inspiration, finished1), "gone"))

    @Test fun noPhotosNoCover() = assertNull(CoverRules.cover(emptyList(), "x"))
}
