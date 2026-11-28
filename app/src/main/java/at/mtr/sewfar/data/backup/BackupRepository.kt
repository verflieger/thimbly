package at.mtr.sewfar.data.backup

import android.database.Cursor
import androidx.room.withTransaction
import at.mtr.sewfar.data.TimeSource
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.photos.PhotoFiles
import at.mtr.sewfar.data.syncToStorage
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class BackupException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_FORMAT, PROTOTYPE }
}

/** A validated backup, ready to replace the current data (SPEC 8.2, import steps 1–3). */
data class ImportPlan(val file: File, val format: Int, val exportedAt: String, val counts: Map<String, Int>)

/** The only entries ever written to disk, so a zip slip (`../` in a name) can't escape `photos.new` (SPEC 8.2). */
private val PhotoEntry = Regex("""^photos/[A-Za-z0-9-]+\.jpg$""")

/**
 * Backup zip (SPEC 8): `backup.json` with the live rows of every table plus `deletion_log`, and `photos/<id>.jpg` for
 * every live photo. Rows still inside the Undo window are left out, as if already purged. Tables are copied column by
 * column, so the format follows the Room schema without per-entity code.
 */
class BackupRepository(
    private val db: SewFarDatabase,
    private val photoFiles: PhotoFiles,
    private val filesDir: File,
    private val cacheDir: File,
    private val time: TimeSource,
    private val appVersion: String,
) {
    private val json = Json { prettyPrint = false }

    val safetyExport: File get() = File(filesDir, "import-safety.zip")
    private val photosNew get() = File(filesDir, "photos.new")
    private val photosOld get() = File(filesDir, "photos.old")
    private val swapMarker get() = File(filesDir, "photo-swap.marker")

    // ---- Export (SPEC 8.1) ----

    /** Writes a complete backup to [out]. Tables are read in one transaction, so the snapshot is consistent. */
    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        val tables = liveRows(
            db.withTransaction {
                val sql = db.openHelper.readableDatabase
                BackupTables.associateWith { table -> sql.query("SELECT * FROM $table").use { cursor -> JsonArray(cursor.rows()) } }
            },
        )
        val livePhotoIds = livePhotoIds(JsonObject(tables))
        val root = JsonObject(
            mapOf(
                "format" to JsonPrimitive(FORMAT),
                "appVersion" to JsonPrimitive(appVersion),
                "exportedAt" to JsonPrimitive(Instant.ofEpochMilli(time.nowMillis()).toString()),
                "tables" to JsonObject(tables),
            ),
        )
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.encodeToString(JsonObject.serializer(), root).toByteArray())
            zip.closeEntry()
            livePhotoIds.forEach { id ->
                val file = photoFiles.file(id)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("$PHOTOS_DIR$id.jpg"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    // ---- Import (SPEC 8.2) ----

    /**
     * Copies the zip from [input] to the cache and validates it (steps 1–2). Throws [BackupException] if it is not
     * importable; the current data is untouched.
     */
    suspend fun prepareImport(input: InputStream): ImportPlan = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "import-pending.zip")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        validate(file)
    }

    /**
     * "Undo last restore" (SPEC 8.2): imports a copy of the safety export, so the normal import makes a new safety
     * export of the current data first and nothing is lost (Undo works like a toggle).
     */
    suspend fun prepareSafetyRestore(): ImportPlan = withContext(Dispatchers.IO) {
        val copy = File(cacheDir, "import-pending.zip")
        safetyExport.copyTo(copy, overwrite = true)
        validate(copy)
    }

    private fun validate(file: File): ImportPlan {
        var root: JsonObject? = null
        try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    if (entry.name == JSON_ENTRY) {
                        // Two backup.json entries could validate one and import the other.
                        if (root != null) throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Duplicate $JSON_ENTRY")
                        root = json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Unreadable backup", e)
        }
        val backup = root ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP, "No $JSON_ENTRY")
        val format = backup["format"]?.jsonPrimitive?.intOrNull
            ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP, "No format")
        if (format > FORMAT) throw BackupException(BackupException.Reason.NEWER_FORMAT, "Format $format is newer than $FORMAT")
        val tables = backup["tables"] as? JsonObject ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP, "No tables")
        // Backups of the 1.0 prototype also say format 1 but have other tables; they can't be restored (SPEC 8.1).
        if (!BackupTables.containsAll(tables.keys)) {
            throw BackupException(BackupException.Reason.PROTOTYPE, "Unknown tables ${tables.keys - BackupTables.toSet()}")
        }
        // Photo rows without a file are accepted (the photo shows as missing), so one lost file never makes every
        // later backup unrestorable (SPEC 8.2).
        val counts = liveRows(tables).filterKeys { it in SoftDeleter.AllTables }.mapValues { (_, rows) -> rows.size }
        return ImportPlan(file, format, backup["exportedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(), counts)
    }

    /**
     * Replaces all data with [plan] (steps 4–7): safety export, photos to `photos.new`, tables in one transaction,
     * then the photo folder swap. A crash during the swap is resolved by [recoverPhotoSwap] on the next start.
     */
    suspend fun import(plan: ImportPlan) = withContext(Dispatchers.IO) {
        validate(plan.file) // the file could have changed since the confirm dialog
        val backup = readJson(plan.file)

        // 4. Safety export of the current data, kept until the next import.
        val safetyTemp = File(filesDir, "import-safety.zip.tmp")
        safetyTemp.outputStream().use { export(it) }
        safetyTemp.syncToStorage()
        safetyExport.delete()
        if (!safetyTemp.renameTo(safetyExport)) throw IOException("Could not store the safety export")

        // 5. Extract photos.
        photosNew.deleteRecursively()
        photosNew.mkdirs()
        ZipInputStream(plan.file.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory && PhotoEntry.matches(entry.name)) {
                    File(photosNew, entry.name.removePrefix(PHOTOS_DIR)).apply { outputStream().use { zip.copyTo(it) } }.syncToStorage()
                }
            }
        }

        // 6. Replace the tables. The marker lists the backup's live photo IDs: after a crash, recovery compares them
        // with the committed database to tell whether this import committed.
        // Older backups also hold deleted rows; they are dropped as the purge would have.
        val tables = JsonObject(liveRows(backup["tables"] as JsonObject))
        swapMarker.writeText(livePhotoIds(tables).sorted().joinToString("\n"))
        swapMarker.syncToStorage()
        // Photos, marker and safety export must be on storage before the commit makes the database depend on them.
        photosNew.syncToStorage()
        filesDir.syncToStorage()
        try {
            replaceTables(tables)
        } catch (e: Exception) {
            // Rolled back (e.g. a foreign key violation at commit): the old data and photos stay.
            photosNew.deleteRecursively()
            swapMarker.delete()
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Backup data is inconsistent", e)
        }

        // 7. Swap the photo folders.
        finishSwap()
        plan.file.takeIf { it.parentFile?.canonicalPath == cacheDir.canonicalPath }?.delete()
    }

    /**
     * Completes or rolls back a photo swap interrupted by a crash (SPEC 8.2 step 7). The import committed iff the
     * database's live photo IDs equal the backup's (listed in the marker): then finish the swap, otherwise drop
     * `photos.new` and put `photos.old` back if the old folder was already moved aside. (Equal ID sets also cover
     * an uncommitted import of identical photos: photo files are immutable, so either folder fits.)
     */
    suspend fun recoverPhotoSwap() = withContext(Dispatchers.IO) {
        if (!swapMarker.exists()) return@withContext
        val expected = swapMarker.readText().lines().filter { it.isNotBlank() }.toSet()
        val liveIds = db.openHelper.readableDatabase.query("SELECT id FROM photo WHERE deletedAt IS NULL").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        val committed = liveIds == expected
        if (committed) {
            finishSwap()
        } else {
            photosNew.deleteRecursively()
            photosOld.takeIf { it.exists() && !photoFiles.dir.exists() }?.renameTo(photoFiles.dir)
            swapMarker.delete()
        }
    }

    private fun finishSwap() {
        val photos = photoFiles.dir
        if (photosNew.exists()) {
            photosOld.deleteRecursively()
            if (photos.exists() && !photos.renameTo(photosOld)) throw IOException("Could not move photos aside")
            if (!photosNew.renameTo(photos)) throw IOException("Could not move imported photos in place")
            filesDir.syncToStorage() // the swap is on storage before the old photos go
        }
        photosOld.deleteRecursively()
        swapMarker.delete()
    }

    private suspend fun replaceTables(tables: JsonObject) {
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            // Rows arrive in any order; foreign keys are checked at commit (SPEC 8).
            sql.execSQL("PRAGMA defer_foreign_keys = ON")
            BackupTables.forEach { sql.execSQL("DELETE FROM $it") }
            for (table in BackupTables) {
                val columns = sql.query("PRAGMA table_info($table)").use { c ->
                    val name = c.getColumnIndexOrThrow("name")
                    buildSet { while (c.moveToNext()) add(c.getString(name)) }
                }
                tables[table]?.jsonArray?.forEach { row ->
                    val values = row.jsonObject.filterKeys { it in columns }
                    if (values.isEmpty()) return@forEach
                    val names = values.keys.toList()
                    sql.execSQL(
                        "INSERT INTO $table (${names.joinToString()}) VALUES (${names.joinToString { "?" }})",
                        names.map { values.getValue(it).toSqlValue() }.toTypedArray(),
                    )
                }
            }
            db.projectDao().foldLearnedIntoNotes(time.nowMillis())
        }
    }

    private fun livePhotoIds(tables: JsonObject): List<String> = tables["photo"]?.jsonArray.orEmpty().mapNotNull { row ->
        val obj = row.jsonObject
        obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { (obj["deletedAt"] ?: JsonNull) is JsonNull }
    }

    /**
     * What a backup holds: the live rows, minus rows whose cascade parent is not live, with detached references to
     * rows not kept cleared (SoftDeleter.Cascade / Detach, the same rules as the purge). Parents come first in
     * AllTables, so each table is filtered against its already filtered parents. `deletion_log` passes unchanged.
     */
    private fun liveRows(tables: Map<String, JsonElement>): Map<String, JsonArray> {
        val kept = linkedMapOf<String, JsonArray>()
        val keptIds = mutableMapOf<String, Set<String>>()
        fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        for (table in SoftDeleter.AllTables) {
            val rows = tables[table]?.jsonArray.orEmpty().map { it.jsonObject }
                .filter { (it["deletedAt"] ?: JsonNull) is JsonNull }
                .filter { row ->
                    SoftDeleter.Cascade.all { (parent, refs) ->
                        refs.filter { it.table == table && (it.ownerType == null || it.ownerType == row.text("ownerType")) }
                            .all { ref -> row.text(ref.column) in keptIds[parent].orEmpty() }
                    }
                }
                .map { row ->
                    SoftDeleter.Detach.entries.fold(row) { acc, (parent, refs) ->
                        refs.filter { it.table == table }.fold(acc) { r, ref ->
                            val value = r.text(ref.column)
                            if (value == null || value in keptIds[parent].orEmpty()) r else JsonObject(r + (ref.column to JsonNull))
                        }
                    }
                }
            kept[table] = JsonArray(rows)
            keptIds[table] = rows.mapNotNull { it.text("id") }.toSet()
        }
        kept[LogTable] = tables[LogTable]?.jsonArray ?: JsonArray(emptyList())
        return kept
    }

    private fun readJson(file: File): JsonObject = ZipInputStream(file.inputStream().buffered()).use { zip ->
        generateSequence { zip.nextEntry }.first { it.name == JSON_ENTRY }
        json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject
    }

    private fun Cursor.rows(): List<JsonElement> = buildList {
        while (moveToNext()) {
            add(JsonObject((0 until columnCount).associate { i -> getColumnName(i) to cell(i) }))
        }
    }

    private fun Cursor.cell(i: Int): JsonElement = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
        else -> JsonPrimitive(getString(i))
    }

    private fun JsonElement.toSqlValue(): Any? {
        if (this is JsonNull) return null
        val p = jsonPrimitive
        return when {
            p.isString -> p.content
            p.longOrNull != null -> p.longOrNull
            p.doubleOrNull != null -> p.doubleOrNull
            p.booleanOrNull != null -> if (p.booleanOrNull == true) 1L else 0L
            else -> p.content
        }
    }

    companion object {
        /**
         * Backup format version (SPEC 8.1); newer apps must read every older format. 1: v0.1. 2: `pattern.coverPhotoId`
         * (older backups restore without a pinned pattern cover, as missing columns keep their default). 3:
         * `pattern.garmentType` and the `project_tag` table (older backups restore without types and tags). 4: the `purchase` table (older backups restore without purchases). 5: `purchase.notes` (older purchases restore
         * without notes, from the column default).
         */
        const val FORMAT = 5
        private const val LogTable = "deletion_log"

        /** Tables in a backup, in restore order. */
        private val BackupTables = SoftDeleter.AllTables + LogTable
        const val JSON_ENTRY = "backup.json"
        const val PHOTOS_DIR = "photos/"

        /** `thimbly-backup-YYYY-MM-DD-HHmmss.zip` / `thimbly-auto-…` (SPEC 8.2). */
        fun fileName(prefix: String, at: java.time.LocalDateTime): String =
            "$prefix-" + at.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".zip"
    }
}
