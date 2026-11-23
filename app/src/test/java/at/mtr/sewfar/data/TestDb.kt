package at.mtr.sewfar.data

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.seed.Seeder
import at.mtr.sewfar.domain.Ids
import java.time.LocalDate

/** Clock under test control. */
class FakeTime(var now: Long = 1_000_000L, var day: LocalDate = LocalDate.of(2026, 11, 23)) : TimeSource() {
    override fun nowMillis() = now
    override fun today() = day
    fun advance(millis: Long = 1_000) { now += millis }
}

/** In-memory database with seeded lists and all repositories, plus raw helpers for tables without a repository yet. */
class TestDb {
    val time = FakeTime()
    val db: SewFarDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SewFarDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val deleter = SoftDeleter(db, time)
    val projects = ProjectRepository(db, deleter, time)
    val patterns = PatternRepository(db, deleter, time)
    val persons = PersonRepository(db, deleter, time)
    val lists = ListRepository(db, deleter, time)

    suspend fun seed() = Seeder(db, time).seed()

    fun close() = db.close()

    fun lessonTagId(key: String) = Ids.builtIn("lesson_tag", key)

    /** Number of rows matching [where]; [live] = only rows with deletedAt IS NULL. */
    fun count(table: String, where: String = "1", live: Boolean = true): Int {
        val liveFilter = if (live) " AND deletedAt IS NULL" else ""
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $table WHERE ($where)$liveFilter")).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun deletedAt(table: String, id: String): Long? =
        db.query(SimpleSQLiteQuery("SELECT deletedAt FROM $table WHERE id = ?", arrayOf(id))).use { cursor ->
            cursor.moveToFirst()
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }

    private fun exec(sql: String, vararg args: Any?) = db.openHelper.writableDatabase.execSQL(sql, args)

    fun insertPhoto(ownerType: String, ownerId: String, logEntryId: String? = null, tag: String? = "PROGRESS"): String {
        val id = Ids.random()
        exec(
            "INSERT INTO photo (id, ownerType, ownerId, logEntryId, tag, sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, 0, 1, 1)",
            id, ownerType, ownerId, logEntryId, tag,
        )
        return id
    }

    fun insertMachine(): String {
        val id = Ids.random()
        exec("INSERT INTO machine (id, name, brand, model, type, notes, createdAt, updatedAt) VALUES (?, 'M', '', '', 'OVERLOCKER', '', 1, 1)", id)
        return id
    }

    fun insertField(machineId: String): String {
        val id = Ids.random()
        exec("INSERT INTO machine_field (id, machineId, customName, sortOrder, hidden, createdAt, updatedAt) VALUES (?, ?, 'Diff', 0, 0, 1, 1)", id, machineId)
        return id
    }

    fun insertSetup(machineId: String, fieldId: String): String {
        val id = Ids.random()
        exec("INSERT INTO setup (id, machineId, title, fabric, notes, createdAt, updatedAt) VALUES (?, ?, 'Hem', '', '', 1, 1)", id, machineId)
        exec("INSERT INTO setup_value (id, setupId, fieldId, value, createdAt, updatedAt) VALUES (?, ?, ?, '1.3', 1, 1)", Ids.random(), id, fieldId)
        return id
    }

    fun insertSetupUse(setupId: String, projectId: String, logEntryId: String? = null): String {
        val id = Ids.random()
        exec(
            "INSERT INTO setup_use (id, setupId, projectId, logEntryId, snapshotAt, tweaked, snapshot, createdAt, updatedAt) VALUES (?, ?, ?, ?, 1, 0, '[]', 1, 1)",
            id, setupId, projectId, logEntryId,
        )
        return id
    }
}
