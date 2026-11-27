package at.mtr.sewfar.data

import at.mtr.sewfar.domain.ProjectStatus
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun onlyLongTimersAreReportedHoweverTheyStop() = runTest {
        val a = t.projects.create("A")
        val b = t.projects.create("B")
        val reported = mutableListOf<String>()
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { t.projects.longStops.collect { reported += it.projectId } }
        t.projects.startTimer(a)
        t.time.advance(LongTimerMillis - 1)
        t.projects.stopTimer(a) // just under the limit
        t.projects.startTimer(a)
        t.time.advance(LongTimerMillis)
        t.projects.startTimer(b) // stops A: forgotten
        t.time.advance(LongTimerMillis)
        t.projects.setStatus(b, ProjectStatus.DONE) // stops B: forgotten
        t.projects.startTimer(a)
        t.time.advance(LongTimerMillis)
        t.projects.delete(a) // stops A, but the project is going away: no question
        assertEquals(listOf(a, b), reported)
        collecting.cancel()
    }

    @Test
    fun editTimeKeepsTheStartOnTheSameDayAndMovesToNoonOnAnother() = runTest {
        val zone = ZoneOffset.UTC
        val id = t.projects.create("A")
        t.time.now = LocalDate.of(2026, 11, 23).atTime(9, 0).toInstant(zone).toEpochMilli()
        t.projects.startTimer(id)
        t.time.advance(9 * 60 * 60_000L) // forgotten until 18:00
        t.projects.stopTimer(id)
        val entry = t.projects.observeTime(id).first().single()

        t.projects.editTime(entry.id, LocalDate.of(2026, 11, 23), 90, zone)
        val sameDay = t.projects.observeTime(id).first().single()
        assertEquals(entry.startedAt, sameDay.startedAt)
        assertEquals(90 * 60_000L, sameDay.endedAt!! - sameDay.startedAt)

        t.projects.editTime(entry.id, LocalDate.of(2026, 11, 20), 45, zone)
        val moved = t.projects.observeTime(id).first().single()
        assertEquals(LocalDate.of(2026, 11, 20).atTime(12, 0).toInstant(zone).toEpochMilli(), moved.startedAt)
        assertEquals(45 * 60_000L, moved.endedAt!! - moved.startedAt)
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
        t.deleter.undo(token)
        assertEquals(1, t.count("time_entry", "endedAt IS NULL"))
    }

    @Test
    fun aRunningEntryCannotBeDeleted() = runTest {
        val id = t.projects.create("A")
        t.projects.startTimer(id)
        val entry = t.projects.observeTime(id).first().single()
        assertNull(t.projects.deleteTime(entry.id))
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
        t.deleter.undo(token)
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

    /** SPEC 3.2: a tag is linked once per project, removed with Undo, and re-adding it revives its row. */
    @Test
    fun tagsLinkOnceUndoAndRevive() = runTest {
        val project = t.projects.create("A")
        t.projects.addTag(project, " Gift ")
        t.projects.addTag(project, "Gift")
        t.projects.addTag(project, " ")
        val link = t.projects.observeTags(project).first().single()
        assertEquals("Gift", link.name)
        val token = t.projects.removeTag(link.linkId)!!
        assertTrue(t.projects.observeTags(project).first().isEmpty())
        t.deleter.undo(token)
        assertEquals(listOf("Gift"), t.projects.observeTags(project).first().map { it.name })
        t.projects.removeTag(link.linkId)
        t.projects.addTag(project, "gift") // another spelling revives the removed "Gift", not a second tag
        assertEquals(link.linkId, t.projects.observeTags(project).first().single().linkId)
        assertEquals(1, t.count("project_tag", live = false))
        assertEquals(mapOf(project to listOf("Gift")), t.projects.observeAllTags().first())
    }

    /** SPEC 3.2: renaming a label changes it on every project; a case fix stays in place, another name merges. */
    @Test
    fun renamingALabelFixesItEverywhereAndMerges() = runTest {
        val a = t.projects.create("A")
        val b = t.projects.create("B")
        t.projects.addTag(a, "Geshenk")
        t.projects.addTag(b, "Geshenk")
        t.projects.addTag(b, "Geschenk")
        t.projects.renameTag("Geshenk", "Geschenk")
        assertEquals(listOf("Geschenk"), t.projects.observeTags(a).first().map { it.name })
        assertEquals(listOf("Geschenk"), t.projects.observeTags(b).first().map { it.name }) // merged, not twice
        t.projects.renameTag("geschenk", "GESCHENK")
        assertEquals(mapOf(a to listOf("GESCHENK"), b to listOf("GESCHENK")), t.projects.observeAllTags().first())
        t.projects.renameTag("GESCHENK", " ")
        assertEquals(listOf("GESCHENK"), t.projects.observeTags(a).first().map { it.name }) // blank does nothing
    }

    @Test
    fun relinkingARemovedSettingRevivesItsRow() = runTest {
        val project = t.projects.create("A")
        val setup = t.machines.createSetup(t.machines.create("M"), "Hem")
        t.projects.addSetup(project, setup)
        val link = t.projects.observeSetups(project).first().single()
        t.projects.removeSetup(link.linkId)
        t.projects.addSetup(project, setup)
        assertEquals(link.linkId, t.projects.observeSetups(project).first().single().linkId)
        assertEquals(1, t.count("project_setup", live = false))
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
    fun learnedTextMovesToTheNotes() = runTest {
        val both = t.projects.create("A")
        t.projects.edit(both) { it.copy(notes = "Fits", learned = "Grade the seams") }
        val onlyLearned = t.projects.create("B")
        t.projects.edit(onlyLearned) { it.copy(learned = "Pre-wash") }
        val none = t.projects.create("C")
        t.projects.edit(none) { it.copy(notes = "Kept") }

        t.db.projectDao().foldLearnedIntoNotes(t.time.nowMillis())

        assertEquals("Fits\n\nGrade the seams" to "", t.projects.get(both)!!.let { it.notes to it.learned })
        assertEquals("Pre-wash" to "", t.projects.get(onlyLearned)!!.let { it.notes to it.learned })
        assertEquals("Kept", t.projects.get(none)!!.notes)
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
