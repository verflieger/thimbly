package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FabPosition
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.TextDialog
import at.mtr.sewfar.ui.common.formatDuration
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.common.rememberNow
import at.mtr.sewfar.ui.photos.PhotoImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Above this font scale the two home buttons stack: on a 411 dp wide phone "Neues Projekt" fits side by side at
 * Android's "Large" (1.15) but not at 1.3 (AccessibilityTest).
 * shortcut: ignores the screen width, so on a 360 dp phone at "Large" the German label is cut off; measure the labels
 * against the width (TextMeasurer) if that shows up.
 */
private const val StackFontScale = 1.2f

/** Home screen (SPEC 2, 3.1): projects grouped by status, search, quick timer, "New project". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectListViewModel,
    /** Off: no timer button or time on the cards, except to stop a running timer (SPEC 9). */
    trackTime: Boolean,
    onOpenProject: (id: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val anyRunning = state.sections.values.any { list -> list.any { it.runningSince != null } }
    val now = rememberNow(anyRunning)
    val stacked = LocalDensity.current.fontScale > StackFontScale

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            query, { viewModel.query.value = it },
                            placeholder = { Text(stringResource(R.string.action_search)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("search"),
                        )
                    } else {
                        Text(stringResource(R.string.app_name))
                    }
                },
                navigationIcon = {
                    if (searching) {
                        IconButton(onClick = { searching = false; viewModel.query.value = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search)) }
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings")) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            // Two equal buttons across the full width (FabPosition.Center, so the row starts at the left edge); at a
            // large font size they stack, so "Neues Projekt" isn't cut off (SPEC 10).
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = if (stacked) 1 else 2,
            ) {
                ExtendedFloatingActionButton(onClick = { naming = true }, modifier = Modifier.weight(1f).testTag("newProject")) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.project_new))
                }
                ExtendedFloatingActionButton(
                    onClick = onOpenLibrary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f).testTag("library"),
                ) {
                    Icon(Icons.Outlined.LocalLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library_title))
                }
            }
        },
    ) { padding ->
        if (state.loaded && state.isEmpty) {
            EmptyState(stringResource(R.string.projects_empty), padding)
            return@Scaffold
        }
        // Room for the buttons below the last card: one row of 56 dp buttons, or two when stacked.
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = if (stacked) 168.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionOrder.forEach { status ->
                val projects = state.sections[status].orEmpty()
                if (projects.isEmpty()) return@forEach
                val collapsed = status in state.collapsed
                item(key = "header-$status") {
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.setCollapsed(status, !collapsed) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${stringResource(status.label)} (${projects.size})",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null)
                    }
                }
                if (!collapsed) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project, now, trackTime,
                            onOpen = { onOpenProject(project.id) },
                            onTimer = { if (project.runningSince != null) viewModel.stopTimer(project.id) else viewModel.startTimer(project.id) },
                        )
                    }
                }
            }
        }
    }
    if (naming) {
        NewProjectDialog(
            onCreate = { name ->
                naming = false
                scope.launch { val id = viewModel.create(name); withContext(Dispatchers.Main) { onOpenProject(id) } }
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun NewProjectDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    TextDialog(stringResource(R.string.project_new), stringResource(R.string.project_name), onSave = onCreate, onDismiss = onDismiss)
}

@Composable
private fun ProjectCard(project: ProjectSummary, now: Long, trackTime: Boolean, onOpen: () -> Unit, onTimer: () -> Unit) {
    val px = with(LocalDensity.current) { 112.dp.roundToPx() }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (project.coverPhotoId != null) PhotoImage(project.coverPhotoId, minEdgePx = px, modifier = Modifier.size(56.dp))
                else Icon(Icons.Outlined.Checkroom, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    project.name.ifBlank { stringResource(R.string.project_untitled) },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(project.patternNames.firstOrNull(), project.personName).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val total = project.timeMillis + (project.runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
                if ((trackTime && total > 0) || project.runningSince != null) {
                    Text(
                        formatDuration(total), style = MaterialTheme.typography.bodyMedium,
                        color = if (project.runningSince != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if ((trackTime && project.status != at.mtr.sewfar.domain.ProjectStatus.DONE) || project.runningSince != null) {
                IconButton(onClick = onTimer, modifier = Modifier.testTag("timer-${project.id}")) {
                    if (project.runningSince != null) Icon(Icons.Outlined.Stop, stringResource(R.string.timer_stop), tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.PlayArrow, stringResource(R.string.timer_start))
                }
            }
        }
    }
}
