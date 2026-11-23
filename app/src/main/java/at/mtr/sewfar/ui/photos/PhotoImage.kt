package at.mtr.sewfar.ui.photos

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import at.mtr.sewfar.R
import at.mtr.sewfar.data.photos.PhotoLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Provided once at the app root (MainActivity). */
val LocalPhotoLoader = staticCompositionLocalOf<PhotoLoader> { error("No PhotoLoader provided") }

private sealed interface Loaded {
    data object Loading : Loaded
    data object Missing : Loaded
    data class Ready(val bitmap: Bitmap) : Loaded
}

/** A stored photo, decoded off the main thread at roughly [minEdgePx] (thumbnails) or full size (viewer). */
@Composable
fun PhotoImage(
    photoId: String,
    minEdgePx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = stringResource(R.string.photo_content_description),
) {
    val loader = LocalPhotoLoader.current
    val state by produceState<Loaded>(Loaded.Loading, photoId, minEdgePx) {
        value = withContext(Dispatchers.IO) { loader.load(photoId, minEdgePx) }?.let(Loaded::Ready) ?: Loaded.Missing
    }
    when (val s = state) {
        is Loaded.Ready -> Image(s.bitmap.asImageBitmap(), contentDescription, modifier, contentScale = contentScale)
        Loaded.Missing -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.BrokenImage, stringResource(R.string.photo_load_failed),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Loaded.Loading -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
