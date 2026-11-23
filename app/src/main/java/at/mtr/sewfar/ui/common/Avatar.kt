package at.mtr.sewfar.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import at.mtr.sewfar.ui.photos.PhotoImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Person avatar (SPEC 5.1): the photo if there is one, else the initial on the person's colour. */
@Composable
fun Avatar(name: String, color: Int, size: Dp = 24.dp, modifier: Modifier = Modifier, photoId: String? = null) {
    if (photoId != null) {
        val px = with(LocalDensity.current) { (size * 2).roundToPx() }
        PhotoImage(photoId, minEdgePx = px, modifier = modifier.size(size).clip(CircleShape), contentDescription = name)
        return
    }
    Box(modifier.size(size).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = (size.value * 0.45f).sp),
        )
    }
}
