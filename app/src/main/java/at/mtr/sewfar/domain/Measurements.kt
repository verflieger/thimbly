package at.mtr.sewfar.domain

import java.time.LocalDate

/** One measured value with its session date. */
data class Measured(val typeId: String, val valueMm: Int, val date: LocalDate)

object Measurements {
    /** Latest value per measurement type (SPEC 5.2); [values] in any order. Ties: the first given wins. */
    fun latest(values: List<Measured>): Map<String, Measured> =
        values.groupBy { it.typeId }.mapValues { (_, list) -> list.maxWith(compareBy { it.date }) }

    /** History per type, newest first. */
    fun history(values: List<Measured>): Map<String, List<Measured>> =
        values.groupBy { it.typeId }.mapValues { (_, list) -> list.sortedByDescending { it.date } }

    /** "52,5" / "52.5" cm → 525 mm; one decimal (SPEC 5.2). Null for blank or invalid. */
    fun parseCm(text: String): Int? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val cm = cleaned.toBigDecimalOrNull() ?: return null
        if (cm.signum() <= 0) return null
        return cm.movePointRight(1).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
    }

    fun formatCm(mm: Int, locale: java.util.Locale): String =
        java.text.DecimalFormat("0.#", java.text.DecimalFormatSymbols.getInstance(locale)).format(mm / 10.0)
}

object Lessons {
    /** Tag counts, most frequent first (SPEC 4.2: "Lengthen ×2"). */
    fun counts(tagIds: List<String>): List<Pair<String, Int>> =
        tagIds.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
}
