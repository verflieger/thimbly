package at.mtr.sewfar.domain

/** Naming for "Sew again" (SPEC 3.7). */
object SewAgain {
    private val numbered = Regex("""^(.*) \((\d+)\)$""")

    /** Name without a trailing " (number)". */
    fun baseName(name: String): String = numbered.matchEntire(name.trim())?.groupValues?.get(1) ?: name.trim()

    /**
     * "<base> (n)" with n one higher than the highest number used for that base among [existingNames]; the plain
     * base counts as 1, so the first copy is "(2)".
     */
    fun nextName(name: String, existingNames: Collection<String>): String {
        val base = baseName(name)
        val highest = existingNames.maxOfOrNull { existing ->
            val trimmed = existing.trim()
            when {
                trimmed == base -> 1
                else -> numbered.matchEntire(trimmed)?.takeIf { it.groupValues[1] == base }?.groupValues?.get(2)?.toIntOrNull() ?: 0
            }
        } ?: 0
        return "$base (${maxOf(highest, 1) + 1})"
    }
}
