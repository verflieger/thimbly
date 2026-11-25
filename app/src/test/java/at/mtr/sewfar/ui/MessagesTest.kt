package at.mtr.sewfar.ui

import androidx.compose.material3.SnackbarHostState
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.ui.common.Messages
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC 6: an Undo snackbar never outlives the Undo window, so Undo can't silently do nothing after the purge. */
class MessagesTest {
    @Test
    fun undoSnackbarClosesBeforeThePurge() = runTest {
        val host = SnackbarHostState()
        val undone = mutableListOf<DeletionToken>()
        // A very long accessibility timeout is still capped before the purge.
        val messages = Messages(host, backgroundScope, "Undo", { undone += it }, undoMillis = Long.MAX_VALUE)

        messages.showUndo("Deleted", DeletionToken(1))
        runCurrent()
        assertNotNull(host.currentSnackbarData)
        advanceTimeBy(Messages.UndoShownMillis + 1)
        assertNull(host.currentSnackbarData)
        assertTrue(Messages.UndoShownMillis < SoftDeleter.UndoWindowMillis)

        messages.showUndo("Deleted", DeletionToken(2))
        runCurrent()
        host.currentSnackbarData!!.performAction()
        runCurrent()
        assertEquals(listOf(DeletionToken(2)), undone)
    }

    @Test
    fun undoBarClosesAfter6sAndANewOneReplacesTheOld() = runTest {
        val host = SnackbarHostState()
        val messages = Messages(host, backgroundScope, "Undo", {})

        messages.showUndo("First", DeletionToken(1))
        runCurrent()
        messages.showUndo("Second", DeletionToken(2))
        runCurrent()
        assertEquals("Second", host.currentSnackbarData?.visuals?.message)

        advanceTimeBy(Messages.UndoMillis + 1)
        assertNull(host.currentSnackbarData)
    }
}
