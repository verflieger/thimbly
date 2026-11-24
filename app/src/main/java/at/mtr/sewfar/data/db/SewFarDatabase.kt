package at.mtr.sewfar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * SPEC 8. Schema version 1 is frozen with the v1.0 release (`SchemaFreezeTest` pins its identity hash). Every
 * schema change from now on raises the version, ships a migration in [MIGRATIONS] and a migration test; the
 * database is never dropped.
 */
@Database(
    version = SewFarDatabase.VERSION,
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
    abstract fun photoDao(): PhotoDao
    abstract fun machineDao(): MachineDao

    companion object {
        /** Current schema version. Raise it with every schema change (and add a migration). */
        const val VERSION = 1

        /** Migrations between schema versions (none yet: version 1 is the first released schema). */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        fun create(context: Context): SewFarDatabase =
            Room.databaseBuilder(context, SewFarDatabase::class.java, "sewfar.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
