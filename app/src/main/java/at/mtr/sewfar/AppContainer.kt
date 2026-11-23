package at.mtr.sewfar

import android.content.Context
import at.mtr.sewfar.data.SettingsRepository

/** Creates and owns long-lived objects (settings; database and repositories follow in M1.2). */
class AppContainer(private val context: Context) {
    val settings: SettingsRepository by lazy { SettingsRepository.create(context) }

    val versionName: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
