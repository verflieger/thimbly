package at.mtr.sewfar.domain

import at.mtr.sewfar.domain.Amounts.ParseResult
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEC 1 (locale decimals) and SPEC 8 (amounts stored as cm). */
class AmountsTest {
    @Test fun parsesBothSeparators() {
        assertEquals(ParseResult.Cm(80), Amounts.parseMeters("0,8"))
        assertEquals(ParseResult.Cm(80), Amounts.parseMeters("0.8"))
        assertEquals(ParseResult.Cm(150), Amounts.parseMeters(" 1.5 "))
        assertEquals(ParseResult.Cm(124), Amounts.parseMeters("1,235"))
    }

    @Test fun blankAndInvalid() {
        assertEquals(ParseResult.Empty, Amounts.parseMeters(""))
        assertEquals(ParseResult.Invalid, Amounts.parseMeters("abc"))
        assertEquals(ParseResult.Invalid, Amounts.parseMeters("-1"))
    }

    @Test fun formatsPerLocale() {
        assertEquals("0.8", Amounts.formatMeters(80, Locale.ENGLISH))
        assertEquals("0,8", Amounts.formatMeters(80, Locale.GERMAN))
        assertEquals("2", Amounts.formatMeters(200, Locale.GERMAN))
        assertEquals("1,25", Amounts.formatMeters(125, Locale.GERMAN))
    }
}
