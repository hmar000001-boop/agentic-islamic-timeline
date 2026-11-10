# Phase 4B CI hotfix — AGP/Kotlin DSL compatibility

**Date:** 2026-11-08
**Source baseline:** 0.4.2
**Hotfix version:** 0.4.3
**Scope:** Gradle configuration, CI verification, and documentation only.

## Failure observed

The GitHub Actions runner successfully launched Gradle 9.5.0. Gradle then failed during project configuration while applying `org.jetbrains.kotlin.android` 2.4.21 to `:app`. AGP 9.3.1 had its new DSL enabled (`android.newDsl=true` by default), while the project explicitly opted out of AGP built-in Kotlin and continued to apply the external Kotlin Android plugin. The dependency-report and CycloneDX tasks failed for the same reason because Gradle configures the Android project before executing those tasks.

## Root cause

This is a known AGP 9 compatibility boundary. When keeping the external `org.jetbrains.kotlin.android` plugin, both AGP opt-outs must be disabled together:

- `android.builtInKotlin=false`
- `android.newDsl=false`

The existing project already had the first property but omitted the second, so KGP encountered the incompatible AGP new DSL.

Official guidance:

- Android built-in Kotlin migration / opt-out requirements: https://developer.android.com/build/migrate-to-built-in-kotlin
- AGP DSL/API migration roadmap: https://developer.android.com/build/releases/gradle-plugin-roadmap
- Kotlin Gradle Plugin compatibility matrix: https://kotlinlang.org/docs/gradle-configure-project.html

## Change applied

1. Added `android.newDsl=false` next to the existing `android.builtInKotlin=false` in `gradle.properties`.
2. Kept pinned AGP 9.3.1, Gradle 9.5.0, Kotlin Gradle Plugin 2.4.21, KSP 2.3.10, and Compose compiler plugin 2.4.21 unchanged.
3. Added `scripts/verify-agp-kotlin-mode.py` to enforce the paired flags, AGP 9-only transitional profile, catalog plugin identity, and consistent Kotlin Android plugin declaration across Android modules.
4. Added six positive/negative unit cases in `scripts/test-agp-kotlin-mode.py` and wired those tests plus the live-project guard into GitHub Actions before Gradle starts.
5. Updated README and changelog with the temporary migration boundary.

## Why this is a compatibility hotfix, not the final AGP migration

AGP's official migration guide says that when not yet ready for built-in Kotlin, projects may opt out of built-in Kotlin, and must also opt out of the new DSL because `kotlin-android` is incompatible with it. This keeps the established external Kotlin/Compose/KSP/Room toolchain in place while fixing the exact current CI failure.

This is temporary. The AGP roadmap says the `android.newDsl` and `android.builtInKotlin` opt-out flags have no replacement and are removed in AGP 10. Before moving to AGP 10+, migrate Android modules to AGP built-in Kotlin, verify the pinned Compose compiler integration, and verify KSP/Room code generation with the exact AGP version on a real runner. The CI guard deliberately fails if AGP 10+ is pinned before that migration.

## Verification performed in this authoring environment

- AGP/Kotlin compatibility guard: PASS (AGP 9.3.1; both opt-outs; 14 Android modules).
- Guard unit cases: 6/6 PASS (valid profile, missing each flag, AGP 10 rejection, wrong catalog plugin ID, missing module plugin).
- Phase 4 Room v3→v4 schema and lifecycle guard: PASS.
- Gradle bootstrap executable permission regression: PASS.
- Secret scan: PASS.
- GitHub Actions YAML parse and module configuration consistency: PASS.
- No Kotlin application source, Room entities/migrations, corpus, retrieval, authorization, or agent runtime source changed.

**Not run:** actual Gradle configuration, unit-test tasks, Android compilation, KSP/Room generation, instrumentation, emulator or device testing. The authoring container has no installed Gradle distribution or Android SDK. The next hosted CI run must confirm that Gradle proceeds beyond plugin application; any following KSP/Compose/Room/compiler diagnostics are separate issues to fix from fresh logs.
