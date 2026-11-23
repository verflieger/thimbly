package at.mtr.sewfar.ui.people

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.TabScaffold

/** Persons and measurements (SPEC 5). Placeholder until M1.5. */
@Composable
fun PeopleScreen(onOpenSettings: () -> Unit) {
    TabScaffold(title = stringResource(R.string.tab_people), onOpenSettings = onOpenSettings) { padding ->
        EmptyState(stringResource(R.string.people_empty), padding)
    }
}
