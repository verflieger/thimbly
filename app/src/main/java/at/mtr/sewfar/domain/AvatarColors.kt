package at.mtr.sewfar.domain

/** Avatar palette for persons (SPEC 5.1), as ARGB. The default colour is derived from the person's ID. */
object AvatarColors {
    val palette: List<Int> = listOf(
        0xFF2F6474, 0xFF8E4E33, 0xFF5E6135, 0xFF7A4F7E, 0xFF3F6B4A, 0xFFA0662A, 0xFF4A5D8F, 0xFF9A4A5C,
    ).map { it.toInt() }

    fun forId(id: String): Int = palette[Math.floorMod(id.hashCode(), palette.size)]
}
