package at.mtr.sewfar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * SPEC 7. Schema version 1 is frozen once released (`SchemaFreezeTest` pins its identity hash). Every schema change
 * from then on raises the version, ships a migration in [MIGRATIONS] and a migration test; the database is never
 * dropped.
 */
@Database(
    version = SewFarDatabase.VERSION,
    entities = [
        PersonEntity::class, PatternEntity::class, ProjectEntity::class, ProjectPatternEntity::class,
        TimeEntryEntity::class, PhotoEntity::class, MachineEntity::class, SetupEntity::class,
        SetupValueEntity::class, ProjectSetupEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class SewFarDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun patternDao(): PatternDao
    abstract fun projectDao(): ProjectDao
    abstract fun photoDao(): PhotoDao
    abstract fun machineDao(): MachineDao

    companion object {
        /** Current schema version. Raise it with every schema change (and add a migration). */
        const val VERSION = 1

        /** Migrations between schema versions (none yet). */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        /**
         * The 1.0 prototype used `sewfar.db` with an incompatible schema that also had version 1; its data is not
         * carried over (decided 2026-11-24), so this database has its own file and the old one is removed.
         */
        private const val FILE = "sewfar2.db"
        private const val OLD_FILE = "sewfar.db"

        fun create(context: Context): SewFarDatabase {
            context.deleteDatabase(OLD_FILE)
            return Room.databaseBuilder(context, SewFarDatabase::class.java, FILE)
                .addMigrations(*MIGRATIONS)
                .build()
        }
    }
}
