package at.mtr.sewfar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The app's current locale (follows the phone or the per-app language setting). */
val currentLocale: Locale
    @Composable @ReadOnlyComposable
    get() = LocalConfiguration.current.locales[0]

/** Localized medium date, e.g. "23 Nov 2026" / "23.11.2026". Never use fixed patterns (SPEC 1). */
@Composable
@ReadOnlyComposable
fun LocalDate.format(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale))
