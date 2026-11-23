package at.mtr.sewfar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.ui.SewFarRoot
import at.mtr.sewfar.ui.photos.LocalPhotoLoader
import at.mtr.sewfar.ui.theme.SewFarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SewFarApp).container
        setContent {
            val dynamicColor by container.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
            SewFarTheme(dynamicColor = dynamicColor) {
                CompositionLocalProvider(LocalPhotoLoader provides container.photoLoader) {
                    SewFarRoot(container)
                }
            }
        }
    }
}
