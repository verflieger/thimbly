package at.mtr.sewfar.ui.photos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import at.mtr.sewfar.data.TestDb
import at.mtr.sewfar.data.photos.PhotoFiles
import at.mtr.sewfar.data.photos.PhotoLoader
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SPEC 3.5: full-screen viewer swipes between photos and closes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en")
class PhotoViewerTest {
    @get:Rule val compose = createComposeRule()

    private val files = PhotoFiles(File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "viewer-test"))
    private val ids = listOf("p1", "p2", "p3").onEach { id -> files.write(id) { TestDb.jpeg(60, 40).inputStream() } }

    @After fun tearDown() { files.dir.deleteRecursively() }

    private fun show(initial: String?, onClose: () -> Unit = {}) = compose.setContent {
        CompositionLocalProvider(LocalPhotoLoader provides PhotoLoader(files)) {
            PhotoViewerScreen(ids, initial, onClose)
        }
    }

    @Test
    fun swipesBetweenPhotos() {
        show(initial = null)
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithTag("photoPager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    @Test
    fun opensAtInitialPhotoAndCloses() {
        var closed = false
        show(initial = "p3") { closed = true }
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        assertTrue(closed)
    }
}
