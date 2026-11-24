package at.mtr.sewfar.data.db

import androidx.room.withTransaction
import at.mtr.sewfar.data.TimeSource

/**
 * Identifies one deletion. Every row it removed (the root and its cascade) carries [stamp] as `deletedAt`,
 * and no other deletion uses the same stamp, so [SoftDeleter.undo] restores exactly these rows (SPEC 6).
 */
@JvmInline
value class DeletionToken(val stamp: Long)

/** Soft delete with cascade and undo (SPEC 6). Rows are never removed from the database. */
class SoftDeleter(private val db: SewFarDatabase, private val time: TimeSource) {
    private var lastStamp = 0L

    /** Child rows that a deletion of a row in the key table also deletes (SPEC 6, cascade table). */
    private val cascade: Map<String, List<Child>> = mapOf(
        "project" to listOf(
            Child("project_pattern", "projectId"),
            Child("project_setup", "projectId"),
            Child("time_entry", "projectId"),
            Child("photo", "ownerId", ownerType = "PROJECT"),
        ),
        "pattern" to listOf(Child("project_pattern", "patternId"), Child("photo", "ownerId", ownerType = "PATTERN")),
        "machine" to listOf(Child("setup", "machineId")),
        "setup" to listOf(Child("setup_value", "setupId"), Child("project_setup", "setupId")),
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
            // stamped so far in the parent table.
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
        /** Every table; a new table must be added here (backup, undo) and to the cascade if it has a parent. */
        val AllTables = listOf(
            "person", "pattern", "project", "project_pattern", "time_entry", "photo",
            "machine", "setup", "setup_value", "project_setup",
        )
    }
}
