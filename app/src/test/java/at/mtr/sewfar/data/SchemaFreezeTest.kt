package at.mtr.sewfar.data

import at.mtr.sewfar.data.db.SewFarDatabase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC 7: schema version 1 is frozen since v0.1. Room re-exports `schemas/.../1.json` on every build, so a schema
 * change without a version bump changes its identity hash and fails here. Changing the schema means: raise the version,
 * add a migration to [SewFarDatabase.MIGRATIONS] and a migration test; never edit this hash.
 */
class SchemaFreezeTest {
    private fun identityHash(version: Int): String {
        val file = File("schemas/at.mtr.sewfar.data.db.SewFarDatabase/$version.json")
        assertTrue("missing exported schema $file", file.exists())
        return Regex("\"identityHash\":\\s*\"([0-9a-f]+)\"").find(file.readText())!!.groupValues[1]
    }

    @Test
    fun schemaOneIsUnchanged() {
        assertEquals(FROZEN_V1, identityHash(1))
    }

    /** From v2 on: every version has an exported schema and a migration from the previous one. */
    @Test
    fun everyVersionStepHasSchemaAndMigration() {
        for (version in 2..SewFarDatabase.VERSION) {
            identityHash(version)
            assertTrue(
                "missing migration ${version - 1} -> $version in SewFarDatabase.MIGRATIONS",
                SewFarDatabase.MIGRATIONS.any { it.startVersion == version - 1 && it.endVersion == version },
            )
        }
    }

    private companion object {
        const val FROZEN_V1 = "d7c597a118b2e657bc80284513159d6d"
    }
}
