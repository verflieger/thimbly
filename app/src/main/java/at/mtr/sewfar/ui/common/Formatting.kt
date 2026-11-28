package at.mtr.sewfar.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** The app's current locale (follows the phone or the per-app language setting). */
val currentLocale: Locale
    @Composable @ReadOnlyComposable
    get() = LocalConfiguration.current.locales[0]

/** Localized medium date, e.g. "23 Nov 2026" / "23.11.2026". Never use fixed patterns (SPEC 1). */
@Composable
@ReadOnlyComposable
fun LocalDate.format(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale))

/**
 * The purchases' currency (SPEC 4.3): the chosen [code], else the phone's region's (the system locale: the app's own
 * language "de"/"en" has no region), else EUR.
 */
fun currencyOf(code: String?): Currency =
    code?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        ?: runCatching { Currency.getInstance(Resources.getSystem().configuration.locales[0]) }.getOrNull()
        ?: Currency.getInstance("EUR")

/** "18,50 €" / "€18.50": hundredths of [currency] in the locale's format; never rounded to whole yen etc. */
fun formatMoney(cents: Long, currency: Currency, locale: Locale): String =
    NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency; maximumFractionDigits = 2 }.format(BigDecimal.valueOf(cents, 2))
