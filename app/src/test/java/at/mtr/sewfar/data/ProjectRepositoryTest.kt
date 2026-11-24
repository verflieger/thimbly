package at.mtr.sewfar.data

import at.mtr.sewfar.domain.ProjectStatus
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 3, 5.2: status dates, timer, manual time, links, setting values. */
@RunWith(RobolectricTestRunner::class)
class ProjectRepositoryTest {
    private lateinit var t: TestDb

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    @Test
    fun statusChangesSetAndClearDates() = runTest {
        val id = t.projects.create("Shirt")
        t.projects.setStatus(id, ProjectStatus.IN_PROGRESS)
        assertEquals(t.time.day, t.projects.get(id)!!.startedOn)
        t.time.day = t.time.day.plusDays(3)
        t.projects.setStatus(id, ProjectStatus.DONE)
        assertEquals(t.time.day, t.projects.get(id)!!.finishedOn)
        t.projects.setStatus(id, ProjectStatus.IN_PROGRESS)
        val p = t.projects.get(id)!!
        assertNull(p.finishedOn)
        assertEquals(t.time.day.minusDays(3), p.startedOn) // the first start stays
    }

    @Test(expected = IllegalArgumentException::class)
    fun editCannotChangeStatus() = runTest {
        val id = t.projects.create("Shirt")
        t.projects.edit(id) { it.copy(status = ProjectStatus.DONE) }
    }

    @Test
    fun onlyOneTimerRunsAndStartingMovesPlannedToInProgress() = runTest {
        val a = t.projects.create("A")
        val b = t.projects.create("B")
        t.projects.startTimer(a)
        assertEquals(ProjectStatus.IN_PROGRESS, t.projects.get(a)!!.status)
        t.time.advance(30 * 60_000)
        t.projects.startTimer(b) // stops A
        val timeA = t.projects.observeTime(a).first().single()
        assertEquals(30 * 60_000L, timeA.endedAt!! - timeA.startedAt)
        assertNull(t.projects.observeTime(b).first().single().endedAt)
        t.projects.startTimer(b) // already running: no second entry
        assertEquals(1, t.projects.observeTime(b).first().size)
    }

    @Test
    fun doneStopsTheTimer() = runTest {
        val id = t.projects.create("A")
        t.projects.startTimer(id)
        t.time.advance(60_000)
        t.projects.setStatus(id, ProjectStatus.DONE)
        assertNotNull(t.projects.observeTime(id).first().single().endedAt)
    }

    @Test
    fun undoingADeletionDoesNotReviveARunningTimer() = runTest {
        val a = t.projects.create("A")
        val b = t.projects.create("B")
        t.projects.startTimer(a)
        val token = t.projects.delete(a)!!
        t.projects.startTimer(b)
        t.projects.undo(token)
        assertEquals(1, t.count("time_entry", "endedAt IS NULL"))
    }

    @Test
    fun manualTimeIsStoredOnThatDay() = runTest {
        val id = t.projects.create("A")
        t.projects.addTime(id, LocalDate.of(2026, 11, 2), 90, ZoneOffset.UTC)
        val entry = t.projects.observeTime(id).first().single()
        assertEquals(90 * 60_000L, entry.endedAt!! - entry.startedAt)
        assertEquals(LocalDate.of(2026, 11, 2), java.time.Instant.ofEpochMilli(entry.startedAt).atZone(ZoneOffset.UTC).toLocalDate())
        val token = t.projects.deleteTime(entry.id)!!
        assertTrue(t.projects.observeTime(id).first().isEmpty())
        t.projects.undo(token)
        assertEquals(1, t.projects.observeTime(id).first().size)
    }

    @Test
    fun relinkingARemovedPatternRevivesItsRow() = runTest {
        val project = t.projects.create("A")
        val pattern = t.patterns.create("Raglan")
        t.projects.addPattern(project, pattern)
        t.projects.addPattern(project, pattern)
        val link = t.projects.observePatterns(project).first().single()
        t.projects.removePattern(link.linkId)
        t.projects.addPattern(project, pattern)
        assertEquals(link.linkId, t.projects.observePatterns(project).first().single().linkId)
        assertEquals(1, t.count("project_pattern", live = false))
    }

    @Test
    fun concurrentEditsOfDifferentFieldsAreAllKept() = runBlocking {
        val id = t.projects.create("A")
        listOf(
            async { t.projects.edit(id) { it.copy(notes = "notes") } },
            async { t.projects.edit(id) { it.copy(learned = "learned") } },
            async { t.projects.edit(id) { it.copy(rating = 4) } },
        ).awaitAll()
        val p = t.projects.get(id)!!
        assertEquals(Triple("notes", "learned", 4), Triple(p.notes, p.learned, p.rating))
    }

    @Test
    fun setupValuesAreTrimmedClearedAndRevived() = runTest {
        val machine = t.machines.create("Overlock")
        val setup = t.machines.createSetup(machine, "Jersey")
        t.machines.setValue(setup, "differential_feed", " 1.3 ")
        assertEquals("1.3", t.machines.observeValues(setup).first().single().value)
        t.machines.setValue(setup, "differential_feed", "")
        assertTrue(t.machines.observeValues(setup).first().isEmpty())
        t.machines.setValue(setup, "differential_feed", "1.5")
        assertEquals(1, t.count("setup_value", live = false))
    }
}
