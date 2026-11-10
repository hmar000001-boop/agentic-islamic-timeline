# Phase 6 CI hotfix — API 26 BigInteger conversion

**Date:** 2026-11-09
**Project version:** 0.7.0 (`versionCode = 25`, unchanged)
**Failure:** Android Lint `NewApi` in `feature:inheritance` and `feature:zakat`.

## Diagnosis

`java.math.BigInteger.longValueExact()` is not available on the app's minimum Android API 26 (Lint reports API 31). The latest CI log identifies exactly two production calls: the inheritance remainder and Zakat amount conversion. A repository-wide source scan found no other `longValueExact`, `intValueExact`, `shortValueExact` or `byteValueExact` calls.

## Fix

Added `core:common` extension `BigInteger.toLongExactCompat()`. It checks the value against the signed `Long` minimum and maximum, throws `ArithmeticException` when outside the range, and only then uses Kotlin `toLong()` (mapped to `BigInteger.longValue()`), which is available on API 26. Both feature modules now depend on `core:common` and use the shared helper. This preserves exact-conversion semantics rather than truncating.

Added tests for minimum/maximum valid values, positive/negative overflow and maximum-value domain calculations in inheritance and Zakat. Added CI execution of `:core:common:testDebugUnitTest` and `scripts/test-api26-biginteger-conversion.py`.

## Validation boundary

The source guard, syntax checks and locally available Kotlin tests were executed. A full Android Gradle/Lint run is not claimed here; the next hosted CI run must confirm both feature lint tasks pass. No minSdk change, database migration, encryption change or fiqh ruleset change was made.
