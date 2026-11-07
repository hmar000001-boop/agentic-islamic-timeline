# Changelog

## 0.3.1-ci-fix — 2026-11-07
- Fixed the Android CI JDK selection step: `GITHUB_ENV` now receives only a valid `JAVA_HOME=VALUE` record; the previous bare-path line caused the runner to fail with `Invalid format '/usr/lib/jvm/temurin-17-jdk-amd64'` after Java 17 had already launched successfully.
- Added explicit checks that `JAVA_HOME_17_X64` is set and that its `bin/java` executable exists before exporting it.
- Verified the workflow YAML structure and shell syntax locally; full GitHub Actions / Android Gradle execution still requires the hosted runner and SDK/dependency access.

## 0.1.0-phase1 — 2026-11-06
- Materialized the actual Gradle multi-module Android project and package structure (previous workspace contained only project documentation).
- Added Room schema v1 with 24 user-state tables, DAOs and Timeline repository adapter.
- Added SQLCipher Room 2 integration candidate and Android Keystore AES-GCM database-key envelope with fail-closed recovery rules.
- Added Arabic/English resources and DataStore locale preference contract.
- Added offline/Play flavors (neither currently requests INTERNET), tests, CI and documentation.
- Build and Android instrumentation remain unexecuted here because Android SDK/Gradle/dependency resolution are unavailable in the authoring environment.
- Added task lease/event and Canvas step entities; isolated Keystore test aliases to prevent test interaction with the application database key.
- Added explicit Android backup and device-transfer exclusion resources; expanded authorization DAO with execution receipt access.


## 0.2.0-phase2 — 2026-11-06
- Added dedicated `:feature:timeline` and `:feature:canvas` modules; wired them into the existing application without replacing the Phase 1 project.
- Replaced the placeholder app shell with a Room-backed daily Timeline, local-date/week navigation, filters, event/task/habit entry, and completion/cancellation for user-owned tasks and habits.
- Corrected daily retrieval to include events overlapping midnight; added DST-aware date window boundaries.
- Added persistent Canvas workspace create/edit/archive, versioned draft encoding, compare-and-set updates, atomic session/snapshot writes, and snapshot hashes.
- Added English/Arabic feature resources and Android per-app locale metadata.
- Added date-window, filter, Canvas codec unit tests and instrumented midnight-window / Canvas snapshot tests; expanded CI to execute new unit-test modules.
- Local Kotlin-only compilation and seven targeted test methods passed using a local harness. Full Android Gradle/KSP build and device tests remain unverified because the container has no Android SDK and DNS resolution for services.gradle.org fails.


## 0.3.0-phase3 — 2026-11-07
- Added `:core:agent-runtime` bounded one-step orchestration, persistent checkpoints, state transitions and startup recovery coordination.
- Added owner/fencing-token-checked Room leases with monotonically increasing fencing tokens; released rows are retained rather than deleted.
- Implemented `AuthorizationEngine` with canonical parameter SHA-256 binding, single-proposal approvals, confirmation freshness/lifetime checks and strong confirmation for high-impact or irreversible actions.
- Added the only Phase 3 persistent grant: Hifz review-interval adjustment, exact plan/revision/schedule scope, bounded delta, revocation/expiry and active policy-version checking.
- Added `ToolExecutionGateway`, atomic `CLAIMED → EXECUTING` checks and durable execution/outbox outcomes. Claims cannot be started after lease/approval/grant expiry; cancellation attempts to persist `OUTCOME_UNKNOWN` in a non-cancellable section.
- Execution outcomes are now reported with the original `ExecutionClaim`. A known result requeues only when the same fencing token still owns the task; stale workers cannot change a newer worker's state.
- Added Keystore-backed HMAC-SHA256 audit-chain writes in the same Room transaction as authorization mutations, plus audit verification before app-level agent entry points are enabled.
- Added Room migrations 1→2 and 2→3; Room schema version is now 3 (24 entities). The generated schema JSON remains pending the first successful KSP build.
- Added unit tests for delayed executor start, grant policy-version invalidation, cancellation, verified outcome requeue, and instrumented Room tests for leases, approvals, concurrency, audit integrity and crash reconciliation.
- Local pure-Kotlin test harness: 34/34 selected tests passed; SQLite migration SQL smoke test passed. Full Android Gradle build and instrumentation remain unexecuted. Android Gradle build and instrumentation remain not run because there is no Android SDK and DNS cannot resolve `services.gradle.org`.

- Phase 3 hardening: process-scoped, mutex-protected agent recovery prevents Activity recreation from reconciling live work a second time.
- Moved Hifz grant policy constants into `core:agent-api`, removing the persistence adapter dependency on the concrete authorization engine.
- Added future-dated grant rejection, additional lease timestamp checks, and an atomic lease-release assertion when proposals enter approval wait.
- Updated local verification: 34/34 selected pure-Kotlin tests pass; Android/Room/Keystore tests are still awaiting a network-enabled Android runner.
