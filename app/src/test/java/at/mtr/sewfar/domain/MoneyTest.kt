package at.mtr.sewfar.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEC 4.3: prices with comma or dot, at most two decimals, stored in hundredths. */
class MoneyTest {
    @Test
    fun parsesPrices() {
        assertEquals(1850L, Money.parseCents("18,50"))
        assertEquals(1850L, Money.parseCents("18.5"))
        assertEquals(1800L, Money.parseCents(" 18 "))
        assertEquals(5L, Money.parseCents("0,05"))
        assertEquals(0L, Money.parseCents("0"))
        for (bad in listOf("", "abc", "-3", "1,234", "18,", ",5", "1.000,00", "12345678")) assertEquals(bad, null, Money.parseCents(bad))
    }

    @Test
    fun inputRoundTrips() {
        assertEquals("18,50", Money.toInput(1850, ','))
        assertEquals("0.05", Money.toInput(5, '.'))
        assertEquals(1850L, Money.parseCents(Money.toInput(1850, ',')))
    }
}
