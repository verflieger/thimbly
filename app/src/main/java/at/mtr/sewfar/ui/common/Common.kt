package at.mtr.sewfar.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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

/** A section label with an ✎ button; the button's tag is `edit-<tag>`. */
@Composable
fun EditHeader(title: String, tag: String, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.weight(1f))
        IconButton(onClick = onEdit, modifier = Modifier.testTag("edit-$tag")) {
            Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit), Modifier.size(20.dp))
        }
    }
}

/** A text of a library record, shown read-only; a tap or ✎ edits it in a [TextDialog] (SPEC 2). */
@Composable
fun EditableText(label: String, value: String, tag: String, singleLine: Boolean = false, onSave: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Column {
        EditHeader(label, tag) { editing = true }
        Text(
            value.ifBlank { stringResource(R.string.tap_to_add) },
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().clickable { editing = true },
        )
    }
    if (editing) {
        TextDialog(
            label, label, initial = value, singleLine = singleLine, required = false,
            onSave = { onSave(it); editing = false }, onDismiss = { editing = false },
        )
    }
}

/** A stored value shown read-only: small label, the value below (nothing if blank). */
@Composable
fun ReadOnlyField(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        SectionLabel(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Frame of a detail page (SPEC 2). Viewing: back, [title], edit (if [onEdit]) and delete. Editing: ✕ discards and
 * Save saves; back saves when [dirty] (and does nothing while the form can't be saved, so nothing typed is lost).
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
    BackHandler(enabled = editing) { if (!dirty) onCancel() else if (canSave) onSave() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (editing) {
                        IconButton(onClick = onCancel, modifier = Modifier.testTag("cancel")) {
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
}

/**
 * Asks for one text (a name, notes) with Save and Cancel (SPEC 2). Save is disabled while [required] text is blank.
 * A tap outside does nothing, so typed text is never lost by accident; Cancel and back discard.
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
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            LabeledTextField(
                text, { text = it }, label,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                fieldModifier = Modifier.focusRequester(focus).testTag("dialogText"),
                showLabel = label != title, // e.g. "Size" as title and label: show it once
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = !required || text.isNotBlank(), modifier = Modifier.testTag("dialogSave")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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
    suffix: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    showLabel: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) SectionLabel(label, Modifier.clearAndSetSemantics {})
        OutlinedTextField(
            value, onChange,
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            suffix = suffix?.let { { Text(it) } },
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
