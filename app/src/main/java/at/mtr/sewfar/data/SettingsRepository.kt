package at.mtr.sewfar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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

    companion object {
        private val DynamicColor = booleanPreferencesKey("dynamic_color")

        fun create(context: Context) = SettingsRepository(context.applicationContext.dataStore)
    }
}
