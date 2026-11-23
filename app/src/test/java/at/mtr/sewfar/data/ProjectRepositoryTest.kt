package at.mtr.sewfar.data

import at.mtr.sewfar.domain.LogKind
import at.mtr.sewfar.domain.ProjectStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectRepositoryTest {
    private lateinit var t: TestDb

    @Before fun setUp() = runTest { t = TestDb(); t.seed() }
    @After fun tearDown() = t.close()

    @Test
    fun newProjectIsPlannedWithPatternsInOrder() = runTest {
        val a = t.patterns.create("Hoodie")
        val b = t.patterns.create("Hood")
        val id = t.projects.create("Hoodie", patternIds = listOf(a, b, a))

        val project = t.projects.get(id)!!
        assertEquals(ProjectStatus.PLANNED, project.status)
        assertEquals(listOf(a, b), t.projects.patterns(id).map { it.patternId })
    }

    @Test
    fun statusChangeAppliesDatesAndWritesLogEntry() = runTest {
        val id = t.projects.create("Shirt")
        t.projects.setStatus(id, ProjectStatus.IN_PROGRESS)
        val done = t.projects.setStatus(id, ProjectStatus.DONE)

        assertTrue(done.opensCompletionSheet)
        assertEquals(t.time.day, done.project.startedOn)
        assertEquals(t.time.day, done.project.finishedOn)
        val log = t.projects.log(id)
        assertEquals(2, log.size)
        assertTrue(log.all { it.kind == LogKind.STATUS })
        assertEquals(setOf(ProjectStatus.PLANNED to ProjectStatus.IN_PROGRESS, ProjectStatus.IN_PROGRESS to ProjectStatus.DONE),
            log.map { it.fromStatus to it.toStatus }.toSet())

        val reopened = t.projects.setStatus(id, ProjectStatus.PAUSED)
        assertNull(reopened.project.finishedOn)
        assertFalse(reopened.opensCompletionSheet)
    }

    @Test
    fun settingSameStatusDoesNothing() = runTest {
        val id = t.projects.create("Shirt")
        t.projects.setStatus(id, ProjectStatus.PLANNED)
        assertEquals(0, t.projects.log(id).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateCannotChangeStatus() = runTest {
        val id = t.projects.create("Shirt")
        t.projects.update(t.projects.get(id)!!.copy(status = ProjectStatus.DONE))
    }

    @Test
    fun childChangesBumpLastActivity() = runTest {
        val id = t.projects.create("Shirt")
        val created = t.projects.get(id)!!.lastActivityAt
        t.time.advance()
        t.projects.addFabric(id, "Jersey", 150)
        val afterFabric = t.projects.get(id)!!.lastActivityAt
        t.time.advance()
        t.projects.addNote(id, "cut")
        val afterNote = t.projects.get(id)!!.lastActivityAt

        assertTrue(afterFabric > created)
        assertTrue(afterNote > afterFabric)
    }

    @Test
    fun readdingPatternRevivesLinkAndRemovingTakesLessons() = runTest {
        val patternId = t.patterns.create("Raglan")
        val id = t.projects.create("Shirt", patternIds = listOf(patternId))
        val link = t.projects.patterns(id).single()
        t.projects.setPatternSize(link.id, "110")
        t.projects.setLesson(id, link.id, t.lessonTagId("size_up"), set = true)

        t.projects.removePattern(link.id)
        assertEquals(0, t.projects.lessons(id).size)

        val revived = t.projects.addPattern(id, patternId)
        assertEquals(link.id, revived)
        assertEquals("110", t.projects.patterns(id).single().sizeSewn)
        assertEquals(1, t.count("project_pattern", "projectId = '$id'", live = false))
    }

    @Test
    fun lessonTogglesReviveSingleRowIncludingProjectLevel() = runTest {
        val id = t.projects.create("Repair jeans")
        val tag = t.lessonTagId("fabric_suited")
        repeat(3) {
            t.projects.setLesson(id, null, tag, set = true)
            t.projects.setLesson(id, null, tag, set = false)
        }
        t.projects.setLesson(id, null, tag, set = true)
        t.projects.setLesson(id, null, tag, set = true)

        assertEquals(1, t.projects.lessons(id).size)
        assertEquals(1, t.count("project_lesson", "projectId = '$id'", live = false))
    }

    @Test
    fun statusEntriesCannotBeEditedButCanBeDeleted() = runTest {
        val id = t.projects.create("Shirt")
        t.projects.setStatus(id, ProjectStatus.IN_PROGRESS)
        val entry = t.projects.log(id).single()

        val error = runCatching { t.projects.updateNote(entry.copy(text = "x")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)

        t.projects.deleteLogEntry(entry.id)
        assertEquals(0, t.projects.log(id).size)
    }

    @Test
    fun measurementValueRevivesOnReentry() = runTest {
        val personId = t.persons.create("Josi")
        val waist = t.lists.entries(ListKind.MEASUREMENT_TYPE).first { it.builtinKey == "waist" }.id
        val session = t.persons.createSession(personId, t.time.day, mapOf(waist to 520))

        t.persons.setValue(session, waist, null)
        assertEquals(0, t.persons.values(session).size)
        t.persons.setValue(session, waist, 530)

        assertEquals(listOf(530), t.persons.values(session).map { it.valueMm })
        assertEquals(1, t.count("measurement_value", "sessionId = '$session'", live = false))
    }
}
