package app.timeline.islamic.core.common

import java.math.BigInteger

/**
 * Exact BigInteger-to-Long conversion available on Android API 26+.
 *
 * The JDK exact-conversion convenience method is only available from Android API 31.
 * This helper preserves its important semantics (throw instead of truncating) by validating
 * the signed Long range before calling the older, API-safe longValue().
 */
fun BigInteger.toLongExactCompat(): Long {
    val minimum = BigInteger.valueOf(Long.MIN_VALUE)
    val maximum = BigInteger.valueOf(Long.MAX_VALUE)
    if (compareTo(minimum) < 0 || compareTo(maximum) > 0) {
        throw ArithmeticException("BigInteger out of long range")
    }
    return toLong()
}
