package at.mtr.sewfar.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.common.Avatar
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.NameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** People list (SPEC 5.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(viewModel: PeopleViewModel, onOpenSettings: () -> Unit, onOpenPerson: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_people)) },
                actions = {
                    IconToggleButton(checked = state.showArchived, onCheckedChange = viewModel::setShowArchived) {
                        Icon(Icons.Outlined.Inventory2, stringResource(R.string.show_archived))
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.action_settings)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.person_new)) }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.persons.isEmpty() -> EmptyState(stringResource(R.string.people_empty), padding)
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.persons, key = { it.id }) { person ->
                    ListItem(
                        headlineContent = { Text(person.name) },
                        overlineContent = if (person.archivedAt != null) ({ Text(stringResource(R.string.archived_label)) }) else null,
                        leadingContent = { Avatar(person.name, person.avatarColor, size = 40.dp, photoId = state.photoByPerson[person.id]) },
                        modifier = Modifier.clickable { onOpenPerson(person.id) },
                    )
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.person_new), label = stringResource(R.string.person_name),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                creating = false // close at once: a second tap can't create twice
                scope.launch {
                    val id = viewModel.create(name)
                    withContext(Dispatchers.Main) { onOpenPerson(id) }
                }
            },
            onDismiss = { creating = false },
        )
    }
}
