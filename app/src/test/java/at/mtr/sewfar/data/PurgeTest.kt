package at.mtr.sewfar.data

import androidx.sqlite.db.SimpleSQLiteQuery
import at.mtr.sewfar.data.db.SoftDeleter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 6: deleted rows are removed for good after the Undo window and recorded in deletion_log. */
@RunWith(RobolectricTestRunner::class)
class PurgeTest {
    private lateinit var t: TestDb

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    private fun afterUndoWindow() = t.time.advance(SoftDeleter.UndoWindowMillis + 1_000)

    /** deletedAt per logged row ID. */
    private fun log(): Map<String, Long> =
        t.db.query(SimpleSQLiteQuery("SELECT rowId, deletedAt FROM deletion_log")).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }

    @Test
    fun purgeRemovesRowsOnlyAfterUndoWindowAndLogsEachOne() = runTest {
        val project = t.projects.create("Shirt")
        t.projects.addPattern(project, t.patterns.create("Raglan"))
        val link = t.projects.observePatterns(project).first().single().linkId
        t.projects.addTime(project, t.time.day, 30)
        val time = t.projects.observeTime(project).first().single().id
        val token = t.projects.delete(project)!!

        assertEquals(0, t.deleter.purge())
        assertEquals("inside the Undo window the rows stay", 1, t.count("project", live = false))

        afterUndoWindow()
        assertEquals(3, t.deleter.purge())
        for (table in listOf("project", "project_pattern", "time_entry")) assertEquals(table, 0, t.count(table, live = false))
        assertEquals(1, t.count("pattern")) // not part of the deletion
        assertEquals(mapOf(project to token.stamp, link to token.stamp, time to token.stamp), log())
    }

    @Test
    fun purgingADeletedPersonClearsItFromProjects() = runTest {
        val person = t.persons.create("Josi")
        val project = t.projects.create("Shirt")
        t.projects.edit(project) { it.copy(personId = person) }
        t.persons.delete(person)
        afterUndoWindow()

        t.deleter.purge()

        assertNotNull(t.projects.get(project))
        assertNull(t.projects.get(project)!!.personId)
        assertEquals(0, t.count("person", live = false))
    }

    @Test
    fun childRestoredAfterItsParentWasDeletedGoesWithTheParent() = runTest {
        val project = t.projects.create("Shirt")
        t.projects.addPattern(project, t.patterns.create("Raglan"))
        val link = t.projects.observePatterns(project).first().single().linkId
        val unlink = t.projects.removePattern(link)!!
        t.projects.delete(project)
        t.deleter.undo(unlink) // the link is live again, but its project is deleted
        afterUndoWindow()

        t.deleter.purge()

        assertEquals(0, t.count("project_pattern", live = false))
        assertEquals("logged with the purge time", t.time.now, log()[link])
    }
}
