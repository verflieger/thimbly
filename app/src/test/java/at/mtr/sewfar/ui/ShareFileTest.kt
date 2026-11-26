package at.mtr.sewfar.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import at.mtr.sewfar.ui.common.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Backups (SPEC 8.2) and photos (SPEC 3.5) reach the share sheet as a readable content URI. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareFileTest {
    // The test runs on Robolectric's paused main looper; a test Main dispatcher lets withContext(Main) proceed.
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensShareSheetWithTheWrittenFile() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shareFile(app, "thimbly-test.jpg", "image/jpeg") { it.write(byteArrayOf(1, 2, 3)) }

        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/jpeg", send.type)
        @Suppress("DEPRECATION") val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(listOf<Byte>(1, 2, 3), app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toList())
        assertEquals("preview", uri, send.clipData!!.getItemAt(0).uri)
    }
}
