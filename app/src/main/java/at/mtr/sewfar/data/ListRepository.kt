package at.mtr.sewfar.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import kotlinx.coroutines.flow.Flow

/** The editable lists (SPEC 10, "Lists"). [usedIn] is an EXISTS-subquery body: live rows referencing `:id`. */
enum class ListKind(val table: String, val usedIn: String) {
    MEASUREMENT_TYPE("measurement_type", "SELECT 1 FROM measurement_value WHERE typeId = ? AND deletedAt IS NULL"),
    PATTERN_CATEGORY("pattern_category", "SELECT 1 FROM pattern WHERE categoryId = ? AND deletedAt IS NULL"),
    LESSON_TAG("lesson_tag", "SELECT 1 FROM project_lesson WHERE tagId = ? AND deletedAt IS NULL"),
    PURPOSE("purpose", "SELECT 1 FROM setup WHERE purposeId = ? AND deletedAt IS NULL"),
}

/**
 * Add, rename, hide, reorder and delete list entries. Built-in entries and entries in use can only be hidden
 * (SPEC 7); hidden entries stay visible where they are already used.
 */
class ListRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.listDao()

    private fun select(kind: ListKind, extra: String = "") =
        "SELECT id, builtinKey, customName, sortOrder, hidden FROM ${kind.table} WHERE deletedAt IS NULL $extra ORDER BY sortOrder"

    /** All live entries including hidden ones, in their order (for Settings). */
    fun observe(kind: ListKind): Flow<List<ListEntryRow>> = dao.observe(SimpleSQLiteQuery(select(kind)))

    /** Entries offered in pickers: live and not hidden. */
    fun observeSelectable(kind: ListKind): Flow<List<ListEntryRow>> =
        dao.observe(SimpleSQLiteQuery(select(kind, "AND hidden = 0")))

    suspend fun entries(kind: ListKind): List<ListEntryRow> = dao.query(SimpleSQLiteQuery(select(kind)))

    /** Adds a custom entry at the end and returns its ID. */
    suspend fun add(kind: ListKind, name: String): String = db.withTransaction {
        val id = Ids.random()
        val now = time.nowMillis()
        val order = dao.queryInt(SimpleSQLiteQuery("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM ${kind.table}"))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO ${kind.table} (id, builtinKey, customName, sortOrder, hidden, createdAt, updatedAt, deletedAt) " +
                "VALUES (?, NULL, ?, ?, 0, ?, ?, NULL)",
            arrayOf<Any>(id, name.trim(), order, now, now),
        )
        id
    }

    /** Sets the name shown in every language; for a built-in entry it overrides the translation (SPEC 1). */
    suspend fun rename(kind: ListKind, id: String, name: String) = db.withTransaction {
        exec("UPDATE ${kind.table} SET customName = ?, updatedAt = ? WHERE id = ?", name.trim(), time.nowMillis(), id)
    }

    suspend fun setHidden(kind: ListKind, id: String, hidden: Boolean) = db.withTransaction {
        exec("UPDATE ${kind.table} SET hidden = ?, updatedAt = ? WHERE id = ?", if (hidden) 1 else 0, time.nowMillis(), id)
    }

    /** Stores [orderedIds] as the new order. */
    suspend fun reorder(kind: ListKind, orderedIds: List<String>) = db.withTransaction {
        val now = time.nowMillis()
        orderedIds.forEachIndexed { index, id ->
            exec("UPDATE ${kind.table} SET sortOrder = ?, updatedAt = ? WHERE id = ? AND sortOrder != ?", index, now, id, index)
        }
    }

    suspend fun isInUse(kind: ListKind, id: String): Boolean =
        dao.queryBoolean(SimpleSQLiteQuery("SELECT EXISTS(${kind.usedIn})", arrayOf(id)))

    suspend fun delete(kind: ListKind, id: String): DeleteResult {
        val entry = entries(kind).firstOrNull { it.id == id } ?: return DeleteResult.InUse // already gone
        if (entry.builtinKey != null) return DeleteResult.BuiltIn
        if (isInUse(kind, id)) return DeleteResult.InUse
        val token = deleter.delete(kind.table, id) ?: return DeleteResult.InUse
        return DeleteResult.Deleted(token)
    }

    /** Raw write; call only inside [androidx.room.withTransaction] so it runs off the main thread and notifies observers. */
    private fun exec(sql: String, vararg args: Any) = db.openHelper.writableDatabase.execSQL(sql, args)
}
