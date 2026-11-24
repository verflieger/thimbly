package at.mtr.sewfar.ui

import androidx.compose.ui.test.hasText
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
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithText("Library").performClick()
        compose.waitFor("No patterns yet.")
        compose.onNodeWithTag("tab-MACHINES").performClick()
        compose.waitFor("No machines yet.")
        compose.onNodeWithTag("tab-PEOPLE").performClick()
        compose.waitFor("No people yet.")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitFor("No projects yet.")
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
        compose.waitFor("Genäht für")
    }
}
