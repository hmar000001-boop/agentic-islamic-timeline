# Timeline agentique — Phase 3 implementation report

**Date:** 2026-11-07  
**Source baseline:** `0.2.0-phase2` (continued in-place; earlier Phase 1/2 archives retained)  
**Source version:** `0.3.0-phase3`  
**Package ID:** provisional `app.timeline.islamic`  
**Architecture:** `docs/reference/Cahier_architecture_Timeline_agentique_v1.2.md`

## 1. Continuity and scope

This delivery continues the existing project at `/mnt/data/agentic-islamic-timeline`. The Phase 2 Timeline/Canvas implementation and its Room schema were retained. This tranche adds the persistent agent-task runtime and authorization/execution platform on top of the existing Room tables. It does not recreate the Android application and does not replace prior ZIPs or checkpoints.

Room schema advances from v1 to v3 through explicit migrations; it remains at 24 entities. No destructive migration fallback was introduced.

## 2. Implemented

### 2.1 Persistent agent runtime

- Added typed agent task, step, checkpoint, lease, event, recovery and state-machine contracts in `core:agent-api`.
- Added `core:agent-runtime` with a bounded one-step `AgentOrchestrator`. One call executes one planner step and yields; it does not loop indefinitely or hold a worker while waiting for user input/approval.
- Checkpoints are versioned and their payload hash is checked before persistence.
- Task state is persisted in Room. The runtime reads the latest durable checkpoint rather than relying on process memory.
- Added startup reconciliation for expired task leases, expired pending approvals and abandoned execution/outbox records.

### 2.2 Lease ownership and fencing

- Implemented Room-backed leases with owner identity and monotonically increasing `fencingToken`.
- Lease rows are retained after release; deleting and recreating a lease cannot reset its token to the initial value.
- Acquire, heartbeat, release, checkpoint and task transition paths validate the task's lease identity and timestamp bounds.
- When a known action outcome is reported, the task is requeued only if the reporting claim still owns the current fencing token. If a newer worker has acquired the task, the stale worker can persist its execution result but cannot change the task or release the newer lease.

### 2.3 Authorization engine

- Added `AuthorizationEngine` with app-computed SHA-256 binding over canonical parameter bytes. Empty/oversized parameter payloads and changed hashes are rejected.
- One-time approvals are bound to the exact proposal ID/hash and have freshness and maximum-lifetime checks.
- High-impact, sensitive, external, financial, destructive, irreversible or unknown-reversibility actions require strong confirmation. Ordinary mutations still require a one-time approval by default.
- Approval/rejection and task state changes are performed in Room transactions.
- The persistent-grant path is narrowly limited to `hifz.adjust_review_interval`: exact plan ID, base revision and schedule-profile fingerprint; bounded delta; non-revoked state; expiry; and current policy version `hifz-interval.v1`.
- Grant policy constants now live in `core:agent-api`; `core:persistence` no longer depends on the concrete authorization implementation.
- Grants cannot authorize a change before their creation time. Grant issue UI and its user-confirmation workflow are not wired into the app in this tranche.

### 2.4 Execution gateway, outbox and crash behavior

- Added `ToolExecutionGateway` as the only path from an `ExecutionClaim` to a registered action handler.
- A missing handler or parameter-hash mismatch is rejected before the external handler is invoked.
- `beginClaimedExecution` revalidates proposal hash/target/type, approval or grant, task state, lease owner/fencing token and real current expiry in the same Room transaction that changes the execution and outbox from `CLAIMED` to `EXECUTING`.
- Claim creation alone never invokes a handler. A claim that expires before `EXECUTING` can be safely aborted.
- `CLAIMED` after process death is reconciled as `ABORTED_BEFORE_EXECUTION`; `EXECUTING` after process death becomes `OUTCOME_UNKNOWN`. Neither is automatically replayed.
- Handler exception/cancellation is treated as uncertainty. Cancellation attempts to persist `OUTCOME_UNKNOWN` inside `NonCancellable`; if that write cannot complete, startup reconciliation handles the still-`EXECUTING` row without replay.
- Result recording is bound to the full `ExecutionClaim`, including worker identity and fencing token. Known results (`VERIFIED`, `FAILED`) requeue only if the same fence is still current; ambiguous results interrupt the task.

### 2.5 HMAC audit chain and startup gate

- Added HMAC-SHA256 linked audit records using an Android Keystore key.
- State changes and their audit records are committed within the same Room transaction.
- Audit records contain payload hashes rather than raw user/private payload values.
- Before agent features become available, the app verifies the audit chain and reconciles task/outbox state. Failure is fail-closed for agent actions; Timeline/Canvas remain available.
- Initialization is mutex-protected and cached once per Android process. Activity recreation does not re-run reconciliation against an operation that is still legitimately executing in the same process. A new process performs verification/recovery again.
- **Audit limitation:** the local HMAC chain detects modified/reordered records, sequence gaps and missing historical Keystore keys. It has no independent external anti-truncation anchor; deleting the entire tail or table cannot be proven from the remaining local chain alone. It is not described as tamper-proof.

### 2.6 Room schema and dependencies

- Room remains at 24 entities and advances to schema v3.
- Migration 1→2 adds nullable `pending_approval.sourceGrantId`.
- Migration 2→3 adds nullable `action_execution.proposalId` and a unique index.
- No Room JSON snapshot is fabricated. KSP must generate and the team must review/commit schema v3 after the first successful Android Gradle build.
- Dependency direction was corrected: shared grant/approval policy limits are in `core:agent-api`, so persistence does not depend on `core:authorization`.

## 3. Tests and verification

### Local tests actually executed

| Check | Result | What it proves / does not prove |
|---|---|---|
| Selected pure Kotlin authorization/runtime test suite | **34/34 passed** | Compiled with the installed Kotlin 1.9 compiler and a lightweight local test harness/shims. It is not the official Gradle/JUnit runner. |
| Secret-pattern scan | PASS | `python3 scripts/secret-scan.py`; not a full SAST review. |
| Android XML parsing | PASS | 21 XML files parse; not Android resource linking. |
| Version catalog TOML parsing | PASS | TOML syntax is valid; no dependency resolution. |
| GitHub Actions YAML parsing | PASS | YAML parses; workflow has not run on GitHub. |
| Offline source manifest check | PASS | Main source manifest has no INTERNET declaration; merged-manifest check remains a CI job. |
| Room entity inventory | PASS | 24 entity registrations and database schema version 3 confirmed statically. |
| Gradle project-reference check | PASS | 11 included Gradle modules have build files and project dependency references resolve to included modules. |
| Migration SQL smoke check | PASS | The SQL steps run in an in-memory SQLite exercise for representative v1→v2→v3 and v2→v3 shapes. This is not Room `MigrationTestHelper` or a legacy database migration test. |
| Kotlin test source compile | PASS | The selected API/engine/gateway/orchestrator tests compile and execute in the local harness. Does not compile Android/Room sources. |

### Android build/tests not executed

A real Gradle attempt was made on 2026-11-07:

```text
./gradlew :core:authorization:testDebugUnitTest :core:agent-runtime:testDebugUnitTest :core:persistence:assembleDebug --stacktrace
curl: (6) Could not resolve host: services.gradle.org
```

The authoring environment has JDK 21 but no installed Gradle distribution and no Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` unset). Consequently these remain **NOT RUN**:

- Android Gradle configuration/build and app assemble/lint;
- KSP/Room compiler processing and generated schema v3 JSON;
- Gradle/JUnit unit-test tasks;
- Room/SQLCipher instrumented tests;
- Keystore HMAC and key invalidation tests on Android;
- emulator/device concurrency tests;
- Compose runtime/rendering and Arabic RTL acceptance;
- Galaxy S24 FE physical-device acceptance.

The archive retains the CI workflow for a network-enabled GitHub runner with JDK 17 and Android SDK Platform 36. Static checks and the local harness are not substitutes for that CI run.

## 4. Explicit Phase 3 boundaries

Not included in this delivery:

- production Android OS action adapters (Calendar, Contacts, file operations, sharing, notifications);
- registration of real action handlers in the tool registry;
- user-facing Decision Card approval/rejection UI;
- user-facing UI and explicit confirmation path to issue/revoke persistent Hifz grants;
- a production model-driven `AgentStepProcessor` or LiteRT-LM integration;
- WorkManager scheduling/heartbeat/lease orchestration;
- actual external action execution; the handler registry is empty in the app;
- automatic resumption policy/UI for interrupted tasks;
- signed Corpus Packs, RAG, model import/runtime, OCR, ASR or religious-domain calculations.

The interfaces are real extension points, not simulated integrations. At present the app cannot mutate Calendar/Contacts/files or another Android resource through this code tranche because no production action handlers are registered.

## 5. Immediate continuation point

Continue from this exact source tree and do not recreate it.

1. On a host with network, JDK 17 and Android SDK Platform 36, run `./gradlew --version` and the complete CI workflow.
2. Fix actual AGP/Kotlin/KSP/Compose/Room compiler diagnostics, if any.
3. Generate/review/commit `core/persistence/schemas/3.json`, dependency lockfiles and `gradle/verification-metadata.xml`; the current authoring environment could not generate them.
4. Run all unit-test tasks and `:core:persistence:connectedDebugAndroidTest` plus app instrumentation on API 36.
5. Specifically verify Room migrations 1→2→3, SQLCipher open/close/reopen/recovery, monotonic lease fencing, simultaneous `beginExecution`, grant revocation/expiry races, atomic execution/outbox/audit writes and audit-key invalidation.
6. Only after those tests pass, connect the first concrete tool adapter and Decision Card UI; keep all mutations routed through the same approval and execution gates.

## 6. Release posture

**Status: Phase 3 source implementation created; not a release candidate.** No APK/AAB is claimed. No Android build, Room/KSP code generation, SQLCipher/Keystore instrumentation or physical-device test is claimed as passed. Sensitive production data should not be entered until the encryption/recovery instrumentation gate is green.
## CI hotfix 0.3.1 — JDK environment-file format

The supplied GitHub Actions log reaches `java -version` successfully using Temurin 17.0.21, then fails when the runner parses `$GITHUB_ENV`. The former workflow wrote the bare value of `JAVA_HOME_17_X64` as a line before writing `JAVA_HOME=<value>`. GitHub Actions environment files require `NAME=VALUE` records, so the bare path was rejected. The workflow now checks that the variable is non-empty, checks that `$JAVA_HOME_17_X64/bin/java` is executable, and writes only `JAVA_HOME=<value>` using `printf`.

Local verification confirms the malformed bare-path write is absent and the shell block parses. This corrects the exact reported failure; it does not establish that later Gradle, SDK, KSP, SQLCipher or instrumentation steps pass. Those remain to be verified by the next hosted CI run.
