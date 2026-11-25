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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.ui.common.DetailScaffold
import at.mtr.sewfar.ui.common.DiscardDialog
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.LabeledTextField
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.MeasurementsView
import at.mtr.sewfar.ui.common.TextDialog
import at.mtr.sewfar.ui.photos.PhotoStrip
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
    data object Size : Picking
    data object Fabric : Picking
    data object Notes : Picking
    data object Learned : Picking
    /** Just set to Done: ask for rating and "What I learned" (SPEC 3.4). */
    data object Finish : Picking
}

/** One project on one screen, edited in place (SPEC 3). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    /** Off: no Time section, unless this project's timer is running (SPEC 9). */
    trackTime: Boolean,
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
        scope.launch { remove()?.let { token -> messages.showUndo(removedText, token) } }
    }

    DetailScaffold(
        title = project.name.ifBlank { stringResource(R.string.project_untitled) },
        onBack = onBack,
        onEdit = { picking = Picking.Rename },
        onDelete = {
            scope.launch {
                val token = viewModel.delete()
                withContext(Dispatchers.Main) { onBack() }
                if (token != null) messages.showUndo(deletedText, token)
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
                        onClick = {
                            if (status == ProjectStatus.DONE && project.status != ProjectStatus.DONE) picking = Picking.Finish
                            viewModel.setStatus(status)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, ProjectStatus.entries.size),
                        modifier = Modifier.testTag("status-$status"),
                    ) { Text(stringResource(status.label), maxLines = 1) }
                }
            }

            if (trackTime || state.running != null) TimeSection(state, viewModel, onRemove = { id -> removeWithUndo { viewModel.deleteTime(id) } })

            PhotoStrip(
                state.photos, viewModel::addPhotos, viewModel::deletePhoto, onOpenPhoto,
                coverId = state.coverPhotoId, pinnedId = project.coverPhotoId, setCover = viewModel::setCover,
            )

            // Empty sections fold into one "Add" row below the filled ones (SPEC 3.2).
            val addChips = mutableListOf<@Composable () -> Unit>()

            if (state.patterns.isNotEmpty()) {
                Links(stringResource(R.string.project_patterns), stringResource(R.string.project_add_pattern), "addPattern",
                    state.patterns.map { Triple(it.linkId, it.name, it.patternId) },
                    onOpen = onOpenPattern, onRemove = { link -> removeWithUndo { viewModel.removePattern(link) } },
                    onAdd = { picking = Picking.Pattern })
            } else addChips += { AddChip(stringResource(R.string.project_add_pattern), "addPattern") { picking = Picking.Pattern } }

            if (project.size.isNotBlank()) {
                TextBlock(stringResource(R.string.project_size), project.size, "size") { picking = Picking.Size }
            } else addChips += { AddChip(stringResource(R.string.project_size), "edit-size") { picking = Picking.Size } }
            if (project.fabric.isNotBlank()) {
                TextBlock(stringResource(R.string.project_fabric), project.fabric, "fabric") { picking = Picking.Fabric }
            } else addChips += { AddChip(stringResource(R.string.project_fabric), "edit-fabric") { picking = Picking.Fabric } }

            val person = state.person
            if (person != null) {
                Links(stringResource(R.string.project_person), "", "addPerson",
                    listOf(Triple(person.id, person.name, person.id)),
                    onOpen = onOpenPerson,
                    onRemove = { personId ->
                        viewModel.setPerson(null)
                        messages.showUndo(removedText) { viewModel.setPerson(personId) }
                    },
                    onAdd = {}, canAdd = false)
                // Read-only here; they are edited on the person's page (SPEC 3.2).
                val m = state.measurements
                if (m != null && m.valuesCm.isNotEmpty()) {
                    MeasurementsView(
                        stringResource(R.string.measurements_from, m.measuredOn.format()), m,
                        Modifier.fillMaxWidth().clickable { onOpenPerson(person.id) }.testTag("measurements"),
                    )
                }
            } else addChips += { AddChip(stringResource(R.string.project_add_person), "addPerson") { picking = Picking.Person } }

            if (state.setups.isNotEmpty()) {
                Links(stringResource(R.string.project_setups), stringResource(R.string.project_add_setup), "addSetup",
                    state.setups.map { Triple(it.linkId, setupLabel(it.title, it.machineName), it.setupId) },
                    onOpen = onOpenSetup, onRemove = { link -> removeWithUndo { viewModel.removeSetup(link) } },
                    onAdd = { picking = Picking.Setup })
            } else addChips += { AddChip(stringResource(R.string.project_add_setup), "addSetup") { picking = Picking.Setup } }

            if (project.notes.isNotBlank()) {
                TextBlock(stringResource(R.string.project_notes), project.notes, "notes") { picking = Picking.Notes }
            } else addChips += { AddChip(stringResource(R.string.project_notes), "edit-notes") { picking = Picking.Notes } }

            if (addChips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(stringResource(R.string.action_add))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        addChips.forEach { it() }
                    }
                }
            }

            if (project.status == ProjectStatus.DONE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.project_rating))
                    StarRating(project.rating, "star", viewModel::setRating)
                    TextBlock(stringResource(R.string.project_learned), project.learned, "learned", stringResource(R.string.learned_add)) {
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
        Picking.Size -> TextDialog(
            stringResource(R.string.project_size), stringResource(R.string.project_size), initial = project.size,
            required = false, onSave = { viewModel.setSize(it); picking = null }, onDismiss = { picking = null },
        )
        Picking.Fabric -> TextDialog(
            stringResource(R.string.project_fabric), stringResource(R.string.project_fabric), initial = project.fabric,
            singleLine = false, required = false, onSave = { viewModel.setFabric(it); picking = null }, onDismiss = { picking = null },
        )
        Picking.Notes -> TextDialog(
            stringResource(R.string.project_notes), stringResource(R.string.project_notes), initial = project.notes,
            singleLine = false, required = false, onSave = { viewModel.setNotes(it); picking = null }, onDismiss = { picking = null },
        )
        Picking.Learned -> TextDialog(
            stringResource(R.string.project_learned), stringResource(R.string.project_learned), initial = project.learned,
            singleLine = false, required = false, onSave = { viewModel.setLearned(it); picking = null }, onDismiss = { picking = null },
        )
        Picking.Finish -> FinishDialog(
            initialRating = project.rating, initialLearned = project.learned,
            onSave = { rating, learned -> viewModel.finish(rating, learned); picking = null },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

/** 1–5 stars; tapping the current rating again clears it (SPEC 3.2). */
@Composable
private fun StarRating(rating: Int?, tagPrefix: String, onChange: (Int?) -> Unit) {
    Row {
        (1..5).forEach { stars ->
            IconButton(onClick = { onChange(if (rating == stars) null else stars) }, modifier = Modifier.testTag("$tagPrefix-$stars")) {
                Icon(
                    if ((rating ?: 0) >= stars) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    stringResource(R.string.project_stars, stars),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Shown when a project is set to Done (SPEC 3.4): rating and "What I learned", Save or Later. Like any text dialog,
 * typed text is not dropped without asking.
 */
@Composable
private fun FinishDialog(initialRating: Int?, initialLearned: String, onSave: (Int?, String) -> Unit, onDismiss: () -> Unit) {
    var rating by rememberSaveable { mutableStateOf(initialRating) }
    var learned by rememberSaveable { mutableStateOf(initialLearned) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val later = { if (rating != initialRating || learned != initialLearned) confirmDiscard = true else onDismiss() }
    if (confirmDiscard) {
        DiscardDialog(onDiscard = onDismiss, onKeep = { confirmDiscard = false })
        return
    }
    AlertDialog(
        onDismissRequest = later,
        title = { Text(stringResource(R.string.finish_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    SectionLabel(stringResource(R.string.project_rating))
                    StarRating(rating, "finishStar") { rating = it }
                }
                LabeledTextField(
                    learned, { learned = it }, stringResource(R.string.project_learned),
                    fieldModifier = Modifier.testTag("finishLearned"), singleLine = false, minLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rating, learned.trim()) }, modifier = Modifier.testTag("finishSave")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = later, modifier = Modifier.testTag("finishLater")) { Text(stringResource(R.string.action_later)) } },
    )
}

/** A text of the project shown read-only with an edit button; [addLabel] while it is empty. */
@Composable
private fun TextBlock(title: String, text: String, tag: String, addLabel: String = "", onEdit: () -> Unit) {
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
            AddChip(addLabel, "edit-$tag", onEdit)
        }
    }
}

@Composable
private fun AddChip(label: String, tag: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        modifier = Modifier.testTag(tag),
    )
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
                    // The ×'s touch target grows to 48 dp; a wide enough label keeps a tap on a short name ("Lena") opening it.
                    label = { Text(label.ifBlank { "…" }, Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close, stringResource(R.string.action_remove),
                            Modifier.size(18.dp).clickable { onRemove(key) }.testTag("remove-$key"),
                        )
                    },
                )
            }
            if (canAdd) AddChip(addLabel, addTag, onAdd)
        }
    }
}
