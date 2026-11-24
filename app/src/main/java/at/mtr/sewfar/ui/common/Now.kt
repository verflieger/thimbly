package at.mtr.sewfar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** The current time, updated every [tickMillis] while [ticking] (for running timers). */
@Composable
fun rememberNow(ticking: Boolean, tickMillis: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        while (ticking) {
            delay(tickMillis)
            now = System.currentTimeMillis()
        }
    }
    return now
}
