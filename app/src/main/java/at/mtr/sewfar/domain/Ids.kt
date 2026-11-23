package at.mtr.sewfar.domain

import java.security.MessageDigest
import java.util.UUID

/** Record IDs (SPEC 1): random UUIDs for user data, name-based UUIDv5 for built-in seed rows. */
object Ids {
    /**
     * Namespace for [builtIn]. **Never change it**: seed IDs must be identical on every device and in every backup,
     * so a future sync can match built-in rows.
     */
    private val Namespace: UUID = UUID.fromString("6f1c2b8e-3d4a-5c6b-9e7f-a1b2c3d4e5f6")

    fun random(): String = UUID.randomUUID().toString()

    /** Deterministic ID of a built-in row: UUIDv5 of `"<table>:<builtinKey>"` (SPEC 1). */
    fun builtIn(table: String, builtinKey: String): String = uuid5(Namespace, "$table:$builtinKey").toString()

    /** RFC 4122 name-based UUID, version 5 (SHA-1). */
    internal fun uuid5(namespace: UUID, name: String): UUID {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(namespace.toBytes())
        sha1.update(name.toByteArray(Charsets.UTF_8))
        val hash = sha1.digest()
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte() // version 5
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte() // IETF variant
        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) msb = (msb shl 8) or (hash[i].toLong() and 0xff)
        for (i in 8 until 16) lsb = (lsb shl 8) or (hash[i].toLong() and 0xff)
        return UUID(msb, lsb)
    }

    private fun UUID.toBytes(): ByteArray {
        val bytes = ByteArray(16)
        for (i in 0 until 8) bytes[i] = (mostSignificantBits ushr (8 * (7 - i))).toByte()
        for (i in 0 until 8) bytes[8 + i] = (leastSignificantBits ushr (8 * (7 - i))).toByte()
        return bytes
    }
}
