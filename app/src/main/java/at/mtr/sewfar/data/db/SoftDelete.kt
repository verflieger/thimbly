package at.mtr.sewfar.data.db

import androidx.room.withTransaction
import at.mtr.sewfar.data.TimeSource

/**
 * Identifies one deletion. Every row it removed (the root and its cascade) carries [stamp] as `deletedAt`,
 * and no other deletion uses the same stamp, so [SoftDeleter.undo] restores exactly these rows (SPEC 7).
 */
@JvmInline
value class DeletionToken(val stamp: Long)

/** Outcome of deleting an item that may be referenced elsewhere (SPEC 7). */
sealed interface DeleteResult {
    data class Deleted(val token: DeletionToken) : DeleteResult

    /** Still referenced: patterns, persons and machines must be archived, list entries hidden. */
    data object InUse : DeleteResult

    /** Built-in list entries can only be hidden. */
    data object BuiltIn : DeleteResult
}

/** Soft delete with cascade and undo (SPEC 7). Rows are never removed from the database. */
class SoftDeleter(private val db: SewFarDatabase, private val time: TimeSource) {
    private var lastStamp = 0L

    /** Child rows that a deletion of a row in the key table also deletes (SPEC 7, cascade table). */
    private val cascade: Map<String, List<Child>> = mapOf(
        "project" to listOf(
            Child("project_pattern", "projectId"),
            Child("project_fabric", "projectId"),
            Child("project_lesson", "projectId"),
            Child("log_entry", "projectId"),
            Child("setup_use", "projectId"),
            Child("photo", "ownerId", ownerType = "PROJECT"),
        ),
        "log_entry" to listOf(Child("photo", "logEntryId"), Child("setup_use", "logEntryId")),
        "project_pattern" to listOf(Child("project_lesson", "projectPatternId")),
        "person" to listOf(Child("measurement_session", "personId"), Child("photo", "ownerId", ownerType = "PERSON")),
        "measurement_session" to listOf(Child("measurement_value", "sessionId")),
        "pattern" to listOf(Child("pattern_yardage", "patternId"), Child("photo", "ownerId", ownerType = "PATTERN")),
        "machine" to listOf(
            Child("machine_field", "machineId"),
            Child("setup", "machineId"),
            Child("photo", "ownerId", ownerType = "MACHINE"),
        ),
        // Not setup_use: snapshots outlive their setup (SPEC 6.4).
        "setup" to listOf(Child("setup_value", "setupId")),
    )

    private data class Child(val table: String, val column: String, val ownerType: String? = null)

    /**
     * Soft-deletes the live row [id] of [table] and its cascade in one transaction.
     * Returns null if the row doesn't exist or is already deleted.
     */
    suspend fun delete(table: String, id: String): DeletionToken? {
        require(table in AllTables) { "Unknown table $table" }
        return db.withTransaction {
            val stamp = nextStamp()
            val sql = db.openHelper.writableDatabase
            val changed = sql.compileStatement(
                "UPDATE $table SET deletedAt = ?, updatedAt = ? WHERE id = ? AND deletedAt IS NULL",
            ).use { statement ->
                statement.bindLong(1, stamp)
                statement.bindLong(2, stamp)
                statement.bindString(3, id)
                statement.executeUpdateDelete()
            }
            if (changed == 0) return@withTransaction null
            // Walk the cascade breadth-first: each step deletes the live children of all rows this deletion has
            // stamped so far in the parent table. A table may be reached twice (project_lesson); that is harmless.
            val queue = ArrayDeque(listOf(table))
            while (queue.isNotEmpty()) {
                val parent = queue.removeFirst()
                for (child in cascade[parent].orEmpty()) {
                    val ownerFilter = child.ownerType?.let { "ownerType = '$it' AND " } ?: ""
                    sql.execSQL(
                        "UPDATE ${child.table} SET deletedAt = ?, updatedAt = ? " +
                            "WHERE deletedAt IS NULL AND $ownerFilter${child.column} IN " +
                            "(SELECT id FROM $parent WHERE deletedAt = ?)",
                        arrayOf(stamp, stamp, stamp),
                    )
                    queue.addLast(child.table)
                }
            }
            DeletionToken(stamp)
        }
    }

    /** Restores every row the deletion [token] removed; rows deleted earlier or later stay deleted. */
    suspend fun undo(token: DeletionToken) {
        db.withTransaction {
            val now = time.nowMillis()
            val sql = db.openHelper.writableDatabase
            for (table in AllTables) {
                sql.execSQL(
                    "UPDATE $table SET deletedAt = NULL, updatedAt = ? WHERE deletedAt = ?",
                    arrayOf(now, token.stamp),
                )
            }
        }
    }

    /** Strictly increasing within the process, so two deletions never share a stamp. */
    @Synchronized
    private fun nextStamp(): Long {
        val stamp = maxOf(time.nowMillis(), lastStamp + 1)
        lastStamp = stamp
        return stamp
    }

    companion object {
        val AllTables = listOf(
            "person", "measurement_type", "measurement_session", "measurement_value",
            "pattern_category", "pattern", "pattern_yardage",
            "project", "project_pattern", "project_fabric", "lesson_tag", "project_lesson", "log_entry", "photo",
            "machine", "machine_field", "purpose", "setup", "setup_value", "setup_use",
        )
    }
}
