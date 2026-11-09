# Timeline Agentique checkpoint — Phase 6 API 26 BigInteger lint hotfix

**Date:** 2026-11-09
**Project:** 0.7.0, versionCode 25 (unchanged)
**Status:** source correction and local contracts complete; hosted CI pending.

## CI diagnosis

The latest CI run passed compilation and unit tests, then Android Lint failed in two places because `BigInteger.longValueExact()` requires API 31 while `minSdk` is 26:

- `feature/inheritance/InheritanceCaseEngine.kt`
- `feature/zakat/ZakatCalculator.kt`

A full Kotlin source scan found no other exact BigInteger conversion calls.

## Changes

- Added API-26-compatible shared `BigInteger.toLongExactCompat()` in `core:common`.
- Checks signed Long bounds before `longValue()`; overflow raises `ArithmeticException`.
- Connected inheritance and zakat modules to `core:common`.
- Added common unit tests for both boundaries and both overflow directions.
- Added maximum-value domain regressions in inheritance and Zakat.
- Added CI source guard and `:core:common:testDebugUnitTest`.

## Next

Rerun the previous hosted workflow. Confirm `:feature:inheritance:lint` and `:feature:zakat:lint`, then proceed to any subsequent lint findings. API 36 SQLCipher/Room instrumented tests and device checks remain separate gates.
