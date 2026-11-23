package at.mtr.sewfar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.mtr.sewfar.domain.ProjectStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences (SPEC 10). Not part of the backup (SPEC 9.1). */
class SettingsRepository(private val store: DataStore<Preferences>) {
    /** Material You colours from the wallpaper instead of SewFar's own scheme (SPEC 1). */
    val dynamicColor: Flow<Boolean> = store.data.map { it[DynamicColor] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DynamicColor] = enabled }
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

    companion object {
        private val CollapsedSections = stringSetPreferencesKey("collapsed_sections")
        private val DynamicColor = booleanPreferencesKey("dynamic_color")

        fun create(context: Context) = SettingsRepository(context.applicationContext.dataStore)
    }
}
