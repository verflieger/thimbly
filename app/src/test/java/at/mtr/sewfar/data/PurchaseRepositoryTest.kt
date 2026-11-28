package at.mtr.sewfar.data

import at.mtr.sewfar.domain.PurchaseCategory
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 4.3: newest first, edit, delete with Undo. */
@RunWith(RobolectricTestRunner::class)
class PurchaseRepositoryTest {
    private lateinit var t: TestDb

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    @Test
    fun newestFirstEditAndUndo() = runTest {
        val old = t.purchases.create("Scissors ", PurchaseCategory.TOOLS, 2490, LocalDate.of(2025, 3, 1))
        val new = t.purchases.create("Jersey", PurchaseCategory.FABRIC, 1850, LocalDate.of(2026, 11, 28))
        assertEquals(listOf(new, old), t.purchases.observeAll().first().map { it.id })
        assertEquals("Scissors", t.purchases.observeAll().first().last().name)

        t.purchases.edit(old, "Scissors", PurchaseCategory.TOOLS, 2990, LocalDate.of(2025, 3, 2))
        assertEquals(2990L, t.purchases.observeAll().first().single { it.id == old }.priceCents)

        val token = t.purchases.delete(new)!!
        assertEquals(listOf(old), t.purchases.observeAll().first().map { it.id })
        t.deleter.undo(token)
        assertEquals(listOf(new, old), t.purchases.observeAll().first().map { it.id })
    }
}
