package at.mtr.sewfar.domain

import java.util.UUID

/** Record IDs (SPEC 1): random UUID strings, so data from several devices can be merged later. */
object Ids {
    fun random(): String = UUID.randomUUID().toString()
}
