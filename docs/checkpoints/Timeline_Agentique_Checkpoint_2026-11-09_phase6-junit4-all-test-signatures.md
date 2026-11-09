# Timeline Agentique checkpoint — JUnit 4 signatures across all tests

**Date:** 2026-11-09
**Source version:** `0.7.0-phase6-domain-engines`
**App versionCode:** `25`
**Status:** source correction and CI guard implemented; API 36 rerun pending

## Latest failure

The API 36 run showed two JUnit initialization failures:

1. `BundledSqliteCorpusIndexTest.embeddingBatchRejectsPassagesWhoseIdentityOrContentChangedAfterPackValidation()`
2. `RoomDomainWorkspaceTest.encryptedRoomWorkspaceSupportsHashVerifiedCasAndRejectsTampering()`

Both methods used expression-bodied `= runBlocking { ... }` declarations whose final expression returned the `Throwable` produced by `assertThrows`. JUnit 4 rejects non-void test methods before executing the class.

## Correction

- Explicit `: Unit` is now declared on every expression-bodied `@Test` coroutine function discovered in all module `src/test` and `src/androidTest` trees: **99 methods across 16 Kotlin files**.
- This includes test functions in authorization, model manager, corpus, retrieval and persistence. The two visible failures are fixed, and the same signature family is addressed proactively in the other coroutine-based tests.
- The earlier ModelManager cancellation test remains block-bodied and retains its cancellation/rollback assertions.
- CI's JUnit signature script now scans the entire unit and Android-test source tree instead of inspecting only `ModelManagerTest`.

## Validation

- Signature guard: 99/99 expression-bodied tests explicitly declare `Unit`; scanned 42 Kotlin test source files.
- Compiler probe verified a `: Unit = runBlocking` expression containing a non-Unit `assertThrows` result and a Boolean cleanup expression emits JVM descriptor `()V`.
- CI workflow invokes the global guard before test tasks.
- Android API 36 execution remains pending. A successful next run must show the retrieval and persistence instrumentation classes initialize and their test bodies finish, rather than report `initializationError`.

## Scope

Test source, CI guard and documentation only. No production source, authorization behavior, database schema, encryption configuration, dependencies, version name or versionCode changes.

## Resume instruction

Continue from this checkpoint and version 0.7.0. Do not restart the project. On the next CI result, first confirm `:core:retrieval:connectedDebugAndroidTest` and `:core:persistence:connectedDebugAndroidTest`; investigate any actual test assertion failures separately from runner-initialization errors.
