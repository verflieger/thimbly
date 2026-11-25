package at.mtr.sewfar.data

import androidx.sqlite.db.SimpleSQLiteQuery
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.PhotoOwner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hand-written table registry in [SoftDeleter] (AllTables, Cascade, Detach) must match the real schema, or backup,
 * undo and purge silently skip a table or leave children behind (SPEC 6, 8).
 */
@RunWith(RobolectricTestRunner::class)
class TableRegistryTest {
    private lateinit var t: TestDb

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    private data class ForeignKey(val child: String, val column: String, val parent: String)

    private fun strings(query: String, column: Int = 0): List<String> = t.db.query(SimpleSQLiteQuery(query)).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(column)) }
    }

    private fun entityTables() = strings(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table')",
    ).toSet()

    private fun foreignKeys(): List<ForeignKey> = entityTables().flatMap { table ->
        t.db.query(SimpleSQLiteQuery("PRAGMA foreign_key_list($table)")).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(ForeignKey(table, c.getString(c.getColumnIndexOrThrow("from")), c.getString(c.getColumnIndexOrThrow("table"))))
                }
            }
        }
    }

    /** (parent table, referencing column) of every Cascade and Detach entry. */
    private val edges = (SoftDeleter.Cascade.toList() + SoftDeleter.Detach.toList())
        .flatMap { (parent, refs) -> refs.map { parent to it } }

    @Test
    fun everyTableIsRegistered() {
        assertEquals(entityTables(), SoftDeleter.AllTables.toSet() + "deletion_log")
        assertEquals("no duplicates", SoftDeleter.AllTables.size, SoftDeleter.AllTables.toSet().size)
    }

    @Test
    fun everyForeignKeyCascadesOrIsDetached() {
        for (fk in foreignKeys()) {
            assertTrue("$fk is neither in Cascade nor in Detach", edges.any { (parent, ref) ->
                parent == fk.parent && ref.table == fk.child && ref.column == fk.column && ref.ownerType == null
            })
        }
    }

    @Test
    fun everyPlainEdgeIsAForeignKey() {
        val fks = foreignKeys()
        for ((parent, ref) in edges.filter { it.second.ownerType == null }) {
            assertTrue("$parent -> $ref has no foreign key", fks.contains(ForeignKey(ref.table, ref.column, parent)))
        }
    }

    @Test
    fun parentsComeBeforeChildren() {
        val order = SoftDeleter.AllTables
        val pairs = foreignKeys().map { it.parent to it.child } + edges.map { (parent, ref) -> parent to ref.table }
        for ((parent, child) in pairs) {
            assertTrue("$parent must come before $child in AllTables", order.indexOf(parent) < order.indexOf(child))
        }
    }

    @Test
    fun everyPhotoOwnerCascadesToItsPhotos() {
        for (owner in PhotoOwner.entries) {
            val table = owner.name.lowercase()
            assertTrue("$table is not a table", table in SoftDeleter.AllTables)
            assertTrue("no photo cascade for $owner", SoftDeleter.Cascade[table].orEmpty().any {
                it.table == "photo" && it.column == "ownerId" && it.ownerType == owner.name
            })
        }
    }
}
