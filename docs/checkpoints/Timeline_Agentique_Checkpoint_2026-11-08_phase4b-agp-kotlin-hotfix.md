# Timeline Agentique continuity checkpoint — 0.4.3 CI hotfix

**Date:** 2026-11-08
**Baseline:** 0.4.2 Phase 4B publisher trust lifecycle + Gradle bootstrap fix
**Current source:** 0.4.3 AGP/Kotlin DSL compatibility hotfix

## Continuity — do not restart

Continue from the complete 0.4.2 project. Do not regenerate the Android app or replace Phases 1–4A/4B. This hotfix changes Gradle configuration, CI checks and documentation only. Timeline, Canvas, agent runtime, authorization, Room schema v4, corpus packs, trust management, and retrieval source are unchanged.

## Latest CI error

Gradle 9.5.0 now downloads and starts correctly. The next failure occurs while evaluating `app/build.gradle.kts`: Kotlin Gradle Plugin 2.4.21 cannot apply `org.jetbrains.kotlin.android` while AGP 9.3.1's new DSL is enabled. The project already sets `android.builtInKotlin=false` and applies the external Kotlin Android plugin.

## Fix in 0.4.3

- Retain the external Kotlin Android plugin and pinned Kotlin/KSP/Compose toolchain.
- Add `android.newDsl=false` alongside `android.builtInKotlin=false` in `gradle.properties`. AGP's official opt-out guidance requires both flags when retaining `org.jetbrains.kotlin.android`.
- Add `scripts/verify-agp-kotlin-mode.py` and `scripts/test-agp-kotlin-mode.py`.
- Wire both checks into CI before `./gradlew --version`.
- Document AGP 10 as a migration gate because the transitional opt-out properties are removed in AGP 10.

## Validation actually executed

- AGP/Kotlin configuration guard: PASS; confirms AGP 9.3.1 and both opt-out flags across 14 Android modules.
- Guard test cases: 6/6 PASS.
- Phase 4 Room v3→v4 migration/lifecycle guard: PASS.
- Gradle bootstrap executable-permission test: PASS.
- Secret scan: PASS.
- GitHub Actions YAML parse and consistency checks: PASS.

No real Gradle or Android build was run here; the authoring container has no installed Gradle or Android SDK. Do not claim that the hosted Android tests pass until GitHub Actions reports them.

## Resume point

Push the 0.4.3 archive/source and rerun the full workflow. Expected result: AGP/Kotlin plugin application advances past the current new-DSL exception. Treat any later KSP, Compose compiler, Room generation, resource linking or source compilation failures as independent next blockers and fix them from their fresh logs.
