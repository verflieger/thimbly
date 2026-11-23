package at.mtr.sewfar.domain

import java.text.Normalizer
import java.util.Locale

/** Case- and accent-insensitive matching for search fields ("nah" finds "Näh", "strasse" finds "Straße"). */
object SearchText {
    private val marks = Regex("\\p{Mn}+")

    fun fold(text: String): String =
        marks.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "").replace("ß", "ss")

    /** True if every word of [query] occurs in one of [fields]. A blank query matches everything. */
    fun matches(query: String, fields: List<String>): Boolean {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = fields.joinToString("\n") { fold(it) }
        return words.all { it in haystack }
    }
}
