# Phase 6 CI hotfix — JUnit 4 signatures across unit and Android tests

**Date:** 2026-11-09
**Source version:** `0.7.0-phase6-domain-engines` (`versionCode = 25`)
**Scope:** test sources and CI guard only; no production runtime/schema/dependency changes

## 1. Hosted failure evidence

The API 36 connected test run reached the instrumentation runner. The app instrumentation target completed its 8 tests. The retrieval and persistence instrumentation tasks failed before test methods ran because AndroidJUnit4 could not instantiate classes containing methods with non-void JVM signatures:

- `core/retrieval/src/androidTest/.../BundledSqliteCorpusIndexTest.kt`: `embeddingBatchRejectsPassagesWhoseIdentityOrContentChangedAfterPackValidation()`
- `core/persistence/src/androidTest/.../RoomDomainWorkspaceTest.kt`: `encryptedRoomWorkspaceSupportsHashVerifiedCasAndRejectsTampering()`

Both methods were declared as expression-bodied Kotlin functions delegating to generic `runBlocking`. Their final lambda expression was `assertThrows(...)`, whose result is a Throwable. Kotlin therefore emitted a non-void method descriptor. JUnit 4 requires a public, zero-argument JVM `void` method annotated with `@Test`.

This is the same family as the earlier ModelManager initialization failure, but the earlier guard covered only one class. The previous hotfix was incomplete because it did not scan Android instrumentation test sources.

## 2. Complete source correction

- Added an explicit `: Unit` return type to **all 99 expression-bodied `@Test` methods** that use `runBlocking` across 16 Kotlin test files in `src/test` and `src/androidTest`.
- This includes both failing instrumentation methods, every other coroutine-based unit/instrumentation test, and the 21 expression-bodied tests in `ModelManagerTest`.
- The previously corrected `cancellationDuringImportRollsBackStagingAndRegistry()` remains block-bodied. Its cancellation, staging rollback and registry-cleanup assertions remain intact.
- No test assertion or scenario was removed. The JUnit return value was never part of test semantics; assertions still execute inside the coroutine, while explicit `Unit` guarantees a JVM `()V` descriptor.

## 3. Global CI regression guard

The existing `scripts/test-model-manager-junit-signatures.py` is broadened to scan all Kotlin test and Android-test source files under every module. Every expression-bodied `@Test` declaration must explicitly declare `: Unit` (or `: kotlin.Unit`). Block-bodied test functions remain allowed. It also retains the earlier cancellation rollback assertions as a dedicated regression check.

The workflow step is renamed to `Verify all JUnit 4 unit and Android test method signatures` and remains before the Gradle unit/instrumentation tasks.

## 4. Verification performed here

- Global signature source guard: passed; 99 expression-bodied test methods checked across 42 Kotlin test source files.
- Kotlin compiler probe: explicit `: Unit = runBlocking { ... }` compiled to JVM method descriptor `()V` for both representative non-Unit `assertThrows` results and non-Unit cleanup expressions.
- The exact two previously failing test declarations are now explicitly `: Unit`.
- Existing Phase 5C and Phase 6 source-contract scripts are rerun and recorded with this hotfix.
- `git diff --check`, workflow YAML parsing, ZIP integrity and patch-reproduction checks pass.

This authoring session did not execute the full Android Gradle build or API 36 connected tests. The next hosted run must confirm that retrieval/persistence test runners initialize and the SQLCipher/bundled-SQLite test bodies actually execute.

## 5. Acceptance status

This hotfix addresses the exact common cause reported by the latest CI log across all 99 coroutine-based expression-bodied tests, rather than fixing only the two visible classes. It does not establish Android instrumentation success until the new GitHub Actions run completes.

No app version metadata was changed. Continue with `0.7.0-phase6-domain-engines`, versionCode 25.
