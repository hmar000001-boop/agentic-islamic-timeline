# Phase 4C CI hotfix — Android SDK Platform 37.0

**Date:** 2026-11-08  
**Base:** `0.5.2-phase4c-compose-api37`  
**New source:** `0.5.3-phase4c-compile-sdk37.0`

## CI failure diagnosis

The workflow requested `platforms;android-37`. The Android SDK repository publishes this platform as `platforms;android-37.0`; GitHub-hosted Ubuntu runners list and install it under `platforms/android-37.0`. Therefore sdkmanager correctly returned `Failed to find package 'platforms;android-37'` and exited non-zero. This is a package identifier mismatch, not a missing API 37 platform release.

A related toolchain issue is that SDK platform directories now include the minor API component. The build should express API 37.0 via AGP's typed minor API DSL rather than depend on a manually-created symlink.

## Changes

1. Workflow now installs `platforms;android-37.0`; API 36 and the API 36 Google APIs emulator remain installed for runtime tests.
2. All 15 Android modules now use:

   ```kotlin
   compileSdk {
       version = release(37) {
           minorApiLevel = 0
       }
   }
   ```

3. Added `scripts/verify-android-sdk-platform.py`, which verifies the installed directory, non-empty `android.jar`, `source.properties`, API level 37 and minor API level 0. It rejects path resolution outside the SDK's platforms directory.
4. Added `scripts/test-android-sdk-platform.py` with six regression tests covering valid API 37.0, missing package, obsolete integer-only package directory, wrong API level, wrong minor API level, missing jar and symlink escape.
5. Updated `scripts/verify-compose-compile-sdk.py` to enforce the exact package path and typed minor API declaration across all Android modules.

## Runtime compatibility deliberately unchanged

- `compileSdk`: API 37.0, for compile-time access and Compose 1.12.1 AAR metadata.
- `targetSdk`: 36, unchanged.
- `minSdk`: 26, unchanged.
- CI emulator: API 36, unchanged.
- Build Tools: 36.0.0, unchanged per AGP 9.3.1 compatibility baseline.

Raising compileSdk does not by itself opt the application into Android 17 runtime behavior; targetSdk remains API 36.

## Verification performed locally

- API 37.0 SDK validator tests: **6/6 passed**.
- Compose/API compileSdk guard: all **15/15 modules** select release(37) minorApiLevel 0; CI package and runtime target validated.
- AGP built-in Kotlin and public DSL tests: **11/11 passed**.
- Dependency reproducibility/locking guard: passed.
- Room v5 schema/migration guard: passed.
- Phase 4C encrypted corpus/embedding foundation guard: passed.
- Gradle bootstrap executable regression: passed.
- Secret scan: passed.
- Workflow YAML and project XML/TOML static checks: passed.
- ZIP extraction and SHA-256: to be recorded in the release checkpoint after packaging.

## Not run / remaining validation

The authoring environment has no Android SDK Platform 37.0, no installed Gradle distribution and no connected emulator. A real Android Gradle/KSP build, JUnit tasks, Room/SQLCipher instrumentation, `assemble`, lint, CycloneDX and emulator tests have not been run locally. The user must rerun GitHub Actions with this archive and share the next diagnostics. No claim of a green Android build is made.

## Continuation

Continue from this exact source tree and keep Phases 1–4C intact. This is a CI/SDK-resolution correction only; do not restart Phase 5 or change application behavior while validating it.
