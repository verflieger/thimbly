package at.mtr.sewfar.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import at.mtr.sewfar.MainActivity
import at.mtr.sewfar.SewFarApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC 6: create an overlocker and a setup, use it in a project, change the setup, see the diff. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class MachinesFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun setupUsedInProjectShowsChanges() {
        compose.onNodeWithText("Machines").performClick()
        compose.onNodeWithContentDescription("New setup").performClick()
        // No machines yet: the machine dialog comes first.
        compose.onNodeWithTag("machineName").performTextInput("Juki")
        compose.onNodeWithText("Overlocker").performClick()
        compose.onNodeWithText("Create").performClick()
        // The new setup opens directly; its title fills from purpose and fabric later (SPEC 6.3).
        waitFor("Differential feed")
        compose.onNodeWithTag("setupValue-differential_feed").performScrollTo().performTextInput("1.3")
        val container = (compose.activity.application as SewFarApp).container
        compose.waitUntil(5_000) { runBlocking { container.setups.observeAllValues().first().any { it.value == "1.3" } } }
        back()

        compose.onNodeWithText("Projects").performClick()
        compose.onNodeWithContentDescription("New project").performClick()
        compose.onNodeWithTag("newProjectName").performTextInput("Leggings")
        compose.onNodeWithText("Create").performClick()
        waitFor("Machine setups used")
        compose.onNodeWithTag("useSetup").performScrollTo().performClick()
        compose.onAllNodesWithText("Juki").onFirst().performClick()
        waitFor("Differential feed: 1.3")

        // Change the setup elsewhere: the project keeps its snapshot and shows the difference.
        runBlocking {
            val setup = container.setups.observeAll().first().single()
            val field = container.machines.fields(setup.machineId).first { it.builtinKey == "differential_feed" }
            container.setups.setValue(setup.id, field.id, "1.5")
        }
        waitFor("Setup changed since")
        waitFor("Differential feed: 1.3 → 1.5")
    }
}
