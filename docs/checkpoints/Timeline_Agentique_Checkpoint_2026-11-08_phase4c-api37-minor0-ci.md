# Timeline Agentique — Phase 4C CI/API 37.0 checkpoint

**Date:** 2026-11-08  
**Checkpoint:** `PHASE4C-CI-API37-MINOR-001`  
**Source:** `0.5.3-phase4c-compile-sdk37.0`  
**Project:** `/mnt/data/ta-work`  
**Library project:** `/Timeline_Agentique/`

## Resume rule

Continue incrementally from source 0.5.3. Preserve the full Phase 1–4C app, Room, Timeline/Canvas, agent runtime, authorization, publisher trust, corpus manager and retrieval source. Do not regenerate or start over.

## Exact CI issue addressed

The hosted runner's SDK repository exposes API 37 under package and directory name `platforms;android-37.0` / `platforms/android-37.0`. The previous workflow requested `platforms;android-37`, causing sdkmanager to fail before Gradle. The workflow now installs the exact `.0` package.

All 15 Android modules declare API 37.0 using AGP's typed compile SDK API:

```kotlin
compileSdk {
    version = release(37) {
        minorApiLevel = 0
    }
}
```

The app remains targetSdk 36 and minSdk 26; the API 36 emulator/system image and Build Tools 36.0.0 remain unchanged. No filesystem alias is required by this configuration.

## New guard scripts

- `scripts/verify-android-sdk-platform.py`: validates real SDK API 37.0 before Gradle.
- `scripts/test-android-sdk-platform.py`: six temp-SDK regression tests.
- `scripts/verify-compose-compile-sdk.py`: now checks the exact minor API DSL and SDK Manager package identifier.

CI installs `platforms;android-37.0`, calls the real SDK verifier, runs the regression tests, then the existing build/test workflow.

## Local verification

- API 37.0 SDK validation test: 6/6.
- Compose/API guard: 15 modules validated.
- Built-in Kotlin / typed AGP DSL tests: 11/11.
- Dependency lock-policy verifier: passed.
- Room schema v5/migrations: passed.
- Phase 4C integration guard: passed.
- Gradle bootstrap executable test: passed.
- Secret scan: passed.

No actual Android Gradle/KSP/JUnit/instrumentation or emulator run was possible here. The hosted GitHub Actions workflow remains the authoritative next validation. If it fails again, fix the exact next error on this checkpoint—do not revert unrelated Android/API settings or restart earlier phases.

## Files changed in this tranche

- `.github/workflows/android-ci.yml`
- `app/build.gradle.kts`
- all 14 library module `build.gradle.kts` files
- `scripts/verify-compose-compile-sdk.py`
- `scripts/verify-android-sdk-platform.py`
- `scripts/test-android-sdk-platform.py`
- `gradlew` unchanged
- README / CHANGELOG / this report and checkpoint

## Next action

Push version 0.5.3. Confirm the SDK install step says Android SDK Platform 37.0 installed at `platforms/android-37.0`, then run Gradle. Share the first subsequent failure if any. Phase 5 work should remain paused until the core build/CI baseline is stable.
