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

    private companion object {
        const val DB = "migration-test"
    }
}
