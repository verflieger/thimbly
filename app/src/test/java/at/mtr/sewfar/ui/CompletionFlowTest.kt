package at.mtr.sewfar.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsEnabled
import at.mtr.sewfar.MainActivity
import at.mtr.sewfar.SewFarApp
import at.mtr.sewfar.domain.ProjectStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC 3.4, 3.5, 3.7: finish a project with a lesson, sew it again and see "Last time"; write a log note. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class CompletionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun finishSewAgainAndLog() {
        compose.onNodeWithContentDescription("New project").performClick()
        compose.onNodeWithTag("newProjectName").performTextInput("Tote bag")
        compose.onNodeWithText("Create").performClick()
        waitFor("Progress log")

        compose.onNodeWithTag("statusChip").performClick()
        compose.onNodeWithText("Done").performClick()
        waitFor("Finished!")
        compose.onNodeWithText("Fit was good").performScrollTo().performClick()
        compose.onNodeWithContentDescription("4 of 5 stars").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()

        waitFor("Lessons & rating")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Sew again").performClick()
        waitFor("Tote bag (2)")
        waitFor("Last time")
        compose.onNodeWithText("Fit was good").assertExists()

        compose.onNodeWithText("Add note").performScrollTo().performClick()
        compose.onNodeWithTag("noteText").performTextInput("Cut out the handles")
        compose.onNodeWithText("Save").performClick()
        waitFor("Cut out the handles")
    }

    private val container get() = (compose.activity.application as SewFarApp).container

    private fun projectId(name: String) = runBlocking { container.projects.observeAll().first().first { it.name == name }.id }

    private fun newProject(name: String) {
        compose.onNodeWithContentDescription("New project").performClick()
        compose.onNodeWithTag("newProjectName").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
        waitFor("Progress log")
    }

    private fun setStatus(label: String) {
        compose.onNodeWithTag("statusChip").performScrollTo().performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test
    fun skipDiscardsStagedLessons() {
        newProject("Apron")
        setStatus("Done")
        waitFor("Finished!")
        compose.onNodeWithText("Fit was good").performScrollTo().performClick()
        compose.onNodeWithText("Skip").performClick()
        waitFor("Lessons & rating")
        compose.onNodeWithText("Fit was good").performScrollTo().assertIsNotSelected()
        assertEquals(0, runBlocking { container.projects.lessons(projectId("Apron")).size })
    }

    @Test
    fun recompletingKeepsRatingAndNotesAndSaveClearsRemovedLesson() {
        newProject("Skirt")
        setStatus("Done")
        waitFor("Finished!")
        compose.onNodeWithText("Shorten").performScrollTo().performClick()
        compose.onNodeWithContentDescription("4 of 5 stars").performScrollTo().performClick()
        compose.onNodeWithTag("completionNotes").performScrollTo().performTextInput("Use a lining")
        compose.onNodeWithText("Save").performClick()
        waitFor("Lessons & rating")
        val id = projectId("Skirt")
        compose.waitUntil(5_000) { runBlocking { container.projects.get(id)?.rating == 4 } }

        // Back to Paused keeps rating and notes (SPEC 3.2); finishing again and saving must not wipe them.
        setStatus("Paused")
        compose.waitUntil(5_000) { runBlocking { container.projects.get(id)?.status == ProjectStatus.PAUSED } }
        setStatus("Done")
        waitFor("Finished!")
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Shorten").performScrollTo().performClick() // remove the lesson
        compose.onNodeWithText("Save").performClick()
        waitFor("Lessons & rating")

        compose.waitUntil(5_000) { runBlocking { container.projects.lessons(id).isEmpty() } }
        val project = runBlocking { container.projects.get(id)!! }
        assertEquals(4, project.rating)
        assertEquals("Use a lining", project.completionNotes)
    }
}
