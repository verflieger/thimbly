package at.mtr.sewfar.data.db

import androidx.room.withTransaction
import at.mtr.sewfar.data.TimeSource

/**
 * Identifies one deletion. Every row it removed (the root and its cascade) carries [stamp] as `deletedAt`,
 * and no other deletion uses the same stamp, so [SoftDeleter.undo] restores exactly these rows (SPEC 6).
 */
@JvmInline
value class DeletionToken(val stamp: Long)

/**
 * Delete with cascade, undo, and purge (SPEC 6). A deletion only stamps `deletedAt`, so Undo can restore it; once the
 * Undo window has passed, [purge] removes the rows for good and records them in `deletion_log`.
 *
 * @param onDelete called after every deletion (the app schedules a purge for when the Undo window has passed).
 */
class SoftDeleter(
    private val db: SewFarDatabase,
    private val time: TimeSource,
    private val onDelete: () -> Unit = {},
) {
    private var lastStamp = 0L

    /**
     * Soft-deletes the live row [id] of [table] and its cascade in one transaction.
     * Returns null if the row doesn't exist or is already deleted.
     */
    suspend fun delete(table: String, id: String): DeletionToken? {
        require(table in AllTables) { "Unknown table $table" }
        val token = db.withTransaction {
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
                for (child in Cascade[parent].orEmpty()) {
                    sql.execSQL(
                        "UPDATE ${child.table} SET deletedAt = ?, updatedAt = ? " +
                            "WHERE deletedAt IS NULL AND ${child.filter}${child.column} IN " +
                            "(SELECT id FROM $parent WHERE deletedAt = ?)",
                        arrayOf(stamp, stamp, stamp),
                    )
                    queue.addLast(child.table)
                }
            }
            DeletionToken(stamp)
        }
        if (token != null) onDelete()
        return token
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

    /**
     * Removes rows deleted more than [UndoWindowMillis] ago for good, with every row that still refers to them
     * through the cascade (e.g. a link restored by its own Undo after its project was deleted), and logs each one in
     * `deletion_log`. References that don't cascade ([Detach]) are cleared. Photo files of removed rows become
     * orphans, which `PhotoRepository.purgeFiles` removes. Returns the number of rows removed.
     */
    suspend fun purge(): Int = db.withTransaction {
        val now = time.nowMillis()
        val cutoff = now - UndoWindowMillis
        val sql = db.openHelper.writableDatabase
        fun ids(query: String, args: Array<Any>): Set<String> = sql.query(query, args).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        // AllTables runs parents first (TableRegistryTest), so a parent's set is complete before its children's.
        val doomed = linkedMapOf<String, Set<String>>()
        for (table in AllTables) {
            val own = ids("SELECT id FROM $table WHERE deletedAt IS NOT NULL AND deletedAt < ?", arrayOf(cutoff))
            val inherited = Cascade.flatMap { (parent, children) ->
                children.filter { it.table == table }.flatMap { child ->
                    doomed[parent].orEmpty().chunked(MaxArgs).flatMap { chunk ->
                        ids(
                            "SELECT id FROM $table WHERE ${child.filter}${child.column} IN (${chunk.joinToString { "?" }})",
                            chunk.toTypedArray(),
                        )
                    }
                }
            }
            doomed[table] = own + inherited
        }
        for ((parent, refs) in Detach) for (ref in refs) for (chunk in doomed[parent].orEmpty().chunked(MaxArgs)) {
            sql.execSQL(
                "UPDATE ${ref.table} SET ${ref.column} = NULL, updatedAt = ? WHERE ${ref.column} IN (${chunk.joinToString { "?" }})",
                (listOf<Any>(now) + chunk).toTypedArray(),
            )
        }
        for (table in AllTables.reversed()) for (chunk in doomed.getValue(table).chunked(MaxArgs)) {
            val marks = chunk.joinToString { "?" }
            // A row that never had a deletedAt (an inherited child) is logged with the purge time.
            sql.execSQL(
                "INSERT OR REPLACE INTO deletion_log (rowId, tableName, deletedAt) " +
                    "SELECT id, '$table', COALESCE(deletedAt, ?) FROM $table WHERE id IN ($marks)",
                (listOf<Any>(now) + chunk).toTypedArray(),
            )
            sql.execSQL("DELETE FROM $table WHERE id IN ($marks)", chunk.toTypedArray())
        }
        doomed.values.sumOf { it.size }
    }

    /** Strictly increasing within the process, so two deletions never share a stamp. */
    @Synchronized
    private fun nextStamp(): Long {
        val stamp = maxOf(time.nowMillis(), lastStamp + 1)
        lastStamp = stamp
        return stamp
    }

    /** A column of [table] that refers to a parent row; [ownerType] narrows a polymorphic reference (photos). */
    data class Ref(val table: String, val column: String, val ownerType: String? = null) {
        internal val filter get() = ownerType?.let { "ownerType = '$it' AND " } ?: ""
    }

    companion object {
        /** Longer than any Undo snackbar, so a restored row (and its photo file) is still there. */
        const val UndoWindowMillis = 60_000L

        /** SQLite's limit on bound arguments is 999. */
        private const val MaxArgs = 500

        /**
         * Every soft-deletable table, parents before children (backup restore and the purge rely on that order).
         * A new table goes here (backup, undo, purge) and into [Cascade] or [Detach] for each foreign key;
         * `TableRegistryTest` checks both against the schema.
         */
        val AllTables = listOf(
            "person", "measurement", "measurement_value", "pattern", "project", "project_pattern", "project_tag", "time_entry", "photo",
            "machine", "setup", "setup_value", "project_setup", "purchase",
        )

        /** Child rows that a deletion of a row in the key table also deletes (SPEC 6, cascade table). */
        val Cascade: Map<String, List<Ref>> = mapOf(
            "person" to listOf(Ref("measurement", "personId")),
            "measurement" to listOf(Ref("measurement_value", "measurementId")),
            "project" to listOf(
                Ref("project_pattern", "projectId"),
                Ref("project_tag", "projectId"),
                Ref("project_setup", "projectId"),
                Ref("time_entry", "projectId"),
                Ref("photo", "ownerId", ownerType = "PROJECT"),
            ),
            "pattern" to listOf(Ref("project_pattern", "patternId"), Ref("photo", "ownerId", ownerType = "PATTERN")),
            "machine" to listOf(Ref("setup", "machineId")),
            "setup" to listOf(Ref("setup_value", "setupId"), Ref("project_setup", "setupId")),
        )

        /** References that survive their parent's deletion; the purge clears them (a deleted person's projects stay). */
        val Detach: Map<String, List<Ref>> = mapOf(
            "person" to listOf(Ref("project", "personId")),
        )
    }
}
