package at.mtr.sewfar.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import at.mtr.sewfar.MainActivity
import at.mtr.sewfar.SewFarApp
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    private fun pageOpen() =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("status-DONE").fetchSemanticsNodes().isNotEmpty() }

    @After fun resetSettings() = runBlocking { (compose.activity.application as SewFarApp).container.settings.setTrackTime(true) }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun createWithPatternSettingAndTimerThenDeleteAndUndo() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Hoodie")
        compose.onNodeWithTag("dialogSave").performClick()
        pageOpen()

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
        compose.onNodeWithTag("type-OVERLOCKER").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("field-differential_feed").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("field-differential_feed").performTextInput("1.3")
        compose.onNodeWithTag("save").performClick()
        waitFor("Overlock · Overlocker") // view mode: machine chip with its type
        compose.onNodeWithText("1.3").assertExists()
        back()
        waitFor("Jersey seam · Overlock")

        compose.onNodeWithTag("timer").performScrollTo().performClick()
        waitFor("Stop")
        back()
        waitFor("In progress (1)")

        compose.onNodeWithText("Hoodie").performClick()
        pageOpen() // the project page is open, not still sliding in
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
        pageOpen()
        compose.onNodeWithTag("delete").performClick()
        waitFor("Project deleted")

        compose.activityRule.scenario.recreate()

        waitFor("Project deleted")
        compose.onNodeWithText("Undo").performClick()
        waitFor("Skirt")
    }

    /** SPEC 3.1, 3.2: a tag picked from the suggestions; the empty search offers it and a tap filters the list. */
    @Test
    fun tagFromSuggestionsFiltersTheSearch() {
        for (name in listOf("Skirt", "Jeans")) {
            compose.onNodeWithTag("newProject").performClick()
            compose.onNodeWithTag("dialogText").performTextInput(name)
            compose.onNodeWithTag("dialogSave").performClick()
            pageOpen()
            if (name == "Jeans") {
                compose.onNodeWithTag("addTag").performScrollTo().performClick()
                compose.onNodeWithText("Repair").performClick()
                waitFor("Tags")
            }
            back()
            waitFor("Planned")
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithTag("suggest-Repair").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Skirt")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Jeans").assertExists()
    }

    /** SPEC 4.1: a pattern's type groups the Library pattern list. */
    @Test
    fun patternTypeGroupsTheLibrary() {
        compose.onNodeWithTag("library").performClick()
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Raglan")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.onNodeWithTag("edit-garmentType").performClick()
        compose.onNodeWithTag("type-TOP").performClick()
        waitFor("Top")
        back()
        waitFor("Top (1)")
    }

    /** SPEC 5.1: a new machine is asked for its type, which is then shown read-only. */
    @Test
    fun newMachineAsksForItsTypeOnce() {
        compose.onNodeWithTag("library").performClick()
        compose.onNodeWithTag("tab-MACHINES").performClick()
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Juki")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.onNodeWithTag("type-COVERSTITCH").performClick()
        waitFor("Coverstitch")
    }

    @Test
    fun libraryTextsAreEditedInADialog() {
        compose.onNodeWithTag("library").performClick()
        compose.onNodeWithTag("tab-PATTERNS").performClick()
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Raglan")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Raglan")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit-designer").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Studio X")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Studio X")
        compose.onNodeWithTag("edit").performClick() // rename
        compose.onNodeWithTag("dialogText").performTextReplacement("Raglan shirt")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Raglan shirt")
    }

    @Test
    fun measurementsEnteredOnThePersonShowOnTheProject() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Dress for Lena")
        compose.onNodeWithTag("dialogSave").performClick()
        pageOpen()
        compose.onNodeWithTag("addPerson").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Lena")
        compose.onNodeWithText("Create “Lena”").performClick()
        // Linked once the add chip is gone (the title also contains "Lena").
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("addPerson").fetchSemanticsNodes().isEmpty() }
        // A plain tap on a short name opens the person, not the remove × next to it.
        compose.onNodeWithText("Lena", useUnmergedTree = true).performScrollTo().performClick()
        waitFor("Not measured yet")

        compose.onNodeWithTag("edit-measurements").performClick()
        compose.onNodeWithTag("measure-waist").performScrollTo().performTextInput("55,5")
        compose.waitForIdle() // on a phone there is always a frame between typing and back
        back() // saves the form
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
        pageOpen()
        compose.onNodeWithTag("edit-size").performScrollTo().performClick()
        compose.onNodeWithTag("dialogText").performTextInput("122")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.onNodeWithTag("edit-fabric").performScrollTo().performClick()
        compose.onNodeWithTag("dialogText").performTextInput("1,5 m jersey")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("1,5 m jersey")
        compose.onNodeWithText("122").assertExists()
    }

    @Test
    fun emptySectionsFoldIntoTheAddRow() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Apron")
        compose.onNodeWithTag("dialogSave").performClick()
        pageOpen()
        compose.onNodeWithText("Sewn for").assertDoesNotExist() // empty person section folded into the Add row
        compose.onNodeWithTag("edit-notes").performScrollTo().performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Pocket left")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("Pocket left")
    }

    @Test
    fun timeTrackingOffHidesTheTimer() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("trackTime").performClick()
        back()
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Apron")
        compose.onNodeWithTag("dialogSave").performClick()
        pageOpen()
        compose.onNodeWithTag("timer").assertDoesNotExist()
        compose.onNodeWithTag("timeTotal").assertDoesNotExist()
        back()
        waitFor("Planned (1)")
        compose.onAllNodes(hasContentDescription("Start")).assertCountEquals(0)
    }

    @Test
    fun aRunningTimerStaysStoppableWhenTimeTrackingIsOff() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Apron")
        compose.onNodeWithTag("dialogSave").performClick()
        pageOpen()
        compose.onNodeWithTag("timer").performScrollTo().performClick()
        waitFor("Stop")
        runBlocking { (compose.activity.application as SewFarApp).container.settings.setTrackTime(false) }
        compose.waitForIdle()
        compose.onNodeWithTag("timer").performScrollTo().performClick() // Stop
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("timer").fetchSemanticsNodes().isEmpty() }
    }
}
