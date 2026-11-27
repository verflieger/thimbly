package at.mtr.sewfar.data

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.photos.PhotoFiles
import at.mtr.sewfar.domain.Ids
import android.graphics.Bitmap
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** Clock under test control. */
class FakeTime(var now: Long = 1_000_000L, var day: LocalDate = LocalDate.of(2026, 11, 23)) : TimeSource() {
    override fun nowMillis() = now
    override fun today() = day
    fun advance(millis: Long = 1_000) { now += millis }
}

/** In-memory database with all repositories. */
class TestDb {
    val time = FakeTime()
    val db: SewFarDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SewFarDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val deleter = SoftDeleter(db, time)
    val projects = ProjectRepository(db, deleter, time)
    val patterns = PatternRepository(db, deleter, time)
    val persons = PersonRepository(db, deleter, time)
    val machines = MachineRepository(db, deleter, time)

    val photoFiles = PhotoFiles(File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "test-${Ids.random()}"))
    val photos = PhotoRepository(db, deleter, photoFiles, time, projects)

    val projectPage = ProjectPage(db, projects, persons, patterns, machines, photos)

    /** What the app runs after the Undo window: purge deleted rows, then files left without a row. */
    suspend fun purge() {
        deleter.purge()
        photos.purgeFiles()
    }

    fun close() {
        db.close()
        photoFiles.dir.deleteRecursively()
    }

    suspend fun ProjectRepository.observeAllOnce(): List<String> = observeAll().first().map { it.id }

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

    companion object {
        /** A JPEG of the given size as bytes, for feeding the photo pipeline. */
        fun jpeg(width: Int, height: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            return java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        }
    }
}
