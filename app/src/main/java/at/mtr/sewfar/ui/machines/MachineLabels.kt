package at.mtr.sewfar.ui.machines

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.SetupResult

/** Names of built-in machine fields (SPEC 6.2); keys match `MachineTemplates`. */
object FieldLabels {
    private val labels: Map<String, Int> = mapOf(
        "stitch" to R.string.field_stitch,
        "stitch_length" to R.string.field_stitch_length,
        "stitch_width" to R.string.field_stitch_width,
        "needle_tension" to R.string.field_needle_tension,
        "presser_foot_pressure" to R.string.field_presser_foot_pressure,
        "foot" to R.string.field_foot,
        "needle" to R.string.field_needle,
        "thread" to R.string.field_thread,
        "threads" to R.string.field_threads,
        "tension_left_needle" to R.string.field_tension_left_needle,
        "tension_right_needle" to R.string.field_tension_right_needle,
        "tension_upper_looper" to R.string.field_tension_upper_looper,
        "tension_lower_looper" to R.string.field_tension_lower_looper,
        "differential_feed" to R.string.field_differential_feed,
        "cutting_width" to R.string.field_cutting_width,
        "needles_used" to R.string.field_needles_used,
        "looper_tension" to R.string.field_looper_tension,
        "hoop" to R.string.field_hoop,
        "stabilizer" to R.string.field_stabilizer,
        "speed" to R.string.field_speed,
    )

    @StringRes fun of(key: String): Int? = labels[key]

    val keys: Set<String> get() = labels.keys

    /** Custom name if set, else the built-in name in the current language. */
    fun resolve(field: MachineFieldEntity, getString: (Int) -> String): String =
        field.customName ?: field.builtinKey?.let { labels[it] }?.let(getString) ?: field.builtinKey.orEmpty()
}

/** Field name for display. */
@Composable
@ReadOnlyComposable
fun MachineFieldEntity.displayName(): String {
    val resources = LocalContext.current.resources
    return FieldLabels.resolve(this) { resources.getString(it) }
}

@get:StringRes
val MachineType.label: Int
    get() = when (this) {
        MachineType.SEWING_MACHINE -> R.string.mtype_sewing_machine
        MachineType.OVERLOCKER -> R.string.mtype_overlocker
        MachineType.COVERSTITCH -> R.string.mtype_coverstitch
        MachineType.EMBROIDERY -> R.string.mtype_embroidery
        MachineType.OTHER -> R.string.mtype_other
    }

@get:StringRes
val SetupResult.label: Int
    get() = when (this) {
        SetupResult.GOOD -> R.string.result_good
        SetupResult.OK -> R.string.result_ok
        SetupResult.BAD -> R.string.result_bad
    }
