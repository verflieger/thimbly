package at.mtr.sewfar.data

import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 3.7 */
@RunWith(RobolectricTestRunner::class)
class SewAgainRepositoryTest {
    private lateinit var t: TestDb

    @Before fun setUp() = runTest { t = TestDb(); t.seed() }
    @After fun tearDown() = t.close()

    @Test
    fun copiesTypePersonPatternsAndSizesIncludingArchived() = runTest {
        val person = t.persons.create("Josi")
        val hoodie = t.patterns.create("Hoodie")
        val hood = t.patterns.create("Hood")
        val source = t.projects.create("Hoodie", ProjectType.UPCYCLING, person, listOf(hoodie, hood))
        t.projects.patterns(source).forEachIndexed { i, link -> t.projects.setPatternSize(link.id, if (i == 0) "110" else "104") }
        t.projects.setStatus(source, ProjectStatus.DONE)
        t.patterns.setArchived(hood, true)
        t.persons.setArchived(person, true)

        val copy = t.projects.get(t.projects.sewAgain(source)!!)!!

        assertEquals("Hoodie (2)", copy.name)
        assertEquals(ProjectStatus.PLANNED, copy.status)
        assertEquals(ProjectType.UPCYCLING, copy.type)
        assertEquals(person, copy.personId)
        assertEquals(source, copy.sourceProjectId)
        assertNull(copy.finishedOn)
        assertEquals(listOf(hoodie to "110", hood to "104"), t.projects.patterns(copy.id).map { it.patternId to it.sizeSewn })
        assertEquals(0, t.projects.lessons(copy.id).size)
    }
}
