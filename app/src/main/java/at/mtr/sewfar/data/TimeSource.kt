package at.mtr.sewfar.data

import java.time.Clock
import java.time.LocalDate

/** Current time for timestamps and "today" (replaceable in tests). */
open class TimeSource(private val clock: Clock = Clock.systemDefaultZone()) {
    open fun nowMillis(): Long = clock.millis()
    open fun today(): LocalDate = LocalDate.now(clock)
}
