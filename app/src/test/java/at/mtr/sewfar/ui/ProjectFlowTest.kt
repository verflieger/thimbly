package at.mtr.sewfar.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import at.mtr.sewfar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC 2, 3: name dialog, pattern and machine setting created inline, edit form, timer, delete and undo. */
@RunWith(RobolectricTestRunner::class)
// Default Robolectric screen (320x470dp): a larger qualifier made dialogs with text fields never go idle.
// Off-screen controls are reached with performScrollTo.
@Config(qualifiers = "en")
class ProjectFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun createWithPatternSettingAndTimerThenDeleteAndUndo() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Hoodie")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Sewn for")

        compose.onNodeWithTag("addPattern").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Raglan")
        compose.onNodeWithText("Create “Raglan”").performClick()
        waitFor("Raglan")

        // A machine setting with a new machine, straight from the project; its form opens for the values.
        compose.onNodeWithTag("addSetup").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Jersey seam")
        compose.onNodeWithText("New setting “Jersey seam”").performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Overlock")
        compose.onNodeWithText("Create “Overlock”").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("field-stitch_length").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("field-stitch_length").performTextInput("3")
        compose.onNodeWithTag("save").performClick()
        waitFor("Overlock") // view mode: machine chip
        compose.onNodeWithText("3").assertExists()
        back()
        waitFor("Jersey seam · Overlock")

        compose.onNodeWithTag("timer").performScrollTo().performClick()
        waitFor("Stop")
        back()
        waitFor("In progress (1)")

        compose.onNodeWithText("Hoodie").performClick()
        waitFor("Sewn for") // the project page is open, not still sliding in
        compose.onNodeWithTag("delete").performClick()
        waitFor("Project deleted")
        compose.onNodeWithText("Undo").performClick()
        waitFor("Hoodie")
    }

    @Test
    fun undoBarSurvivesRotation() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Skirt")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Sewn for")
        compose.onNodeWithTag("delete").performClick()
        waitFor("Project deleted")

        compose.activityRule.scenario.recreate()

        waitFor("Project deleted")
        compose.onNodeWithText("Undo").performClick()
        waitFor("Skirt")
    }

    @Test
    fun cancellingAnEditFormAsksBeforeDiscarding() {
        compose.onNodeWithTag("library").performClick()
        compose.onNodeWithTag("tab-PATTERNS").performClick()
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Raglan")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Raglan")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit").performClick()
        compose.onNodeWithTag("field-designer").performTextReplacement("Studio X")
        compose.waitForIdle() // on a phone there is always a frame between typing and back
        back()
        compose.waitForIdle()
        compose.onNodeWithText("Discard changes?").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("save").performClick()
        waitFor("Studio X")
    }

    @Test
    fun settingDoneAsksForRatingAndWhatILearned() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Skirt")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Sewn for")

        compose.onNodeWithTag("status-DONE").performClick()
        compose.onNodeWithTag("finishStar-4").performClick()
        compose.onNodeWithTag("finishLearned").performTextInput("Grade the seams")
        compose.onNodeWithTag("finishSave").performClick()
        waitFor("Grade the seams")
        compose.onNodeWithTag("finishSave").assertDoesNotExist()
    }

    @Test
    fun measurementsEnteredOnThePersonShowOnTheProject() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Dress for Lena")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Sewn for")
        compose.onNodeWithTag("addPerson").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Lena")
        compose.onNodeWithText("Create “Lena”").performClick()
        // Linked once the add chip is gone (the title also contains "Lena").
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("addPerson").fetchSemanticsNodes().isEmpty() }
        // A plain tap on a short name opens the person, not the remove × next to it.
        compose.onNodeWithText("Lena", useUnmergedTree = true).performScrollTo().performClick()
        waitFor("Not measured yet")

        compose.onNodeWithTag("edit").performClick()
        compose.onNodeWithTag("measure-waist").performScrollTo().performTextInput("55,5")
        compose.onNodeWithTag("save").performClick()
        waitFor("Measured on")
        compose.onNodeWithText("55.5 cm").assertExists()
        back()
        waitFor("Measurements from")
    }

    @Test
    fun sizeAndFabricAreShownLikeNotes() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Hoodie")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Sewn for")
        compose.onNodeWithTag("edit-size").performScrollTo().performClick()
        compose.onNodeWithTag("dialogText").performTextInput("122")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.onNodeWithTag("edit-fabric").performScrollTo().performClick()
        compose.onNodeWithTag("dialogText").performTextInput("1,5 m jersey")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("1,5 m jersey")
        compose.onNodeWithText("122").assertExists()
    }
}
