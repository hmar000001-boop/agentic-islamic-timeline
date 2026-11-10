# Timeline agentique — CI correction 0.5.2 (Compose API 37)

**Date:** 2026-11-08  
**Baseline:** `0.5.1-phase4c-buildconfig-nullability-fix`  
**Version:** `0.5.2-phase4c-compose-api37`

## 1. CI failure

The 0.5.1 GitHub Actions run passed Gradle bootstrap, AGP built-in Kotlin configuration, and Kotlin DSL compilation. Main Kotlin compilation began for core and feature modules, and `:core:persistence:kspDebugKotlin` started. The first failure was `:app:checkOfflineSideloadDebugAarMetadata`.

AAR metadata reported 11 Compose Android artifacts at version 1.12.1 requiring `compileSdk` API 37 or later, while all project Android modules were configured for API 36. Dependency reports and CycloneDX were downstream tasks blocked by the same build configuration state; they were not independent root causes.

## 2. Fix

- Updated all 15 Android modules to `compileSdk = 37`.
- Installed `platforms;android-37` in CI.
- Kept Build Tools at `36.0.0`, consistent with AGP 9.3's documented default.
- Kept app `targetSdk = 36`, `minSdk = 26`, and API 36 emulator/system image unchanged.
- Added `scripts/verify-compose-compile-sdk.py`, run by CI before Gradle tasks.
- Bumped app versionCode to 7 and versionName to `0.5.2-phase4c-compose-api37`.

No Android runtime target, permissions, persistence schema, corpus trust logic, or UI behavior was changed in this hotfix.

## 3. Rationale

Jetpack Compose August 2026 stable release introduced Compose 1.12 and specifies compileSdk API 37. Compose BOM `2026.09.00` selects Compose 1.12.1. AGP 9.3 supports up to API 37 and uses Gradle 9.5.0 / Build Tools 36.0.0. Android's SDK documentation distinguishes compile-time API use from `targetSdk` behavior. Thus the correct change is compileSdk 37 without raising targetSdk.

## 4. Verification performed locally

- Compose/API37 guard: passed; verified all 15 Android module compile SDK declarations.
- App targetSdk check: remains 36.
- CI emulator check: remains API 36.
- CI SDK package check: includes platforms 36 and 37.
- Version catalog TOML parse: passed.
- GitHub Actions YAML parse: passed.
- 23 Android XML resource/manifest files parsed successfully.
- Extracted ZIP rerun: all static guards passed, including the Gradle wrapper executable-bit regression (system `unzip` preserved mode 0755).
- SHA-256 verified against the final archive.
- Android Gradle build / KSP completion / Room instrumentation: not run in this authoring environment; no Android SDK or installed Gradle distribution is available here. The CI log shows KSP began, but the AAR metadata task failed first.

## 5. What this should resolve

The specific `CheckAarMetadataWorkAction` errors stating that Compose 1.12.1 requires compileSdk 37 should be removed. This does not guarantee later Kotlin, KSP, Room, resource, lint, unit-test, or instrumentation tasks will pass; those must be evaluated from the next actual CI run.
