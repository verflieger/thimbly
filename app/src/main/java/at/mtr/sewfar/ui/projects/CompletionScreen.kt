package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.BottomAppBar
import androidx.compose.foundation.layout.Spacer
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.photos.PhotoImage
import at.mtr.sewfar.ui.photos.rememberPickPhotos
import at.mtr.sewfar.ui.photos.rememberTakePhoto

/**
 * Completion sheet (SPEC 3.5), shown when a project becomes Done. Lessons, rating and notes are staged and only
 * written on Save; Skip (or system back) discards them. Photos are stored when taken and kept either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionScreen(viewModel: ProjectDetailViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val failedText = stringResource(R.string.photo_import_failed)
    var initialized by rememberSaveable { mutableStateOf(false) }
    // Staged values: lesson keys as "linkId|tagId" strings so they survive rotation.
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    // What the sheet started with: Save only writes what changed, so kept values are never wiped.
    var initialRating by rememberSaveable { mutableStateOf<Int?>(null) }
    var initialNotes by rememberSaveable { mutableStateOf("") }
    // Guards against a double tap (or tap + back) popping two screens.
    var closed by rememberSaveable { mutableStateOf(false) }
    val close = { if (!closed) { closed = true; onClose() } }

    LaunchedEffect(state.loaded) {
        val project = state.project
        if (state.loaded && project != null && !initialized) {
            selected = state.lessons.map { "${it.projectPatternId.orEmpty()}|${it.tagId}" }
            rating = project.rating
            notes = project.completionNotes
            initialRating = project.rating
            initialNotes = project.completionNotes
            initialized = true
        }
        if (state.loaded && project == null) close()
    }
    BackHandler(onBack = close)

    val add: suspend (List<() -> java.io.InputStream>) -> Unit = { sources ->
        if (viewModel.addPhotos(sources, PhotoTag.FINISHED).second > 0) messages.show(failedText)
    }
    val take = rememberTakePhoto { add(listOf(it)) }
    val pick = rememberPickPhotos(multiple = true) { add(it) }
    val keys: Set<LessonKey> = selected.map { it.substringBefore('|').ifEmpty { null } to it.substringAfter('|') }.toSet()

    fun save() {
        if (!initialized || closed) return
        val before = state.lessons.keys()
        (keys - before).forEach { (link, tag) -> viewModel.setLesson(link, tag, true) }
        (before - keys).forEach { (link, tag) -> viewModel.setLesson(link, tag, false) }
        if (rating != initialRating) viewModel.setRating(rating)
        if (notes.trim() != initialNotes.trim()) viewModel.setCompletionNotes(notes.trim())
        close()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.completion_title)) }) },
        bottomBar = {
            BottomAppBar {
                TextButton(onClick = close) { Text(stringResource(R.string.completion_skip)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, enabled = initialized, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.action_save)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.project?.let { Text(it.name, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall) }
            SectionLabel(stringResource(R.string.completion_photos))
            val finished = state.photos.filter { it.tag == PhotoTag.FINISHED }
            val px = with(LocalDensity.current) { 192.dp.roundToPx() }
            if (finished.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(finished, key = { it.id }) { PhotoImage(it.id, minEdgePx = px, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = take) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.action_take_photo), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = pick) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.action_pick_photos), Modifier.padding(start = 8.dp))
                }
            }
            SectionLabel(stringResource(R.string.completion_lessons))
            LessonTagRows(state.patterns, state.lessonTags, keys) { link, tag, set ->
                val key = "${link.orEmpty()}|$tag"
                selected = if (set) selected + key else selected - key
            }
            SectionLabel(stringResource(R.string.completion_rating))
            RatingBar(rating) { rating = it }
            OutlinedTextField(
                notes, { notes = it }, label = { Text(stringResource(R.string.completion_notes)) }, minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("completionNotes"),
            )
        }
    }
}
