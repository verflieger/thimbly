package at.mtr.sewfar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.AppContainer
import kotlinx.coroutines.launch

/** Settings (SPEC 9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings = container.settings
    val versionName = container.versionName
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    val finePhotos by settings.finePhotos.collectAsStateWithLifecycle(initialValue = false)
    val trackTime by settings.trackTime.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { enabled -> scope.launch { settings.setDynamicColor(enabled) } },
                    )
                },
            )
            HorizontalDivider()
            SectionHeader(stringResource(R.string.project_time))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_track_time)) },
                supportingContent = { Text(stringResource(R.string.settings_track_time_summary)) },
                trailingContent = {
                    Switch(
                        checked = trackTime,
                        onCheckedChange = { enabled -> scope.launch { settings.setTrackTime(enabled) } },
                        modifier = Modifier.testTag("trackTime"),
                    )
                },
            )
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_photos))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_fine_photos)) },
                supportingContent = { Text(stringResource(R.string.settings_fine_photos_summary)) },
                trailingContent = {
                    Switch(
                        checked = finePhotos,
                        onCheckedChange = { enabled -> scope.launch { settings.setFinePhotos(enabled) } },
                        modifier = Modifier.testTag("finePhotos"),
                    )
                },
            )
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_backup))
            BackupSection(container)
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version, versionName)) },
                supportingContent = { Text(stringResource(R.string.settings_licence)) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}
