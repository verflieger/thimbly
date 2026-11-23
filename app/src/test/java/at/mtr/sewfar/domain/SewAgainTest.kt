package at.mtr.sewfar.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEC 3.7 naming. */
class SewAgainTest {
    @Test fun firstCopyIsTwo() = assertEquals("Hoodie (2)", SewAgain.nextName("Hoodie", listOf("Hoodie")))

    @Test fun copyOfCopyContinuesFromBase() =
        assertEquals("Hoodie (4)", SewAgain.nextName("Hoodie (2)", listOf("Hoodie", "Hoodie (2)", "Hoodie (3)")))

    @Test fun otherNamesDontCount() =
        assertEquals("Hoodie (2)", SewAgain.nextName("Hoodie", listOf("Hoodie", "Hoodie for Josi (7)", "Hoodies (5)")))

    @Test fun deletedOriginalStillStartsAtTwo() = assertEquals("Hoodie (2)", SewAgain.nextName("Hoodie", emptyList()))

    @Test fun baseNameStripsOnlyTrailingNumber() {
        assertEquals("Size (110) shirt", SewAgain.baseName("Size (110) shirt"))
        assertEquals("Shirt", SewAgain.baseName("Shirt (12)"))
    }
}
