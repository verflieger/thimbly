package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.ui.common.DetailScaffold
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.TextDialog
import at.mtr.sewfar.ui.common.PickerItem
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.common.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which picker is open; one dialog at a time. */
private sealed interface Picking {
    data object Pattern : Picking
    data object Person : Picking
    data object Setup : Picking
    /** A new setup named [title] needs a machine. */
    data class SetupMachine(val title: String) : Picking
    data object Rename : Picking
    data object Notes : Picking
    data object Learned : Picking
}

/** One project on one screen, edited in place (SPEC 3). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenPattern: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenSetup: (String) -> Unit,
    /** A setting just created here: opens with its form for the values. */
    onOpenNewSetup: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.project_deleted)
    val removedText = stringResource(R.string.removed)
    var picking by remember { mutableStateOf<Picking?>(null) }

    // Deleted elsewhere: nothing to show.
    LaunchedEffect(state.loaded, state.project) { if (state.loaded && state.project == null) onBack() }
    val project = state.project ?: return

    fun removeWithUndo(remove: suspend () -> DeletionToken?) {
        scope.launch { remove()?.let { token -> messages.showUndo(removedText) { viewModel.undo(token) } } }
    }

    DetailScaffold(
        title = project.name.ifBlank { stringResource(R.string.project_untitled) },
        onBack = onBack,
        onEdit = { picking = Picking.Rename },
        onDelete = {
            scope.launch {
                val token = viewModel.delete()
                withContext(Dispatchers.Main) { onBack() }
                if (token != null) messages.showUndo(deletedText) { viewModel.undo(token) }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ProjectStatus.entries.forEachIndexed { i, status ->
                    SegmentedButton(
                        selected = project.status == status,
                        onClick = { viewModel.setStatus(status) },
                        shape = SegmentedButtonDefaults.itemShape(i, ProjectStatus.entries.size),
                        modifier = Modifier.testTag("status-$status"),
                    ) { Text(stringResource(status.label), maxLines = 1) }
                }
            }

            TimeSection(state, viewModel, onRemove = { id -> removeWithUndo { viewModel.deleteTime(id) } })

            ProjectPhotos(state, viewModel, onOpenPhoto)

            Links(stringResource(R.string.project_patterns), stringResource(R.string.project_add_pattern), "addPattern",
                state.patterns.map { Triple(it.linkId, it.name, it.patternId) },
                onOpen = onOpenPattern, onRemove = { link -> removeWithUndo { viewModel.removePattern(link) } },
                onAdd = { picking = Picking.Pattern })

            Links(stringResource(R.string.project_person), stringResource(R.string.project_add_person), "addPerson",
                listOfNotNull(state.person?.let { Triple(it.id, it.name, it.id) }),
                onOpen = onOpenPerson,
                onRemove = { personId ->
                    viewModel.setPerson(null)
                    messages.showUndo(removedText) { viewModel.setPerson(personId) }
                },
                onAdd = { picking = Picking.Person }, canAdd = state.person == null)

            Links(stringResource(R.string.project_setups), stringResource(R.string.project_add_setup), "addSetup",
                state.setups.map { Triple(it.linkId, setupLabel(it.title, it.machineName), it.setupId) },
                onOpen = onOpenSetup, onRemove = { link -> removeWithUndo { viewModel.removeSetup(link) } },
                onAdd = { picking = Picking.Setup })

            TextBlock(stringResource(R.string.project_notes), project.notes, stringResource(R.string.notes_add), "notes") { picking = Picking.Notes }

            if (project.status == ProjectStatus.DONE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.project_rating))
                    Row {
                        (1..5).forEach { stars ->
                            IconButton(onClick = { viewModel.setRating(if (project.rating == stars) null else stars) }, modifier = Modifier.testTag("star-$stars")) {
                                Icon(
                                    if ((project.rating ?: 0) >= stars) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                    stringResource(R.string.project_stars, stars),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    TextBlock(stringResource(R.string.project_learned), project.learned, stringResource(R.string.learned_add), "learned") {
                        picking = Picking.Learned
                    }
                }
            }

            val dates = listOfNotNull(
                project.startedOn?.let { stringResource(R.string.project_started_on, it.format()) },
                project.finishedOn?.let { stringResource(R.string.project_finished_on, it.format()) },
            )
            if (dates.isNotEmpty()) {
                Text(dates.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    when (val p = picking) {
        Picking.Pattern -> {
            val linked = state.patterns.map { it.patternId }.toSet()
            EntityPicker(
                title = stringResource(R.string.picker_choose_pattern),
                items = choices.patterns.filter { it.id !in linked }.map { PickerItem(it.id, it.name, it.designer) },
                // Typing a linked pattern's name must not offer a duplicate.
                existingNames = choices.patterns.map { it.name },
                onPick = { id -> id?.let(viewModel::addPattern); picking = null },
                onCreate = { name -> viewModel.createPattern(name); picking = null },
                onDismiss = { picking = null },
            )
        }
        Picking.Person -> EntityPicker(
            title = stringResource(R.string.picker_choose_person),
            items = choices.persons.map { PickerItem(it.id, it.name) },
            onPick = { id -> viewModel.setPerson(id); picking = null },
            onCreate = { name -> viewModel.createPerson(name); picking = null },
            onDismiss = { picking = null },
        )
        Picking.Setup -> {
            val linked = state.setups.map { it.setupId }.toSet()
            EntityPicker(
                title = stringResource(R.string.picker_choose_setup),
                items = choices.setups.filter { it.id !in linked }.map { PickerItem(it.id, setupLabel(it.title, ""), it.machineName) },
                onPick = { id -> id?.let(viewModel::addSetup); picking = null },
                onCreate = { title ->
                    val only = choices.machines.singleOrNull()
                    if (only != null) {
                        picking = null
                        scope.launch {
                            val id = viewModel.createSetup(title, only.id, null)
                            withContext(Dispatchers.Main) { onOpenNewSetup(id) }
                        }
                    } else {
                        picking = Picking.SetupMachine(title)
                    }
                },
                onDismiss = { picking = null },
                // The same name may exist on another machine, so "New setting" is always offered.
                existingNames = emptyList(),
                createLabel = R.string.picker_create_setup,
            )
        }
        is Picking.SetupMachine -> EntityPicker(
            title = stringResource(R.string.picker_choose_machine),
            items = choices.machines.map { PickerItem(it.id, it.name) },
            onPick = { machineId ->
                picking = null
                if (machineId != null) scope.launch {
                    val id = viewModel.createSetup(p.title, machineId, null)
                    withContext(Dispatchers.Main) { onOpenNewSetup(id) }
                }
            },
            onCreate = { machineName ->
                picking = null
                scope.launch {
                    val id = viewModel.createSetup(p.title, null, machineName)
                    withContext(Dispatchers.Main) { onOpenNewSetup(id) }
                }
            },
            onDismiss = { picking = null },
        )
        Picking.Rename -> TextDialog(
            stringResource(R.string.project_rename), stringResource(R.string.project_name), initial = project.name,
            onSave = { viewModel.rename(it); picking = null }, onDismiss = { picking = null },
        )
        Picking.Notes -> TextDialog(
            stringResource(R.string.project_notes), stringResource(R.string.project_notes), initial = project.notes,
            singleLine = false, required = false, onSave = { viewModel.setNotes(it); picking = null }, onDismiss = { picking = null },
        )
        Picking.Learned -> TextDialog(
            stringResource(R.string.project_learned), stringResource(R.string.project_learned), initial = project.learned,
            singleLine = false, required = false, onSave = { viewModel.setLearned(it); picking = null }, onDismiss = { picking = null },
        )
        null -> Unit
    }
}

/** A text of the project shown read-only with an edit button; [addLabel] while it is empty. */
@Composable
private fun TextBlock(title: String, text: String, addLabel: String, tag: String, onEdit: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title, Modifier.weight(1f))
            if (text.isNotBlank()) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit-$tag")) {
                    Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit), Modifier.size(20.dp))
                }
            }
        }
        if (text.isNotBlank()) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit))
        } else {
            AssistChip(
                onClick = onEdit,
                label = { Text(addLabel) },
                leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("edit-$tag"),
            )
        }
    }
}

@Composable
internal fun setupLabel(title: String, machineName: String): String {
    val shown = title.ifBlank { stringResource(R.string.setup_untitled) }
    return if (machineName.isBlank()) shown else "$shown · $machineName"
}

/**
 * A labelled row of linked items: tap opens one, × removes it, the add chip links another.
 * [items] are (key for removal, label, ID to open).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(
    title: String,
    addLabel: String,
    addTag: String,
    items: List<Triple<String, String, String>>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (key, label, openId) ->
                InputChip(
                    selected = false,
                    onClick = { onOpen(openId) },
                    label = { Text(label.ifBlank { "…" }) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close, stringResource(R.string.action_remove),
                            Modifier.size(18.dp).clickable { onRemove(key) }.testTag("remove-$key"),
                        )
                    },
                )
            }
            if (canAdd) {
                AssistChip(
                    onClick = onAdd,
                    label = { Text(addLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag(addTag),
                )
            }
        }
    }
}
