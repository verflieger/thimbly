package at.mtr.sewfar.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Fabric amounts: entered in metres (locale decimal separator, SPEC 1), stored as integer centimetres (SPEC 8). */
object Amounts {
    /** "0,8" / "0.8" / "1" → 80 / 80 / 100 cm; blank → null; invalid or negative → [Invalid]. */
    fun parseMeters(text: String): ParseResult {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty()) return ParseResult.Empty
        val metres = cleaned.toBigDecimalOrNull() ?: return ParseResult.Invalid
        if (metres.signum() < 0) return ParseResult.Invalid
        return ParseResult.Cm(metres.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt())
    }

    /** 80 → "0.8" (en) / "0,8" (de); at most two decimals, no trailing zeros. */
    fun formatMeters(cm: Int, locale: Locale): String {
        val format = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale))
        return format.format(BigDecimal(cm).movePointLeft(2))
    }

    sealed interface ParseResult {
        data object Empty : ParseResult
        data object Invalid : ParseResult
        data class Cm(val cm: Int) : ParseResult
    }
}
