package at.mtr.sewfar.ui.machines

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.domain.SetupResult
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.NameDialog
import at.mtr.sewfar.ui.common.PickerItem
import at.mtr.sewfar.ui.common.namesById
import at.mtr.sewfar.ui.patterns.Filter
import at.mtr.sewfar.ui.photos.PhotoImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the "new setup" flow is doing: one dialog at a time. */
private sealed interface Creating {
    data object PickMachine : Creating
    data class NewMachine(val name: String, val thenSetup: Boolean) : Creating
}

/** Machines tab (SPEC 6.3). The FAB creates a setup; machines are added from the machine row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinesScreen(
    viewModel: MachinesViewModel,
    onOpenSettings: () -> Unit,
    onOpenMachine: (String) -> Unit,
    onOpenSetup: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var flow: Creating? by androidx.compose.runtime.remember { mutableStateOf(null) }
    val purposeNames = state.purposes.namesById(ListKind.PURPOSE)

    fun startNewSetup() {
        flow = if (state.machines.isEmpty()) Creating.NewMachine("", thenSetup = true) else Creating.PickMachine
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_machines)) },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.action_settings)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = ::startNewSetup) { Icon(Icons.Outlined.Add, stringResource(R.string.setup_new)) }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            MachineRow(state, onOpenMachine, onAdd = { flow = Creating.NewMachine("", thenSetup = false) })
            when {
                state.machines.isEmpty() && !state.anySetups -> Text(
                    stringResource(R.string.machines_none), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
                )
                !state.anySetups -> Text(
                    stringResource(R.string.setups_empty), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
                )
                else -> {
                    OutlinedTextField(
                        state.query, viewModel::setQuery, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        placeholder = { Text(stringResource(R.string.setups_search)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    val f = state.filter
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        item { Filter(stringResource(R.string.filter_machine), state.machines.map { it.id to it.name }, f.machineId) { viewModel.setFilter(f.copy(machineId = it)) } }
                        item {
                            Filter(stringResource(R.string.setup_purpose), state.purposes.filter { !it.hidden }.map { it.id to purposeNames.getValue(it.id) }, f.purposeId) {
                                viewModel.setFilter(f.copy(purposeId = it))
                            }
                        }
                        item { Filter(stringResource(R.string.filter_result), SetupResult.entries.map { it to stringResource(it.label) }, f.result) { viewModel.setFilter(f.copy(result = it)) } }
                    }
                    if (state.setups.isEmpty()) {
                        Text(stringResource(R.string.setups_no_match), modifier = Modifier.padding(24.dp))
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(state.setups, key = { it.id }) { setup ->
                                ListItem(
                                    headlineContent = { Text(at.mtr.sewfar.domain.SetupTitles.shown(setup.title, setup.machineName)) },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(setup.machineName, setup.purposeId?.let(purposeNames::get), setup.fabric.ifBlank { null },
                                                setup.result?.let { stringResource(it.label) }).joinToString(" · "),
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = { Icon(Icons.Outlined.Tune, null) },
                                    modifier = Modifier.clickable { onOpenSetup(setup.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val step = flow) {
        Creating.PickMachine -> EntityPicker(
            title = stringResource(R.string.setup_choose_machine),
            items = state.machines.map { PickerItem(it.id, it.name, stringResource(it.type.label)) },
            onPick = { id ->
                flow = null
                if (id != null) scope.launch { val setup = viewModel.createSetup(id, ""); withContext(Dispatchers.Main) { onOpenSetup(setup) } }
            },
            onCreate = { name -> flow = Creating.NewMachine(name, thenSetup = true) },
            onDismiss = { flow = null },
        )
        is Creating.NewMachine -> NewMachineDialog(
            initialName = step.name,
            onCreate = { name, type ->
                flow = null
                scope.launch {
                    val id = viewModel.createMachine(name, type)
                    if (step.thenSetup) {
                        // New setups start without a title; it fills from purpose and fabric (SPEC 6.3).
                        val setup = viewModel.createSetup(id, "")
                        withContext(Dispatchers.Main) { onOpenSetup(setup) }
                    } else {
                        withContext(Dispatchers.Main) { onOpenMachine(id) }
                    }
                }
            },
            onDismiss = { flow = null },
        )
        null -> Unit
    }
}

@Composable
private fun MachineRow(state: MachinesState, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val px = with(LocalDensity.current) { 120.dp.roundToPx() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(state.machines, key = { it.id }) { machine ->
            Card(onClick = { onOpen(machine.id) }, modifier = Modifier.width(132.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().size(72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        val photo = state.machinePhotos[machine.id]
                        if (photo != null) PhotoImage(photo, minEdgePx = px, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text(machine.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text(stringResource(machine.type.label), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        item {
            OutlinedCard(onClick = onAdd, modifier = Modifier.width(132.dp)) {
                Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null) }
                    Text(stringResource(R.string.machine_add), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
