package at.mtr.sewfar.domain

/** Prices of purchases (SPEC 4.3), stored as hundredths of the chosen currency. */
object Money {
    private val Price = Regex("""^(\d{1,7})(?:[.,](\d{1,2}))?$""")

    /** "18,5" / "18.50" / "18" → hundredths; null if not a price (blank, negative, more than two decimals). */
    fun parseCents(text: String): Long? {
        val match = Price.matchEntire(text.trim()) ?: return null
        val (whole, fraction) = match.destructured
        return whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
    }

    /** The editable form of [cents]: "18.50" with the locale's decimal [separator]. */
    fun toInput(cents: Long, separator: Char): String = "%d%c%02d".format(cents / 100, separator, cents % 100)
}
