package at.mtr.sewfar

import android.content.Context
import at.mtr.sewfar.data.ListRepository
import at.mtr.sewfar.data.PatternRepository
import at.mtr.sewfar.data.PersonRepository
import at.mtr.sewfar.data.ProjectRepository
import at.mtr.sewfar.data.SettingsRepository
import at.mtr.sewfar.data.TimeSource
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.seed.Seeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val lists: ListRepository by lazy { ListRepository(database, deleter, time) }

    val settings: SettingsRepository by lazy { SettingsRepository.create(context) }

    val versionName: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    /** Adds missing built-in list entries (SPEC 1). Cheap when nothing is missing; called on start. */
    fun seedInBackground() {
        appScope.launch { Seeder(database, time).seed() }
    }
}
