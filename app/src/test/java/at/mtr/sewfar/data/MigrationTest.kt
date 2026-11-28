package at.mtr.sewfar.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import at.mtr.sewfar.data.db.SewFarDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 7: every schema step migrates existing data; Room checks the result against the exported schema. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SewFarDatabase::class.java)

    @Test
    fun oneToTwoKeepsPatternsWithoutPinnedCover() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO pattern (id, name, designer, notes, createdAt, updatedAt) VALUES ('p1', 'Raglan', 'Studio', 'M', 1, 2)")
        }
        helper.runMigrationsAndValidate(DB, 2, true, *SewFarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, designer, notes, coverPhotoId FROM pattern WHERE id = 'p1'").use { c ->
                c.moveToFirst()
                assertEquals(listOf("Raglan", "Studio", "M", null), (0..3).map { c.getString(it) })
            }
        }
    }

    @Test
    fun twoToThreeAddsUntypedPatternsAndTheTagTable() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL("INSERT INTO pattern (id, name, designer, notes, createdAt, updatedAt) VALUES ('p1', 'Raglan', 'Studio', 'M', 1, 2)")
            db.execSQL("INSERT INTO project (id, name, status, size, fabric, notes, learned, lastActivityAt, createdAt, updatedAt) VALUES ('j1', 'Shirt', 'PLANNED', '', '', '', '', 1, 1, 1)")
        }
        helper.runMigrationsAndValidate(DB, 3, true, *SewFarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, garmentType FROM pattern WHERE id = 'p1'").use { c ->
                c.moveToFirst()
                assertEquals(listOf("Raglan", null), (0..1).map { c.getString(it) })
            }
            db.execSQL("INSERT INTO project_tag (id, projectId, name, sortOrder, createdAt, updatedAt) VALUES ('t1', 'j1', 'Gift', 0, 1, 1)")
            db.query("SELECT COUNT(*) FROM project_tag").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
    }

    @Test
    fun threeToFourAddsAnEmptyPurchaseTable() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO pattern (id, name, designer, notes, createdAt, updatedAt) VALUES ('p1', 'Raglan', 'Studio', 'M', 1, 2)")
        }
        helper.runMigrationsAndValidate(DB, 4, true, *SewFarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM pattern").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.execSQL("INSERT INTO purchase (id, name, category, priceCents, boughtOn, createdAt, updatedAt) VALUES ('b1', 'Jersey', 'FABRIC', 1850, '2026-11-28', 1, 1)")
            db.query("SELECT COUNT(*) FROM purchase").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
