# Timeline agentique — Phase 4C nullable BuildConfig CI checkpoint

**Date:** 2026-11-08  
**Checkpoint ID:** `PHASE4C-AGP-BUILDCONFIG-NULLABLE-002`  
**Source baseline:** Phase 4C / 0.5.0  
**Current source:** `0.5.1-phase4c-buildconfig-nullability-fix`  
**Project path:** `/mnt/data/ta-work`  
**Library folder:** `/Timeline_Agentique/`

## Resume rule

Continue from this exact source tree or the versioned 0.5.1 ZIP. Do not regenerate the Android app. Preserve all earlier archives and checkpoints.

## CI issue and resolution

GitHub Actions reached AGP 9.3.1 configuration. Kotlin DSL compilation failed because `Variant.buildConfigFields` is nullable and the app directly called `.put` twice. The dependency report and CycloneDX steps repeated the same upstream app script-compilation error.

`app/build.gradle.kts` already enables `android.buildFeatures.buildConfig = true`. It now checks the nullable Variant property with `checkNotNull(variant.buildConfigFields)` and adds both publisher-root fields through the non-null local reference. If AGP does not expose the map, configuration fails with a specific diagnostic; no trust fields are silently omitted.

Official API: https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/Variant (the property is nullable).

## Files changed

- `app/build.gradle.kts` — null guard, app versionCode 6 and versionName 0.5.1.
- `scripts/test-built-in-kotlin.py` — regression test for null guard and BuildConfig enabled.
- `scripts/verify-built-in-kotlin.py` — verifies null guard and rejects unsafe direct nullable access.
- `README.md`, `CHANGELOG.md`, report and checkpoint updated.

## Local checks actually run

- Built-in Kotlin regression suite: 11/11 passed.
- Public Android DSL / built-in Kotlin verifier: passed for 15 modules.
- Gradle reproducibility guard: passed.
- Phase 4 Room v5 / migrations / encrypted-index guard: passed.
- Phase 4C feature/encrypted embedding guard: passed.
- Gradle bootstrap executable test: passed.
- Secret scan, YAML parse and TOML parse: passed.

## Not run

Actual Gradle distribution/configuration, AGP/KGP/KSP build, Android Gradle tasks, KSP Room schema generation, Gradle/JUnit tests, Android instrumentation, emulator and Galaxy S24 FE tests were not run locally. No CI success is claimed.

## Next action

Push 0.5.1 and rerun the workflow. The current specific nullable `buildConfigFields` script compilation error should be resolved. Inspect the next logs for the first subsequent genuine build/test failure. Once the Phase 4C baseline has a successful actual CI run, continue Phase 5 (model manager, SAF model installation, LiteRT-LM compatibility/preflight, real local embedding implementation and graceful degraded behavior).
