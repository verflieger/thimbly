package at.mtr.sewfar.domain

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SPEC 5.2 and the lesson aggregation of SPEC 4.2. */
class MeasurementsTest {
    private val jan = LocalDate.of(2026, 1, 1)
    private val jun = LocalDate.of(2026, 6, 1)

    @Test fun latestPerTypeAndHistoryNewestFirst() {
        val values = listOf(Measured("h", 1000, jan), Measured("h", 1040, jun), Measured("w", 520, jan))
        assertEquals(1040, Measurements.latest(values).getValue("h").valueMm)
        assertEquals(listOf(1040, 1000), Measurements.history(values).getValue("h").map { it.valueMm })
    }

    @Test fun parsesCentimetresToMillimetres() {
        assertEquals(525, Measurements.parseCm("52,5"))
        assertEquals(525, Measurements.parseCm("52.5"))
        assertEquals(1040, Measurements.parseCm("104"))
        assertNull(Measurements.parseCm(""))
        assertNull(Measurements.parseCm("x"))
        assertNull(Measurements.parseCm("0"))
    }

    @Test fun formatsPerLocale() {
        assertEquals("52,5", Measurements.formatCm(525, Locale.GERMAN))
        assertEquals("104", Measurements.formatCm(1040, Locale.ENGLISH))
    }

    @Test fun lessonCountsMostFrequentFirst() {
        assertEquals(listOf("lengthen" to 2, "fit" to 1), Lessons.counts(listOf("fit", "lengthen", "lengthen")))
    }
}
