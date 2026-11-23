package at.mtr.sewfar.data

import at.mtr.sewfar.data.seed.BuiltIns
import at.mtr.sewfar.domain.Ids
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 1: deterministic built-in IDs; seeding never overwrites user changes. */
@RunWith(RobolectricTestRunner::class)
class SeederTest {
    private lateinit var t: TestDb

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    @Test
    fun seedsEveryBuiltInWithDeterministicIdInDefaultOrder() = runTest {
        t.seed()
        for ((kind, keys) in BuiltIns.keys) {
            val rows = t.lists.entries(kind)
            assertEquals(keys, rows.map { it.builtinKey })
            assertEquals(keys.map { Ids.builtIn(kind.table, it) }, rows.map { it.id })
        }
    }

    @Test
    fun reseedingKeepsRenamesHidingAndOrder() = runTest {
        t.seed()
        val tags = t.lists.entries(ListKind.LESSON_TAG)
        t.lists.rename(ListKind.LESSON_TAG, tags[0].id, "Passt super")
        t.lists.setHidden(ListKind.LESSON_TAG, tags[1].id, true)
        t.lists.reorder(ListKind.LESSON_TAG, tags.map { it.id }.reversed())
        val custom = t.lists.add(ListKind.LESSON_TAG, "Pre-wash fabric")
        val before = t.lists.entries(ListKind.LESSON_TAG)

        t.seed()

        assertEquals(before, t.lists.entries(ListKind.LESSON_TAG))
        assertEquals("Passt super", before.first { it.id == tags[0].id }.customName)
        assertEquals(custom, before.last().id)
    }
}
