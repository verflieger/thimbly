package at.mtr.sewfar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SPEC 7. Schema version 1 is frozen since v0.1, version 2 adds `pattern.coverPhotoId`, version 3 `pattern.garmentType` and `project_tag`, version 4 `purchase` (`SchemaFreezeTest` pins v1's identity hash). Every schema change raises
 * the version, ships a migration in [MIGRATIONS] and a migration test; the database is never dropped.
 */
@Database(
    version = SewFarDatabase.VERSION,
    entities = [
        PersonEntity::class, MeasurementEntity::class, MeasurementValueEntity::class, PatternEntity::class, ProjectEntity::class, ProjectPatternEntity::class, ProjectTagEntity::class,
        TimeEntryEntity::class, PhotoEntity::class, MachineEntity::class, SetupEntity::class,
        SetupValueEntity::class, ProjectSetupEntity::class, PurchaseEntity::class, DeletionLogEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class SewFarDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun patternDao(): PatternDao
    abstract fun projectDao(): ProjectDao
    abstract fun photoDao(): PhotoDao
    abstract fun machineDao(): MachineDao
    abstract fun purchaseDao(): PurchaseDao

    companion object {
        /** Current schema version. Raise it with every schema change (and add a migration). */
        const val VERSION = 4

        /** Migrations between schema versions. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // 2: pinnable pattern cover (SPEC 3.5).
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) = db.execSQL("ALTER TABLE pattern ADD COLUMN coverPhotoId TEXT")
            },
            // 3: garment type on patterns, tags on projects (SPEC 3.2, 4.1).
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE pattern ADD COLUMN garmentType TEXT")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `project_tag` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                            "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                            "PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `project`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_project_tag_projectId_name` ON `project_tag` (`projectId`, `name`)")
                }
            },
            // 4: purchases log (SPEC 4.3).
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) = db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `purchase` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`priceCents` INTEGER NOT NULL, `boughtOn` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, PRIMARY KEY(`id`))",
                )
            },
        )

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
