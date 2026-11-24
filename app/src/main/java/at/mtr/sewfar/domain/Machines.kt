package at.mtr.sewfar.domain

/**
 * Setting fields of a setup, by machine type (SPEC 5.2). Keys are stored in `setup_value.key`: never rename one.
 * Values of keys a type doesn't list (e.g. after changing the machine type) are kept but not shown.
 */
object MachineTemplates {
    val fields: Map<MachineType, List<String>> = mapOf(
        MachineType.SEWING_MACHINE to listOf("stitch", "stitch_length", "stitch_width", "needle_tension", "foot", "needle"),
        MachineType.OVERLOCKER to listOf(
            "threads", "tension_left_needle", "tension_right_needle", "tension_upper_looper", "tension_lower_looper",
            "differential_feed", "stitch_length", "cutting_width",
        ),
        MachineType.COVERSTITCH to listOf("needles_used", "needle_tension", "looper_tension", "differential_feed", "stitch_length"),
        MachineType.EMBROIDERY to listOf("hoop", "needle", "stabilizer"),
        MachineType.OTHER to emptyList(),
    )

    fun of(type: MachineType): List<String> = fields.getValue(type)

    val allKeys: Set<String> get() = fields.values.flatten().toSet()
}

/** Time spent on a project (SPEC 3.3). */
object Durations {
    /** Total of finished entries plus the running one up to [now]. */
    fun total(entries: List<Pair<Long, Long?>>, now: Long): Long =
        entries.sumOf { (start, end) -> ((end ?: now) - start).coerceAtLeast(0) }

    /** Minutes from text like "1:30", "90", "1,5 h" or "1.5h"; null if invalid or not positive. */
    fun parseMinutes(text: String): Int? {
        val t = text.trim().lowercase().removeSuffix("h").removeSuffix("min").trim().replace(',', '.')
        val minutes = when {
            ':' in t -> {
                val (h, m) = t.split(':', limit = 2)
                val hours = h.ifEmpty { "0" }.toIntOrNull() ?: return null
                val mins = m.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
                hours * 60 + mins
            }
            text.trim().lowercase().endsWith("h") || '.' in t -> ((t.toDoubleOrNull() ?: return null) * 60).toInt()
            else -> t.toIntOrNull() ?: return null
        }
        return minutes.takeIf { it > 0 }
    }
}
