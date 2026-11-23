package at.mtr.sewfar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.Messages
import at.mtr.sewfar.ui.machines.MachinesScreen
import at.mtr.sewfar.ui.patterns.PatternsScreen
import at.mtr.sewfar.ui.people.PeopleScreen
import at.mtr.sewfar.ui.photos.PhotoViewerScreen
import at.mtr.sewfar.ui.projects.ProjectDetailScreen
import at.mtr.sewfar.ui.projects.ProjectDetailViewModel
import at.mtr.sewfar.ui.projects.ProjectListViewModel
import at.mtr.sewfar.ui.projects.ProjectsScreen
import at.mtr.sewfar.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object ProjectsRoute
@Serializable object PatternsRoute
@Serializable object PeopleRoute
@Serializable object MachinesRoute
@Serializable object SettingsRoute
@Serializable data class ProjectDetailRoute(val id: String)
/** All photos of one owner, opened at [photoId] (SPEC 3.6). [ownerType] is a [PhotoOwner] name. */
@Serializable data class PhotoViewerRoute(val ownerType: String, val ownerId: String, val photoId: String? = null)

private enum class Tab(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
    PROJECTS(ProjectsRoute, R.string.tab_projects, Icons.Outlined.Checklist),
    PATTERNS(PatternsRoute, R.string.tab_patterns, Icons.Outlined.ContentCut),
    PEOPLE(PeopleRoute, R.string.tab_people, Icons.Outlined.Group),
    MACHINES(MachinesRoute, R.string.tab_machines, Icons.Outlined.Tune),
}

/** App shell: bottom bar with the four tabs, opening on Projects (SPEC 2); other screens open full screen. */
@Composable
fun SewFarRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isTab = Tab.entries.any { tab -> backStackEntry?.destination?.hasRoute(tab.route::class) == true }
    val openSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
    val messages = remember(snackbarHost, scope, undoLabel) { Messages(snackbarHost, scope, undoLabel) }

    CompositionLocalProvider(LocalMessages provides messages) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                if (isTab) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = backStackEntry?.destination?.hasRoute(tab.route::class) == true,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController, startDestination = ProjectsRoute, modifier = Modifier.padding(padding)) {
                composable<ProjectsRoute> {
                    ProjectsScreen(
                        viewModel = viewModel { ProjectListViewModel(container) },
                        onOpenSettings = openSettings,
                        onOpenProject = { id -> navController.navigate(ProjectDetailRoute(id)) },
                    )
                }
                composable<PatternsRoute> { PatternsScreen(onOpenSettings = openSettings) }
                composable<PeopleRoute> { PeopleScreen(onOpenSettings = openSettings) }
                composable<MachinesRoute> { MachinesScreen(onOpenSettings = openSettings) }
                composable<SettingsRoute> {
                    SettingsScreen(container.settings, container.versionName, onBack = { navController.popBackStack() })
                }
                composable<ProjectDetailRoute> { entry ->
                    val id = entry.toRoute<ProjectDetailRoute>().id
                    ProjectDetailScreen(
                        viewModel = viewModel { ProjectDetailViewModel(container, id) },
                        onBack = { navController.popBackStack() },
                        onOpenPhoto = { photoId ->
                            navController.navigate(PhotoViewerRoute(PhotoOwner.PROJECT.name, id, photoId))
                        },
                    )
                }
                composable<PhotoViewerRoute> { entry ->
                    val route = entry.toRoute<PhotoViewerRoute>()
                    val photos by container.photos.observe(PhotoOwner.valueOf(route.ownerType), route.ownerId)
                        .collectAsState(initial = null)
                    photos?.let { list ->
                        PhotoViewerScreen(list.map { it.id }, route.photoId, onClose = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
