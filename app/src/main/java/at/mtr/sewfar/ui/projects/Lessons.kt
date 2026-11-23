package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.ProjectLessonEntity
import at.mtr.sewfar.ui.common.namesById

/** A lesson key: pattern link (null = project-level) and tag. */
typealias LessonKey = Pair<String?, String>

fun List<ProjectLessonEntity>.keys(): Set<LessonKey> = map { it.projectPatternId to it.tagId }.toSet()

/**
 * Lesson tag rows (SPEC 3.5): one per linked pattern, or one project-level row if there is none. Shows visible tags
 * plus hidden ones already set. [selected] holds (pattern link, tag) pairs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonTagRows(
    patterns: List<LinkedPattern>,
    tags: List<ListEntryRow>,
    selected: Set<LessonKey>,
    onToggle: (String?, String, Boolean) -> Unit,
) {
    val names = tags.namesById(ListKind.LESSON_TAG)
    // Project-level row: when there is no pattern, or when it already has lessons (set before a pattern was
    // linked), so they stay visible and editable (SPEC 3.5).
    val projectLevel = null to stringResource(R.string.completion_project_level)
    val rows: List<Pair<String?, String>> = patterns.map { it.link.id to (it.pattern?.name.orEmpty()) } +
        listOfNotNull(projectLevel.takeIf { patterns.isEmpty() || selected.any { it.first == null } })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (linkId, title) ->
            if (rows.size > 1 || linkId != null) Text(title, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.filter { !it.hidden || (linkId to it.id) in selected }.forEach { tag ->
                    val isSet = (linkId to tag.id) in selected
                    FilterChip(selected = isSet, onClick = { onToggle(linkId, tag.id, !isSet) }, label = { Text(names.getValue(tag.id)) })
                }
            }
        }
    }
}

/** 1–5 stars; tapping the current rating clears it. */
@Composable
fun RatingBar(rating: Int?, onRating: (Int?) -> Unit) {
    Row {
        (1..5).forEach { star ->
            val label = stringResource(R.string.rating_star, star)
            IconButton(onClick = { onRating(if (rating == star) null else star) }, modifier = Modifier.semantics { contentDescription = label }) {
                Icon(
                    if (rating != null && star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline, null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** "Last time" card on a "Sew again" copy (SPEC 3.7): rating, lessons per pattern, completion notes, link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LastTimeCard(source: SourceInfo, tags: List<ListEntryRow>, onOpen: (String) -> Unit) {
    val names = tags.namesById(ListKind.LESSON_TAG)
    val project = source.project
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.last_time), style = MaterialTheme.typography.titleMedium)
            project.rating?.let { r -> Text("★".repeat(r) + "☆".repeat(5 - r), color = MaterialTheme.colorScheme.secondary) }
            val byLink = source.lessons.groupBy { it.projectPatternId }
            byLink.forEach { (linkId, lessons) ->
                val title = source.patterns.firstOrNull { it.link.id == linkId }?.pattern?.name
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title != null) Text("$title:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    lessons.forEach { Text(names[it.tagId].orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            if (project.completionNotes.isNotBlank()) Text(project.completionNotes, style = MaterialTheme.typography.bodyMedium)
            if (source.lessons.isEmpty() && project.completionNotes.isBlank() && project.rating == null) {
                Text(stringResource(R.string.last_time_nothing), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { onOpen(project.id) }) { Text(stringResource(R.string.last_time_open)) }
        }
    }
}
