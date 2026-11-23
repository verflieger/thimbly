package at.mtr.sewfar.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdsTest {
    @Test
    fun uuid5MatchesReferenceImplementation() {
        // Python: uuid.uuid5(uuid.UUID('6f1c2b8e-3d4a-5c6b-9e7f-a1b2c3d4e5f6'), 'lesson_tag:fit_good')
        assertEquals("154e36c4-7ca5-5fba-b808-cd3b729704cf", Ids.builtIn("lesson_tag", "fit_good"))
        // RFC 4122 DNS namespace example: uuid.uuid5(uuid.NAMESPACE_DNS, 'python.org')
        assertEquals(
            UUID.fromString("886313e1-3b8a-5372-9b90-0c9aee199e5d"),
            Ids.uuid5(UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"), "python.org"),
        )
    }

    @Test
    fun builtInIdsDependOnTableAndKey() {
        assertEquals(Ids.builtIn("purpose", "hem"), Ids.builtIn("purpose", "hem"))
        assertNotEquals(Ids.builtIn("purpose", "hem"), Ids.builtIn("lesson_tag", "hem"))
    }
}
