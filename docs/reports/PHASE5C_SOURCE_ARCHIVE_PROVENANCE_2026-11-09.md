# Phase 5C source archive provenance

**Archive version:** `0.6.2-phase5c-dense-retrieval` (versionCode 23)
**Archive state:** complete reconstructed source tree with the Android directory-sync CI hotfix integrated.

## Source reconstruction

The exact original 0.6.2 ZIP was not present in the active working files. This archive was assembled from the recovered Phase 5C source tree available in the workspace: the 0.6.1 baseline was used as the continuity base, with the available 0.6.2 Phase 5C source, database migration, retrieval integration, tests, build metadata and release documentation retained in the reconstructed 0.6.2 tree. The Android directory-sync hotfix and its regression contract/CI step are then applied.

The resulting tree contains the full Android project structure, all modules and scripts present in that recovered tree, and the hotfix. It is not possible to certify that it is byte-for-byte identical to an unavailable original 0.6.2 checkout or that no unarchived local-only files existed there.

## Validation included

The following checks were run against the packaged working tree on 2026-11-09: Android model directory-sync contract, Model Manager source contract, LiteRT-LM runtime source contract (31 checks), dense-index persistence contract (8 checks), app compile contract (10 checks), secret scan, XML parsing, GitHub Actions YAML parsing and shell syntax. All passed.

These local checks do not replace hosted Android Gradle/KSP compilation or API 36 connected instrumentation. In particular, the original hosted failure must still be confirmed resolved by rerunning GitHub Actions, and the API 36 `AndroidModelFileStoreTest` must pass. No Galaxy S24 FE model performance or production readiness is claimed.
