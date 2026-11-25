package at.mtr.sewfar.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.SoftDeleter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Snackbars shown by the app shell, so they survive leaving a screen (e.g. "Project deleted · Undo"). */
class Messages(
    val host: SnackbarHostState,
    private val scope: CoroutineScope,
    private val undoLabel: String,
    /** Undoes a deletion (AppContainer.undo). */
    private val undoDeletion: suspend (DeletionToken) -> Unit,
    /** How long an Undo bar stays; longer with an accessibility timeout, never past [UndoShownMillis]. */
    undoMillis: Long = UndoMillis,
) {
    private val undoShownMillis = undoMillis.coerceAtMost(UndoShownMillis)

    fun show(text: String) {
        scope.launch { host.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    /** Shows [text] with an Undo action that restores what the deletion [token] removed. */
    fun showUndo(text: String, token: DeletionToken) = showUndo(text) { undoDeletion(token) }

    /**
     * Shows [text] with an Undo action that runs [undo]; for changes that are not deletions (a person removed). It
     * replaces the bar on screen instead of queueing behind it, and closes before the purge (SPEC 6).
     */
    fun showUndo(text: String, undo: suspend () -> Unit) {
        host.currentSnackbarData?.dismiss()
        scope.launch {
            val result = withTimeoutOrNull(undoShownMillis) {
                host.showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    companion object {
        /** On screen for 6 s: Compose's Short (4 s) is tight for slow readers, Long (10 s) felt endless. */
        const val UndoMillis = 6_000L

        /** Including time waiting behind other snackbars; well inside the Undo window. */
        const val UndoShownMillis = SoftDeleter.UndoWindowMillis - 10_000
    }
}

val LocalMessages = staticCompositionLocalOf<Messages> { error("No Messages provided") }
