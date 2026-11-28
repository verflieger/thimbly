package at.mtr.sewfar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.mtr.sewfar.domain.ProjectStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences (SPEC 9). Not part of the backup (SPEC 8.1). */
class SettingsRepository(private val store: DataStore<Preferences>) {
    /** Material You colours from the wallpaper instead of SewFar's own scheme (SPEC 1). */
    val dynamicColor: Flow<Boolean> = store.data.map { it[DynamicColor] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DynamicColor] = enabled }
    }

    /** New photos at up to 4096 px instead of 2048 px (SPEC 3.5). */
    val finePhotos: Flow<Boolean> = store.data.map { it[FinePhotos] ?: false }

    suspend fun setFinePhotos(enabled: Boolean) {
        store.edit { it[FinePhotos] = enabled }
    }

    /** Timer and times on projects (SPEC 3.3, 9); off for people who don't track time. */
    val trackTime: Flow<Boolean> = store.data.map { it[TrackTime] ?: true }

    suspend fun setTrackTime(enabled: Boolean) {
        store.edit { it[TrackTime] = enabled }
    }

    /** Collapsed sections of the project list (SPEC 3.1); Done is collapsed until the user opens it. */
    val collapsedSections: Flow<Set<ProjectStatus>> = store.data.map { prefs ->
        prefs[CollapsedSections]?.mapNotNull { name -> ProjectStatus.entries.firstOrNull { it.name == name } }?.toSet()
            ?: setOf(ProjectStatus.DONE)
    }

    suspend fun setSectionCollapsed(status: ProjectStatus, collapsed: Boolean) {
        store.edit { prefs ->
            val current = prefs[CollapsedSections] ?: setOf(ProjectStatus.DONE.name)
            prefs[CollapsedSections] = if (collapsed) current + status.name else current - status.name
        }
    }

    /** ISO 4217 code of the purchases' currency (SPEC 4.3, 9); null = the phone's currency. */
    val currency: Flow<String?> = store.data.map { it[Currency] }

    suspend fun setCurrency(code: String) {
        store.edit { it[Currency] = code }
    }

    // ---- Automatic backup (SPEC 8.2) ----

    /** SAF tree URI of the chosen folder; null = automatic backup off. */
    val autoBackupFolder: Flow<String?> = store.data.map { it[AutoBackupFolder] }
    val lastAutoBackupAt: Flow<Long?> = store.data.map { it[LastAutoBackupAt] }
    val lastAutoBackupError: Flow<String?> = store.data.map { it[LastAutoBackupError] }

    suspend fun setAutoBackupFolder(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(AutoBackupFolder) else prefs[AutoBackupFolder] = uri
            prefs.remove(LastAutoBackupError)
        }
    }

    /** A success updates the last run and clears the error; a failure keeps the last success and stores the error. */
    suspend fun recordAutoBackup(success: Boolean, error: String?, at: Long) {
        store.edit { prefs ->
            if (success) {
                prefs[LastAutoBackupAt] = at
                prefs.remove(LastAutoBackupError)
            } else {
                prefs[LastAutoBackupError] = error.orEmpty()
            }
        }
    }

    companion object {
        private val AutoBackupFolder = stringPreferencesKey("auto_backup_folder")
        private val LastAutoBackupAt = longPreferencesKey("last_auto_backup_at")
        private val LastAutoBackupError = stringPreferencesKey("last_auto_backup_error")
        private val Currency = stringPreferencesKey("currency")
        private val CollapsedSections = stringSetPreferencesKey("collapsed_sections")
        private val DynamicColor = booleanPreferencesKey("dynamic_color")
        private val FinePhotos = booleanPreferencesKey("fine_photos")
        private val TrackTime = booleanPreferencesKey("track_time")

        fun create(context: Context) = SettingsRepository(context.applicationContext.dataStore)
    }
}
