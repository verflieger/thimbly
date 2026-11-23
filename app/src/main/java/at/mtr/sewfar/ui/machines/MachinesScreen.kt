package at.mtr.sewfar.ui.machines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.TabScaffold

/** Machines and setups (SPEC 6). Placeholder until M2. */
@Composable
fun MachinesScreen(onOpenSettings: () -> Unit) {
    TabScaffold(title = stringResource(R.string.tab_machines), onOpenSettings = onOpenSettings) { padding ->
        EmptyState(stringResource(R.string.machines_empty), padding)
    }
}
