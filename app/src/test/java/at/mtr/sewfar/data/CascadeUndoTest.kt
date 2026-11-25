package at.mtr.sewfar.data

import at.mtr.sewfar.domain.PhotoOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** SPEC 6: soft delete with cascade, undo restores exactly that deletion. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CascadeUndoTest {
    private lateinit var t: TestDb
    private val image = TestDb.jpeg(20, 20)

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    private suspend fun fullProject(): String {
        val id = t.projects.create("Shirt")
        t.projects.addPattern(id, t.patterns.create("Raglan"))
        t.projects.addSetup(id, t.machines.createSetup(t.machines.create("M"), "Hem"))
        t.projects.addTime(id, t.time.day, 30)
        t.photos.add(PhotoOwner.PROJECT, id) { image.inputStream() }
        return id
    }

    @Test
    fun deletingProjectCascadesToAllChildrenAndUndoRestoresThem() = runTest {
        val id = fullProject()
        val token = t.projects.delete(id)!!
        for (table in listOf("project", "project_pattern", "project_setup", "time_entry", "photo")) assertEquals(table, 0, t.count(table))
        assertEquals(1, t.count("pattern")) // the pattern itself stays
        t.deleter.undo(token)
        for (table in listOf("project", "project_pattern", "project_setup", "time_entry", "photo")) assertEquals(table, 1, t.count(table))
    }

    @Test
    fun undoRestoresOnlyRowsOfThatDeletion() = runTest {
        val id = fullProject()
        val time = t.projects.observeTime(id).first().single()
        t.projects.deleteTime(time.id)
        t.time.advance()
        val token = t.projects.delete(id)!!
        t.deleter.undo(token)
        assertEquals(0, t.count("time_entry")) // deleted earlier, stays deleted
        assertEquals(1, t.count("project"))
    }

    @Test
    fun deletionsInTheSameMillisecondGetDistinctStamps() = runTest {
        val a = t.projects.create("A")
        val b = t.projects.create("B")
        val ta = t.projects.delete(a)!!
        val tb = t.projects.delete(b)!!
        assertNotEquals(ta, tb)
        t.deleter.undo(ta)
        assertNull(t.projects.get(b))
    }

    @Test
    fun deletingPatternRemovesItFromProjects() = runTest {
        val project = t.projects.create("A")
        val pattern = t.patterns.create("Raglan")
        t.projects.addPattern(project, pattern)
        val token = t.patterns.delete(pattern)!!
        assertEquals(0, t.count("project_pattern"))
        t.deleter.undo(token)
        assertEquals(1, t.count("project_pattern"))
    }

    @Test
    fun deletingMachineTakesSetupsValuesAndLinks() = runTest {
        val project = t.projects.create("A")
        val machine = t.machines.create("M")
        val setup = t.machines.createSetup(machine, "Hem")
        t.machines.setValue(setup, "stitch", "3")
        t.projects.addSetup(project, setup)
        val token = t.machines.delete(machine)!!
        for (table in listOf("machine", "setup", "setup_value", "project_setup")) assertEquals(table, 0, t.count(table))
        t.deleter.undo(token)
        for (table in listOf("machine", "setup", "setup_value", "project_setup")) assertEquals(table, 1, t.count(table))
    }

    @Test
    fun deletingAlreadyDeletedRowReturnsNull() = runTest {
        val id = t.projects.create("A")
        t.projects.delete(id)
        assertNull(t.projects.delete(id))
    }
}
