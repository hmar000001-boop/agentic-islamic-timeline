package app.timeline.islamic.core.common

import java.time.Instant

fun interface Clock {
    fun now(): Instant
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}

@JvmInline
value class StableId(val value: String) {
    init { require(value.isNotBlank()) { "StableId must not be blank" } }
}
