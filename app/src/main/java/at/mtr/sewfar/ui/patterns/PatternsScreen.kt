package at.mtr.sewfar.ui.patterns

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.TabScaffold

/** Pattern library (SPEC 4). Placeholder until M1.5. */
@Composable
fun PatternsScreen(onOpenSettings: () -> Unit) {
    TabScaffold(title = stringResource(R.string.tab_patterns), onOpenSettings = onOpenSettings) { padding ->
        EmptyState(stringResource(R.string.patterns_empty), padding)
    }
}
