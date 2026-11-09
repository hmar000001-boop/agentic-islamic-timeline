# Phase 6 CI hotfix — cross-module thermal status nullability

**Date:** 2026-11-09
**Source line:** `0.7.0-phase6-domain-engines` (`versionCode = 25`; unchanged)
**Scope:** fix the sole fatal compiler error in the supplied 18:19 UTC Gradle run and clean the non-fatal compiler warnings printed by that run.

## 1. Diagnosis from the hosted log

The previous JUnit 4 correction is confirmed by the user's new hosted log: `:core:model-manager:testDebugUnitTest` completes successfully. The listed authorization, agent-runtime, timeline API, persistence, knowledge API, corpus, retrieval, model-manager, inference-runtime, domain API and Phase 6 feature unit-test tasks also complete. `feature:canvas`, `feature:corpus-management` and `feature:model-management` report `NO-SOURCE` for their unit-test source sets. The app unit-test task does not run because app main Kotlin compilation fails first.

There is one fatal error:

```text
TimelineApplication.kt:478:43
Smart cast to 'Int' is impossible, because 'thermalStatus' is a public API property declared in different module.
```

The expression read `snapshot.thermalStatus` twice in a single null-check/comparison. `ModelResourceSnapshot` is supplied by `:core:model-manager`, so the app module cannot smart-cast repeated reads of its nullable public property as stable.

## 2. Corrective change

`TimelineApplication.runtimeLoadBlockReason()` now reads the property exactly once:

```kotlin
val thermalStatus = snapshot.thermalStatus
```

The `when` condition checks the local immutable value. `ModelManager.preflight()` uses the same stable-read pattern and returns that exact captured observation in `ModelPreflightResult`. This preserves the previous threshold policy and unknown-status semantics; no thermal threshold, model lifecycle or resource policy changed.

A new unit test verifies that a null thermal observation does not get misclassified as severe and still requires an actual runtime probe. The existing severe thermal test remains.

A new CI source-contract check guards both paths against reintroducing the cross-module nullable-property smart-cast pattern.

## 3. Non-fatal warnings from the same log cleaned

- `SignedPublisherKeyringArchiveImporter`: replaced redundant `!!` with explicit `requireNotNull` after validation.
- `CorpusManagementFeature`: replaced deprecated Material3 `Divider` with `HorizontalDivider`.
- `PrayerCalculatorTest`: removed the redundant `!!` on the non-null sunrise result.
- `AppDatabaseMigrations`: aligned override parameter names to `db`.
- `RoomAuthorizationStore`: replaced redundant `lease!!` with an explicit invariant check after fence ownership was established.

These are warning cleanups only; they do not alter archive trust, UI ordering, prayer calculation, schema SQL or lease-fencing semantics.

## 4. Local verification performed

- New thermal/nullability and warning-cleanup source contract: PASS.
- JUnit 4 `ModelManagerTest` signature guard: PASS (28 test methods; user-hosted log separately confirms the Gradle task now passes).
- Model Manager source contract: PASS.
- Phase 6 domain-engine contracts: 56/56 PASS.
- Phase 6 Room v7/domain-workspace contract: 12/12 PASS.
- Phase 5C dense retrieval source contracts: 33/33 PASS.
- Dense-index persistence contract: 12/12 PASS.
- Persistence Android-test source contract: 14/14 PASS.
- LiteRT-LM runtime contract: 31/31 PASS.
- App module compile/source contract: 11/11 PASS.
- English/Arabic translation tests: 6/6 PASS; five resource modules verified.
- Secret scan, workflow YAML parse, Python script compilation and Gradle wrapper shell syntax: PASS.
- Kotlin two-module compiler reproducer: PASS. The model data class was compiled into a separate JAR, then an app compilation unit imported it and compiled the stable local nullable-value check. This exercises the same module-boundary smart-cast rule as the hosted error.

## 5. Explicit validation not claimed

The full Android Gradle build was not executed in this authoring environment. `./gradlew --version` attempted to download the pinned Gradle 9.5.0 distribution and failed because DNS could not resolve `services.gradle.org`. Consequently, this hotfix is not yet confirmed by a new hosted CI run. No API 36 instrumentation or Galaxy S24 FE validation is claimed.

## 6. Required hosted confirmation

Rerun the same Gradle command from the user's log. Expected outcomes:

1. `:core:model-manager:testDebugUnitTest` remains green (the prior JUnit error stays fixed).
2. All selected module tests continue to pass.
3. `:app:compileOfflineSideloadDebugKotlin` passes the `runtimeLoadBlockReason()` thermal check.
4. `:app:testOfflineSideloadDebugUnitTest` can then execute.
5. The previous warnings listed in section 3 no longer appear.

If another diagnostic appears after app compilation advances, treat it as a separate newly exposed issue; do not infer it from this log.

## 7. Provenance

The patch and complete ZIP are based on the recovered 0.7.0 JUnit-hotfix archive available in this session. They are checked against that recovered archive, not against a byte-identical copy of the user's remote Git checkout. Compare/apply to the exact branch before merging, preserving any user commits made since the source archive was recovered.
