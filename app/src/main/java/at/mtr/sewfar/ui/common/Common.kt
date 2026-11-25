package at.mtr.sewfar.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R

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

/** A stored value shown read-only: small label, the value below (nothing if blank and [placeholder] is null). */
@Composable
fun ReadOnlyField(label: String, value: String, placeholder: String? = null) {
    if (value.isBlank() && placeholder == null) return
    Column {
        SectionLabel(label)
        Text(
            value.ifBlank { placeholder.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Frame of a detail page (SPEC 2). Viewing: back, [title], edit (if [onEdit]) and delete. Editing: ✕ and Save; back
 * or ✕ discards, asking first when [dirty].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    editing: Boolean = false,
    dirty: Boolean = false,
    canSave: Boolean = true,
    onSave: () -> Unit = {},
    onCancel: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val cancel = { if (dirty) confirmDiscard = true else onCancel() }
    BackHandler(enabled = editing) { cancel() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (editing) {
                        IconButton(onClick = cancel, modifier = Modifier.testTag("cancel")) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel))
                        }
                    } else {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    }
                },
                actions = {
                    if (editing) {
                        TextButton(onClick = onSave, enabled = canSave, modifier = Modifier.testTag("save")) {
                            Text(stringResource(R.string.action_save))
                        }
                    } else {
                        if (onEdit != null) {
                            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit")) {
                                Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit))
                            }
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete")) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        content = content,
    )
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onCancel() }, onKeep = { confirmDiscard = false })
}

/**
 * Asks for one text (a name, notes) with Save and Cancel (SPEC 2). Save is disabled while [required] text is blank.
 */
@Composable
fun TextDialog(
    title: String,
    label: String,
    initial: String = "",
    singleLine: Boolean = true,
    required: Boolean = true,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // Typed text is never dropped silently (SPEC 2): Cancel, back and tapping outside ask first.
    val cancel = { if (text != initial) confirmDiscard = true else onDismiss() }
    if (confirmDiscard) {
        DiscardDialog(onDiscard = onDismiss, onKeep = { confirmDiscard = false })
        return
    }
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(title) },
        text = {
            LabeledTextField(
                text, { text = it }, label,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                fieldModifier = Modifier.focusRequester(focus).testTag("dialogText"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = !required || text.isNotBlank(), modifier = Modifier.testTag("dialogSave")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.action_cancel)) } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** "Discard changes?" with Discard and Keep editing. */
@Composable
fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.discard_title)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.keep_editing)) } },
    )
}

/** Text field of an edit form. */
@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    /** Test tag of the field itself. */
    tag: String? = null,
) {
    LabeledTextField(
        value, onChange, label, modifier, fieldModifier = tag?.let { Modifier.testTag(it) } ?: Modifier,
        singleLine = singleLine, minLines = if (singleLine) 1 else 3,
    )
}

/**
 * The one text input of the app (SPEC 2): its [label] always sits above the field, like on the read-only page, so a
 * field never changes size when focused. TalkBack reads the label as the field's description. [modifier] sizes the
 * whole, [fieldModifier] goes to the field itself (focus, test tag).
 */
@Composable
fun LabeledTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    fieldModifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label, Modifier.clearAndSetSemantics {})
        OutlinedTextField(
            value, onChange,
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.then(fieldModifier),
        )
    }
}

/** "3 h 20 min", "45 min" (SPEC 3.3). */
@Composable
fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 60) stringResource(R.string.duration_h_min, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_min, minutes)
}
