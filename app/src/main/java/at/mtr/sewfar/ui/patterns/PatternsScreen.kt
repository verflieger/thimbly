package at.mtr.sewfar.ui.patterns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.domain.PatternFormat
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.NameDialog
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.common.namesById
import at.mtr.sewfar.ui.photos.PhotoImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pattern library (SPEC 4.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternsScreen(viewModel: PatternsViewModel, onOpenSettings: () -> Unit, onOpenPattern: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val categoryNames = state.categories.namesById(ListKind.PATTERN_CATEGORY)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_patterns)) },
                actions = {
                    IconToggleButton(checked = state.showArchived, onCheckedChange = viewModel::setShowArchived) {
                        Icon(Icons.Outlined.Inventory2, stringResource(R.string.show_archived))
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.action_settings)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.pattern_new)) }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.empty && !state.showArchived -> EmptyState(stringResource(R.string.patterns_empty), padding)
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    state.query, viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.patterns_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    item {
                        Filter(stringResource(R.string.pattern_category), state.categories.filter { !it.hidden }.map { it.id to categoryNames.getValue(it.id) },
                            state.categoryId, viewModel::setCategory)
                    }
                    item {
                        Filter(stringResource(R.string.pattern_format), PatternFormat.entries.map { it to stringResource(it.label) },
                            state.format, viewModel::setFormat)
                    }
                }
                if (state.rows.isEmpty()) {
                    EmptyState(stringResource(R.string.patterns_no_match), PaddingValues())
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(state.rows, key = { it.pattern.id }) { row ->
                            PatternListItem(row, categoryNames[row.pattern.categoryId], onClick = { onOpenPattern(row.pattern.id) })
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.pattern_new), label = stringResource(R.string.pattern_name),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                creating = false // close at once: a second tap can't create twice
                scope.launch {
                    val id = viewModel.create(name)
                    withContext(Dispatchers.Main) { onOpenPattern(id) }
                }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PatternListItem(row: PatternRow, category: String?, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { 112.dp.roundToPx() }
    val p = row.pattern
    ListItem(
        headlineContent = { Text(p.name) },
        supportingContent = {
            val line = listOfNotNull(p.designer.ifBlank { null }, category, p.sizeRange.ifBlank { null }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line)
        },
        overlineContent = if (p.archivedAt != null) ({ Text(stringResource(R.string.archived_label)) }) else null,
        leadingContent = {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                if (row.photoId != null) PhotoImage(row.photoId, minEdgePx = px, modifier = Modifier.size(56.dp))
                else Icon(Icons.Outlined.ContentCut, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
internal fun <T> Filter(label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
    Box {
        FilterChip(
            selected = selected != null, onClick = { open = true },
            label = { Text(selectedLabel ?: label) },
            trailingIcon = {
                if (selected != null) Icon(Icons.Outlined.Close, stringResource(R.string.filter_clear), Modifier.size(18.dp).clickable { onSelect(null) })
                else Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
            },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, onClick = { onSelect(null); open = false })
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false }) }
        }
    }
}
