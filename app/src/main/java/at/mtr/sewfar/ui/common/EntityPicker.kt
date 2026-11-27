package at.mtr.sewfar.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.SearchText

/** One choice in an [EntityPicker]; [leading] is e.g. a cover thumbnail. */
data class PickerItem(val id: String, val title: String, val subtitle: String? = null, val leading: (@Composable () -> Unit)? = null)

/**
 * Searchable single-choice picker with a "Create …" row (SPEC 2): typing a name that doesn't exist
 * offers to create it, so an empty library never blocks. [noneLabel] adds a "none" choice (selects null).
 * With [selected] it picks several (tags, SPEC 3.2): each row shows a checkbox, the ticked ones come first above a
 * line, a tap or "Create …" applies at once and the dialog stays open until Done.
 */
@Composable
fun EntityPicker(
    title: String,
    items: List<PickerItem>,
    onPick: (String?) -> Unit,
    /** Null: no "Create …" row (items that can't be created from a name). */
    onCreate: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    noneLabel: String? = null,
    /** Names that count as existing for the create row; null = the titles of [items]. */
    existingNames: List<String>? = null,
    /** Text of the create row, with the typed name as argument. */
    @StringRes createLabel: Int = R.string.picker_create_new,
    /** IDs that are ticked; non-null = pick several. */
    selected: Set<String>? = null,
    /** Placeholder of the search field, e.g. "Search or new label". */
    searchHint: String = stringResource(R.string.action_search),
    /** IDs with a ✎ that calls [onEdit] (renaming a label, SPEC 3.2). */
    editable: Set<String> = emptySet(),
    onEdit: (String) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = items.filter { SearchText.matches(query, listOfNotNull(it.title, it.subtitle)) }
    val exact = (existingNames ?: items.map { it.title }).any { SearchText.fold(it) == SearchText.fold(query.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(searchHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pickerSearch"),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    if (onCreate != null && query.isNotBlank() && !exact) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(createLabel, query.trim())) },
                                leadingContent = { Icon(Icons.Outlined.Add, null) },
                                modifier = Modifier.clickable { onCreate(query.trim()); if (selected != null) query = "" },
                            )
                        }
                    }
                    if (noneLabel != null && query.isBlank()) {
                        item {
                            ListItem(headlineContent = { Text(noneLabel) }, modifier = Modifier.clickable { onPick(null) })
                        }
                    }
                    val (first, rest) = matches.partition { selected != null && it.id in selected }
                    // Picking several: no keys, so the list keeps its scroll position instead of following a row that a
                    // tick moves above the line (it would scroll the ticked labels out of view).
                    items(first + rest, key = if (selected == null) ({ it.id }) else null) { item ->
                        if (item === rest.firstOrNull() && first.isNotEmpty()) HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(item.title) },
                            // Picking several, every row has the ✎ slot (empty where there is nothing to rename), so ticking
                            // a suggestion, which then gets ✎, never changes a row's size.
                            trailingContent = if (item.id in editable) ({
                                IconButton(onClick = { onEdit(item.id) }, modifier = Modifier.testTag("edit-${item.id}")) {
                                    Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit))
                                }
                            }) else if (selected != null) ({ Spacer(Modifier.size(48.dp)) }) else null,
                            supportingContent = item.subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                            leadingContent = if (selected != null) ({ Checkbox(item.id in selected, onCheckedChange = null) }) else item.leading,
                            modifier = Modifier.clickable { onPick(item.id) }.testTag("pick-${item.id}"),
                        )
                    }
                    if (matches.isEmpty() && query.isBlank()) {
                        item {
                            Text(
                                stringResource(R.string.picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        },
        // Several: every tap is already applied, so there is nothing to cancel.
        confirmButton = { if (selected != null) TextButton(onClick = onDismiss, modifier = Modifier.testTag("pickerDone")) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { if (selected == null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
