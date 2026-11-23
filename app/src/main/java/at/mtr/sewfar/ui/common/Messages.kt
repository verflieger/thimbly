package at.mtr.sewfar.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Snackbars shown by the app shell, so they survive leaving a screen (e.g. "Project deleted · Undo"). */
class Messages(val host: SnackbarHostState, private val scope: CoroutineScope, private val undoLabel: String) {
    fun show(text: String) {
        scope.launch { host.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    /** Shows [text] with an Undo action that runs [undo]. */
    fun showUndo(text: String, undo: suspend () -> Unit) {
        scope.launch {
            val result = host.showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
}

val LocalMessages = staticCompositionLocalOf<Messages> { error("No Messages provided") }
