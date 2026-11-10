# Timeline Agentique checkpoint — Phase 6 thermal-status CI hotfix

**Date:** 2026-11-09
**Base:** `0.7.0-phase6-domain-engines` + prior JUnit 4 test hotfix
**App version:** `0.7.0-phase6-domain-engines`, versionCode 25 (unchanged)

## Latest hosted CI evidence

The user-provided 18:19 UTC log confirms that `:core:model-manager:testDebugUnitTest` now passes. The previous `InvalidTestClassError` has therefore been corrected. The listed module unit tests and Phase 6 module compilation progress through to application compilation.

The build now stops at one fatal Kotlin error in `TimelineApplication.kt:478`: a nullable public `thermalStatus` property from another Gradle module was read twice in the same smart-cast condition.

## Fix in this checkpoint

- Snapshot `snapshot.thermalStatus` once into local immutable `thermalStatus` in `TimelineApplication.runtimeLoadBlockReason()`.
- Apply the same pattern in `ModelManager.preflight()` and reuse the captured value in its result.
- Add a null thermal-observation regression test and CI source-contract guard.
- Clean the non-fatal warnings in the exact supplied log: keyring `!!`, Compose `Divider`, Prayer test `!!`, Room migration parameter naming and authorization lease `!!`.
- No product behavior, database schema, dependencies, app version name/code or offline permission policy changed.

## Local validation

Thermal/warning contract PASS; JUnit signature contract PASS; Model Manager contract PASS; Phase 6 engine contracts 56/56; workspace persistence 12/12; Phase 5C retrieval 33/33; dense persistence 12/12; persistence instrumentation source contract 14/14; inference runtime contract 31/31; app compile contract 11/11; translations 6/6; five resource modules; secret scan and workflow YAML PASS; Kotlin two-module smart-cast reproducer PASS.

Full Gradle could not run locally because the Gradle wrapper download failed DNS resolution for `services.gradle.org`. The new source fix therefore awaits the user's next hosted CI run. API 36 and Galaxy S24 FE tests remain open.

## Continue

Continue from this checkpoint and rerun the same user-provided Gradle test command. Confirm `:app:compileOfflineSideloadDebugKotlin` and the app unit tests before taking up any newly exposed failures. Do not restart Phase 6.
