package at.mtr.sewfar.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 4.2 (projects and lessons of a pattern) and SPEC 5 (projects and measurements of a person). */
@RunWith(RobolectricTestRunner::class)
class PatternPersonQueriesTest {
    private lateinit var t: TestDb

    @Before fun setUp() = runTest { t = TestDb(); t.seed() }
    @After fun tearDown() = t.close()

    @Test
    fun patternShowsLiveProjectsAndTheirLessons() = runTest {
        val pattern = t.patterns.create("Raglan")
        val a = t.projects.create("Shirt A", patternIds = listOf(pattern))
        val b = t.projects.create("Shirt B", patternIds = listOf(pattern))
        val gone = t.projects.create("Shirt C", patternIds = listOf(pattern))
        val lengthen = t.lessonTagId("lengthen")
        for (p in listOf(a, b, gone)) t.projects.setLesson(p, t.projects.patterns(p).single().id, lengthen, set = true)
        t.projects.setLesson(a, null, t.lessonTagId("fit_good"), set = true) // project-level: not a pattern lesson
        t.projects.delete(gone)

        assertEquals(setOf("Shirt A", "Shirt B"), t.patterns.observeProjects(pattern).first().map { it.projectName }.toSet())
        assertEquals(listOf(lengthen, lengthen), t.patterns.observeLessonTagIds(pattern).first())
    }

    @Test
    fun personMeasurementsSkipDeletedSessions() = runTest {
        val person = t.persons.create("Josi")
        val height = t.lists.entries(ListKind.MEASUREMENT_TYPE).first { it.builtinKey == "height" }.id
        t.persons.createSession(person, t.time.day.minusMonths(6), mapOf(height to 1000))
        val newer = t.persons.createSession(person, t.time.day, mapOf(height to 1040))
        val deleted = t.persons.createSession(person, t.time.day.minusMonths(1), mapOf(height to 9999))
        t.persons.deleteSession(deleted)

        val rows = t.persons.observeMeasurements(person).first()
        assertEquals(listOf(1040, 1000), rows.map { it.valueMm })
        assertEquals(newer, rows.first().sessionId)
    }
}
