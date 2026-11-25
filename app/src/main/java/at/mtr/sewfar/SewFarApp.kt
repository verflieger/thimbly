package at.mtr.sewfar

import android.app.Application

/**
 * Process-wide entry point. Holds the [AppContainer] (manual dependency injection):
 * screens get their dependencies from `(application as SewFarApp).container`.
 */
class SewFarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.purgeInBackground()
        container.keepAutoBackupScheduled()
    }
}
