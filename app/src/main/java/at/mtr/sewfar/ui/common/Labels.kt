package at.mtr.sewfar.ui.common

import androidx.annotation.StringRes
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.GarmentType
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.PurchaseCategory

@get:StringRes
val ProjectStatus.label: Int
    get() = when (this) {
        ProjectStatus.PLANNED -> R.string.status_planned
        ProjectStatus.IN_PROGRESS -> R.string.status_in_progress
        ProjectStatus.DONE -> R.string.status_done
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
val GarmentType.label: Int
    get() = when (this) {
        GarmentType.DRESS -> R.string.garment_dress
        GarmentType.SKIRT -> R.string.garment_skirt
        GarmentType.T_SHIRT -> R.string.garment_t_shirt
        GarmentType.HOODIE -> R.string.garment_hoodie
        GarmentType.TOP -> R.string.garment_top
        GarmentType.TROUSERS -> R.string.garment_trousers
        GarmentType.JACKET -> R.string.garment_jacket
        GarmentType.BAG -> R.string.garment_bag
        GarmentType.OTHER -> R.string.garment_other
    }

@get:StringRes
val PurchaseCategory.label: Int
    get() = when (this) {
        PurchaseCategory.FABRIC -> R.string.pcat_fabric
        PurchaseCategory.NOTIONS -> R.string.pcat_notions
        PurchaseCategory.PATTERNS -> R.string.pcat_patterns
        PurchaseCategory.TOOLS -> R.string.pcat_tools
        PurchaseCategory.MACHINES -> R.string.pcat_machines
        PurchaseCategory.OTHER -> R.string.pcat_other
    }

/** Names of setting fields (SPEC 5.2); keys match `MachineTemplates`. */
@StringRes
fun fieldLabel(key: String): Int = when (key) {
    "stitch" -> R.string.field_stitch
    "stitch_length" -> R.string.field_stitch_length
    "stitch_width" -> R.string.field_stitch_width
    "needle_tension" -> R.string.field_needle_tension
    "foot" -> R.string.field_foot
    "needle" -> R.string.field_needle
    "threads" -> R.string.field_threads
    "tension_left_needle" -> R.string.field_tension_left_needle
    "tension_right_needle" -> R.string.field_tension_right_needle
    "tension_upper_looper" -> R.string.field_tension_upper_looper
    "tension_lower_looper" -> R.string.field_tension_lower_looper
    "differential_feed" -> R.string.field_differential_feed
    "cutting_width" -> R.string.field_cutting_width
    "needles_used" -> R.string.field_needles_used
    "looper_tension" -> R.string.field_looper_tension
    "hoop" -> R.string.field_hoop
    "stabilizer" -> R.string.field_stabilizer
    else -> error("Unknown field $key")
}
