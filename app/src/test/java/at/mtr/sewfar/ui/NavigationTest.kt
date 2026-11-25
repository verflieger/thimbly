package at.mtr.sewfar.ui

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.mtr.sewfar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitFor(text: String) =
    waitUntil(5_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

/** SPEC 2: opens on the project list; Library and Settings from the menu; back returns. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun libraryTabsAndBack() {
        compose.waitFor("No projects yet.")
        compose.onNodeWithTag("library").performClick()
        compose.waitFor("No patterns yet.") // Patterns is the first tab
        compose.onNodeWithTag("tab-PEOPLE").performClick()
        compose.waitFor("No people yet.")
        compose.onNodeWithTag("tab-MACHINES").performClick()
        compose.waitFor("No machines yet.")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitFor("No projects yet.")
    }

    @Test
    fun newProjectAndLibraryShareTheBottomRow() {
        val newProject = compose.onNodeWithTag("newProject").getBoundsInRoot()
        val library = compose.onNodeWithTag("library").getBoundsInRoot()
        val width = compose.onRoot().getBoundsInRoot().right
        assertTrue(newProject.right < library.left) // New project on the left
        assertEquals((newProject.right - newProject.left).value, (library.right - library.left).value, 1f)
        assertEquals(16f, newProject.left.value, 1f)
        assertEquals(width.value - 16f, library.right.value, 1f)
    }

    @Test
    fun settingsFromMenu() {
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.waitFor("Wallpaper colours")
    }
}

/** The German UI uses the German strings. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de")
class GermanNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsGermanTexts() {
        compose.waitFor("Noch keine Projekte.")
        compose.onNodeWithTag("newProject").performClick()
        compose.waitFor("Neues Projekt")
        compose.waitFor("Speichern")
    }
}
