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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Person avatar: initial on the person's colour (SPEC 5.1). */
@Composable
fun Avatar(name: String, color: Int, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = (size.value * 0.45f).sp),
        )
    }
}
