# Timeline agentique — Phase 2 continuity checkpoint

**Date:** 2026-11-06  
**Checkpoint ID:** PHASE2-TIMELINE-CANVAS-001  
**Source version:** `0.2.0-phase2`  
**Project path:** `/mnt/data/agentic-islamic-timeline`  
**Library project folder:** `/Timeline_Agentique/`

## 1. Resume rule

Continue from the Phase 2 code in the current project directory. Do not recreate the Android project or replace the Phase 1 source/checkpoint. Phase 1 ZIP `agentic-islamic-timeline-phase1-0.1.0.zip` remains an immutable prior delivery.

## 2. Existing Phase 1 baseline kept

- AGP 9.3.1 / Gradle 9.5.0 / Kotlin 2.4.21 / KSP 2.3.10.
- Android min SDK 26, compile/target SDK 36, JDK 17 target.
- Offline / Play distribution flavors; no product backend and no INTERNET permission in this tranche.
- Room schema v1 with 24 entities; SQLCipher and Keystore key envelope integration candidate retained.
- Existing authorization baseline and four default-deny unit tests retained.

## 3. Phase 2 additions

- Gradle modules `:feature:timeline` and `:feature:canvas` added to `settings.gradle.kts`.
- App version bumped to code 2 / `0.2.0-phase2`.
- Timeline UI: date navigation, locale-sensitive week start, filters, create event/task/habit, complete/cancel user-owned tasks/habits, empty/loading/storage-error states.
- Timeline DAO now queries interval overlap; local date windows account for DST.
- Canvas contracts in `core/agent-api`; Room Canvas repository in `core/persistence`; Canvas UI for draft create/edit/archive.
- Canvas draft codec version 1; Room session+snapshot writes transactional; session updates compare expected versions; snapshot payload hashes computed.
- Arabic and English feature resources; app per-app locale metadata for `en` and `ar`.
- CI updated to run unit tests for `core:timeline-api`, `core:persistence`, `feature:timeline`, `feature:canvas`.

## 4. Key file locations

- `feature/timeline/src/main/java/app/timeline/islamic/feature/timeline/TimelineFeature.kt`
- `feature/timeline/src/main/java/app/timeline/islamic/feature/timeline/TimelineViewModel.kt`
- `feature/canvas/src/main/java/app/timeline/islamic/feature/canvas/CanvasFeature.kt`
- `feature/canvas/src/main/java/app/timeline/islamic/feature/canvas/CanvasViewModel.kt`
- `core/agent-api/src/main/java/app/timeline/islamic/core/agent/CanvasContracts.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomCanvasRepository.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt`
- `core/timeline-api/src/main/java/app/timeline/islamic/core/timeline/TimelineContract.kt`
- `app/src/main/java/app/timeline/islamic/MainActivity.kt`
- `.github/workflows/android-ci.yml`

## 5. Verification state

- Local Kotlin compilation of timeline contracts/ViewModels/Canvas repository: PASS.
- Local targeted tests: 7/7 test methods passed through a Kotlin harness with minimal JUnit stubs; not a Gradle JUnit run.
- Static checks: XML/resources/localization keys, catalog aliases, included modules, workflow YAML, offline INTERNET check and secret scan: PASS.
- Android Gradle build and Room KSP schema export: NOT RUN.
- Instrumented SQLCipher/Room tests and Compose/RTL rendering: NOT RUN.

**Known environment blocker:** no `ANDROID_HOME`/`ANDROID_SDK_ROOT`, no installed Gradle; `./gradlew --version` failed because DNS could not resolve `services.gradle.org`.

## 6. Immediate next actions

1. On a network-enabled Android build host, run `./gradlew --version` and resolve/compile the project.
2. Run all unit-test tasks and assemble/lint the offline debug flavor.
3. Run `:core:persistence:connectedDebugAndroidTest` and `:app:connectedOfflineSideloadDebugAndroidTest` on API 36.
4. Commit generated Room schema `1.json`, dependency lockfiles and verification metadata after successful resolution.
5. Fix any actual Gradle/KSP/Compose/Room diagnostics before extending UI behavior.
6. Continue the Phase 2 backlog: full edit flows, recurring habits, notification consent/scheduling, Canvas steps and history.

## 7. Important constraints

- Do not seed fake prayer times or fabricated agent suggestions.
- Do not make agent proposal cards executable; Phase 3 Authorization Engine remains the only authority for mutating agent actions.
- No server, analytics, tracking, billing or ads.
- Do not claim SQLCipher production readiness or RTL/device acceptance before tests run.
- Room schema remains version 1 in this delivery; if any subsequent entity changes are made, add explicit migration and migration tests rather than destructive fallback.
