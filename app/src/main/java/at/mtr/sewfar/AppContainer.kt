package at.mtr.sewfar

import android.content.Context
import at.mtr.sewfar.data.MachineRepository
import at.mtr.sewfar.data.PatternRepository
import at.mtr.sewfar.data.PersonRepository
import at.mtr.sewfar.data.PhotoRepository
import at.mtr.sewfar.data.ProjectRepository
import at.mtr.sewfar.data.SettingsRepository
import at.mtr.sewfar.data.TimeSource
import at.mtr.sewfar.data.backup.BackupRepository
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.photos.PhotoFiles
import at.mtr.sewfar.data.photos.PhotoLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Creates and owns long-lived objects (database, repositories, settings). */
class AppContainer(private val context: Context) {
    /** Lives as long as the app process; for work that must not be cancelled with a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val time = TimeSource()

    val database: SewFarDatabase by lazy { SewFarDatabase.create(context) }

    private val deleter: SoftDeleter by lazy { SoftDeleter(database, time) }

    val projects: ProjectRepository by lazy { ProjectRepository(database, deleter, time) }
    val patterns: PatternRepository by lazy { PatternRepository(database, deleter, time) }
    val persons: PersonRepository by lazy { PersonRepository(database, deleter, time) }
    val machines: MachineRepository by lazy { MachineRepository(database, deleter, time) }

    private val photoFiles: PhotoFiles by lazy { PhotoFiles(context.filesDir) }
    val photos: PhotoRepository by lazy { PhotoRepository(database, deleter, photoFiles, time, appScope) }
    val photoLoader: PhotoLoader by lazy { PhotoLoader(photoFiles) }

    val backup: BackupRepository by lazy {
        BackupRepository(
            database, photoFiles, context.filesDir, context.cacheDir, time, versionName,
        )
    }

    val settings: SettingsRepository by lazy { SettingsRepository.create(context) }

    val versionName: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    /**
     * Runs [block] in [appScope] and waits for it: the write finishes even if the calling screen goes away
     * (CLAUDE.md, "UI conventions").
     */
    /** Restores everything one deletion removed (SPEC 6); runs in [appScope]. The only way to undo a deletion. */
    suspend fun undo(token: DeletionToken) = runInApp { deleter.undo(token) }

    suspend fun <T> runInApp(block: suspend () -> T): T = appScope.async { block() }.await()

    /** Re-applies the weekly auto-backup schedule on start (KEEP: an existing schedule stays as it is). */
    fun keepAutoBackupScheduled() {
        appScope.launch {
            val folder = settings.autoBackupFolder.first()
            // A start-up chore must never crash the app (e.g. WorkManager not initialised in tests).
            runCatching { at.mtr.sewfar.data.backup.AutoBackupWorker.schedule(context, enabled = folder != null) }
                .onFailure { android.util.Log.w("SewFar", "Could not schedule automatic backup", it) }
        }
    }

    /**
     * On start: finish or roll back a photo swap an interrupted import left behind (SPEC 8.2), then remove files of
     * photos whose Undo window has passed and orphan files (SPEC 6). Order matters: the purge must see the final folder.
     */
    fun purgePhotoFilesInBackground() {
        appScope.launch {
            runCatching { backup.recoverPhotoSwap() }.onFailure { android.util.Log.e("SewFar", "Photo swap recovery failed", it) }
            photos.purgeFiles()
        }
    }
}
