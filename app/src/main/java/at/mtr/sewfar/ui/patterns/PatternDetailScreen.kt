package at.mtr.sewfar.ui.patterns

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.domain.Amounts
import at.mtr.sewfar.domain.PatternFormat
import at.mtr.sewfar.ui.common.AutoSaveTextField
import at.mtr.sewfar.ui.common.Avatar
import at.mtr.sewfar.ui.common.EntityPhoto
import at.mtr.sewfar.ui.common.EntityPicker
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.PickerItem
import at.mtr.sewfar.ui.common.currentLocale
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.common.namesById
import at.mtr.sewfar.ui.projects.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pattern detail, edited in place (SPEC 4.2). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatternDetailScreen(
    viewModel: PatternDetailViewModel,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val deletedText = stringResource(R.string.pattern_deleted)
    val inUseText = stringResource(R.string.pattern_in_use)
    val photoDeletedText = stringResource(R.string.photo_deleted)
    var menuOpen by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }

    LaunchedEffect(state.loaded, state.pattern) { if (state.loaded && state.pattern == null) onBack() }
    val pattern = state.pattern ?: return
    val categoryNames = state.categories.namesById(ListKind.PATTERN_CATEGORY)
    val tagNames = state.lessonTags.namesById(ListKind.LESSON_TAG)
    val archived = pattern.archivedAt != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pattern.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pattern_start_project)) },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { viewModel.startProject()?.let { id -> withContext(Dispatchers.Main) { onOpenProject(id) } } }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
                            leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                            onClick = { menuOpen = false; viewModel.setArchived(!archived) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pattern_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    when (val result = viewModel.delete()) {
                                        is DeleteResult.Deleted -> {
                                            withContext(Dispatchers.Main) { onBack() }
                                            messages.showUndo(deletedText) { viewModel.undo(result.token) }
                                        }
                                        else -> messages.show(inUseText)
                                    }
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (archived) Text(stringResource(R.string.archived_label), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntityPhoto(state.photoId, onSet = viewModel::setPhoto, onOpen = onOpenPhoto, onRemove = {
                    state.photoId?.let { id -> scope.launch { viewModel.removePhoto(id)?.let { messages.showUndo(photoDeletedText) { viewModel.undo(it) } } } }
                })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSaveTextField(pattern.name, viewModel::rename, stringResource(R.string.pattern_name), "pname-${pattern.id}",
                        isValid = { it.isNotBlank() }, modifier = Modifier.fillMaxWidth())
                    AutoSaveTextField(pattern.designer, viewModel::setDesigner, stringResource(R.string.pattern_designer), "designer-${pattern.id}",
                        modifier = Modifier.fillMaxWidth())
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.pattern_category))
                AssistChip(
                    onClick = { pickingCategory = true },
                    label = { Text(pattern.categoryId?.let(categoryNames::get) ?: stringResource(R.string.pattern_no_category)) },
                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
                )
                SectionLabel(stringResource(R.string.pattern_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatternFormat.entries.forEach { format ->
                        FilterChip(
                            selected = pattern.format == format,
                            onClick = { viewModel.setFormat(if (pattern.format == format) null else format) },
                            label = { Text(stringResource(format.label)) },
                        )
                    }
                }
            }
            AutoSaveTextField(pattern.sizeRange, viewModel::setSizeRange, stringResource(R.string.pattern_size_range), "range-${pattern.id}",
                modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                AutoSaveTextField(pattern.sourceUrl, viewModel::setSourceUrl, stringResource(R.string.pattern_source), "url-${pattern.id}",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f))
                if (pattern.sourceUrl.isNotBlank()) {
                    IconButton(onClick = {
                        val url = pattern.sourceUrl.let { if ("://" in it) it else "https://$it" }
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.pattern_open_source)) }
                }
            }
            AutoSaveTextField(pattern.suitedFabrics, viewModel::setSuitedFabrics, stringResource(R.string.pattern_suited_fabrics),
                "fabrics-${pattern.id}", singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
            Yardage(state, viewModel)
            AutoSaveTextField(pattern.notes, viewModel::setNotes, stringResource(R.string.project_notes), "pnotes-${pattern.id}",
                singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            // Read-only: projects sewn with this pattern and what was learned (SPEC 4.2).
            Column {
                SectionLabel(stringResource(R.string.pattern_projects))
                if (state.projects.isEmpty()) Text(stringResource(R.string.pattern_no_projects), style = MaterialTheme.typography.bodyMedium)
                state.projects.forEach { row ->
                    val person = row.personId?.let(state.persons::get)
                    ListItem(
                        headlineContent = { Text(row.projectName) },
                        supportingContent = {
                            Text(listOfNotNull(person?.name, row.sizeSewn.ifBlank { null }, stringResource(row.status.label)).joinToString(" · "))
                        },
                        leadingContent = person?.let { { Avatar(it.name, it.avatarColor, size = 32.dp) } },
                        modifier = Modifier.clickable { onOpenProject(row.projectId) },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.pattern_lessons))
                val notes = state.projects.filter { it.completionNotes.isNotBlank() }
                if (state.lessonCounts.isEmpty() && notes.isEmpty()) {
                    Text(stringResource(R.string.pattern_no_lessons), style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.lessonCounts.forEach { (tagId, count) ->
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.lesson_count, tagNames[tagId].orEmpty(), count)) })
                    }
                }
                notes.forEach { row ->
                    Text("${row.projectName}: ${row.completionNotes}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (pickingCategory) {
        EntityPicker(
            title = stringResource(R.string.pattern_category),
            items = state.categories.filter { !it.hidden }.map { PickerItem(it.id, categoryNames.getValue(it.id)) },
            hiddenNames = state.categories.filter { it.hidden }.map { categoryNames.getValue(it.id) },
            noneLabel = stringResource(R.string.pattern_no_category),
            onPick = { viewModel.setCategory(it); pickingCategory = false },
            onCreate = { name -> scope.launch { viewModel.setCategory(viewModel.createCategory(name)); pickingCategory = false } },
            onDismiss = { pickingCategory = false },
        )
    }
}

@Composable
private fun Yardage(state: PatternDetailState, viewModel: PatternDetailViewModel) {
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale
    val removedText = stringResource(R.string.action_remove)
    val invalidText = stringResource(R.string.project_fabric_amount_invalid)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.pattern_yardage))
        state.yardage.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                AutoSaveTextField(row.size, { text -> viewModel.editYardage(row.id) { it.copy(size = text.trim()) } }, stringResource(R.string.yardage_size),
                    "ysize-${row.id}", modifier = Modifier.weight(1f))
                AutoSaveTextField(
                    if (row.amountCm > 0) Amounts.formatMeters(row.amountCm, locale) else "",
                    { text -> viewModel.editYardage(row.id) { it.copy(amountCm = (Amounts.parseMeters(text) as? Amounts.ParseResult.Cm)?.cm ?: 0) } },
                    stringResource(R.string.yardage_amount), "yamount-${row.id}",
                    isValid = { Amounts.parseMeters(it) != Amounts.ParseResult.Invalid }, errorText = invalidText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(96.dp),
                )
                AutoSaveTextField(
                    row.fabricWidthCm?.toString().orEmpty(),
                    { text -> viewModel.editYardage(row.id) { it.copy(fabricWidthCm = text.trim().toIntOrNull()?.takeIf { w -> w > 0 }) } },
                    stringResource(R.string.yardage_width), "ywidth-${row.id}",
                    isValid = { it.isBlank() || (it.trim().toIntOrNull() ?: 0) > 0 }, errorText = invalidText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp),
                )
                IconButton(onClick = {
                    scope.launch { viewModel.deleteYardage(row.id)?.let { token -> messages.showUndo(removedText) { viewModel.undo(token) } } }
                }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove)) }
            }
            AutoSaveTextField(row.note, { text -> viewModel.editYardage(row.id) { it.copy(note = text.trim()) } },
                stringResource(R.string.yardage_note), "ynote-${row.id}", modifier = Modifier.fillMaxWidth())
        }
        AssistChip(
            onClick = viewModel::addYardage,
            label = { Text(stringResource(R.string.pattern_add_yardage)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        )
    }
}
