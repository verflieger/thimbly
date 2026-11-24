package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.common.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Library sections (SPEC 2). */
enum class LibraryTab { PATTERNS, MACHINES, PEOPLE }

/** A library row: title and an optional second line. */
data class LibraryItem(val id: String, val title: String, val subtitle: String)

class LibraryViewModel(private val container: AppContainer, initialTab: LibraryTab) : ViewModel() {
    val tab = MutableStateFlow(initialTab)

    val items = combine(
        container.patterns.observeAll(), container.machines.observeAll(), container.machines.observeSetups(), container.persons.observeAll(),
    ) { patterns, machines, setups, persons ->
        val setupCount = setups.groupingBy { it.machineId }.eachCount()
        mapOf(
            LibraryTab.PATTERNS to patterns.map { LibraryItem(it.id, it.name, it.designer) },
            LibraryTab.MACHINES to machines.map { LibraryItem(it.id, it.name, (setupCount[it.id] ?: 0).toString()) },
            LibraryTab.PEOPLE to persons.map { LibraryItem(it.id, it.name, "") },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun create(tab: LibraryTab): String = container.runInApp {
        when (tab) {
            LibraryTab.PATTERNS -> container.patterns.create()
            LibraryTab.MACHINES -> container.machines.create()
            LibraryTab.PEOPLE -> container.persons.create()
        }
    }
}

/** Patterns, machines (with their settings) and people (SPEC 2, 4, 5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onBack: () -> Unit, onOpen: (LibraryTab, String, Boolean) -> Unit) {
    val tab by viewModel.tab.collectAsState()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    LibraryTab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { viewModel.tab.value = t }, text = { Text(stringResource(t.title)) },
                            modifier = Modifier.testTag("tab-$t"))
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { scope.launch { val id = viewModel.create(tab); withContext(Dispatchers.Main) { onOpen(tab, id, true) } } },
                modifier = Modifier.testTag("add"),
            ) { Icon(Icons.Outlined.Add, stringResource(tab.addLabel)) }
        },
    ) { padding ->
        val list = items?.get(tab) ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(tab.empty), padding)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(list, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title.ifBlank { stringResource(R.string.untitled) }) },
                    supportingContent = when {
                        tab == LibraryTab.MACHINES -> ({ val n = item.subtitle.toInt(); Text(pluralStringResource(R.plurals.machine_setup_count, n, n)) })
                        item.subtitle.isNotBlank() -> ({ Text(item.subtitle) })
                        else -> null
                    },
                    modifier = Modifier.clickable { onOpen(tab, item.id, false) },
                )
            }
        }
    }
}

private val LibraryTab.title: Int
    get() = when (this) {
        LibraryTab.PATTERNS -> R.string.library_patterns
        LibraryTab.MACHINES -> R.string.library_machines
        LibraryTab.PEOPLE -> R.string.library_people
    }

private val LibraryTab.addLabel: Int
    get() = when (this) {
        LibraryTab.PATTERNS -> R.string.pattern_new
        LibraryTab.MACHINES -> R.string.machine_new
        LibraryTab.PEOPLE -> R.string.person_new
    }

private val LibraryTab.empty: Int
    get() = when (this) {
        LibraryTab.PATTERNS -> R.string.patterns_empty
        LibraryTab.MACHINES -> R.string.machines_empty
        LibraryTab.PEOPLE -> R.string.people_empty
    }
