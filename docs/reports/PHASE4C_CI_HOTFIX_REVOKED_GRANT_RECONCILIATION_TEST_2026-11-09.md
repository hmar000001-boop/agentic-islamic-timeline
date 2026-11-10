# Phase 4C CI hotfix — revoked scoped-grant reconciliation instrumentation

**Date:** 2026-11-09
**Source baseline:** `0.5.15-phase4c-persistence-androidtest-compile-fix`
**Updated source:** `0.5.16-phase4c-revoked-grant-reconciliation-test-fix`
**Application version:** versionCode `20`
**Scope:** persistence Android instrumentation test and its static regression contract; no production authorization or persistence behavior changed.

## 1. Evidence from the hosted run

The 0.5.15 GitHub Actions run passed API 36 emulator startup, Gradle configuration, main Kotlin compilation, Room KSP, Android-test Kotlin compilation, test APK packaging and all four connected instrumentation targets. The app target completed 6 tests, corpus completed 8 tests, retrieval completed 1 test, and persistence ran 22 tests with one failure:

```text
AgentAuthorizationRoomTest.revokedScopedGrantIsRecheckedAtExecutorStart
expected:<0> but was:<1>
AgentAuthorizationRoomTest.kt:103
```

The failure was at the first call to `authorizationStore.reconcileExecutingExecutions(clock.now())`, immediately after `beginExecution()` correctly returned `false` for a revoked grant.

## 2. Root cause — contradictory test expectations

The old test sequence was:

1. Create a valid scoped Hifz grant and a proposal covered by that grant.
2. Claim execution.
3. Revoke the grant.
4. Assert `engine.beginExecution(claim)` is false.
5. Assert recovery returns `0` and the execution remains `CLAIMED`.
6. Call the same recovery method again at the same clock time and assert it returns `1` and changes the same record to `ABORTED_BEFORE_EXECUTION`.

Steps 5 and 6 cannot both be true without an intervening state change. The implementation explicitly reconciles `CLAIMED` executions: a claim means the external handler has not started because `ToolExecutionGateway` invokes a handler only after `beginExecution()` atomically transitions execution and outbox from `CLAIMED` to `EXECUTING`. On process startup, a leftover `CLAIMED` item is therefore safe to abort. The method returns the number of records it changed.

The first recovery call correctly returned `1`, and the test incorrectly expected `0`. This was a test expectation defect, not an authorization bypass or a recovery implementation defect.

## 3. Security/transaction trace

`RoomAuthorizationStore.beginClaimedExecution()` performs the following operations inside one Room transaction:

- Loads and verifies the execution and outbox are both `CLAIMED` and match the supplied claim.
- Re-reads the persisted proposal and approval, checking hashes, expiry and source grant.
- For a grant-backed approval, loads the grant row, maps its `revokedAtEpochMillis` to `HifzIntervalGrant.revokedAt`, decodes the canonical Hifz change and calls `grant.matches(change, now)`.
- `HifzIntervalGrant.matches()` requires `revokedAt == null`; a revoked grant returns false.
- Returns before `startExecution`, `startOutbox`, executor-start task event and executor-start audit append.

Grant revocation is also a Room transaction. If revocation commits before the executor-start transaction, that transaction observes the revoked row and cannot start. If an executor-start transaction commits first, the operation has crossed the defined execution-start linearization point; a later revocation applies to subsequent starts and cannot retroactively undo an already-authorized external effect.

## 4. Changes in 0.5.16

### 4.1 Keep production semantics unchanged

No changes were made to `AuthorizationEngine`, `RoomAuthorizationStore`, DAOs, Room entities, SQLCipher, authorization policy, task lifecycle, recovery implementation, or `ToolExecutionGateway`.

### 4.2 Strengthen the revoked-grant instrumentation test

The corrected `revokedScopedGrantIsRecheckedAtExecutorStart` now:

1. Asserts the grant revocation is persisted (`revokedAtEpochMillis != null`).
2. Asserts direct `beginExecution()` is denied.
3. Runs the real `ToolExecutionGateway` using a fake handler backed by an `AtomicInteger`; asserts `ClaimRejected` and zero handler invocations.
4. Asserts both execution and outbox remain `CLAIMED` before recovery.
5. Asserts no `ACTION_EXECUTOR_STARTED` task event and no `ACTION_EXECUTION_STARTED` audit event exist.
6. Runs recovery once and expects exactly one transition to `ABORTED_BEFORE_EXECUTION` for both execution and outbox.
7. Verifies the task is `INTERRUPTED`, its lease cannot be heartbeated, and a second recovery pass returns `0`.

This asserts the required safety property at the real side-effect gateway, not merely in an engine helper test.

### 4.3 Static regression guard and version

Extended `scripts/test-persistence-androidtest-contract.py` from 6 to 14 checks. It now guards gateway execution denial, zero side-effect handler calls, pre-recovery `CLAIMED` states, no executor-start audit/task events, one-time recovery and task/lease cleanup.

Bumped app versionCode to `20` and versionName to `0.5.16-phase4c-revoked-grant-reconciliation-test-fix`.

## 5. Verification performed in the authoring container

Passed:

- persistence Android-test source/behavior contract: 14/14;
- API 36 emulator setup regression suite: 10/10;
- English/Arabic translation tests: 6/6 and complete four-module locale verifier;
- AGP/built-in Kotlin regression suite: 11/11 and 15-module verifier;
- Android SDK metadata tests: 10/10;
- Room dense-index contract: 8/8;
- app compile/version contract: 9/9;
- Room v5 schema/migration guard, 27 entities;
- Phase 4C foundation guard;
- Compose API 37 compile SDK / API 36 target and emulator split;
- Gradle reproducibility / exact dependency-lock policy;
- Gradle bootstrap executable-permission regression;
- secret scan;
- 24 XML files, version catalog TOML, GitHub Actions YAML, shell syntax and Python compilation.

The authoring environment has no installed Android SDK, no KVM emulator and no cached Gradle 9.5.0 distribution; its network cannot resolve `services.gradle.org`. Therefore this environment did not execute the Android Gradle instrumentation test. The 0.5.15 hosted log has already demonstrated that the Android-test compilation and emulator are working; a new GitHub Actions run must confirm the corrected runtime test and all connected suites pass.

## 6. Files changed

- `core/persistence/src/androidTest/.../AgentAuthorizationRoomTest.kt` — correct recovery expectations and end-to-end no-side-effect gateway assertions.
- `scripts/test-persistence-androidtest-contract.py` — additional static regression checks.
- `app/build.gradle.kts` — versionCode 20 / versionName 0.5.16.
- `scripts/verify-app-module-compile-contract.py` — expected app version.
- `README.md`, `CHANGELOG.md`, this report and a new checkpoint.

No production app behavior, authorization implementation, Room schema/migrations, encryption, corpus trust, retrieval, Timeline or Canvas behavior changed.

## 7. Next CI action

Push 0.5.16 and rerun the same workflow:

```bash
./gradlew --no-daemon --no-configuration-cache --write-locks \
  :app:connectedOfflineSideloadDebugAndroidTest \
  :core:persistence:connectedDebugAndroidTest \
  :core:corpus:connectedDebugAndroidTest \
  :core:retrieval:connectedDebugAndroidTest
```

Expected specific behavior in the revoked-grant test: `beginExecution()` is rejected, the fake handler invocation count remains zero, the claim/outbox remain `CLAIMED` before recovery, one recovery pass aborts them, and the second pass returns zero. Then inspect the complete connected-test summary rather than inferring success from task compilation alone.
