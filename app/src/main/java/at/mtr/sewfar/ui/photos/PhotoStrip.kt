package at.mtr.sewfar.ui.photos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.SectionLabel
import kotlinx.coroutines.launch

/**
 * Photos of a project or pattern (SPEC 3.5): newest first, then the "Add photo" tile; tap opens, long-press deletes (with
 * Undo) and, if [setCover] is given, pins or unpins the cover. [coverId] gets a star, [pinnedId] is the pinned one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoStrip(
    photos: List<PhotoEntity>,
    add: suspend (List<ImageSource>) -> Int,
    delete: suspend (String) -> DeletionToken?,
    onOpen: (String) -> Unit,
    coverId: String? = null,
    pinnedId: String? = null,
    setCover: ((String?) -> Unit)? = null,
) {
    val messages = LocalMessages.current
    val failedText = stringResource(R.string.photo_import_failed)
    val deletedText = stringResource(R.string.photo_deleted)
    val scope = rememberCoroutineScope()
    val addAll: suspend (List<ImageSource>) -> Unit = { if (add(it) > 0) messages.show(failedText) }
    val takePhoto = rememberTakePhoto { addAll(listOf(it)) }
    val pickPhotos = rememberPickPhotos(multiple = true) { addAll(it) }
    val px = with(LocalDensity.current) { 192.dp.roundToPx() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(R.string.project_photos))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(photos, key = { it.id }) { photo ->
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = { onOpen(photo.id) }, onLongClick = { menuOpen = true })) {
                        PhotoImage(photo.id, minEdgePx = px, modifier = Modifier.size(96.dp))
                        if (photo.id == coverId) {
                            Icon(
                                Icons.Outlined.Star, stringResource(R.string.photo_cover), tint = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.5f)).padding(2.dp).size(14.dp),
                            )
                        }
                    }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (setCover != null) {
                            val pinned = photo.id == pinnedId
                            DropdownMenuItem(
                                text = { Text(stringResource(if (pinned) R.string.photo_unpin_cover else R.string.photo_set_cover)) },
                                leadingIcon = { Icon(Icons.Outlined.Star, null) },
                                onClick = { menuOpen = false; setCover(if (pinned) null else photo.id) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.photo_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { delete(photo.id)?.let { token -> messages.showUndo(deletedText, token) } }
                            },
                        )
                    }
                }
            }
            item { AddPhotoTile(takePhoto, pickPhotos) }
        }
    }
}

/** A read-only photo row (SPEC 4.1: photos of the projects using a pattern); tap opens one. */
@Composable
fun ReadOnlyPhotoRow(title: String, photoIds: List<String>, onOpen: (String) -> Unit) {
    val px = with(LocalDensity.current) { 192.dp.roundToPx() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photoIds, key = { it }) { id ->
                PhotoImage(id, minEdgePx = px, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpen(id) })
            }
        }
    }
}

/** Last tile of a photo row (SPEC 3.5): one "Add photo" button that asks for camera or gallery. */
@Composable
fun AddPhotoTile(takePhoto: () -> Unit, pickPhotos: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { open = true }.testTag("addPhoto"),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                val style = MaterialTheme.typography.labelMedium
                val fontScale = LocalDensity.current.fontScale
                // The label grows with the font size up to 130 %: beyond, "hinzufügen" would break mid-word in the 96 dp tile (SPEC 10).
                Text(
                    stringResource(R.string.photo_add),
                    style = style.copy(fontSize = style.fontSize * (minOf(fontScale, MaxLabelScale) / fontScale)),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_take_photo)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = { open = false; takePhoto() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_pick_photos)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = { open = false; pickPhotos() },
            )
        }
    }
}

private const val MaxLabelScale = 1.3f
