# Phase 4C CI hotfix — dense embedding builder unit-test fixtures

**Date:** 2026-11-08  
**Base:** `0.5.4-phase4c-sdk-metadata-validation-fix`  
**New source:** `0.5.5-phase4c-embedding-index-test-fixture-fix`

## CI observation

The supplied GitHub Actions log shows that SDK Platform 37.0 verification, AGP built-in Kotlin configuration, all production Kotlin compile tasks shown, AAR metadata validation, and `:core:persistence:kspDebugKotlin` progressed successfully. The task failed at `:core:retrieval:testDebugUnitTest`: 19 tests completed, three failed.

Failures:

- `completedBuildActivatesOnlyAfterAllBatchesAreWritten`: `IllegalArgumentException` before the builder could run its successful path.
- `modelFailureAbortsStagingAndNeverActivates`: expected an aborted staging generation, but none had been created.
- `oversizedPassageBatchIsRejectedAndAborted`: expected an aborted staging generation, but none had been created.

## Root cause

All three tests used `File.createTempFile("corpus-test", ".sqlite")`, which creates a zero-byte file. `LocalEmbeddingIndexBuilder.build()` checks that the installed corpus SQLite index is an existing, non-empty file **before** calling `beginGeneration()`. That is the correct production behavior: a missing/empty database must not start an index generation. The fixtures failed this guard, so the two expected aborts could not occur.

## Correction

- Added `createFixtureIndexFile()` that writes a non-empty test placeholder. `FixtureSource` is an in-memory fake and deliberately does not parse SQLite; production source implementations still open and validate the real signed pack index.
- Added an explicit diagnostic message for an absent/empty corpus index.
- Changed the wrong-dimension model test to assert the expected exception/message and assert `beginGeneration == 1`, `abortGeneration == 1`, and no activation.
- Changed the oversized-batch test to assert the exact source-limit error and the same begin/abort/no-activation lifecycle.
- Added a new empty-index regression test asserting failure occurs before any store mutation (`beginCount == 0`, `abortCount == 0`, no activation).

No production validation was weakened. The change is restricted to diagnostics and the test fixtures/assertions.

## Verification

- Targeted Kotlin compile/run harness compiled the actual `CorpusContracts.kt`, `LocalEmbeddingIndexBuilder.kt`, `QuantizedEmbeddingCodec.kt`, and updated `LocalEmbeddingIndexBuilderTest.kt` using a minimal JUnit API shim plus the installed coroutine runtime: **5/5 tests passed**.
- Project regression checks: built-in Kotlin 11/11; SDK 37.0 metadata 10/10; Gradle bootstrap; Phase 4 schema v5; Phase 4C integration; AGP DSL; dependency locking; Compose/API 37.0 and secret scan all passed. All 23 XML files parsed successfully.
- The authoring workspace does not contain a Gradle distribution or Android SDK, so the official Gradle/JUnit task and Android tests cannot be executed locally. The supplied GitHub runner already demonstrates Android source compilation, AAR metadata validation, and KSP task invocation prior to this unit-test failure.

## Next step

Push 0.5.5 and rerun the same CI workflow. Continue from this exact codebase; do not remove the non-empty index precondition to make a faulty test fixture pass.
