package at.mtr.sewfar.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import at.mtr.sewfar.MainActivity
import at.mtr.sewfar.SewFarApp
import at.mtr.sewfar.data.TestDb
import at.mtr.sewfar.domain.PhotoOwner
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SPEC 4.1: the pattern page shows the photos of the projects using it; the viewer leads to the project. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en")
class PatternGalleryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun projectPhotoOnThePatternPageLeadsToTheProject() {
        val c = (compose.activity.application as SewFarApp).container
        runBlocking {
            val pattern = c.patterns.create("Raglan")
            val project = c.projects.create("Shirt for Lena")
            c.projects.addPattern(project, pattern)
            c.photos.add(PhotoOwner.PROJECT, project) { TestDb.jpeg(60, 40).inputStream() }
        }
        compose.onNodeWithTag("library").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Raglan")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Raglan").performClick()
        compose.onNodeWithTag("readOnlyPhotos").performScrollTo()
        // The project photo is the only photo on the page.
        compose.onAllNodesWithContentDescription("Photo").onLast().performClick()
        compose.onNodeWithTag("photoDelete").assertDoesNotExist()
        compose.onNodeWithTag("photoCaption").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("status-DONE")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Shirt for Lena").assertExists()
    }
}
