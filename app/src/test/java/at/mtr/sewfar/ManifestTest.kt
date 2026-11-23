package at.mtr.sewfar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC 1: network permissions and CAMERA are stripped in the source manifest. The merged manifest is checked
 * at build time by the check<Variant>Permissions task.
 */
class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun forbiddenPermissionsAreRemoved() {
        for (permission in listOf("INTERNET", "ACCESS_NETWORK_STATE", "CAMERA")) {
            val removeDirective = Regex(
                """<uses-permission\s+android:name="android\.permission\.$permission"\s+tools:node="remove"\s*/>""",
            )
            assertTrue("$permission must be declared with tools:node=\"remove\"", removeDirective.containsMatchIn(manifest))
        }
    }

    @Test
    fun androidBackupIsDisabled() {
        assertTrue(manifest.contains("""android:allowBackup="false""""))
    }
}
