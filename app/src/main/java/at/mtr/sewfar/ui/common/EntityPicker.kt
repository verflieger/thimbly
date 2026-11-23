package at.mtr.sewfar.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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

/** One choice in an [EntityPicker]. */
data class PickerItem(val id: String, val title: String, val subtitle: String? = null, val avatarColor: Int? = null)

/**
 * Searchable single-choice picker with a "Create …" row (SPEC 2, "No dead ends"): typing a name that doesn't exist
 * offers to create it, so an empty library never blocks. [noneLabel] adds a "none" choice (selects null).
 */
@Composable
fun EntityPicker(
    title: String,
    items: List<PickerItem>,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    noneLabel: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = items.filter { SearchText.matches(query, listOfNotNull(it.title, it.subtitle)) }
    val exact = items.any { SearchText.fold(it.title) == SearchText.fold(query.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pickerSearch"),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    if (query.isNotBlank() && !exact) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.picker_create_new, query.trim())) },
                                leadingContent = { Icon(Icons.Outlined.Add, null) },
                                modifier = Modifier.clickable { onCreate(query.trim()) },
                            )
                        }
                    }
                    if (noneLabel != null && query.isBlank()) {
                        item {
                            ListItem(headlineContent = { Text(noneLabel) }, modifier = Modifier.clickable { onPick(null) })
                        }
                    }
                    items(matches, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = item.subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                            leadingContent = item.avatarColor?.let { { Avatar(item.title, it, size = 32.dp) } },
                            modifier = Modifier.clickable { onPick(item.id) },
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
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
