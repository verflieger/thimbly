package at.mtr.sewfar.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import at.mtr.sewfar.data.db.DeletionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Snackbars shown by the app shell, so they survive leaving a screen (e.g. "Project deleted · Undo"). */
class Messages(
    val host: SnackbarHostState,
    private val scope: CoroutineScope,
    private val undoLabel: String,
    /** Undoes a deletion (AppContainer.undo). */
    private val undoDeletion: suspend (DeletionToken) -> Unit,
) {
    fun show(text: String) {
        scope.launch { host.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    /** Shows [text] with an Undo action that restores what the deletion [token] removed. */
    fun showUndo(text: String, token: DeletionToken) = showUndo(text) { undoDeletion(token) }

    /** Shows [text] with an Undo action that runs [undo]; for changes that are not deletions (a person removed). */
    fun showUndo(text: String, undo: suspend () -> Unit) {
        scope.launch {
            val result = host.showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
}

val LocalMessages = staticCompositionLocalOf<Messages> { error("No Messages provided") }
