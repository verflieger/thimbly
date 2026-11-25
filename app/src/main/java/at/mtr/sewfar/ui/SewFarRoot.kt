package at.mtr.sewfar.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.Messages
import at.mtr.sewfar.ui.library.LibraryScreen
import at.mtr.sewfar.ui.library.LibraryTab
import at.mtr.sewfar.ui.library.LibraryViewModel
import at.mtr.sewfar.ui.library.MachineScreen
import at.mtr.sewfar.ui.library.MachineViewModel
import at.mtr.sewfar.ui.library.PatternScreen
import at.mtr.sewfar.ui.library.PatternViewModel
import at.mtr.sewfar.ui.library.PersonScreen
import at.mtr.sewfar.ui.library.PersonViewModel
import at.mtr.sewfar.ui.library.SetupScreen
import at.mtr.sewfar.ui.library.SetupViewModel
import at.mtr.sewfar.ui.photos.PhotoViewerScreen
import at.mtr.sewfar.ui.projects.ProjectDetailScreen
import at.mtr.sewfar.ui.projects.ProjectDetailViewModel
import at.mtr.sewfar.ui.projects.ProjectListViewModel
import at.mtr.sewfar.ui.projects.ProjectsScreen
import at.mtr.sewfar.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object ProjectsRoute
@Serializable object SettingsRoute
/** [tab] is a [LibraryTab] name. */
@Serializable data class LibraryRoute(val tab: String = LibraryTab.entries.first().name)
@Serializable data class ProjectRoute(val id: String)
@Serializable data class PatternRoute(val id: String)
@Serializable data class PersonRoute(val id: String)
@Serializable data class MachineRoute(val id: String)
/** [edit]: just created, so the form opens right away for the values. */
@Serializable data class SetupRoute(val id: String, val edit: Boolean = false)
/** All photos of one owner, opened at [photoId] (SPEC 3.5). [ownerType] is a [PhotoOwner] name. */
@Serializable data class PhotoViewerRoute(val ownerType: String, val ownerId: String, val photoId: String? = null)

/** Short slides instead of the default 700 ms fades, which made screen changes feel sluggish. */
private const val TransitionMillis = 250

/**
 * Pops [entry] only while it is the resumed top destination, so a double tap (or tap + back) on a screen that is
 * already leaving can't pop the screen below it too.
 */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}

private class ShellViewModel : ViewModel() {
    val snackbarHost = SnackbarHostState()
}

/** App shell (SPEC 2): the project list is home; everything else opens on top of it. No bottom bar. */
@Composable
fun SewFarRoot(container: AppContainer) {
    val nav = rememberNavController()
    // Held by the activity's ViewModel, so an Undo bar survives rotation, dark mode and language changes.
    val shell = viewModel { ShellViewModel() }
    val snackbarHost = shell.snackbarHost
    val scope = shell.viewModelScope
    val undoLabel = stringResource(R.string.action_undo)
    val a11y = LocalAccessibilityManager.current
    val undoMillis = a11y?.calculateRecommendedTimeoutMillis(Messages.UndoMillis, containsText = true, containsControls = true)
        ?: Messages.UndoMillis
    val messages = remember(snackbarHost, scope, undoLabel, undoMillis) {
        Messages(snackbarHost, scope, undoLabel, container::undo, undoMillis)
    }
    val openProject = { id: String -> nav.navigate(ProjectRoute(id)) }
    // null until DataStore answers: no time controls flash up for people who switched them off.
    val trackTime = container.settings.trackTime.collectAsStateWithLifecycle(initialValue = null).value == true

    CompositionLocalProvider(LocalMessages provides messages) {
        Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbarHost) }) { _ ->
            NavHost(
                nav,
                startDestination = ProjectsRoute,
                enterTransition = { slideInHorizontally(tween(TransitionMillis)) { it / 4 } + fadeIn(tween(TransitionMillis)) },
                exitTransition = { fadeOut(tween(TransitionMillis)) },
                popEnterTransition = { fadeIn(tween(TransitionMillis)) },
                popExitTransition = { slideOutHorizontally(tween(TransitionMillis)) { it } + fadeOut(tween(TransitionMillis)) },
                // The back gesture has its own defaults (scale to 70 % plus a slow fade); use the same slide.
                predictivePopEnterTransition = { fadeIn(tween(TransitionMillis)) },
                predictivePopExitTransition = { slideOutHorizontally(tween(TransitionMillis)) { it } },
            ) {
                composable<ProjectsRoute> {
                    ProjectsScreen(
                        viewModel = viewModel { ProjectListViewModel(container) },
                        trackTime = trackTime,
                        onOpenProject = { id -> nav.navigate(ProjectRoute(id)) },
                        onOpenLibrary = { nav.navigate(LibraryRoute()) },
                        onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
                    )
                }
                composable<ProjectRoute> { entry ->
                    val route = entry.toRoute<ProjectRoute>()
                    ProjectDetailScreen(
                        viewModel = viewModel { ProjectDetailViewModel(container, route.id) },
                        trackTime = trackTime,
                        onBack = { nav.popFrom(entry) },
                        onOpenPhoto = { photoId -> nav.navigate(PhotoViewerRoute(PhotoOwner.PROJECT.name, route.id, photoId)) },
                        onOpenPattern = { nav.navigate(PatternRoute(it)) },
                        onOpenPerson = { nav.navigate(PersonRoute(it)) },
                        onOpenSetup = { nav.navigate(SetupRoute(it)) },
                        onOpenNewSetup = { nav.navigate(SetupRoute(it, edit = true)) },
                    )
                }
                composable<LibraryRoute> { entry ->
                    val tab = LibraryTab.valueOf(entry.toRoute<LibraryRoute>().tab)
                    LibraryScreen(
                        viewModel = viewModel { LibraryViewModel(container, tab) },
                        onBack = { nav.popFrom(entry) },
                        onOpen = { t, id ->
                            nav.navigate(
                                when (t) {
                                    LibraryTab.PATTERNS -> PatternRoute(id)
                                    LibraryTab.MACHINES -> MachineRoute(id)
                                    LibraryTab.PEOPLE -> PersonRoute(id)
                                },
                            )
                        },
                    )
                }
                composable<PatternRoute> { entry ->
                    val route = entry.toRoute<PatternRoute>()
                    PatternScreen(
                        viewModel = viewModel { PatternViewModel(container, route.id) },
                        onBack = { nav.popFrom(entry) }, onOpenProject = openProject,
                        onOpenPhoto = { photoId -> nav.navigate(PhotoViewerRoute(PhotoOwner.PATTERN.name, route.id, photoId)) },
                    )
                }
                composable<PersonRoute> { entry ->
                    val route = entry.toRoute<PersonRoute>()
                    PersonScreen(
                        viewModel = viewModel { PersonViewModel(container, route.id) },
                        onBack = { nav.popFrom(entry) }, onOpenProject = openProject,
                    )
                }
                composable<MachineRoute> { entry ->
                    val route = entry.toRoute<MachineRoute>()
                    MachineScreen(
                        viewModel = viewModel { MachineViewModel(container, route.id) },
                        onBack = { nav.popFrom(entry) },
                        onOpenSetup = { id, edit -> nav.navigate(SetupRoute(id, edit)) },
                    )
                }
                composable<SetupRoute> { entry ->
                    val route = entry.toRoute<SetupRoute>()
                    SetupScreen(
                        viewModel = viewModel { SetupViewModel(container, route.id) }, startEditing = route.edit,
                        onBack = { nav.popFrom(entry) }, onOpenProject = openProject,
                        onOpenMachine = { nav.navigate(MachineRoute(it)) },
                    )
                }
                composable<SettingsRoute> { entry ->
                    SettingsScreen(container, onBack = { nav.popFrom(entry) })
                }
                composable<PhotoViewerRoute> { entry ->
                    val route = entry.toRoute<PhotoViewerRoute>()
                    val photos by container.photos.observe(PhotoOwner.valueOf(route.ownerType), route.ownerId)
                        .collectAsState(initial = null)
                    photos?.let { list ->
                        PhotoViewerScreen(list.map { it.id }, route.photoId, onClose = { nav.popFrom(entry) })
                    }
                }
            }
        }
    }
}
