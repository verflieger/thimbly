package at.mtr.sewfar.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import at.mtr.sewfar.MainActivity
import at.mtr.sewfar.SewFarApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC 3.3: time entries can be corrected, and a long-running timer asks whether its time is right. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class TimeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun setDuration(text: String) {
        compose.onNodeWithTag("duration").performTextClearance()
        compose.onNodeWithTag("duration").performTextInput(text)
        compose.onNodeWithTag("timeSave").performClick()
    }

    @Test
    fun entryIsCorrectedByTapAndALongTimerAsks() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Shirt")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("addTime")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("addTime").performClick()
        setDuration("1:30")
        waitFor("1 h 30 min")

        // Tap the entry: the dialog opens with its duration, Save replaces it.
        compose.onNodeWithText("Show entries (1)").performScrollTo().performClick()
        val projects = (compose.activity.application as SewFarApp).container.projects
        val projectId = runBlocking { projects.observeAll().first().single().id }
        val entry = runBlocking { projects.observeTime(projectId).first().single() }
        compose.onNodeWithTag("timeEntry-${entry.id}").performScrollTo().performClick()
        compose.onNodeWithTag("duration").assertTextContains("1:30")
        setDuration("45")
        waitFor("45 min")

        // A timer stopped after a long run (here: reported directly) asks on whatever screen is open.
        runBlocking { projects.longStops.emit(projects.observeTime(projectId).first().single()) }
        waitFor("Shirt: the timer ran 45 min. Is that right?")
        setDuration("0:30")
        waitFor("30 min")
    }
}
