package at.mtr.sewfar.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.ListRepository
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.NameDialog
import at.mtr.sewfar.ui.common.displayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@get:StringRes
val ListKind.title: Int
    get() = when (this) {
        ListKind.PATTERN_CATEGORY -> R.string.list_categories
        ListKind.LESSON_TAG -> R.string.list_lesson_tags
        ListKind.MEASUREMENT_TYPE -> R.string.list_measurement_types
        ListKind.PURPOSE -> R.string.list_purposes
    }

/** Edit one list (SPEC 10): add, rename, hide/show, reorder, delete (custom and unused only, SPEC 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListEditScreen(kind: ListKind, lists: ListRepository, appScope: CoroutineScope, onBack: () -> Unit) {
    val entries by remember(kind) { lists.observe(kind) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val deletedText = stringResource(R.string.list_deleted)
    val inUseText = stringResource(R.string.list_in_use)
    val builtInText = stringResource(R.string.list_builtin)
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ListEntryRow?>(null) }

    fun move(index: Int, delta: Int) {
        val ids = entries.map { it.id }.toMutableList()
        val target = index + delta
        if (target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        appScope.launch { lists.reorder(kind, ids) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.action_add)) } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            itemsIndexed(entries, key = { _, e -> e.id }) { index, entry ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = {
                        Text(entry.displayName(kind), color = if (entry.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    },
                    supportingContent = if (entry.hidden) ({ Text(stringResource(R.string.list_hidden)) }) else null,
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; renaming = entry })
                                if (entry.builtinKey != null && entry.customName != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_reset_name)) },
                                        onClick = { menu = false; appScope.launch { lists.resetName(kind, entry.id) } },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (entry.hidden) R.string.action_show else R.string.action_hide)) },
                                    onClick = { menu = false; appScope.launch { lists.setHidden(kind, entry.id, !entry.hidden) } },
                                )
                                if (index > 0) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; move(index, -1) })
                                if (index < entries.lastIndex) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; move(index, 1) })
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        menu = false
                                        scope.launch {
                                            when (val result = lists.delete(kind, entry.id)) {
                                                is DeleteResult.Deleted -> messages.showUndo(deletedText) { lists.undo(result.token) }
                                                DeleteResult.BuiltIn -> messages.show(builtInText)
                                                DeleteResult.InUse -> messages.show(inUseText)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (adding) {
        NameDialog(
            title = stringResource(R.string.action_add), label = stringResource(R.string.list_entry_name),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { name -> appScope.launch { lists.add(kind, name) }; adding = false },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { entry ->
        val shownName = entry.displayName(kind)
        NameDialog(
            title = stringResource(R.string.action_rename), label = stringResource(R.string.list_entry_name),
            confirmLabel = stringResource(R.string.action_save), initial = shownName,
            onConfirm = { name ->
                // Saving the shown name unchanged must not freeze a built-in translation (SPEC 1).
                if (name != shownName) appScope.launch { lists.rename(kind, entry.id, name) }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}
