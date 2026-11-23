package at.mtr.sewfar.data.seed

import at.mtr.sewfar.data.ListKind

/**
 * Built-in entries of the editable lists (SPEC 3.5, 4.2, 5.2, 6.3), in their default order.
 * Keys are permanent: they derive the row IDs (SPEC 1) and map to string resources. Add new keys, never rename.
 */
object BuiltIns {
    val keys: Map<ListKind, List<String>> = mapOf(
        ListKind.MEASUREMENT_TYPE to listOf(
            "height", "chest", "under_bust", "waist", "hip", "inseam", "outside_leg", "arm_length",
            "back_length", "shoulder_width", "neck", "head", "upper_arm", "thigh", "calf", "foot_length",
        ),
        ListKind.PATTERN_CATEGORY to listOf(
            "shirt_top", "sweater_hoodie", "jacket_coat", "dress", "skirt", "pants_shorts",
            "underwear_nightwear", "baby", "accessories", "bag", "home", "other",
        ),
        ListKind.LESSON_TAG to listOf(
            "fit_good", "size_up", "size_down", "lengthen", "shorten", "fabric_suited", "fabric_unsuitable",
        ),
        ListKind.PURPOSE to listOf(
            "seam", "hem", "topstitching", "buttonhole", "gathering", "edge_finishing", "basting", "binding",
            "elastic", "decorative",
        ),
    )
}
