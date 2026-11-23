package at.mtr.sewfar.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import at.mtr.sewfar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC 4 and 5: create a pattern with a new category, create a person and record a measurement. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class PatternsPeopleFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun patternWithNewCategory() {
        compose.onNodeWithText("Patterns").performClick()
        compose.onNodeWithContentDescription("New pattern").performClick()
        compose.onNodeWithTag("nameDialogField").performTextInput("Raglan shirt")
        compose.onNodeWithText("Create").performClick()
        waitFor("No category")
        compose.onNodeWithText("No category").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Leggings")
        compose.onNodeWithText("Create “Leggings”").performClick()
        waitFor("Leggings")
        back()
        waitFor("Raglan shirt")
        waitFor("Leggings")
    }

    @Test
    fun personWithMeasurement() {
        compose.onNodeWithText("People").performClick()
        compose.onNodeWithContentDescription("New person").performClick()
        compose.onNodeWithTag("nameDialogField").performTextInput("Josi")
        compose.onNodeWithText("Create").performClick()
        waitFor("Measure")
        compose.onNodeWithText("Measure").performScrollTo().performClick()
        compose.onNode(hasText("Height") and hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("104")
        compose.onNodeWithText("Save").performClick()
        waitFor("Height")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("104 cm", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun archivedPatternHiddenUntilShowArchived() {
        compose.onNodeWithText("Patterns").performClick()
        compose.onNodeWithContentDescription("New pattern").performClick()
        compose.onNodeWithTag("nameDialogField").performTextInput("Old tote")
        compose.onNodeWithText("Create").performClick()
        waitFor("No category")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Archive").performClick()
        waitFor("Archived")
        back()
        waitFor("No patterns yet.")
        compose.onNodeWithContentDescription("Show archived").performClick()
        waitFor("Old tote")
    }
}
