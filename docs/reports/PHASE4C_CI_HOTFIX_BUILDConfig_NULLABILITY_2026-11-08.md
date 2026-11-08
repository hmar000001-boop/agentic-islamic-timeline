# Timeline agentique — CI correction 0.5.1

**Date:** 2026-11-08  
**Base:** Phase 4C / 0.5.0  
**Version:** `0.5.1-phase4c-buildconfig-nullability-fix`

## 1. Reported CI failure

GitHub Actions reached the AGP 9.3.1 project configuration stage, then Kotlin DSL script compilation failed in `app/build.gradle.kts` on two calls to `variant.buildConfigFields.put(...)`. AGP's public `Variant.buildConfigFields` type is `MapProperty<String, BuildConfigField<Serializable>>?`, so Kotlin correctly rejects a direct call through the nullable receiver. The dependency-report and CycloneDX jobs failed for the same upstream script-compilation error; they are not independent dependency errors.

Official API reference: https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/Variant

## 2. Fix

- Retained `android.buildFeatures.buildConfig = true`.
- In `androidComponents.onVariants`, assigned `val buildConfigFields = checkNotNull(variant.buildConfigFields) { ... }`.
- Added both `CORPUS_PUBLISHER_ROOT_KEY_ID` and `CORPUS_PUBLISHER_ROOT_KEY_B64` through the checked map.
- A missing map now fails configuration with a clear error; the build never silently omits the publisher trust settings.
- Added regression assertions to the built-in Kotlin tests and verifier.
- Bumped app versionCode from 5 to 6 and versionName to `0.5.1-phase4c-buildconfig-nullability-fix`.

## 3. Local verification

Passed:
- `python3 scripts/test-built-in-kotlin.py` — 11/11.
- `python3 scripts/verify-built-in-kotlin.py` — all 15 Android modules.
- `python3 scripts/verify-gradle-reproducibility.py`.
- `python3 scripts/check-phase4-schema.py` — Room v5, 27 entities, migrations and encryption guards.
- `python3 scripts/check-phase4c-foundation.py`.
- `python3 scripts/test-gradle-bootstrap.py`.
- `python3 scripts/secret-scan.py`.
- GitHub Actions YAML and version catalog TOML parsing.

Not executed: actual `./gradlew` configuration, AGP/KGP/KSP compilation, Room KSP generation, Gradle/JUnit unit tests, Android instrumentation, emulator and physical-device tests. This environment has no installed Gradle distribution or Android SDK. Do not claim the GitHub Actions build is green until the user reruns CI.

## 4. Continuity

This is a narrow incremental change to the 0.5.0 source. Phases 1–4C, all existing modules, Room schema and the AGP built-in Kotlin migration are preserved. Continue Phase 5 only after the next CI run identifies whether any subsequent build errors remain.
