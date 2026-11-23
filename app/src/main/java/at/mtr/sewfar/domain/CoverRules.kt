package at.mtr.sewfar.domain

/** A project photo as far as the cover rule cares (SPEC 3.6). */
data class CoverCandidate(val id: String, val tag: PhotoTag?, val sortOrder: Int)

object CoverRules {
    /**
     * The pinned photo if it is still among [photos], otherwise the first Finished photo, otherwise the first photo
     * of any tag (by sort order). Null if there are no photos.
     */
    fun cover(photos: List<CoverCandidate>, pinnedId: String?): String? {
        if (pinnedId != null && photos.any { it.id == pinnedId }) return pinnedId
        val ordered = photos.sortedBy { it.sortOrder }
        return (ordered.firstOrNull { it.tag == PhotoTag.FINISHED } ?: ordered.firstOrNull())?.id
    }
}
