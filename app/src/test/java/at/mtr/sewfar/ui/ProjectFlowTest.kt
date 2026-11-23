package at.mtr.sewfar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** SPEC 2, 3.1–3.3, 7: create a project with a pattern made in the picker, change status, delete and undo. */
@RunWith(RobolectricTestRunner::class)
// Default Robolectric screen (320x470dp): a larger qualifier made dialogs with text fields never go idle.
// Off-screen controls are reached with performScrollTo.
@Config(qualifiers = "en")
class ProjectFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun createChangeStatusDeleteAndUndo() {
        compose.onNodeWithContentDescription("New project").performClick()
        compose.onNodeWithTag("newProjectName").performTextInput("Hoodie for Josi")
        compose.onNodeWithText("Add pattern").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Raglan")
        compose.onNodeWithText("Create “Raglan”").performClick()
        waitFor("Raglan")
        compose.onNodeWithText("Create").performClick()

        // Detail screen of the new project.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Size sewn")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("statusChip").performClick()
        compose.onNodeWithText("In progress").performClick()
        waitFor("In progress")
        back()

        waitFor("In progress · 1")
        compose.onNodeWithText("Hoodie for Josi").assertIsDisplayed()

        // Delete from the detail screen, then undo from the snackbar.
        compose.onNodeWithText("Hoodie for Josi").performClick()
        waitFor("Size sewn")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete project").performClick()
        waitFor("Project deleted")
        waitFor("No projects yet.")
        compose.onNodeWithText("Undo").performClick()
        waitFor("Hoodie for Josi")
    }
}
