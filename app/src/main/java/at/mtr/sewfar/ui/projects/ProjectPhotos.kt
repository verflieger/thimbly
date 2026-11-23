package at.mtr.sewfar.ui.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.label
import at.mtr.sewfar.ui.photos.PhotoImage
import at.mtr.sewfar.ui.photos.rememberPickPhotos
import at.mtr.sewfar.ui.photos.rememberTakePhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Project photos (SPEC 3.6): thumbnails with their tag, tap opens the viewer, long-press changes the tag, pins
 * the cover or deletes. New photos get a tag from the project status ([ProjectDetailViewModel.defaultPhotoTag]).
 */
@Composable
internal fun ProjectPhotos(state: ProjectDetailState, viewModel: ProjectDetailViewModel, onOpenPhoto: (String) -> Unit) {
    val messages = LocalMessages.current
    val failedText = stringResource(R.string.photo_import_failed)
    val add: suspend (List<() -> java.io.InputStream>) -> Unit = { sources ->
        if (viewModel.addPhotos(sources).second > 0) messages.show(failedText)
    }
    val takePhoto = rememberTakePhoto { source -> add(listOf(source)) }
    val pickPhotos = rememberPickPhotos(multiple = true) { sources -> add(sources) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.project_photos))
        if (state.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.photos, key = { it.id }) { photo ->
                    PhotoThumb(photo, isCover = photo.id == state.coverPhotoId, isPinned = photo.id == state.project?.coverPhotoId,
                        viewModel = viewModel, onOpen = { onOpenPhoto(photo.id) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = takePhoto) {
                Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
                Text(stringResource(R.string.action_take_photo), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = pickPhotos) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                Text(stringResource(R.string.action_pick_photos), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoThumb(
    photo: PhotoEntity,
    isCover: Boolean,
    isPinned: Boolean,
    viewModel: ProjectDetailViewModel,
    onOpen: () -> Unit,
) {
    val messages = LocalMessages.current
    val deletedText = stringResource(R.string.photo_deleted)
    val scope: CoroutineScope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val sizePx = with(LocalDensity.current) { 192.dp.roundToPx() }
    Box {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
        ) {
            PhotoImage(photo.id, minEdgePx = sizePx, modifier = Modifier.size(96.dp))
            Row(
                Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCover) Icon(Icons.Outlined.Star, stringResource(R.string.photo_cover), tint = Color.White, modifier = Modifier.size(12.dp))
                photo.tag?.let { Text(stringResource(it.label), color = Color.White, style = MaterialTheme.typography.labelSmall) }
            }
        }
        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
            PhotoTag.entries.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(stringResource(tag.label)) },
                    leadingIcon = { if (photo.tag == tag) Icon(Icons.Outlined.Check, null) },
                    onClick = { menuOpen = false; viewModel.setPhotoTag(photo.id, tag) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(if (isPinned) R.string.photo_unpin_cover else R.string.photo_set_cover)) },
                leadingIcon = { Icon(Icons.Outlined.Star, null) },
                onClick = { menuOpen = false; viewModel.setCover(if (isPinned) null else photo.id) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = {
                    menuOpen = false
                    scope.launch {
                        viewModel.deletePhoto(photo.id)?.let { token -> messages.showUndo(deletedText) { viewModel.undo(token) } }
                    }
                },
            )
        }
    }
}
