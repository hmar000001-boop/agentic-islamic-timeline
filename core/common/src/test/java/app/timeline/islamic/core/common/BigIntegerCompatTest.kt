package app.timeline.islamic.core.common

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BigIntegerCompatTest {
    @Test fun convertsSignedLongBoundariesExactly() {
        assertEquals(Long.MIN_VALUE, BigInteger.valueOf(Long.MIN_VALUE).toLongExactCompat())
        assertEquals(Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE).toLongExactCompat())
        assertEquals(0L, BigInteger.ZERO.toLongExactCompat())
    }

    @Test fun rejectsPositiveOverflowInsteadOfTruncating() {
        assertArithmeticException(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))
    }

    @Test fun rejectsNegativeOverflowInsteadOfTruncating() {
        assertArithmeticException(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE))
    }

    private fun assertArithmeticException(value: BigInteger) {
        try {
            value.toLongExactCompat()
            fail("Expected ArithmeticException for $value")
        } catch (_: ArithmeticException) {
            // Expected: conversion must never silently truncate.
        }
    }
}
