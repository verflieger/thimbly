package at.mtr.sewfar.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val SaveDelayMillis = 600L

/**
 * Text field for in-place editing (SPEC 3.3): saves [onSave] a moment after typing stops and when it leaves the
 * screen. [isValid] blocks saving (and shows [errorText]) while the text is invalid. [key] identifies the edited
 * value, so the field resets when it shows a different record.
 */
@Composable
fun AutoSaveTextField(
    savedValue: String,
    onSave: (String) -> Unit,
    label: String,
    key: Any,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isValid: (String) -> Boolean = { true },
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var text by rememberSaveable(key) { mutableStateOf(savedValue) }
    // Only text the user typed is saved. Without this, a value changed elsewhere (e.g. by the completion sheet)
    // would be overwritten with the stale text this field showed first, when the field leaves the screen.
    var edited by rememberSaveable(key) { mutableStateOf(false) }
    val saved by rememberUpdatedState(savedValue)
    val save by rememberUpdatedState(onSave)
    val valid by rememberUpdatedState(isValid)
    // Follow outside changes while not editing; an edit is done once the stored value caught up with it.
    LaunchedEffect(key, savedValue) {
        if (!edited || text == savedValue) {
            text = savedValue
            edited = false
        }
    }
    LaunchedEffect(key, text) {
        delay(SaveDelayMillis)
        if (edited && text != saved && valid(text)) save(text)
    }
    DisposableEffect(key) {
        onDispose { if (edited && text != saved && valid(text)) save(text) }
    }
    val invalid = !isValid(text)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; edited = true },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = invalid,
        supportingText = if (invalid && errorText != null) ({ Text(errorText) }) else null,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
    )
}
