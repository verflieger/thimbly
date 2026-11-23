package at.mtr.sewfar.ui.common

import androidx.annotation.StringRes
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind

/** String resource of a built-in list entry (SPEC 1); null for an unknown key. Keys match `BuiltIns`. */
object BuiltInLabels {
    private val labels: Map<ListKind, Map<String, Int>> = mapOf(
        ListKind.MEASUREMENT_TYPE to mapOf(
            "height" to R.string.mt_height,
            "chest" to R.string.mt_chest,
            "under_bust" to R.string.mt_under_bust,
            "waist" to R.string.mt_waist,
            "hip" to R.string.mt_hip,
            "inseam" to R.string.mt_inseam,
            "outside_leg" to R.string.mt_outside_leg,
            "arm_length" to R.string.mt_arm_length,
            "back_length" to R.string.mt_back_length,
            "shoulder_width" to R.string.mt_shoulder_width,
            "neck" to R.string.mt_neck,
            "head" to R.string.mt_head,
            "upper_arm" to R.string.mt_upper_arm,
            "thigh" to R.string.mt_thigh,
            "calf" to R.string.mt_calf,
            "foot_length" to R.string.mt_foot_length,
        ),
        ListKind.PATTERN_CATEGORY to mapOf(
            "shirt_top" to R.string.cat_shirt_top,
            "sweater_hoodie" to R.string.cat_sweater_hoodie,
            "jacket_coat" to R.string.cat_jacket_coat,
            "dress" to R.string.cat_dress,
            "skirt" to R.string.cat_skirt,
            "pants_shorts" to R.string.cat_pants_shorts,
            "underwear_nightwear" to R.string.cat_underwear_nightwear,
            "baby" to R.string.cat_baby,
            "accessories" to R.string.cat_accessories,
            "bag" to R.string.cat_bag,
            "home" to R.string.cat_home,
            "other" to R.string.cat_other,
        ),
        ListKind.LESSON_TAG to mapOf(
            "fit_good" to R.string.tag_fit_good,
            "size_up" to R.string.tag_size_up,
            "size_down" to R.string.tag_size_down,
            "lengthen" to R.string.tag_lengthen,
            "shorten" to R.string.tag_shorten,
            "fabric_suited" to R.string.tag_fabric_suited,
            "fabric_unsuitable" to R.string.tag_fabric_unsuitable,
        ),
        ListKind.PURPOSE to mapOf(
            "seam" to R.string.purpose_seam,
            "hem" to R.string.purpose_hem,
            "topstitching" to R.string.purpose_topstitching,
            "buttonhole" to R.string.purpose_buttonhole,
            "gathering" to R.string.purpose_gathering,
            "edge_finishing" to R.string.purpose_edge_finishing,
            "basting" to R.string.purpose_basting,
            "binding" to R.string.purpose_binding,
            "elastic" to R.string.purpose_elastic,
            "decorative" to R.string.purpose_decorative,
        ),
    )

    @StringRes
    fun of(kind: ListKind, key: String): Int? = labels[kind]?.get(key)

    /** For tests: every key with a label. */
    fun keys(kind: ListKind): Set<String> = labels[kind].orEmpty().keys
}
