package at.mtr.sewfar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.ui.common.currencyOf
import at.mtr.sewfar.ui.common.currentLocale
import java.util.Currency
import java.util.Locale
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
    val currencyCode by settings.currency.collectAsStateWithLifecycle(initialValue = null)
    val locale = currentLocale
    val currency = currencyOf(currencyCode)
    var pickingCurrency by rememberSaveable { mutableStateOf(false) }
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
            SectionHeader(stringResource(R.string.library_purchases))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_currency)) },
                supportingContent = { Text(currency.describe(locale)) },
                modifier = Modifier.clickable { pickingCurrency = true }.testTag("currency"),
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
    if (pickingCurrency) {
        CurrencyDialog(currency, onPick = { c -> pickingCurrency = false; scope.launch { settings.setCurrency(c.currencyCode) } }, onDismiss = { pickingCurrency = false })
    }
}

/** Common currencies of sewing hobbyists, plus the phone's and the chosen one (SPEC 9). */
private val CommonCurrencies = listOf("EUR", "CHF", "GBP", "USD", "CAD", "AUD", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF")

/** "Euro (€)" */
private fun Currency.describe(locale: Locale) = getDisplayName(locale) + " (" + getSymbol(locale) + ")"

@Composable
private fun CurrencyDialog(current: Currency, onPick: (Currency) -> Unit, onDismiss: () -> Unit) {
    val locale = currentLocale
    val options = (listOf(current, currencyOf(null)) + CommonCurrencies.map(Currency::getInstance)).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.describe(locale)) },
                        leadingContent = { RadioButton(selected = option == current, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(option) }.testTag("currency-${option.currencyCode}"),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
