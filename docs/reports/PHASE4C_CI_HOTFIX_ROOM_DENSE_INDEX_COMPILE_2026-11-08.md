# Phase 4C CI hotfix — Room dense-index compile contract

**Date:** 2026-11-08
**Base:** `0.5.5-phase4c-embedding-index-test-fixture-fix`
**New source:** `0.5.6-phase4c-room-dense-index-compile-fix`
**Project:** Timeline Agentique Android source

## 1. CI evidence

The latest GitHub Actions run confirms:

- SDK Platform 37.0 installation and metadata verification completed.
- AGP built-in Kotlin configuration and AAR metadata checks completed.
- `:core:agent-api`, `:core:common`, `:core:knowledge-api`, `:core:localization`, `:core:security`, `:core:agent-runtime`, `:core:authorization`, `:core:timeline-api`, `:core:retrieval`, `:core:corpus`, `:feature:canvas`, `:feature:timeline`, and `:feature:corpus-management` production Kotlin compile tasks completed.
- `:core:retrieval:testDebugUnitTest`, `:core:corpus:testDebugUnitTest`, `:core:knowledge-api:testDebugUnitTest`, `:core:timeline-api:testDebugUnitTest`, `:core:agent-runtime:testDebugUnitTest`, `:core:authorization:testDebugUnitTest` and `:feature:timeline:testDebugUnitTest` completed. Canvas and corpus-management unit tests reported `NO-SOURCE`.
- `:core:persistence:kspDebugKotlin` completed.
- The run then failed specifically compiling `:core:persistence`.

## 2. Exact compiler failures

1. `RoomDenseEmbeddingIndexStore.activateGeneration` was expression-bodied. The final `dao.deleteRetiredGenerations(...)` call returns `Int`, which became the function result and did not satisfy `DenseEmbeddingIndexStore.activateGeneration(...): Unit`.
2. `vectorsFor` compares `generation.state` with `STATE_ACTIVE`, but the companion object declared only `STATE_STAGING`.

These are production-source compile defects, not test fixture failures.

## 3. Fix

- Changed `activateGeneration` to a block-bodied `override suspend fun ...: Unit`.
- Preserved the whole retire-old → compare-and-set activate-staging → delete-retired sequence inside one `database.withTransaction` block.
- Added `STATE_ACTIVE = "ACTIVE"`; this matches the literal state used by `DenseIndexDao.activateStagingGeneration` and `DenseIndexDao.activeGeneration` queries.
- Added an eight-check source contract verifier and executed it in the CI preflight.
- Existing Android Room instrumentation coverage already verifies initial activation, replacing an active generation, active vector visibility, removal and abandoned staging recovery.

## 4. Files changed

- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexStore.kt`
- `scripts/verify-room-dense-index-contract.py`
- `.github/workflows/android-ci.yml`
- `app/build.gradle.kts` (versionCode/versionName only)
- `README.md`
- `CHANGELOG.md`
- this report and its checkpoint

No schema version, migration, authorization, corpus pack, retrieval policy, UI or model behavior changed.

## 5. Verification actually performed

- `scripts/verify-room-dense-index-contract.py`: **8/8 passed**.
- Kotlin 1.9 compile harness compiled the actual production `RoomDenseEmbeddingIndexStore.kt` against minimal API shims for Room transactions, DAO entities and domain contracts: **passed**. This validates Kotlin override return compatibility and state symbol resolution, but is not a substitute for Kotlin 2.4/AGP/Room compilation.
- Existing local regression guards are to be rerun against the complete archive before delivery.
- Full GitHub Actions Gradle build, KSP, Room/SQLCipher instrumentation and API 36 emulator tests have not been rerun after this correction.

## 6. Next step

Push version 0.5.6 and rerun the same CI workflow. Confirm `:core:persistence:compileDebugKotlin` and then the remaining unit, assemble, lint, instrumentation, dependency-report and SBOM stages. Diagnose the next actual failure without restarting the project.
