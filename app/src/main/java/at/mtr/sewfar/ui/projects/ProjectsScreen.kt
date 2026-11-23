package at.mtr.sewfar.ui.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.TabScaffold

/** Project list (SPEC 3.1). Placeholder until M1.4. */
@Composable
fun ProjectsScreen(onOpenSettings: () -> Unit) {
    TabScaffold(title = stringResource(R.string.tab_projects), onOpenSettings = onOpenSettings) { padding ->
        EmptyState(stringResource(R.string.projects_empty), padding)
    }
}
