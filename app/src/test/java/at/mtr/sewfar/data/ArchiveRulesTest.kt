package at.mtr.sewfar.data

import at.mtr.sewfar.data.db.DeleteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 7: referenced items are archived, unreferenced ones deleted; list entries in use are hidden. */
@RunWith(RobolectricTestRunner::class)
class ArchiveRulesTest {
    private lateinit var t: TestDb

    @Before fun setUp() = runTest { t = TestDb(); t.seed() }
    @After fun tearDown() = t.close()

    @Test
    fun patternUsedByLiveProjectCannotBeDeleted() = runTest {
        val patternId = t.patterns.create("Raglan")
        val projectId = t.projects.create("Raglan shirt", patternIds = listOf(patternId))

        assertEquals(DeleteResult.InUse, t.patterns.delete(patternId))

        t.projects.delete(projectId)
        assertTrue(t.patterns.delete(patternId) is DeleteResult.Deleted)
    }

    @Test
    fun patternWithRemovedLinkCanBeDeleted() = runTest {
        val patternId = t.patterns.create("Raglan")
        val projectId = t.projects.create("Shirt", patternIds = listOf(patternId))
        t.projects.removePattern(t.projects.patterns(projectId).single().id)

        assertTrue(t.patterns.delete(patternId) is DeleteResult.Deleted)
    }

    @Test
    fun archivedPatternIsHiddenFromListButStillLoadable() = runTest {
        val patternId = t.patterns.create("Old pattern")
        t.patterns.setArchived(patternId, true)

        assertEquals(emptyList<String>(), t.patterns.observe().first().map { it.id })
        assertEquals(listOf(patternId), t.patterns.observe(includeArchived = true).first().map { it.id })

        t.patterns.setArchived(patternId, false)
        assertEquals(listOf(patternId), t.patterns.observe().first().map { it.id })
    }

    @Test
    fun personUsedByLiveProjectCannotBeDeleted() = runTest {
        val personId = t.persons.create("Josi")
        t.projects.create("Dress", personId = personId)

        assertEquals(DeleteResult.InUse, t.persons.delete(personId))
    }

    @Test
    fun builtInListEntriesCanOnlyBeHidden() = runTest {
        val hem = t.lists.entries(ListKind.PURPOSE).first { it.builtinKey == "hem" }
        assertEquals(DeleteResult.BuiltIn, t.lists.delete(ListKind.PURPOSE, hem.id))

        t.lists.setHidden(ListKind.PURPOSE, hem.id, true)
        assertTrue(t.lists.observeSelectable(ListKind.PURPOSE).first().none { it.id == hem.id })
        assertTrue(t.lists.observe(ListKind.PURPOSE).first().any { it.id == hem.id })
    }

    @Test
    fun customListEntryInUseCannotBeDeleted() = runTest {
        val tag = t.lists.add(ListKind.LESSON_TAG, "Use stretch needle")
        val projectId = t.projects.create("Leggings")
        t.projects.setLesson(projectId, null, tag, set = true)

        assertEquals(DeleteResult.InUse, t.lists.delete(ListKind.LESSON_TAG, tag))

        t.projects.setLesson(projectId, null, tag, set = false)
        assertTrue(t.lists.delete(ListKind.LESSON_TAG, tag) is DeleteResult.Deleted)
    }
}
