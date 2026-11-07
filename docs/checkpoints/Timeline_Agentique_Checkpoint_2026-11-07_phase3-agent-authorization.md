# Timeline agentique — Phase 3 continuity checkpoint

**Date:** 2026-11-07  
**Checkpoint ID:** `PHASE3-AGENT-AUTH-001`  
**Source version:** `0.3.0-phase3`  
**Project path:** `/mnt/data/agentic-islamic-timeline`  
**Library project folder:** `/Timeline_Agentique/`

## 1. Resume rule — do not start over

Continue directly from the existing Phase 3 source tree in `/mnt/data/agentic-islamic-timeline`. This is the Phase 2 project with incremental Phase 3 changes. Do not regenerate the app, discard the Timeline/Canvas modules, or overwrite the immutable Phase 1/2 ZIPs and checkpoints.

Previous release archives remain:

- `agentic-islamic-timeline-phase1-0.1.0.zip`
- `agentic-islamic-timeline-phase2-0.2.0.zip`

This checkpoint supersedes the earlier in-progress Phase 3 status messages. The latest selected pure Kotlin test run is **34/34 passed**. The Android/Gradle build has not been executed.

## 2. Current project baseline

- Native Android/Kotlin/Compose project; provisional package `app.timeline.islamic`.
- AGP 9.3.1; Gradle bootstrap 9.5.0; Kotlin Gradle Plugin 2.4.21; KSP 2.3.10; Java/Kotlin bytecode target 17; minSdk 26; compile/target SDK 36.
- Multi-module project currently includes 11 modules: `:app`, `:core:common`, `:core:timeline-api`, `:core:agent-api`, `:core:authorization`, `:core:agent-runtime`, `:core:security`, `:core:localization`, `:core:persistence`, `:feature:timeline`, `:feature:canvas`.
- Both current flavors have no INTERNET permission; Play AI Delivery is not connected.
- Phase 1/2 Timeline and Canvas code/resources remain in place.
- Room database has 24 registered entities and schema version 3.
- SQLCipher and Keystore key-envelope integration remain Android validation gates.

## 3. Phase 3 source additions and key files

### Agent contracts and task runtime

- `core/agent-api/src/main/java/app/timeline/islamic/core/agent/AgentRuntimeContracts.kt`
- `core/agent-api/src/main/java/app/timeline/islamic/core/agent/AuthorizationContracts.kt`
- `core/agent-runtime/src/main/java/app/timeline/islamic/core/agent/runtime/AgentOrchestrator.kt`

The runtime advances one bounded planning step per invocation. Task state, checkpoint, event log and lease are persisted; WorkManager scheduling is not implemented yet.

### Authorization and action execution

- `core/authorization/src/main/java/app/timeline/islamic/core/authorization/AuthorizationEngine.kt`
- `core/authorization/src/main/java/app/timeline/islamic/core/authorization/AuthorizationPolicy.kt`
- `core/authorization/src/main/java/app/timeline/islamic/core/authorization/ToolExecutionGateway.kt`

Approvals bind to proposal ID/hash. Strong confirmation is required for high-impact/sensitive/external/financial/destructive/irreversible/unknown-reversibility actions. The only persistent grant policy is `hifz.adjust_review_interval`, exact plan/revision/schedule scope, bounded delta, expiry, revocation and policy version `hifz-interval.v1`.

### Room persistence, fencing, audit, recovery

- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomAgentTaskStore.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomAuthorizationStore.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomAuditLog.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabaseMigrations.kt`
- `core/security/src/main/java/app/timeline/islamic/core/security/KeystoreAuditMacProvider.kt`
- `app/src/main/java/app/timeline/islamic/TimelineApplication.kt`
- `app/src/main/java/app/timeline/islamic/MainActivity.kt`

Execution startup validates the exact approval/grant, proposal, task and live lease owner/fencing token inside the transaction that changes `CLAIMED` to `EXECUTING`. Known outcomes can requeue only if the original fence still owns the task. Unknown/applied-unverified results interrupt and are never replayed. Recovery/verification is mutex-protected once per app process so Activity recreation does not re-reconcile a live process.

### Database migrations

- v1→v2: add nullable `pending_approval.sourceGrantId`.
- v2→v3: add nullable `action_execution.proposalId` and its unique index.
- No destructive fallback. KSP schema JSON `3.json` is not yet generated because no Android Gradle/KSP build has run.

## 4. Tests and verification at this checkpoint

### Executed locally

- **34/34 selected pure Kotlin tests passed** using the installed Kotlin 1.9 compiler and a small local test harness/shims. This is not the official Gradle/JUnit test task and does not compile Room/Android source.
- Secret-pattern scan passed.
- 21 XML files parsed.
- Version catalog TOML parsed.
- GitHub Actions YAML parsed.
- Source app manifest checked: no INTERNET permission.
- 24 Room entity registrations / database schema v3 checked statically.
- 11 included Gradle modules and project references checked statically.
- In-memory SQLite smoke test applied the migration SQL for representative v1→v2→v3 and v2→v3 table shapes.

### Not run

A real Gradle attempt failed during distribution bootstrap with `curl: (6) Could not resolve host: services.gradle.org`. No installed Gradle distribution; `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset. Android build/KSP/Room codegen, Gradle/JUnit runner, instrumentation, SQLCipher/Keystore, emulator UI/RTL and physical-device tests remain **NOT RUN**.

The instrumentation source tests exist in:

- `core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/AgentAuthorizationRoomTest.kt`
- `core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomAuditLogTest.kt`
- `core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/EncryptedDatabaseTest.kt`

## 5. Important limitations — do not overclaim

- No production OS tool adapter is registered. The app cannot currently modify Calendar/Contacts/files or share externally through Phase 3.
- No Decision Card UI or grant-authoring/revocation UI is connected.
- No model-driven `AgentStepProcessor`, LiteRT-LM, WorkManager scheduler or real action registry population.
- HMAC audit chaining has no independent anti-truncation anchor; deletion of the full tail/table cannot be proven from the local chain alone.
- The custom `gradlew` script bootstraps Gradle 9.5.0 and checks a SHA-256 value; it is not the official Gradle Wrapper JAR.
- No generated Room schema `3.json`, Gradle dependency lockfiles or dependency verification metadata yet.
- No APK/AAB has been built or verified.

## 6. Exact next actions

1. On a network-enabled host with JDK 17 and Android SDK Platform 36, run the build/test/lint tasks in `.github/workflows/android-ci.yml`.
2. Resolve actual AGP/Kotlin/KSP/Compose/Room diagnostics; do not assume compilation from static checks.
3. Generate/review/commit `core/persistence/schemas/3.json`, dependency lockfiles and `gradle/verification-metadata.xml`.
4. Run Room migration/SQLCipher/Keystore instrumentation and API 36 emulator tests, with explicit race tests for begin execution, grants and leases.
5. Continue from this codebase by adding a user-facing Decision Card approval surface and only then the first concrete action handler, always through `AuthorizationEngine` and `ToolExecutionGateway`.

**Next version should be an incremental correction/verification of `0.3.0-phase3`, not a new project.**

## 2026-11-07 CI hotfix checkpoint (0.3.1)

- Reported failure: GitHub Actions `Set preinstalled JDK 17` step starts Temurin 17.0.21 correctly, but environment-file parsing fails with `Invalid format '/usr/lib/jvm/temurin-17-jdk-amd64'`.
- Root cause: `.github/workflows/android-ci.yml` appended `$JAVA_HOME_17_X64` by itself to `$GITHUB_ENV`. The required format is `NAME=VALUE`.
- Fix: validate `JAVA_HOME_17_X64`, validate its `bin/java`, then append only `JAVA_HOME=<path>` using `printf`.
- Scope: narrow CI hotfix only; no application/runtime/authorization behavior changed.
- Next: push this archive/source and rerun GitHub Actions. A successful JDK-selection step will expose any subsequent independent CI/build failures; do not treat this fix as a full Android build verification.
