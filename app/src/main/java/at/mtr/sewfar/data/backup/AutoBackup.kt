package at.mtr.sewfar.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.mtr.sewfar.SewFarApp
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** The folder auto-backups go to; abstracted so the pruning rules can be tested without SAF. */
interface BackupFolder {
    /** False if the folder is gone or the permission was revoked. */
    fun isAccessible(): Boolean
    fun list(): List<String>
    fun create(name: String): OutputStream
    fun delete(name: String)
}

/** Weekly automatic backup (SPEC 9.2): write, then keep the newest [Keep] `sewfar-auto-*.zip` files. */
class AutoBackup(private val backups: BackupRepository) {
    /** Runs one backup into [folder]; throws [IOException] with a user-facing reason on failure. */
    suspend fun run(folder: BackupFolder, now: LocalDateTime) {
        if (!folder.isAccessible()) throw IOException(FOLDER_UNAVAILABLE)
        val name = BackupRepository.fileName(Prefix, now)
        var complete = false
        try {
            folder.create(name).use { backups.export(it) }
            complete = true
        } finally {
            // A failed or cancelled write must not stay behind and count as one of the kept backups.
            if (!complete) runCatching { folder.delete(name) }
        }
        prune(folder)
    }

    /** Deletes older auto-backups beyond the newest [Keep]; other files are never touched. */
    fun prune(folder: BackupFolder) {
        folder.list().filter { AutoName.matches(it) }.sortedDescending().drop(Keep).forEach(folder::delete)
    }

    companion object {
        const val Prefix = "sewfar-auto"
        const val Keep = 5
        const val FOLDER_UNAVAILABLE = "folder_unavailable"

        /** Stored error codes, shown translated in Settings. */
        const val ERROR_FOLDER = "folder_unavailable"
        const val ERROR_FAILED = "failed"
        private val AutoName = Regex("""^sewfar-auto-\d{4}-\d{2}-\d{2}-\d{6}\.zip$""")
    }
}

/** A SAF tree (folder picked by the user, persisted permission). */
class SafBackupFolder(private val context: Context, private val treeUri: Uri) : BackupFolder {
    private val resolver get() = context.contentResolver
    private val folderDoc: Uri get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    override fun isAccessible(): Boolean {
        val granted = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }
        if (!granted) return false
        return runCatching { resolver.query(folderDoc, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.count > 0 } }
            .getOrNull() == true
    }

    private fun children(): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(uri, cols, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1) to c.getString(0)) }
        }.orEmpty()
    }

    override fun list(): List<String> = children().map { it.first }

    override fun create(name: String): OutputStream {
        val doc = DocumentsContract.createDocument(resolver, folderDoc, "application/zip", name) ?: throw IOException("Could not create $name")
        return resolver.openOutputStream(doc) ?: throw IOException("Could not open $name")
    }

    override fun delete(name: String) {
        children().filter { it.first == name }.forEach { (_, id) ->
            DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
        }
    }

    companion object {
        /** Gives up access to a folder that is no longer used. */
        fun release(context: Context, treeUri: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }

        /** Keeps access to the picked folder across restarts. */
        fun persist(context: Context, treeUri: Uri) {
            context.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
}

/** WorkManager entry point; records success or the error in the settings (shown in Settings → Backup). */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SewFarApp).container
        val settings = container.settings
        val folder = settings.autoBackupFolder.first() ?: return Result.success()
        return try {
            AutoBackup(container.backup).run(SafBackupFolder(applicationContext, Uri.parse(folder)), LocalDateTime.now())
            settings.recordAutoBackup(success = true, error = null, at = container.time.nowMillis())
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // stopped by WorkManager: not a failure
        } catch (e: Exception) {
            val code = if (e.message == AutoBackup.FOLDER_UNAVAILABLE) AutoBackup.ERROR_FOLDER else AutoBackup.ERROR_FAILED
            settings.recordAutoBackup(success = false, error = code, at = container.time.nowMillis())
            Result.success() // no retry storm: the error is shown and the next weekly run tries again
        }
    }

    companion object {
        private const val Weekly = "auto-backup-weekly"
        private const val Now = "auto-backup-now"

        /** Weekly, no idle or charging constraint (SPEC 9.2). */
        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (enabled) {
                work.enqueueUniquePeriodicWork(Weekly, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build())
            } else {
                work.cancelUniqueWork(Weekly)
            }
        }

        fun runNow(context: Context) {
            // KEEP: a second tap must not cancel a backup that is being written.
            WorkManager.getInstance(context).enqueueUniqueWork(Now, androidx.work.ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        }
    }
}
