package at.mtr.sewfar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationsTest {
    @Test
    fun parsesCommonForms() {
        assertEquals(45, Durations.parseMinutes("45"))
        assertEquals(90, Durations.parseMinutes("1:30"))
        assertEquals(90, Durations.parseMinutes("1.5h"))
        assertEquals(90, Durations.parseMinutes("1,5 h"))
        assertEquals(120, Durations.parseMinutes("2h"))
        assertEquals(20, Durations.parseMinutes("20 min"))
        assertEquals(30, Durations.parseMinutes(":30"))
    }

    @Test
    fun rejectsInvalidOrZero() {
        listOf("", "abc", "0", "1:75", "-5", "1:", "1,5 min", "0.5min").forEach { assertNull(it, Durations.parseMinutes(it)) }
    }

    @Test
    fun totalCountsRunningEntryUntilNow() {
        assertEquals(150L, Durations.total(listOf(0L to 100L, 50L to null), now = 100))
    }
}
