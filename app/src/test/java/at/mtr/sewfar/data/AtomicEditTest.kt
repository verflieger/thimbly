package at.mtr.sewfar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Edits from the UI arrive late and concurrently (auto-save on leave): none may be lost or revive a deleted row. */
@RunWith(RobolectricTestRunner::class)
class AtomicEditTest {
    private lateinit var t: TestDb

    @Before fun setUp() = runTest { t = TestDb(); t.seed() }
    @After fun tearDown() = t.close()

    @Test
    fun concurrentEditsOfDifferentFieldsAreAllKept() = runBlocking {
        val id = t.patterns.create("Raglan")
        (1..20).map { i ->
            async(Dispatchers.Default) {
                if (i % 2 == 0) t.patterns.edit(id) { it.copy(designer = "D$i") } else t.patterns.edit(id) { it.copy(sizeRange = "R$i") }
            }
        }.awaitAll()
        t.patterns.edit(id) { it.copy(notes = "n") }
        val pattern = t.patterns.get(id)!!
        assert(pattern.designer.startsWith("D")) { "designer lost: $pattern" }
        assert(pattern.sizeRange.startsWith("R")) { "size range lost: $pattern" }
        assertEquals("n", pattern.notes)
    }

    @Test
    fun lateEditDoesNotReviveDeletedRows() = runTest {
        val patternId = t.patterns.create("Raglan")
        val row = t.patterns.addYardage(patternId, "110", 80)
        t.patterns.deleteYardage(row)
        t.patterns.editYardage(row) { it.copy(size = "116") }
        assertEquals(0, t.count("pattern_yardage", "id = '$row'"))

        val projectId = t.projects.create("Shirt")
        val fabric = t.projects.addFabric(projectId, "Jersey")
        t.projects.removeFabric(fabric)
        t.projects.editFabric(fabric) { it.copy(description = "late") }
        assertEquals(0, t.count("project_fabric", "id = '$fabric'"))

        t.projects.delete(projectId)
        t.projects.edit(projectId) { it.copy(notes = "late") }
        assertNull(t.projects.get(projectId))
    }

    @Test
    fun resetNameReturnsToBuiltInTranslation() = runTest {
        val tag = t.lists.entries(ListKind.LESSON_TAG).first { it.builtinKey == "lengthen" }
        t.lists.rename(ListKind.LESSON_TAG, tag.id, "Längen")
        t.lists.resetName(ListKind.LESSON_TAG, tag.id)
        assertNull(t.lists.entries(ListKind.LESSON_TAG).first { it.id == tag.id }.customName)
    }
}
