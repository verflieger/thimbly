package at.mtr.sewfar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.robolectric.RobolectricTestRunner
import at.mtr.sewfar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** SPEC 2: app opens on Projects, four tabs, settings behind the gear, system back returns. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensOnProjectsAndSwitchesTabs() {
        compose.onNodeWithText("No projects yet.").assertIsDisplayed()
        compose.onNodeWithText("Patterns").performClick()
        compose.onNodeWithText("No patterns yet.").assertIsDisplayed()
        compose.onNodeWithText("People").performClick()
        compose.onNodeWithText("No people yet.").assertIsDisplayed()
        compose.onNodeWithText("Machines").performClick()
        compose.onNodeWithText("Add your first machine to save its settings.").assertIsDisplayed()
    }

    @Test
    fun settingsOpenFromGearAndBackReturns() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Wallpaper colours").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("No projects yet.").assertIsDisplayed()
    }
}

/** The German UI uses the German strings. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de")
class GermanNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsGermanTabs() {
        compose.onNodeWithText("Noch keine Projekte.").assertIsDisplayed()
        compose.onNodeWithText("Schnittmuster").performClick()
        compose.onNodeWithText("Noch keine Schnittmuster.").assertIsDisplayed()
    }
}
