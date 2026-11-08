# Phase 4C CI correction — app compile errors (2026-11-08)

## Reported CI evidence

The latest GitHub Actions run passed the API 37.0 SDK verification, AGP configuration, AAR metadata checks, application resource/manifest preparation, compilation of the core and feature modules, retrieval unit tests, Room KSP generation, `:core:persistence:compileDebugKotlin`, and `:core:persistence:testDebugUnitTest`. It then failed compiling the app source with these distinct errors:

1. `MainActivity.kt`: `androidx.compose.foundation.layout.weight` resolved to an inaccessible internal `RowColumnParentData?.weight` property.
2. `MainActivity.kt`: the app compile classpath could not access `androidx.room.RoomDatabase`, a public supertype of `AppDatabase`.
3. `TimelineApplication.kt`: unresolved `TaskLease` and `ExecutionClaimResult`.

## Root causes and corrections

### Compose `weight` import

`Modifier.weight` is a `ColumnScope` member extension, not an importable top-level extension in this context. `MainActivity` already calls it inside the receiver lambda of `Column`, so the explicit `import androidx.compose.foundation.layout.weight` was incorrect and has been removed. The `Modifier.weight(1f)` use remains inside the `ColumnScope` and resolves through the implicit receiver.

### Room public API exposure

`core:persistence` publishes `AppDatabase`, whose declared superclass is `androidx.room.RoomDatabase`. Its Room runtime dependency was `implementation`, hiding that public supertype from downstream compile classpaths. The dependency has been changed to `api(libs.androidx.room.runtime)`. `room-ktx` remains `implementation` because it is an internal persistence implementation dependency. The app continues to consume `:core:persistence` through its existing project dependency; no direct app-level Room dependency is added.

### Missing agent API imports

`TaskLease` and `ExecutionClaimResult` are declared in package `app.timeline.islamic.core.agent`, not in `core.agent.runtime` or `core.authorization`. `TimelineApplication` now imports both from their actual package. The app already declares `implementation(project(":core:agent-api"))`, so no module dependency change is needed.

## Regression prevention

Added `scripts/verify-app-module-compile-contract.py` and a CI step before Gradle compilation. It checks: (a) no explicit Compose `weight` import; (b) the `weight` call remains under a `Column` receiver; (c) imports match the actual agent API declarations and claim contract; (d) Room runtime is exposed as `api` from persistence; (e) `AppDatabase` still extends `RoomDatabase`; (f) the app consumes persistence as a project dependency; and (g) application version metadata is bumped. All 9 checks pass locally.

## Scope and safety

No business logic, UI navigation behavior, SQL schema, database migration, encryption policy, corpus trust policy, agent authorization semantics, compile SDK, target SDK, minimum SDK, or emulator API level was changed. App version is `versionCode=11`, `versionName=0.5.7-phase4c-app-compile-fix`.

## Verification status

Local checks passed: app compile contract 9/9; Room dense-index contract 8/8; AGP built-in Kotlin 11/11; Android SDK metadata 10/10; Gradle bootstrap regression; Phase 4 Room v5 guard; Phase 4C corpus/index guard; dependency reproducibility policy; Compose/API 37 guard; secret scan; version catalog TOML parse; 23 XML parses.

The authoring environment has no installed Gradle distribution and no Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` are unset). The full Android Gradle/Kotlin app compile cannot be rerun locally. A new GitHub Actions run is required to verify that `:app:compileOfflineSideloadDebugKotlin` succeeds and discover any subsequent issue.
