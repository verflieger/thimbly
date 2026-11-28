package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.PurchaseEntity
import at.mtr.sewfar.domain.Money
import at.mtr.sewfar.domain.PurchaseCategory
import at.mtr.sewfar.ui.common.DatePickDialog
import at.mtr.sewfar.ui.common.EmptyState
import at.mtr.sewfar.ui.common.LabeledTextField
import at.mtr.sewfar.ui.common.SectionLabel
import at.mtr.sewfar.ui.common.currentLocale
import at.mtr.sewfar.ui.common.format
import at.mtr.sewfar.ui.common.formatMoney
import at.mtr.sewfar.ui.common.label
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Currency

/**
 * The purchases tab (SPEC 4.3): total on top, then the purchases by year with each year's sum; category chips filter
 * the list and every sum. [purchases] come newest first.
 */
@Composable
fun PurchaseList(purchases: List<PurchaseEntity>, currency: Currency, padding: PaddingValues, onOpen: (PurchaseEntity) -> Unit) {
    if (purchases.isEmpty()) {
        EmptyState(stringResource(R.string.purchases_empty), padding)
        return
    }
    var filter by rememberSaveable { mutableStateOf<PurchaseCategory?>(null) }
    val locale = currentLocale
    fun money(cents: Long) = formatMoney(cents, currency, locale)
    val shown = purchases.filter { filter == null || it.category == filter }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
        item(key = "filter") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PurchaseCategory.entries.forEach { category ->
                    FilterChip(
                        selected = filter == category,
                        onClick = { filter = if (filter == category) null else category },
                        label = { Text(stringResource(category.label)) },
                        modifier = Modifier.testTag("filter-${category.name}"),
                    )
                }
            }
        }
        item(key = "total") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.purchases_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(money(shown.sumOf { it.priceCents }), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("total"))
            }
        }
        shown.groupBy { it.boughtOn.year }.forEach { (year, rows) ->
            item(key = "year-$year") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    val style = MaterialTheme.typography.titleSmall
                    val color = MaterialTheme.colorScheme.primary
                    Text(year.toString(), style = style, color = color, modifier = Modifier.weight(1f))
                    Text(money(rows.sumOf { it.priceCents }), style = style, color = color, modifier = Modifier.testTag("sum-$year"))
                }
            }
            items(rows, key = { it.id }) { purchase ->
                ListItem(
                    headlineContent = { Text(purchase.name) },
                    supportingContent = { Text(purchase.boughtOn.format() + " · " + stringResource(purchase.category.label)) },
                    trailingContent = { Text(money(purchase.priceCents), style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.clickable { onOpen(purchase) },
                )
            }
        }
    }
}

/**
 * Adds ([initial] null) or edits a purchase in one dialog (SPEC 4.3). Save is disabled while the name is blank or the
 * price is not a number; a tap outside does nothing, Cancel and back discard (SPEC 2). [onDelete] only when editing.
 */
@Composable
fun PurchaseDialog(
    initial: PurchaseEntity?,
    currency: Currency,
    today: LocalDate,
    onSave: (name: String, category: PurchaseCategory, priceCents: Long, boughtOn: LocalDate) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var price by rememberSaveable {
        mutableStateOf(initial?.let { Money.toInput(it.priceCents, DecimalFormatSymbols.getInstance(locale).decimalSeparator) }.orEmpty())
    }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: PurchaseCategory.FABRIC) }
    var date by rememberSaveable { mutableStateOf(initial?.boughtOn ?: today) }
    var pickingDate by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val cents = Money.parseCents(price)
    // Before the early return, so coming back from the date picker doesn't run it again.
    LaunchedEffect(Unit) { if (initial == null) runCatching { focus.requestFocus() } }
    if (pickingDate) {
        DatePickDialog(date, onPick = { date = it; pickingDate = false }, onDismiss = { pickingDate = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(if (initial == null) R.string.purchase_new else R.string.purchase_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledTextField(
                    name, { name = it }, stringResource(R.string.purchase_name),
                    fieldModifier = Modifier.focusRequester(focus).testTag("purchaseName"),
                    placeholder = stringResource(R.string.purchase_name_hint),
                )
                LabeledTextField(
                    price, { price = it }, stringResource(R.string.purchase_price),
                    fieldModifier = Modifier.testTag("purchasePrice"),
                    suffix = currency.getSymbol(locale),
                    isError = price.isNotBlank() && cents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, capitalization = KeyboardCapitalization.None),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(stringResource(R.string.purchase_category))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PurchaseCategory.entries.forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = { category = option },
                                label = { Text(stringResource(option.label)) },
                                modifier = Modifier.testTag("category-${option.name}"),
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(stringResource(R.string.purchase_date))
                    OutlinedButton(onClick = { pickingDate = true }) { Text(date.format()) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { cents?.let { onSave(name.trim(), category, it, date) } },
                enabled = name.isNotBlank() && cents != null,
                modifier = Modifier.testTag("purchaseSave"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag("purchaseDelete")) { Text(stringResource(R.string.action_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
