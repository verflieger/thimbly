package at.mtr.sewfar.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import at.mtr.sewfar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC 3: new project without a dialog, pattern and machine setting created inline, timer, delete and undo. */
@RunWith(RobolectricTestRunner::class)
// Default Robolectric screen (320x470dp): a larger qualifier made dialogs with text fields never go idle.
// Off-screen controls are reached with performScrollTo.
@Config(qualifiers = "en")
class ProjectFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty() }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun createWithPatternSettingAndTimerThenDeleteAndUndo() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("name").performTextInput("Hoodie")

        compose.onNodeWithTag("addPattern").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Raglan")
        compose.onNodeWithText("Create “Raglan”").performClick()
        waitFor("Raglan")

        // A machine setting with a new machine, straight from the project.
        compose.onNodeWithTag("addSetup").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Jersey seam")
        compose.onNodeWithText("New setting “Jersey seam”").performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Overlock")
        compose.onNodeWithText("Create “Overlock”").performClick()
        waitFor("Stitch length") // setup screen of a sewing machine (default type)
        back()
        waitFor("Jersey seam · Overlock")

        compose.onNodeWithTag("timer").performScrollTo().performClick()
        waitFor("Stop")
        back()
        waitFor("In progress (1)")

        compose.onNodeWithText("Hoodie").performClick()
        compose.onNodeWithTag("delete").performClick()
        waitFor("Project deleted")
        compose.onNodeWithText("Undo").performClick()
        waitFor("Hoodie")
    }

    @Test
    fun leavingAnEmptyNewProjectDiscardsIt() {
        compose.onNodeWithTag("newProject").performClick()
        waitFor("Sewn for")
        back()
        waitFor("No projects yet.")
        waitGone("Untitled project")
    }
}
