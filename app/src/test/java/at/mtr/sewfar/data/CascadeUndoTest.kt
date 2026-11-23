package at.mtr.sewfar.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 7: soft delete, cascade table, undo. */
@RunWith(RobolectricTestRunner::class)
class CascadeUndoTest {
    private lateinit var t: TestDb

    @Before fun setUp() = runTest { t = TestDb(); t.seed() }
    @After fun tearDown() = t.close()

    @Test
    fun deletingProjectCascadesToAllChildren() = runTest {
        val patternId = t.patterns.create("Hoodie")
        val projectId = t.projects.create("Hoodie for Josi", patternIds = listOf(patternId))
        val link = t.projects.patterns(projectId).single()
        t.projects.addFabric(projectId, "French Terry", 80)
        t.projects.setLesson(projectId, link.id, t.lessonTagId("lengthen"), set = true)
        val noteId = t.projects.addNote(projectId, "cut out")
        t.insertPhoto("PROJECT", projectId, logEntryId = noteId)
        t.insertPhoto("PROJECT", projectId, tag = "FINISHED")
        val machine = t.insertMachine()
        val setup = t.insertSetup(machine, t.insertField(machine))
        t.insertSetupUse(setup, projectId)

        val token = requireNotNull(t.projects.delete(projectId))

        for (table in listOf("project_pattern", "project_fabric", "project_lesson", "log_entry", "setup_use")) {
            assertEquals("$table should be deleted", 0, t.count(table, "projectId = '$projectId'"))
        }
        assertEquals(0, t.count("photo", "ownerId = '$projectId'"))
        assertEquals(token.stamp, t.deletedAt("project", projectId))
        // The pattern itself and the setup are not part of the project.
        assertNotNull(t.patterns.get(patternId))
        assertEquals(1, t.count("setup", "id = '$setup'"))
    }

    @Test
    fun undoRestoresExactlyTheRowsOfThatDeletion() = runTest {
        val projectId = t.projects.create("Shirt")
        val keptFabric = t.projects.addFabric(projectId, "Jersey")
        val removedFabric = t.projects.addFabric(projectId, "Ribbing")
        t.time.advance()
        t.projects.removeFabric(removedFabric)
        t.time.advance()
        val token = requireNotNull(t.projects.delete(projectId))

        t.projects.undo(token)

        assertNotNull(t.projects.get(projectId))
        assertNull(t.deletedAt("project_fabric", keptFabric))
        assertNotNull("deleted before the project, stays deleted", t.deletedAt("project_fabric", removedFabric))
    }

    @Test
    fun deletionsAtTheSameMillisecondGetDistinctStamps() = runTest {
        val a = t.projects.create("A")
        val b = t.projects.create("B")
        val tokenA = requireNotNull(t.projects.delete(a))
        val tokenB = requireNotNull(t.projects.delete(b))
        assertNotEquals(tokenA.stamp, tokenB.stamp)

        t.projects.undo(tokenA)
        assertNotNull(t.projects.get(a))
        assertNull(t.projects.get(b))
    }

    @Test
    fun deletingLogEntryTakesItsPhotosAndSetupUsesOnly() = runTest {
        val projectId = t.projects.create("Dress")
        val noteId = t.projects.addNote(projectId, "hemmed")
        val entryPhoto = t.insertPhoto("PROJECT", projectId, logEntryId = noteId)
        val projectPhoto = t.insertPhoto("PROJECT", projectId, tag = "INSPIRATION")
        val machine = t.insertMachine()
        val setup = t.insertSetup(machine, t.insertField(machine))
        val entryUse = t.insertSetupUse(setup, projectId, logEntryId = noteId)
        val projectUse = t.insertSetupUse(setup, projectId)

        t.projects.deleteLogEntry(noteId)

        assertNotNull(t.deletedAt("photo", entryPhoto))
        assertNotNull(t.deletedAt("setup_use", entryUse))
        assertNull(t.deletedAt("photo", projectPhoto))
        assertNull(t.deletedAt("setup_use", projectUse))
    }

    @Test
    fun deletingSetupKeepsItsSnapshots() = runTest {
        val projectId = t.projects.create("Leggings")
        val machine = t.insertMachine()
        val setup = t.insertSetup(machine, t.insertField(machine))
        val use = t.insertSetupUse(setup, projectId)

        t.deleter.delete("setup", setup)

        assertEquals(0, t.count("setup_value", "setupId = '$setup'"))
        assertNull("snapshot outlives its setup (SPEC 6.4)", t.deletedAt("setup_use", use))
    }

    @Test
    fun deletingMachineCascadesFieldsSetupsAndValues() = runTest {
        val machine = t.insertMachine()
        val field = t.insertField(machine)
        val setup = t.insertSetup(machine, field)
        t.insertPhoto("MACHINE", machine, tag = null)

        t.deleter.delete("machine", machine)

        assertNotNull(t.deletedAt("machine_field", field))
        assertNotNull(t.deletedAt("setup", setup))
        assertEquals(0, t.count("setup_value", "setupId = '$setup'"))
        assertEquals(0, t.count("photo", "ownerId = '$machine'"))
    }

    @Test
    fun deletingPersonCascadesMeasurements() = runTest {
        val personId = t.persons.create("Josi")
        val heightType = t.lists.entries(ListKind.MEASUREMENT_TYPE).first { it.builtinKey == "height" }.id
        val session = t.persons.createSession(personId, t.time.day, mapOf(heightType to 1040))

        val result = t.persons.delete(personId)

        assertTrue(result is at.mtr.sewfar.data.db.DeleteResult.Deleted)
        assertNotNull(t.deletedAt("measurement_session", session))
        assertEquals(0, t.count("measurement_value", "sessionId = '$session'"))
    }

    @Test
    fun deletingAlreadyDeletedRowReturnsNull() = runTest {
        val projectId = t.projects.create("Bag")
        assertNotNull(t.projects.delete(projectId))
        assertNull(t.projects.delete(projectId))
    }
}
