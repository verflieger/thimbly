package at.mtr.sewfar.ui.machines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.domain.SetupResult
import at.mtr.sewfar.ui.common.AutoSaveTextField
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.PickerItem
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.common.namesById
import at.mtr.sewfar.ui.projects.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Setup detail (SPEC 6.3): purpose, fabric, a value per visible field, result, notes, projects that used it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupDetailScreen(viewModel: SetupDetailViewModel, onBack: () -> Unit, onOpenMachine: (String) -> Unit, onOpenProject: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.setup_deleted)
    var pickingPurpose by remember { mutableStateOf(false) }

    LaunchedEffect(state.loaded, state.setup) { if (state.loaded && state.setup == null) onBack() }
    val setup = state.setup ?: return
    val purposeNames = state.purposes.namesById(ListKind.PURPOSE)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(at.mtr.sewfar.domain.SetupTitles.shown(setup.title, state.machine?.name)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val token = viewModel.delete()
                            withContext(Dispatchers.Main) { onBack() }
                            if (token != null) messages.showUndo(deletedText) { viewModel.undo(token) }
                        }
                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.setup_delete)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Key includes the stored title so an automatic title (from purpose/fabric) shows up in the field.
            AutoSaveTextField(setup.title, viewModel::setTitle, stringResource(R.string.setup_title), "stitle-${setup.id}-${setup.title}",
                modifier = Modifier.fillMaxWidth())
            state.machine?.let { machine ->
                ListItem(
                    headlineContent = { Text(if (machine.archivedAt != null) stringResource(R.string.project_archived_suffix, machine.name) else machine.name) },
                    overlineContent = { Text(stringResource(R.string.setup_machine)) },
                    supportingContent = { Text(stringResource(machine.type.label)) },
                    modifier = Modifier.clickable { onOpenMachine(machine.id) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.setup_purpose))
                AssistChip(
                    onClick = { pickingPurpose = true },
                    label = { Text(setup.purposeId?.let(purposeNames::get) ?: stringResource(R.string.setup_no_purpose)) },
                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
                )
            }
            AutoSaveTextField(setup.fabric, { viewModel.setFabric(it, purposeNames) }, stringResource(R.string.setup_fabric), "sfabric-${setup.id}", modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.setup_values))
                // Visible fields, plus hidden ones that already have a value here (SPEC 1).
                state.fields.filter { !it.hidden || state.values[it.id].orEmpty().isNotBlank() }.forEach { field ->
                    AutoSaveTextField(
                        state.values[field.id].orEmpty(), { viewModel.setValue(field.id, it) }, field.displayName(), "value-${setup.id}-${field.id}",
                        modifier = Modifier.fillMaxWidth().testTag("setupValue-${field.builtinKey ?: field.id}"),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.setup_result))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetupResult.entries.forEach { result ->
                        FilterChip(
                            selected = setup.result == result,
                            onClick = { viewModel.setResult(if (setup.result == result) null else result) },
                            label = { Text(stringResource(result.label)) },
                        )
                    }
                }
            }
            AutoSaveTextField(setup.notes, viewModel::setNotes, stringResource(R.string.setup_notes), "snotes-${setup.id}",
                singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Column {
                SectionLabel(stringResource(R.string.setup_used_in))
                if (state.projects.isEmpty()) Text(stringResource(R.string.setup_not_used), style = MaterialTheme.typography.bodyMedium)
                state.projects.forEach { project ->
                    ListItem(
                        headlineContent = { Text(project.name) },
                        supportingContent = { Text(stringResource(project.status.label)) },
                        modifier = Modifier.clickable { onOpenProject(project.id) },
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (pickingPurpose) {
        EntityPicker(
            title = stringResource(R.string.setup_purpose),
            items = state.purposes.filter { !it.hidden }.map { PickerItem(it.id, purposeNames.getValue(it.id)) },
            hiddenNames = state.purposes.filter { it.hidden }.map { purposeNames.getValue(it.id) },
            noneLabel = stringResource(R.string.setup_no_purpose),
            onPick = { viewModel.setPurpose(it, purposeNames); pickingPurpose = false },
            onCreate = { name -> scope.launch { val id = viewModel.createPurpose(name); viewModel.setPurpose(id, purposeNames + (id to name)); pickingPurpose = false } },
            onDismiss = { pickingPurpose = false },
        )
    }
}
