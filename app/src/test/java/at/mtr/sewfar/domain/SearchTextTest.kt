package at.mtr.sewfar.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test fun ignoresCaseAndAccents() {
        assertTrue(SearchText.matches("nah", listOf("Nähkurs")))
        assertTrue(SearchText.matches("STRASSE", listOf("Straße")))
        assertTrue(SearchText.matches("hoodie", listOf("Mein HOODIE")))
    }

    @Test fun everyWordMustMatchSomewhere() {
        assertTrue(SearchText.matches("josi hoodie", listOf("Hoodie", "Josi")))
        assertFalse(SearchText.matches("josi dress", listOf("Hoodie", "Josi")))
    }

    @Test fun blankMatchesAll() = assertTrue(SearchText.matches("  ", emptyList()))
}
