# Timeline Agentique — continuity checkpoint 0.5.2

**Date:** 2026-11-08  
**Checkpoint:** `PHASE4C-COMPOSE-API37-001`  
**Baseline:** Phase 4C source 0.5.1  
**Current version:** `0.5.2-phase4c-compose-api37`

## Resume rule

Continue from this source tree / 0.5.2 archive. Do not regenerate the app or restart Phases 1–4. Preserve the Phase 4 corpus, trust, retrieval, SQLCipher, UI, and embedding foundation.

## Latest CI diagnosis

The latest run got past AGP built-in Kotlin configuration and Kotlin DSL compilation. Core/feature Kotlin compilation began, and Room KSP started. The first task failure was `:app:checkOfflineSideloadDebugAarMetadata` because 11 Compose 1.12.1 AARs (from Compose BOM 2026.09.00) require compileSdk 37 while the project used compileSdk 36. Other dependency-report/SBOM errors in that run are downstream consequences of the earlier failed configuration/build, not new root causes.

## Changes in 0.5.2

- All 15 Android modules now use compileSdk 37.
- App targetSdk remains 36; minSdk remains 26.
- CI installs API 37 platform and API 36 platform + emulator/system image.
- App Build Tools remains pinned to 36.0.0, matching AGP 9.3.1 defaults.
- Build Tools remain 36.0.0.
- Added `scripts/verify-compose-compile-sdk.py` and CI enforcement.
- app versionCode 7, versionName `0.5.2-phase4c-compose-api37`.

## Verification state

- Compose compileSdk guard passed locally.
- All 11 AGP built-in Kotlin tests passed on the extracted 0.5.2 ZIP.
- Gradle reproducibility, Room v5, Phase 4C foundation, wrapper permission, secret scan, version catalog, workflow YAML and 23 XML parse checks passed on the extracted ZIP.
- Archive SHA-256 and ZIP integrity verified.
- Actual Android Gradle build and KSP completion were not executed locally. The environment has no Android SDK or installed Gradle distribution.
- Await actual GitHub Actions result for remaining compile, unit-test, Room/KSP, lint, packaging, and API 36 instrumentation diagnostics.

## Next actions

1. Push 0.5.2 and rerun CI.
2. If AAR metadata passes, address the next real compiler/test failure without broad speculative refactoring.
3. Keep the API 36 instrumentation lane until the target SDK review is separately approved.
4. Only after CI is stable continue Phase 5 implementation; preserve the current Phase 4 baseline.
