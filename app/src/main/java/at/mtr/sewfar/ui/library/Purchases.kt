package at.mtr.sewfar.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.ProjectRef
import at.mtr.sewfar.ui.common.EditableChoice
import at.mtr.sewfar.ui.common.EditableText
import at.mtr.sewfar.ui.common.EditableValue
import at.mtr.sewfar.ui.common.currencyOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import at.mtr.sewfar.data.db.PurchaseEntity
import at.mtr.sewfar.domain.Money
import at.mtr.sewfar.domain.PurchaseCategory
import at.mtr.sewfar.ui.common.ChipChoice
import at.mtr.sewfar.ui.common.DatePickDialog
import at.mtr.sewfar.ui.common.FormDialog
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
 * Adds a purchase with its required fields in one dialog (SPEC 2, 4.3). Save is disabled while the name is blank or the
 * price is not a number. Notes and editing are on the purchase's page ([PurchaseScreen]), which opens after Save.
 */
@Composable
fun NewPurchaseDialog(
    currency: Currency,
    today: LocalDate,
    onSave: (name: String, category: PurchaseCategory, priceCents: Long, boughtOn: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(PurchaseCategory.FABRIC) }
    var date by rememberSaveable { mutableStateOf(today) }
    var pickingDate by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val cents = Money.parseCents(price)
    // Before the early return, so coming back from the date picker doesn't run it again.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    if (pickingDate) {
        DatePickDialog(date, onPick = { date = it; pickingDate = false }, onDismiss = { pickingDate = false })
        return
    }
    FormDialog(
        title = stringResource(R.string.purchase_new),
        canSave = name.isNotBlank() && cents != null,
        onSave = { cents?.let { onSave(name.trim(), category, it, date) } },
        onDismiss = onDismiss,
    ) {
        LabeledTextField(
            name, { name = it }, stringResource(R.string.purchase_name),
            fieldModifier = Modifier.focusRequester(focus).testTag("purchaseName"),
            placeholder = stringResource(R.string.purchase_name_hint),
        )
        PriceField(price, { price = it }, currency)
        ChipChoice(stringResource(R.string.purchase_category), PurchaseCategory.entries, category, { it.label }, "category") { category = it }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(stringResource(R.string.purchase_date))
            OutlinedButton(onClick = { pickingDate = true }) { Text(date.format()) }
        }
    }
}

/** The price input: comma or dot, at most two decimals, the currency as suffix (SPEC 4.3). */
@Composable
private fun PriceField(price: String, onChange: (String) -> Unit, currency: Currency, focus: FocusRequester? = null) {
    LabeledTextField(
        price, onChange, stringResource(R.string.purchase_price),
        fieldModifier = (focus?.let { Modifier.focusRequester(it) } ?: Modifier).testTag("purchasePrice"),
        suffix = currency.getSymbol(currentLocale),
        isError = price.isNotBlank() && Money.parseCents(price) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, capitalization = KeyboardCapitalization.None),
    )
}

// ---- Purchase page (SPEC 4.3): like every library page ----

class PurchaseViewModel(container: AppContainer, private val id: String) : RecordViewModel<PurchaseEntity>(container) {
    private val repo = container.purchases
    override val record = repo.observe(id)
    override val projectsFlow = emptyFlow<List<ProjectRef>>()
    val currency: StateFlow<String?> = container.settings.currency.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override suspend fun delete() = container.runInApp { repo.delete(id) }

    fun change(change: (PurchaseEntity) -> PurchaseEntity) = write { repo.edit(id, change) }
}

/** A purchase's page: price, category, date and notes read-only, each edited by a tap; ✎ renames, the trash icon deletes. */
@Composable
fun PurchaseScreen(viewModel: PurchaseViewModel, onBack: () -> Unit) {
    val currencyCode by viewModel.currency.collectAsStateWithLifecycle()
    val currency = currencyOf(currencyCode)
    val locale = currentLocale
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // "price" or "date"
    RecordScreen(
        viewModel = viewModel,
        startEditing = false,
        deletedText = stringResource(R.string.purchase_deleted),
        onBack = onBack,
        onOpenProject = {},
        titleOf = { it.name },
        nameOf = { it.name },
        nameLabel = stringResource(R.string.purchase_name),
        onRename = { name -> viewModel.change { it.copy(name = name) } },
    ) { purchase, _ ->
        EditableValue(stringResource(R.string.purchase_price), "price", formatMoney(purchase.priceCents, currency, locale)) { editing = "price" }
        EditableChoice(
            stringResource(R.string.purchase_category), purchase.category, "category", stringResource(R.string.purchase_category),
            PurchaseCategory.entries, { it.label },
        ) { category -> viewModel.change { it.copy(category = category) } }
        EditableValue(stringResource(R.string.purchase_date), "date", purchase.boughtOn.format()) { editing = "date" }
        EditableText(stringResource(R.string.purchase_notes), purchase.notes, "notes") { notes -> viewModel.change { it.copy(notes = notes) } }
        when (editing) {
            "price" -> PriceDialog(purchase.priceCents, currency, onSave = { cents -> editing = null; viewModel.change { it.copy(priceCents = cents) } }, onDismiss = { editing = null })
            "date" -> DatePickDialog(purchase.boughtOn, onPick = { date -> editing = null; viewModel.change { it.copy(boughtOn = date) } }, onDismiss = { editing = null })
        }
    }
}

@Composable
private fun PriceDialog(cents: Long, currency: Currency, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    val separator = DecimalFormatSymbols.getInstance(currentLocale).decimalSeparator
    var price by rememberSaveable { mutableStateOf(Money.toInput(cents, separator)) }
    val parsed = Money.parseCents(price)
    val focus = remember { FocusRequester() }
    FormDialog(stringResource(R.string.purchase_price), canSave = parsed != null, onSave = { parsed?.let(onSave) }, onDismiss = onDismiss) {
        PriceField(price, { price = it }, currency, focus)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
