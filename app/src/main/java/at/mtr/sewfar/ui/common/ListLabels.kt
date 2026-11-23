package at.mtr.sewfar.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.domain.PatternFormat

/** Name of a list entry: the user's name if set, else the built-in name in the current language (SPEC 1). */
fun ListEntryRow.resolveName(kind: ListKind, getString: (Int) -> String): String =
    customName ?: builtinKey?.let { BuiltInLabels.of(kind, it) }?.let(getString) ?: builtinKey.orEmpty()

@Composable
@ReadOnlyComposable
fun ListEntryRow.displayName(kind: ListKind): String {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    return resolveName(kind) { resources.getString(it) }
}

/** Lookup of display names by ID, for rows that reference list entries. */
@Composable
@ReadOnlyComposable
fun List<ListEntryRow>.namesById(kind: ListKind): Map<String, String> = associate { it.id to it.displayName(kind) }

@get:StringRes
val PatternFormat.label: Int
    get() = when (this) {
        PatternFormat.PAPER -> R.string.format_paper
        PatternFormat.PDF -> R.string.format_pdf
        PatternFormat.MAGAZINE -> R.string.format_magazine
        PatternFormat.SELF_DRAFTED -> R.string.format_self_drafted
    }
