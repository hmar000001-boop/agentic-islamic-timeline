# Phase 4C CI hotfix — persistence Android-test compilation

**Date:** 2026-11-09
**Source baseline:** `0.5.14-phase4c-api36-avd-home-fix`
**Updated source:** `0.5.15-phase4c-persistence-androidtest-compile-fix`
**Application version:** versionCode `19`
**Scope:** Android instrumentation-test compile classpath and test fixture initialization only

## 1. Evidence from the hosted run

The latest Actions run passed the earlier API 36 emulator startup and proceeded through Gradle task configuration, main source compilation, Room KSP, resource processing and Android-test compilation. It then failed at `:core:persistence:compileDebugAndroidTestKotlin`. The log contained two independent primary errors:

1. `AgentAuthorizationRoomTest.kt` imports `app.timeline.islamic.core.authorization.AuthorizationEngine`, but `core/persistence/build.gradle.kts` did not place `:core:authorization` on the Android-test compile classpath. The unresolved `AuthorizationEngine` and its methods (`propose`, `approve`, `claimExecution`, `beginExecution`, `recordExecutionOutcome`, scoped Hifz grant methods) and the subsequent type-inference errors are downstream effects of that missing module dependency.
2. `RoomAuditLogTest.kt` declared `val context` locally inside `setUp()`. A later test method, `missingHistoricalKeystoreKeyFailsClosedAndDoesNotResetChain`, referenced `context` outside that local scope. Kotlin consequently resolved the identifier against its `context(...)` language feature and emitted the confusing overload diagnostics.

These are not emulator, ADB, Room schema, KSP production-source or runtime failures. The log shows main persistence Kotlin compilation had already been up to date.

## 2. Changes in 0.5.15

### 2.1 Correct test-only dependency

Added the following dependency to `core/persistence/build.gradle.kts`:

```kotlin
androidTestImplementation(project(":core:authorization"))
```

This is deliberately scoped to `androidTestImplementation`. The persistence production `implementation`/`api` dependency graph is unchanged. The test composes the persistence Room stores with the authorization engine, so the instrumented test source set must explicitly depend on both modules. There is no project dependency cycle: `core:authorization` depends on `core:agent-api` and `core:common`, not on persistence.

### 2.2 Fix test fixture Context scope

`RoomAuditLogTest` now owns a fixture property:

```kotlin
private lateinit var context: Context
```

`setUp()` initializes it using `ApplicationProvider.getApplicationContext<Context>()`. The main audit setup and the historical-key test now use that same initialized Android application context. No provider/key lifecycle semantics were changed.

### 2.3 Regression guard

Added `scripts/test-persistence-androidtest-contract.py` and made CI run it before Gradle. It verifies:

- the authorization module appears on the persistence Android-test classpath;
- the dependency is not accidentally promoted to the persistence production or local-unit-test classpath;
- `AgentAuthorizationRoomTest` still imports and uses `AuthorizationEngine`;
- `RoomAuditLogTest` has a fixture-scoped `Context`, initializes it in setup and reuses it in the historical-key test.

Bumped app versionCode to `19`, versionName to `0.5.15-phase4c-persistence-androidtest-compile-fix`.

## 3. Verification performed

The following checks passed in the authoring container:

- persistence Android-test source-set contract: 6/6;
- API 36 emulator setup regression suite: 10/10;
- Android string translation tests: 6/6, with complete English/Arabic resources;
- AGP/built-in Kotlin policy tests: 11/11;
- SDK Platform metadata tests: 10/10;
- Room dense-index persistence contract: 8/8;
- app compile/version contract: 9/9;
- Phase 4 schema/migration guard (Room v5, 27 entities): pass;
- Phase 4C foundation guard: pass;
- typed AGP/Kotlin migration verifier: pass;
- Compose compile SDK / target SDK split verifier: pass;
- Gradle reproducibility/dependency-lock policy: pass;
- Gradle bootstrap permission regression: pass;
- secret scan: pass.

**Important limitation:** this authoring container has no installed Android SDK or cached Gradle 9.5.0 distribution, so it cannot execute `:core:persistence:compileDebugAndroidTestKotlin` or hosted connected instrumentation. The next GitHub Actions run must confirm that real Kotlin Android-test compilation passes. The previous run already demonstrated the API 36 emulator reached the Gradle instrumentation stage; the current known blocker is the two Kotlin test-source errors above.

## 4. Files changed

- `core/persistence/build.gradle.kts` — Android-test-only authorization project dependency.
- `core/persistence/src/androidTest/.../RoomAuditLogTest.kt` — fixture-scoped Context.
- `app/build.gradle.kts` — versionCode 19 / versionName 0.5.15.
- `scripts/test-persistence-androidtest-contract.py` — new regression guard.
- `scripts/verify-app-module-compile-contract.py` — expected app version updated.
- `.github/workflows/android-ci.yml` — run source-set contract before Gradle.
- `README.md`, `CHANGELOG.md`, checkpoint and this report.

No main production Kotlin files, database entities, Room migrations, SQLCipher setup, Keystore behavior, authorization policies, Timeline UI, Canvas UI, corpus trust policy, retrieval behavior or Android permissions were changed.

## 5. Next CI action

Push 0.5.15 and rerun the existing workflow. The expected result is that `:core:persistence:compileDebugAndroidTestKotlin` compiles with `AuthorizationEngine` resolved and `RoomAuditLogTest` resolves the fixture `context` property. After compilation, CI must continue through `:app:connectedOfflineSideloadDebugAndroidTest`, `:core:persistence:connectedDebugAndroidTest`, `:core:corpus:connectedDebugAndroidTest` and `:core:retrieval:connectedDebugAndroidTest`. Do not treat source-set static checks as a substitute for that hosted execution.
