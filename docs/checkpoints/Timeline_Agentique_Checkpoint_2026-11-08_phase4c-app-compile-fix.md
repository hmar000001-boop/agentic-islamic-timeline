# Timeline Agentique — Phase 4C app compile correction checkpoint

**Date:** 2026-11-08
**Source version:** `0.5.7-phase4c-app-compile-fix`
**Previous source:** `0.5.6-phase4c-room-dense-index-compile-fix`
**Project root:** `/mnt/data/agentic-islamic-timeline` (incremental project; archive root has same source layout)
**Library project:** `/Timeline_Agentique/`

## Resume rule

Continue from version 0.5.7. Do not recreate the app or replace earlier phase work. Phases 1–4C remain in place. The last CI run passed SDK installation/validation, Gradle configuration, Compose AAR metadata, the previously failing retrieval tests, Room KSP generation, persistence Kotlin compilation and persistence unit tests. It failed next at app Kotlin compilation.

## Exact CI failure and applied fixes

1. `MainActivity.kt` explicitly imported `androidx.compose.foundation.layout.weight`; this resolved to an inaccessible internal `RowColumnParentData?.weight` symbol. Remove that import. Keep `Modifier.weight(1f)` inside the `Column` content receiver where `ColumnScope.weight` is available.
2. `AppDatabase` publicly extends `androidx.room.RoomDatabase`, but `core:persistence` declared Room runtime as `implementation`. Change only Room runtime to `api(libs.androidx.room.runtime)` so consumer compile classpaths include the public supertype. Keep Room KTX implementation-scoped.
3. `TimelineApplication.kt` uses `TaskLease` and `ExecutionClaimResult`; both are top-level types in `app.timeline.islamic.core.agent`. Add the correct imports. Existing app dependency on `:core:agent-api` already exists.

## Changed files

- `app/src/main/java/app/timeline/islamic/MainActivity.kt`
- `app/src/main/java/app/timeline/islamic/TimelineApplication.kt`
- `core/persistence/build.gradle.kts`
- `app/build.gradle.kts` (versionCode 11 / versionName 0.5.7)
- `scripts/verify-app-module-compile-contract.py` (new 9-check guard)
- `.github/workflows/android-ci.yml` (runs the guard before Gradle)
- `README.md`, `CHANGELOG.md`
- This checkpoint and `docs/reports/PHASE4C_CI_HOTFIX_APP_COMPILE_2026-11-08.md`

## Verification done

- App module compile-contract guard: 9/9.
- Room dense-index contract: 8/8.
- AGP built-in Kotlin tests: 11/11.
- Android SDK metadata tests: 10/10.
- Room v5 migration/schema guard: pass.
- Phase 4C application/corpus/encrypted-index guard: pass.
- Dependency reproducibility/locking policy guard: pass.
- Compose API 37.0 / target API 36 guard: pass.
- Gradle bootstrap executable bit regression: pass.
- Secret scan: pass.
- TOML version catalog: parse pass.
- 23 XML resources: parse pass.
- The real SDK verifier was not run locally because no Android SDK is installed. The actual Gradle build, KSP on this workspace, unit test tasks, instrumentation and physical-device validation remain dependent on GitHub Actions/device runs.

## Next action

Push 0.5.7 and run the same CI workflow. First confirm `:app:compileOfflineSideloadDebugKotlin`; then continue with app unit tests, assemble/lint, merged-manifest audit, Room/SQLCipher instrumentation and API 36 emulator tests. Do not call the build green until the hosted run completes.
