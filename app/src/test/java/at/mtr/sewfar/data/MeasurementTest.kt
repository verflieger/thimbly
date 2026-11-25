package at.mtr.sewfar.data

import at.mtr.sewfar.domain.parseCm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 4.2: one dated measuring per save day; older ones stay for projects finished earlier. */
@RunWith(RobolectricTestRunner::class)
class MeasurementTest {
    private lateinit var t: TestDb

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    private suspend fun newest(person: String) = t.persons.observeMeasurements(person).first()

    @Test
    fun sameDayCorrectsLaterDayAddsAndOldOnesStay() = runTest {
        val lena = t.persons.create("Lena")
        assertNull(newest(lena))

        t.persons.saveMeasurements(lena, mapOf("height" to 116.0, "waist" to 55.0))
        val first = t.time.day
        t.persons.saveMeasurements(lena, mapOf("height" to 116.0, "waist" to 54.5, "hips" to 60.0)) // a fix on the same day
        assertEquals(1, t.count("measurement"))
        assertEquals(first, newest(lena)!!.measuredOn)
        assertEquals(mapOf("height" to 116.0, "waist" to 54.5, "hips" to 60.0), newest(lena)!!.valuesCm)

        t.time.day = first.plusMonths(4)
        t.persons.saveMeasurements(lena, mapOf("height" to 116.0, "waist" to 54.5, "hips" to 60.0)) // unchanged: nothing stored
        assertEquals(1, t.count("measurement"))
        t.persons.saveMeasurements(lena, mapOf("height" to 121.0, "waist" to 56.0))
        assertEquals(2, t.count("measurement"))
        assertEquals(mapOf("height" to 121.0, "waist" to 56.0), newest(lena)!!.valuesCm)

        // A project finished in between shows what it was sewn with.
        val then = t.persons.observeMeasurements(lena, onOrBefore = first.plusMonths(1)).first()!!
        assertEquals(first, then.measuredOn)
        assertEquals(54.5, then.valuesCm.getValue("waist"), 0.0)
        assertNull("finished before the first measuring", t.persons.observeMeasurements(lena, first.minusDays(1)).first())
    }

    @Test
    fun aValueRemovedAndReEnteredOnTheSameDayComesBack() = runTest {
        val anna = t.persons.create("Anna")
        t.persons.saveMeasurements(anna, mapOf("waist" to 74.0, "hips" to 98.0))
        t.persons.saveMeasurements(anna, mapOf("waist" to 74.0))
        assertEquals(mapOf("waist" to 74.0), newest(anna)!!.valuesCm)
        t.persons.saveMeasurements(anna, mapOf("waist" to 74.0, "hips" to 99.0))
        assertEquals(mapOf("waist" to 74.0, "hips" to 99.0), newest(anna)!!.valuesCm)
        assertEquals(2, t.count("measurement_value", live = false))
    }

    @Test
    fun deletingAPersonTakesTheMeasurementsAlongAndUndoRestoresThem() = runTest {
        val anna = t.persons.create("Anna")
        t.persons.saveMeasurements(anna, mapOf("waist" to 74.0))
        val token = t.persons.delete(anna)!!
        assertEquals(0, t.count("measurement_value"))
        t.deleter.undo(token)
        assertEquals(mapOf("waist" to 74.0), newest(anna)!!.valuesCm)
    }

    @Test
    fun parsesCentimetres() {
        assertEquals(74.5, parseCm(" 74,5 ")!!, 0.0)
        assertEquals(74.5, parseCm("74.5")!!, 0.0)
        assertNull(parseCm(""))
        assertNull(parseCm("about 60"))
        assertNull(parseCm("0"))
        assertNull(parseCm("1000"))
    }
}
