# Timeline agentique — Phase 2 implementation report

**Date:** 2026-11-06  
**Source baseline:** `0.1.0-phase1` (kept in the existing project directory)  
**New source version:** `0.2.0-phase2`  
**Package:** provisional `app.timeline.islamic`  
**Architecture source:** `docs/reference/Cahier_architecture_Timeline_agentique_v1.2.md`

## 1. Continuity and scope

This delivery continues the existing `/mnt/data/agentic-islamic-timeline` project and the Phase 1 ZIP. It does not recreate the project, replace the prior checkpoint, or change the Room schema version. The current Room database remains schema version 1 because Phase 2 reuses the `timeline_item`, `canvas_session`, `canvas_snapshot` and `canvas_step` tables already declared in Phase 1; this tranche only adds DAO queries and repository behavior.

This is the first functional Phase 2 slice, not a claim that every Phase 2 roadmap item is complete.

## 2. Implemented

### 2.1 Daily Timeline

- Added the `:feature:timeline` Gradle module and wired it into the existing app.
- Replaced the Phase 1 placeholder with a database-backed daily Timeline.
- Added local-date navigation, a locale-sensitive week strip, Today shortcut and type filters (all, events, tasks, habits, suggestions).
- Added user-created event/task/habit entries with title, local date and local start time.
- Added completion and cancellation transitions for confirmed user-owned task/habit items. The UI never presents an approval button for agent proposals; proposals are labelled as unauthorized.
- Added explicit loading, empty-day and storage-error states. No sample events or synthetic prayer times are inserted.
- Added Arabic and English feature strings.

### 2.2 Temporal correctness

- `TimelineWindow.forDate(date, zone)` computes local day boundaries via `LocalDate.atStartOfDay(zone)`; this correctly handles 23-hour and 25-hour DST days.
- `TimelineDao.observeBetween` now uses interval overlap semantics: `startsAt < dayEnd AND (endsAt IS NULL OR endsAt > dayStart)`. An event spanning midnight appears in the day it overlaps; an item ending exactly at day start and an item starting exactly at day end are excluded.
- Week strip begins on the locale's first weekday rather than always assuming Monday.

### 2.3 Persistent Canvas workspaces

- Added `CanvasRepository` and versioned Canvas domain contracts in `:core:agent-api`.
- Added `RoomCanvasRepository` for create, observe, get, edit and archive operations.
- Canvas draft payloads use a versioned, length-prefixed UTF-8 format; Arabic and English content is round-tripped without lossy delimiter parsing.
- Canvas session changes and their immutable snapshots are committed inside Room transactions.
- Each edit uses compare-and-set on the expected session version; a stale edit receives `Conflict` instead of overwriting a newer draft.
- Snapshots carry SHA-256 payload hashes. The payload is protected at rest by the SQLCipher-encrypted Room database; this does not claim separate per-field encryption or attachment encryption.
- Added Arabic/English Canvas strings.

### 2.4 App wiring and localization

- `MainActivity` now composes Timeline and Canvas surfaces using the existing `TimelineApplication` database instance and Room repositories.
- Added Android `localeConfig` entries for `en` and `ar`; feature strings are present in default, English and Arabic resource folders.
- The existing offline flavor remains free of the `INTERNET` permission. No backend, analytics, model download client, advertisements or billing dependency was added.

### 2.5 Tests and CI

Added/extended tests:

- `core:timeline-api`: ordinary-day, spring-DST, fall-DST and user-creatable item rules.
- `feature:timeline`: event filter excludes tasks and proposals.
- `core:persistence`: Canvas codec round-trip and malformed payload checks.
- `core:persistence` instrumented tests: midnight overlap/boundary semantics and Canvas create/update/stale-write/archive snapshot sequence.
- CI unit-test step now includes `core:timeline-api`, `core:persistence`, `feature:timeline` and `feature:canvas` test tasks.

## 3. Verification results


| Check | Result | Notes |
|---|---|---|
| Timeline contracts and date-window code | PASS | Compiled with local Kotlin compiler and executed DST/domain smoke checks. |
| Timeline/Canvas ViewModel Kotlin sources | PASS | Compiled with local Kotlin compiler against minimal lifecycle stubs plus local coroutines JAR. |
| Canvas repository/codec Kotlin source | PASS | Compiled against minimal Room DAO/entity stubs; Arabic/English codec round-trip and trailing-byte rejection smoke checks passed. |
| Targeted unit-test methods | 7/7 PASS | Invoked through a local Kotlin harness with a minimal JUnit API shim; not run by Gradle's JUnit runner. |
| XML parsing and resource references | PASS | 20 XML files parsed; localized string-key sets match across default/en/ar for app and both features. |
| Gradle Version Catalog aliases | PASS | 31 referenced aliases resolve to declared library/plugin aliases. |
| Gradle module inventory | PASS | 10 included modules have build scripts. |
| GitHub Actions YAML | PASS | Parsed successfully. |
| Secret pattern scan | PASS | `scripts/secret-scan.py` passed. |
| Offline manifest permission check | PASS | No `android.permission.INTERNET` declaration in app manifest; flavors remain offline in this tranche. |
| Android Gradle build / KSP / Room code generation | NOT RUN | No Android SDK or installed Gradle; Gradle 9.5.0 bootstrap failed because DNS could not resolve `services.gradle.org`. |
| Instrumented Room/SQLCipher tests | NOT RUN | Android SDK/emulator unavailable in authoring environment. Tests are added to the repository and CI. |
| Compose rendering / Arabic RTL on device | NOT RUN | Requires Android build and emulator/physical-device validation. |

The checks marked PASS do not substitute for an Android assemble, KSP processing, Room schema export, Android instrumentation, or device UI verification.

## 4. Explicit Phase 2 items still open

This delivery does not yet implement:

- recurring habit schedules, recurrence exceptions or reminders;
- all-day/recurring event editing, full event editing, or Android Calendar Provider integration;
- notification scheduling, `POST_NOTIFICATIONS` onboarding, exact-alarm permissions or quiet-hour settings;
- a dedicated history screen (past entries remain accessible by navigating to earlier dates);
- multi-step Canvas step editing, Canvas attachments, Canvas-specific undo or domain-driven Canvas forms;
- prayer-time calculation/cards, validated prayer profiles or religious decision cards;
- agent-generated suggestions and their Phase 3 approval/execution workflow.

These remain subsequent implementation work and must respect the source/licensing, scholarly, authorization, privacy and device-validation gates in the architecture.

## 5. Next continuation point

1. Run `./gradlew --version` and all unit tasks on a network-enabled host with Android SDK Platform 36.
2. Run `:app:assembleOfflineSideloadDebug`, `:app:lintOfflineSideloadDebug`, all module unit-test tasks and instrumented Room/SQLCipher tests.
3. Resolve any Compose/KSP/Room compiler diagnostics before adding more Phase 2 code.
4. Commit generated `core/persistence/schemas/1.json`, resolved dependency lockfiles and reviewed dependency verification metadata after the first successful dependency resolution.
5. Verify RTL and the encrypted database on an API 36 emulator and the Samsung Galaxy S24 FE.
6. Continue Phase 2 with full item editing, user-controlled reminders/notifications, recurring habits, Canvas steps and history presentation.

## 6. Release posture

**Status: Phase 2 functional slice created; not an Android release candidate.** No APK/AAB is claimed, no Android build is claimed as passing, and the SQLCipher/Keystore production gate remains open.
