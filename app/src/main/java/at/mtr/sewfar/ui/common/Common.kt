package at.mtr.sewfar.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Centered hint shown while a list is empty. */
@Composable
fun EmptyState(text: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

/** Frame of a detail screen: back arrow and a delete action; the name field below says what it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(onBack: () -> Unit, onDelete: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete")) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                    }
                },
            )
        },
        content = content,
    )
}

/**
 * Runs a screen's writes one after another in [scope] (which must outlive the screen, `AppContainer.appScope`), in
 * the order they were requested. Without this, a field's last save and the "discard if empty" check on leaving could
 * run in either order and lose the text (SPEC 2).
 */
class SerialWriter(private val scope: CoroutineScope) {
    private val mutex = Mutex()

    // UNDISPATCHED: the lock is requested right away on the calling thread, so the (fair) mutex keeps call order.
    fun launch(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { block() } }
    }

    suspend fun <T> run(block: suspend () -> T): T =
        scope.async(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { block() } }.await()

    /**
     * Runs [block] after every write requested so far and within [graceMillis] from now: fields save when their
     * screen leaves the composition, which may happen just after the ViewModel is cleared.
     */
    fun launchLast(graceMillis: Long = 1_000, block: suspend () -> Unit) {
        scope.launch {
            delay(graceMillis)
            mutex.withLock { block() }
        }
    }
}

/**
 * The name of a record being edited, held by its ViewModel (SPEC 2): typing saves after a short pause through
 * [writer]; [flushInLock] saves what is left (call it from inside a [SerialWriter] block, e.g. when the screen
 * closes, before an untouched new record is discarded).
 */
class EditableName(private val scope: CoroutineScope, private val writer: SerialWriter, private val save: suspend (String) -> Unit) {
    var text by mutableStateOf("")
        private set
    @Volatile private var edited = false
    private var job: Job? = null

    /** Follows the stored name while the user isn't typing. */
    fun load(saved: String) {
        if (!edited) text = saved
    }

    fun change(value: String) {
        text = value
        edited = true
        job?.cancel()
        job = scope.launch {
            delay(500)
            writer.run {
                save(value)
                if (text == value) edited = false
            }
        }
    }

    suspend fun flushInLock() {
        job?.cancel()
        if (edited) {
            save(text)
            edited = false
        }
    }
}

/** Name field of a detail screen; [focus] puts the cursor in it (new records). */
@Composable
fun NameField(name: EditableName, label: String, focus: Boolean, modifier: Modifier = Modifier) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    OutlinedTextField(
        value = name.text,
        onValueChange = name::change,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester).testTag("name"),
    )
    if (focus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}

/** "3 h 20 min", "45 min" (SPEC 3.3). */
@Composable
fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 60) stringResource(R.string.duration_h_min, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_min, minutes)
}
