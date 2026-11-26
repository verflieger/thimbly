package at.mtr.sewfar.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes [name] into the cache's share folder and opens the share sheet for it (backups SPEC 8.2, photos SPEC 3.5).
 * Older shares are removed after a day; a share still being read by another app stays intact.
 */
suspend fun shareFile(context: Context, name: String, mimeType: String, write: suspend (OutputStream) -> Unit) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }.forEach { it.delete() }
        File(dir, name).also { f -> f.outputStream().use { write(it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
    val send = Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    withContext(Dispatchers.Main) { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
