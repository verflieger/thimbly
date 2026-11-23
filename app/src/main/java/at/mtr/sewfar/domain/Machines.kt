package at.mtr.sewfar.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Setting fields copied to a new machine, by type (SPEC 6.2). Keys are permanent and map to string resources. */
object MachineTemplates {
    val fields: Map<MachineType, List<String>> = mapOf(
        MachineType.SEWING_MACHINE to listOf(
            "stitch", "stitch_length", "stitch_width", "needle_tension", "presser_foot_pressure", "foot", "needle", "thread",
        ),
        MachineType.OVERLOCKER to listOf(
            "threads", "tension_left_needle", "tension_right_needle", "tension_upper_looper", "tension_lower_looper",
            "differential_feed", "stitch_length", "cutting_width", "needle", "thread",
        ),
        MachineType.COVERSTITCH to listOf(
            "needles_used", "needle_tension", "looper_tension", "differential_feed", "stitch_length", "needle", "thread",
        ),
        MachineType.EMBROIDERY to listOf("hoop", "needle", "thread", "stabilizer", "speed"),
        MachineType.OTHER to emptyList(),
    )

    val allKeys: Set<String> get() = fields.values.flatten().toSet()
}

/** Default setup title (SPEC 6.3). */
object SetupTitles {
    fun auto(purposeName: String?, fabric: String): String =
        listOfNotNull(purposeName?.takeIf { it.isNotBlank() }, fabric.trim().takeIf { it.isNotEmpty() }).joinToString(" – ")

    /** What lists show: the title, or the machine name while the setup has no title yet. */
    fun shown(title: String, machineName: String?): String = title.ifBlank { machineName.orEmpty() }
}

/** One field of a setup snapshot (SPEC 6.4): ID, the label at snapshot time, the value. */
@Serializable
data class SnapshotField(val fieldId: String, val label: String, val value: String)

object Snapshots {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SnapshotField.serializer())

    fun encode(fields: List<SnapshotField>): String = json.encodeToString(serializer, fields)
    fun decode(text: String): List<SnapshotField> = runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}

/** A field of the setup as it is now: [visible] = live and not hidden. */
data class CurrentField(val fieldId: String, val label: String, val visible: Boolean)

data class FieldChange(val fieldId: String, val label: String, val kind: Kind, val old: String?, val new: String?) {
    enum class Kind { CHANGED, REMOVED, ADDED }
}

/** "Setup changed since" (SPEC 6.4): compares a snapshot with the setup's current fields and values. */
object SnapshotDiff {
    /**
     * Matched by field ID; a label change alone is no difference. Snapshot fields that are no longer visible are
     * REMOVED, visible fields missing from the snapshot are ADDED, differing values are CHANGED.
     */
    fun diff(snapshot: List<SnapshotField>, current: List<CurrentField>, values: Map<String, String>): List<FieldChange> {
        val visible = current.filter { it.visible }.associateBy { it.fieldId }
        val inSnapshot = snapshot.map { it.fieldId }.toSet()
        val changes = snapshot.mapNotNull { field ->
            val now = visible[field.fieldId]
            when {
                now == null -> FieldChange(field.fieldId, field.label, FieldChange.Kind.REMOVED, field.value, null)
                values[field.fieldId].orEmpty() != field.value ->
                    FieldChange(field.fieldId, now.label, FieldChange.Kind.CHANGED, field.value, values[field.fieldId].orEmpty())
                else -> null
            }
        }
        val added = visible.values.filter { it.fieldId !in inSnapshot }
            .map { FieldChange(it.fieldId, it.label, FieldChange.Kind.ADDED, null, values[it.fieldId].orEmpty()) }
        return changes + added
    }
}
