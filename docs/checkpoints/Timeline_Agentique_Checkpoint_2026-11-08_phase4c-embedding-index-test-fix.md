# Timeline Agentique — Phase 4C embedding-index test-fix checkpoint

**Date:** 2026-11-08  
**Checkpoint:** `PHASE4C-CI-EMBEDDING-TESTS-003`  
**Source:** `0.5.5-phase4c-embedding-index-test-fixture-fix`  
**Project:** `/mnt/data/phase4-0.5.4-current`  
**Library project:** `/Timeline_Agentique/`

## Resume rule

Continue from version 0.5.5. Preserve all Phase 1–4C source, all Timeline/Canvas behavior, agent runtime/authorization, Room/SQLCipher, signed corpus lifecycle, FTS5/RRF retrieval, corpus management UI and encrypted embedding-index foundation. Do not restart from a new template.

## Latest CI evidence

The API 37.0 SDK installation and verifier succeeded. AGP built-in Kotlin configuration succeeded. The shown app/core/feature production Kotlin compile tasks succeeded, `:app:checkOfflineSideloadDebugAarMetadata` succeeded, and `:core:persistence:kspDebugKotlin` started. The unit-test invocation failed with exactly three tests in `:core:retrieval:testDebugUnitTest`.

## Root cause and fix

`LocalEmbeddingIndexBuilder.build()` correctly requires the installed corpus index file to exist and be non-empty before `beginGeneration()`. The three failing tests used zero-byte files returned by `File.createTempFile()`. Those fixtures were invalid for the production contract; the failure/oversized tests therefore never entered the staging/abort path.

0.5.5 changes the three fixture files to non-empty placeholders (the fake in-memory `FixtureSource` does not read database contents), asserts precise failure messages/lifecycle counts, adds a regression test proving empty indexes fail before store mutation, and adds an actionable error message in production builder validation. It does not weaken production index checks.

## Exact modified files

- `core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/LocalEmbeddingIndexBuilder.kt`
- `core/retrieval/src/test/java/app/timeline/islamic/core/retrieval/LocalEmbeddingIndexBuilderTest.kt`
- `app/build.gradle.kts` (versionCode 9 and versionName only)
- `README.md`, `CHANGELOG.md`
- `docs/reports/PHASE4C_CI_HOTFIX_EMBEDDING_INDEX_TESTS_2026-11-08.md`

## Next action

Run the focused `:core:retrieval:testDebugUnitTest` and then the complete CI workflow on a network-enabled JDK 17 + Android SDK 37.0 runner. Review the next actual failure. Do not claim the complete Android build passed yet.
