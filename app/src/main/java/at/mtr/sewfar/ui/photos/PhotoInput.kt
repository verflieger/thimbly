package at.mtr.sewfar.ui.photos

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.launch

/** Opens an input stream on a picked or captured image; called several times by the pipeline. */
typealias ImageSource = () -> InputStream

private fun Context.source(uri: Uri): ImageSource = { requireNotNull(contentResolver.openInputStream(uri)) { "Cannot open $uri" } }

/**
 * Camera via the system camera app (SPEC 3.6): no CAMERA permission. The photo goes to a temp file in the cache,
 * which is deleted after [onPhoto] returns or when capture is cancelled. Returns the launcher.
 */
@Composable
fun rememberTakePhoto(onPhoto: suspend (ImageSource) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Survives the activity being recreated while the camera app is in front.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingPath?.let(::File) ?: return@rememberLauncherForActivityResult
        pendingPath = null
        if (success && file.length() > 0) {
            scope.launch {
                try {
                    onPhoto { file.inputStream() }
                } finally {
                    file.delete()
                }
            }
        } else {
            file.delete()
        }
    }
    return {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        pendingPath = file.path
        launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.camera", file))
    }
}

/** Gallery via the system Photo Picker (no storage permission). [multiple] allows picking several at once. */
@Composable
fun rememberPickPhotos(multiple: Boolean, onPhotos: suspend (List<ImageSource>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    if (multiple) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) scope.launch { onPhotos(uris.map(context::source)) }
        }
        return { launcher.launch(request) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { onPhotos(listOf(context.source(uri))) }
    }
    return { launcher.launch(request) }
}
