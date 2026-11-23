package at.mtr.sewfar.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.ui.photos.ImageSource
import at.mtr.sewfar.ui.photos.PhotoImage
import at.mtr.sewfar.ui.photos.rememberPickPhotos
import at.mtr.sewfar.ui.photos.rememberTakePhoto

/**
 * The single photo of a pattern, person or machine (SPEC 3.6). Tap opens a menu: view, take, pick, remove.
 * [onSet] stores a new photo (replacing the old one); it returns false if the image could not be read.
 */
@Composable
fun EntityPhoto(
    photoId: String?,
    onSet: suspend (ImageSource) -> Boolean,
    onRemove: () -> Unit,
    onOpen: (String) -> Unit,
    size: Dp = 96.dp,
) {
    val messages = LocalMessages.current
    val failedText = stringResource(R.string.photo_import_failed)
    val set: suspend (ImageSource) -> Unit = { if (!onSet(it)) messages.show(failedText) }
    val take = rememberTakePhoto { set(it) }
    val pick = rememberPickPhotos(multiple = false) { sources -> sources.firstOrNull()?.let { set(it) } }
    var menu by remember { mutableStateOf(false) }
    val px = with(LocalDensity.current) { (size * 2).roundToPx() }
    Box {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (photoId != null) PhotoImage(photoId, minEdgePx = px, modifier = Modifier.size(size))
            else Icon(Icons.Outlined.AddAPhoto, stringResource(R.string.photo_set), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            if (photoId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_content_description)) },
                    leadingIcon = { Icon(Icons.Outlined.ZoomIn, null) },
                    onClick = { menu = false; onOpen(photoId) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_take_photo)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = { menu = false; take() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_pick_photos)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = { menu = false; pick() },
            )
            if (photoId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_remove)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}
