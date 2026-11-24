package at.mtr.sewfar.ui.photos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R

private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

/**
 * Full-screen viewer (SPEC 3.5): swipe between [photoIds], pinch or double-tap to zoom.
 * Panning is only active while zoomed, so swipes at normal size go to the pager.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(photoIds: List<String>, initialPhotoId: String?, onClose: () -> Unit) {
    if (photoIds.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(initialPage = photoIds.indexOf(initialPhotoId).coerceAtLeast(0)) { photoIds.size }
    // Always dark, like a photo app, regardless of the theme.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, modifier = Modifier.fillMaxSize().testTag("photoPager"), key = { photoIds[it] }) { page ->
            ZoomablePhoto(photoIds[page], active = pager.currentPage == page)
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = Color.White)
            }
            if (photoIds.size > 1) {
                Text(
                    stringResource(R.string.photo_counter, pager.currentPage + 1, photoIds.size),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(photoId: String, active: Boolean) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Leaving a page resets its zoom.
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, MaxZoom)
        offset = if (scale == 1f) Offset.Zero else offset + panChange
    }
    PhotoImage(
        photoId,
        minEdgePx = Int.MAX_VALUE, // full stored size (at most 2048 px)
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photoId) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = DoubleTapZoom
                    }
                })
            }
            .transformable(transform, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
