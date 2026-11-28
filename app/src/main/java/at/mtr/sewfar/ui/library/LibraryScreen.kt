package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.Box
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.common.GroupHeader
import at.mtr.sewfar.domain.GarmentType
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.data.patternCovers
import at.mtr.sewfar.ui.photos.CoverThumbnail
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.TextDialog
import at.mtr.sewfar.ui.common.currencyOf
import at.mtr.sewfar.domain.PurchaseCategory
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Library sections (SPEC 2). */
/** In the order of the tabs. */
enum class LibraryTab { PATTERNS, PEOPLE, MACHINES, PURCHASES }

/** A library row: title, an optional second line and, for patterns, the cover photo (SPEC 3.5) and type (SPEC 4.1). */
data class LibraryItem(
    val id: String, val title: String, val subtitle: String, val coverPhotoId: String? = null, val garmentType: GarmentType? = null,
)

class LibraryViewModel(private val container: AppContainer, initialTab: LibraryTab) : ViewModel() {
    val tab = MutableStateFlow(initialTab)

    val items = combine(
        container.patterns.observeAll(), container.machines.observeAll(), container.machines.observeSetups(), container.persons.observeAll(),
        container.photos.observeAllOfType(PhotoOwner.PATTERN),
    ) { patterns, machines, setups, persons, photos ->
        val setupCount = setups.groupingBy { it.machineId }.eachCount()
        val covers = patternCovers(patterns, photos)
        mapOf(
            LibraryTab.PATTERNS to patterns.map { LibraryItem(it.id, it.name, it.designer, covers[it.id], it.garmentType) },
            LibraryTab.MACHINES to machines.map { LibraryItem(it.id, it.name, (setupCount[it.id] ?: 0).toString()) },
            LibraryTab.PEOPLE to persons.map { LibraryItem(it.id, it.name, "") },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Purchases (SPEC 4.3), newest first, and the chosen currency code (null = the phone's). */
    val purchases = container.purchases.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val currency = container.settings.currency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = container.time.today()

    fun createPurchase(name: String, category: PurchaseCategory, priceCents: Long, boughtOn: LocalDate) {
        container.appScope.launch {
            // Store the default on first use, so a later region change doesn't relabel the amounts (SPEC 4.3).
            if (container.settings.currency.first() == null) container.settings.setCurrency(currencyOf(null).currencyCode)
            container.purchases.create(name, category, priceCents, boughtOn)
        }
    }

    /** A machine always gets its type picked (SPEC 5.1), so it has no default here. */
    suspend fun createMachine(name: String, type: MachineType): String = container.runInApp { container.machines.create(name, type) }

    suspend fun create(tab: LibraryTab, name: String): String = container.runInApp {
        when (tab) {
            LibraryTab.PATTERNS -> container.patterns.create(name)
            LibraryTab.MACHINES -> error("Machines are created with their type (createMachine)")
            LibraryTab.PEOPLE -> container.persons.create(name)
            LibraryTab.PURCHASES -> error("Purchases are created in their dialog")
        }
    }
}

/** Patterns, machines (with their settings) and people (SPEC 2, 4, 5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onBack: () -> Unit, onOpen: (LibraryTab, String) -> Unit) {
    val tab by viewModel.tab.collectAsState()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var naming by rememberSaveable { mutableStateOf(false) }
    var creatingMachine by rememberSaveable { mutableStateOf(false) }
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currency.collectAsStateWithLifecycle()
    val currency = currencyOf(currencyCode)
    var creatingPurchase by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    },
                )
                // Scrollable: four fixed tabs cut "Schnittmuster" in two on a phone.
                PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                    LibraryTab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { viewModel.tab.value = t }, text = { Text(stringResource(t.title)) },
                            modifier = Modifier.testTag("tab-$t"))
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (tab) {
                        LibraryTab.PURCHASES -> creatingPurchase = true
                        LibraryTab.MACHINES -> creatingMachine = true
                        else -> naming = true
                    }
                },
                modifier = Modifier.testTag("add"),
            ) { Icon(Icons.Outlined.Add, stringResource(tab.addLabel)) }
        },
    ) { padding ->
        if (tab == LibraryTab.PURCHASES) {
            purchases?.let { PurchaseList(it, currency, padding, onOpen = { p -> onOpen(LibraryTab.PURCHASES, p.id) }) }
            return@Scaffold
        }
        val list = items?.get(tab) ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(tab.empty), padding)
            return@Scaffold
        }
        // Patterns are grouped by type like the home list by status, once any has a type (SPEC 4.1); untyped ones last.
        val groups = if (tab == LibraryTab.PATTERNS && list.any { it.garmentType != null }) {
            list.groupBy { it.garmentType }.entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }
                .map { (type, rows) -> (type?.label ?: R.string.no_type) to rows }
        } else listOf(null to list)
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            groups.forEach { (label, rows) ->
                if (label != null) item(key = "header-$label") { Box(Modifier.padding(horizontal = 16.dp)) { GroupHeader(stringResource(label), rows.size) } }
                items(rows, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title.ifBlank { stringResource(R.string.untitled) }) },
                        supportingContent = when {
                            tab == LibraryTab.MACHINES -> ({ val n = item.subtitle.toInt(); Text(pluralStringResource(R.plurals.machine_setup_count, n, n)) })
                            item.subtitle.isNotBlank() -> ({ Text(item.subtitle) })
                            else -> null
                        },
                        leadingContent = if (tab == LibraryTab.PATTERNS) ({ CoverThumbnail(item.coverPhotoId, Icons.Outlined.ContentCut, 48.dp) }) else null,
                        modifier = Modifier.clickable { onOpen(tab, item.id) },
                    )
                }
            }
        }
    }
    if (naming) {
        TextDialog(
            stringResource(tab.addLabel), stringResource(tab.nameLabel),
            onSave = { name ->
                naming = false
                val t = tab
                scope.launch { val id = viewModel.create(t, name); withContext(Dispatchers.Main) { onOpen(t, id) } }
            },
            onDismiss = { naming = false },
        )
    }
    if (creatingPurchase) {
        // A purchase is complete after its dialog, so the list stays (SPEC 2): quick for several receipt lines.
        NewPurchaseDialog(
            currency = currency, today = viewModel.today(),
            onSave = { name, category, cents, date -> creatingPurchase = false; viewModel.createPurchase(name, category, cents, date) },
            onDismiss = { creatingPurchase = false },
        )
    }
    if (creatingMachine) {
        NewMachineDialog(
            onSave = { name, type ->
                creatingMachine = false
                scope.launch { val id = viewModel.createMachine(name, type); withContext(Dispatchers.Main) { onOpen(LibraryTab.MACHINES, id) } }
            },
            onDismiss = { creatingMachine = false },
        )
    }
}

private val LibraryTab.nameLabel: Int
    get() = when (this) {
        LibraryTab.PATTERNS -> R.string.pattern_name
        LibraryTab.MACHINES -> R.string.machine_name
        LibraryTab.PEOPLE -> R.string.person_name
        LibraryTab.PURCHASES -> R.string.purchase_name
    }

private val LibraryTab.title: Int
    get() = when (this) {
        LibraryTab.PATTERNS -> R.string.library_patterns
        LibraryTab.MACHINES -> R.string.library_machines
        LibraryTab.PEOPLE -> R.string.library_people
        LibraryTab.PURCHASES -> R.string.library_purchases
    }

private val LibraryTab.addLabel: Int
    get() = when (this) {
        LibraryTab.PATTERNS -> R.string.pattern_new
        LibraryTab.MACHINES -> R.string.machine_new
        LibraryTab.PEOPLE -> R.string.person_new
        LibraryTab.PURCHASES -> R.string.purchase_new
    }

private val LibraryTab.empty: Int
    get() = when (this) {
        LibraryTab.PATTERNS -> R.string.patterns_empty
        LibraryTab.MACHINES -> R.string.machines_empty
        LibraryTab.PEOPLE -> R.string.people_empty
        LibraryTab.PURCHASES -> R.string.purchases_empty
    }
