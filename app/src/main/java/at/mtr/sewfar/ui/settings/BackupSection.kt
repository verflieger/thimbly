package at.mtr.sewfar.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.mtr.sewfar.data.backup.AutoBackup
import at.mtr.sewfar.data.backup.AutoBackupWorker
import at.mtr.sewfar.data.backup.BackupException
import at.mtr.sewfar.data.backup.BackupRepository
import at.mtr.sewfar.data.backup.ImportPlan
import at.mtr.sewfar.data.backup.SafBackupFolder
import at.mtr.sewfar.ui.common.LocalMessages
import at.mtr.sewfar.ui.common.currentLocale
import at.mtr.sewfar.ui.common.shareFile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val Loading = "\u0000loading"
private const val NoFolder = "\u0000none"

/** Settings → Backup (SPEC 8.2, 9). */
@Composable
fun BackupSection(container: AppContainer) {
    val context = LocalContext.current
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val backup = container.backup
    val settings = container.settings
    // Loading sentinel, so the switch doesn't flicker off-then-on while the setting is read.
    val folderSetting by remember(settings) { settings.autoBackupFolder.map { it ?: NoFolder } }.collectAsStateWithLifecycle(initialValue = Loading)
    val folder = folderSetting.takeIf { it != Loading && it != NoFolder }
    val lastAt by settings.lastAutoBackupAt.collectAsStateWithLifecycle(initialValue = null)
    val lastError by settings.lastAutoBackupError.collectAsStateWithLifecycle(initialValue = null)
    val errorFolderText = stringResource(R.string.auto_backup_error_folder)
    val errorFailedText = stringResource(R.string.auto_backup_error_failed)
    var busy by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var safetyExists by remember { mutableStateOf(backup.safetyExport.exists()) }
    val savedText = stringResource(R.string.backup_saved)
    val restoredText = stringResource(R.string.backup_restored)
    val failedText = stringResource(R.string.backup_failed)
    val errorTexts = mapOf(
        BackupException.Reason.NOT_A_BACKUP to stringResource(R.string.backup_error_not_backup),
        BackupException.Reason.NEWER_FORMAT to stringResource(R.string.backup_error_newer),
        BackupException.Reason.PROTOTYPE to stringResource(R.string.backup_error_prototype),
    )
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(currentLocale)

    /** Runs [block] in the app scope with a busy indicator; reports errors as a snackbar. */
    fun work(successText: String?, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                container.runInApp { block() }
                successText?.let(messages::show)
            } catch (e: BackupException) {
                messages.show(errorTexts[e.reason] ?: failedText)
            } catch (e: Exception) {
                messages.show(failedText)
            } finally {
                busy = false
                safetyExists = backup.safetyExport.exists()
            }
        }
    }

    fun fileName() = BackupRepository.fileName("thimbly-backup", LocalDateTime.now())

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) work(savedText) { context.contentResolver.openOutputStream(uri)!!.use { backup.export(it) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) work(null) { plan = backup.prepareImport(context.contentResolver.openInputStream(uri)!!) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                container.runInApp {
                    SafBackupFolder.persist(context, uri)
                    val previous = settings.autoBackupFolder.first()
                    settings.setAutoBackupFolder(uri.toString())
                    previous?.let { Uri.parse(it) }?.takeIf { it != uri }?.let { SafBackupFolder.release(context, it) }
                }
                AutoBackupWorker.schedule(context, enabled = true)
            }
        }
    }

    Column {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_export)) },
            modifier = Modifier.clickable(enabled = !busy) { exportLauncher.launch(fileName()) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_share)) },
            modifier = Modifier.clickable(enabled = !busy) {
                work(null) { shareFile(context, fileName(), "application/zip") { backup.export(it) } }
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.backup_import)) },
            modifier = Modifier.clickable(enabled = !busy) { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
        )
        if (safetyExists) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_restore_safety)) },
                supportingContent = { Text(stringResource(R.string.backup_restore_safety_summary)) },
                modifier = Modifier.clickable(enabled = !busy) { work(null) { plan = backup.prepareSafetyRestore() } },
            )
        }
        if (folderSetting != Loading) ListItem(
            headlineContent = { Text(stringResource(R.string.auto_backup)) },
            supportingContent = {
                Column {
                    folder?.let { Text(stringResource(R.string.auto_backup_folder, Uri.parse(it).lastPathSegment?.substringAfter(':').orEmpty())) }
                    if (folder != null) {
                        Text(lastAt?.let { stringResource(R.string.auto_backup_last, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(formatter)) }
                            ?: stringResource(R.string.auto_backup_never))
                    }
                    lastError?.let { code ->
                        val reason = if (code == AutoBackup.ERROR_FOLDER) errorFolderText else errorFailedText
                        Text(stringResource(R.string.auto_backup_error, reason), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            trailingContent = {
                Switch(checked = folder != null, enabled = !busy, onCheckedChange = { on ->
                    if (on) {
                        folderLauncher.launch(null)
                    } else {
                        scope.launch {
                            container.runInApp {
                                folder?.let { SafBackupFolder.release(context, Uri.parse(it)) }
                                settings.setAutoBackupFolder(null)
                            }
                            AutoBackupWorker.schedule(context, enabled = false)
                        }
                    }
                })
            },
        )
        if (folder != null) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                TextButton(enabled = !busy, onClick = { AutoBackupWorker.runNow(context) }) { Text(stringResource(R.string.auto_backup_now)) }
                if (lastError != null) {
                    TextButton(enabled = !busy, onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.auto_backup_choose_folder)) }
                }
            }
        }
        Text(
            stringResource(R.string.backup_phone_switch), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    plan?.let { p ->
        AlertDialog(
            onDismissRequest = { plan = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(stringResource(R.string.backup_confirm_text, p.counts["project"] ?: 0, p.counts["pattern"] ?: 0, p.counts["person"] ?: 0, p.counts["photo"] ?: 0, p.counts["purchase"] ?: 0))
            },
            confirmButton = {
                TextButton(onClick = { plan = null; work(restoredText) { backup.import(p) } }) { Text(stringResource(R.string.backup_replace)) }
            },
            dismissButton = { TextButton(onClick = { plan = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
