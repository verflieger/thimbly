package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.ProjectFilter
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectSummary
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.ui.common.Avatar
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.icon
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.photos.PhotoImage

/** Project list (SPEC 3.1): status sections or gallery, search and filters, FAB for a new project. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(viewModel: ProjectListViewModel, onOpenSettings: () -> Unit, onOpenProject: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gallery by viewModel.galleryMode.collectAsStateWithLifecycle()
    val persons by viewModel.persons.collectAsStateWithLifecycle()
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_projects)) },
                actions = {
                    IconButton(onClick = { viewModel.galleryMode.value = !gallery }) {
                        if (gallery) Icon(Icons.AutoMirrored.Outlined.ViewList, stringResource(R.string.view_list))
                        else Icon(Icons.Outlined.GridView, stringResource(R.string.view_gallery))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.project_new))
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.empty -> EmptyState(stringResource(R.string.projects_empty), padding)
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                SearchAndFilters(state, viewModel::setQuery, viewModel::setFilter)
                when {
                    gallery && state.gallery.isEmpty() -> EmptyState(stringResource(R.string.gallery_empty), PaddingValues())
                    gallery -> Gallery(state.gallery, onOpenProject)
                    state.sections.isEmpty() -> EmptyState(stringResource(R.string.projects_no_match), PaddingValues())
                    else -> Sections(state, viewModel::toggleSection, onOpenProject)
                }
            }
        }
    }

    if (creating) {
        NewProjectDialog(
            persons = persons,
            patterns = patterns,
            createPerson = viewModel::createPerson,
            createPattern = viewModel::createPattern,
            createProject = viewModel::createProject,
            onCreated = { id -> creating = false; onOpenProject(id) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SearchAndFilters(state: ProjectListState, onQuery: (String) -> Unit, onFilter: (ProjectFilter) -> Unit) {
    OutlinedTextField(
        value = state.query,
        onValueChange = onQuery,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = if (state.query.isNotEmpty()) ({
            IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.filter_clear)) }
        }) else null,
        placeholder = { Text(stringResource(R.string.projects_search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    val f = state.filter
    val typeOptions = ProjectType.entries.map { it to stringResource(it.label) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            FilterMenu(stringResource(R.string.filter_person), state.filterPersons, f.personId) { onFilter(f.copy(personId = it)) }
        }
        item {
            FilterMenu(stringResource(R.string.filter_pattern), state.filterPatterns, f.patternId) { onFilter(f.copy(patternId = it)) }
        }
        item {
            FilterMenu(stringResource(R.string.filter_type), typeOptions, f.type) { onFilter(f.copy(type = it)) }
        }
        item {
            FilterMenu(stringResource(R.string.filter_year), state.years.map { it to it.toString() }, f.year) { onFilter(f.copy(year = it)) }
        }
    }
}

/** Filter chip with a menu; when active it shows the chosen value and a remove icon (SPEC 3.1). */
@Composable
private fun <T> FilterMenu(label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selectedLabel ?: label) },
            trailingIcon = {
                if (selected != null) {
                    Icon(
                        Icons.Outlined.Close, stringResource(R.string.filter_clear),
                        Modifier.size(18.dp).clickable { onSelect(null) },
                    )
                } else {
                    Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
                }
            },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, onClick = { onSelect(null); open = false })
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

@Composable
private fun Sections(state: ProjectListState, onToggle: (ProjectStatus, Boolean) -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
        state.sections.forEach { section ->
            val collapsed = section.status in state.collapsed
            item(key = "header-${section.status}") {
                SectionHeader(section.status, section.projects.size, collapsed) { onToggle(section.status, !collapsed) }
            }
            if (!collapsed) {
                items(section.projects, key = { it.id }) { project ->
                    ProjectCard(project, onClick = { onOpen(project.id) }, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(status: ProjectStatus, count: Int, collapsed: Boolean, onClick: () -> Unit) {
    val toggleLabel = stringResource(if (collapsed) R.string.section_expand else R.string.section_collapse)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).semantics { contentDescription = toggleLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(status.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.section_title, stringResource(status.label), count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null)
    }
}

@Composable
private fun ProjectCard(project: ProjectSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(project, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (project.personName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(project.personName, project.personColor ?: 0, size = 18.dp, photoId = project.personPhotoId)
                        Spacer(Modifier.width(6.dp))
                        Text(project.personName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val patternLine = project.patterns.joinToString(" · ") { p ->
                    if (p.sizeSewn.isBlank()) p.name else "${p.name} (${p.sizeSewn})"
                }.ifEmpty { project.size }
                if (patternLine.isNotBlank()) {
                    Text(
                        patternLine, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Cover photo, or the type icon on a plain background. */
@Composable
private fun Cover(project: ProjectSummary, modifier: Modifier) {
    val sizePx = with(LocalDensity.current) { 160.dp.roundToPx() }
    if (project.coverPhotoId != null) {
        PhotoImage(project.coverPhotoId, minEdgePx = sizePx, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(project.type.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun Gallery(projects: List<ProjectSummary>, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(projects, key = { it.id }) { project ->
            Column(Modifier.clickable { onOpen(project.id) }) {
                Cover(project, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                Text(
                    project.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
