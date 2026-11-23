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
    val saved by rememberUpdatedState(savedValue)
    val save by rememberUpdatedState(onSave)
    val valid by rememberUpdatedState(isValid)
    LaunchedEffect(key, text) {
        delay(SaveDelayMillis)
        if (text != saved && valid(text)) save(text)
    }
    DisposableEffect(key) {
        onDispose { if (text != saved && valid(text)) save(text) }
    }
    val invalid = !isValid(text)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = invalid,
        supportingText = if (invalid && errorText != null) ({ Text(errorText) }) else null,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
    )
}
