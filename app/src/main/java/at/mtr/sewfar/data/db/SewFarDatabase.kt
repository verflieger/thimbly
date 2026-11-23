package at.mtr.sewfar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import at.mtr.sewfar.BuildConfig

/**
 * SPEC 8. During development the version is raised on every schema change and debug builds drop the database
 * instead of migrating. The schema at the v1.0 release is frozen; from then on every change ships a migration.
 */
@Database(
    version = 1,
    entities = [
        PersonEntity::class, MeasurementTypeEntity::class, MeasurementSessionEntity::class, MeasurementValueEntity::class,
        PatternCategoryEntity::class, PatternEntity::class, PatternYardageEntity::class,
        ProjectEntity::class, ProjectPatternEntity::class, ProjectFabricEntity::class, LessonTagEntity::class,
        ProjectLessonEntity::class, LogEntryEntity::class, PhotoEntity::class,
        MachineEntity::class, MachineFieldEntity::class, PurposeEntity::class, SetupEntity::class,
        SetupValueEntity::class, SetupUseEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class SewFarDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun patternDao(): PatternDao
    abstract fun projectDao(): ProjectDao
    abstract fun listDao(): ListDao

    companion object {
        fun create(context: Context): SewFarDatabase =
            Room.databaseBuilder(context, SewFarDatabase::class.java, "sewfar.db")
                .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
                .build()
    }
}
