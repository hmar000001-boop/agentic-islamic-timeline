# Timeline agentique — Android source foundation (Phases 1–3)

**Version:** `0.3.1-phase3-ci-fix`  
**Architecture:** `Cahier_architecture_Timeline_agentique_v1.2.md`  
**Readiness:** `Rapport_final_readiness_demarrage_Timeline_agentique.md`  
**Package ID:** provisional `app.timeline.islamic`  
**Status:** incremental implementation source; not a production release.

## What is actually in this archive

- Multi-module Android project: `app`, `core:common`, `core:timeline-api`, `core:agent-api`, `core:authorization`, `core:agent-runtime`, `core:security`, `core:localization`, and `core:persistence`.
- Android API baseline: min 26, compile/target 36; Java/Kotlin bytecode target 17.
- Offline and Play-distribution product flavors. Both currently have **no INTERNET permission**; Play AI Delivery is not integrated.
- Room 2 schema v3 (**24 user-state tables; explicit migrations 1→2 and 2→3**), DAO set, Timeline repository adapter, SQLCipher Room 2 helper factory, Keystore-wrapped random database passphrase.
- Database passphrase envelope under `noBackupFilesDir`, atomic file replacement, and fail-closed handling if database exists but the envelope/key cannot be recovered.
- Room tables also cover task leases/events, Canvas steps/artifacts/snapshots, action outbox/execution receipts, Hifz records, prayer profiles, attachments, installed corpus packs and installed models.
- DataStore locale preference contract and Arabic/English resources.
- Compose application shell with edge-to-edge system-bar insets; no LLM is required for the application to run.
- Phase 2 Timeline: local-date navigation, locale-aware week strip, type filters, Room-backed event/task/habit creation, completion/cancellation of user-owned task/habit entries, and an explicit empty/error state without synthetic sample events.
- Phase 2 Canvas: create, reopen, edit and archive local workspaces; versioned draft codec; optimistic version checks; transactional snapshots; Arabic/English notes.
- Timeline date windows use the selected zone's real day boundaries (23/24/25 hours across DST); SQL query includes entries overlapping the displayed day without including boundary-touching entries.
- Arabic and English resource sets for both feature modules, Android per-app language metadata, and RTL-capable layouts.
- Unit tests for date windows, Timeline filters and Canvas draft codec; instrumented encrypted database, midnight overlap and Canvas snapshot/version tests; default-deny authorization tests.
- Phase 3 agent runtime: bounded one-step orchestration, persisted checkpoints, task event log, crash-recovery coordinator, and monotonically increasing lease fencing tokens.
- Phase 3 authorization: proposal SHA-256 binding, one-time approvals, stronger confirmation for high-impact/irreversible actions, one narrowly scoped Hifz interval grant, policy-version checking, revocation and expiry checks.
- Phase 3 execution: `ToolExecutionGateway` is the single route from a claim to a registered handler; Room atomically consumes `CLAIMED` into `EXECUTING`; execution outcomes are persisted with the exact claim/fencing identity.
- Crash policy: abandoned `CLAIMED` records are aborted before effect; abandoned `EXECUTING` records become `OUTCOME_UNKNOWN`; neither is automatically replayed. Known `VERIFIED`/`FAILED` outcomes requeue only when the reporting worker still owns the matching fencing token.
- HMAC-SHA256 audit chain using an Android Keystore key; audit verification runs before app-level agent entry points are enabled. Payload values are hashed rather than copied into the audit table.
- GitHub Actions workflow for build, unit tests, lint, merged-manifest permission audit, manually booted API 36 emulator instrumentation, dependency report, secret-pattern scan, and CycloneDX SBOM. Action references are pinned to a commit SHA.
- CI hotfix `0.3.1`: JDK setup now validates the preinstalled JDK path and writes only the valid `JAVA_HOME=VALUE` assignment to `GITHUB_ENV`; it no longer emits a bare path that GitHub Actions rejects as an invalid environment-file record.

## Honest verification status

This workspace has JDK 21 but **no Android SDK and no installed Gradle**. Outbound Maven/Gradle dependency resolution is unavailable from the authoring container. Consequently, the Android Gradle build and instrumentation suite have **not** been executed in this environment. The archive includes a Gradle 9.5.0 bootstrap script that retrieves the official distribution and verifies it against the SHA-256 value published by `services.gradle.org`; this is not the official Gradle Wrapper JAR. Run the first build on a machine with JDK 17+, Android SDK Platform 36, and network access.

Static repository checks, file inventory, manifest permission checks, script syntax checks, ZIP integrity, and SHA-256 are run before packaging. These checks do not substitute for a Gradle compile or a real-device SQLCipher/Keystore test.

## Toolchain

| Component | Version | Notes |
|---|---:|---|
| AGP | 9.3.1 | architecture baseline |
| Gradle distribution | 9.5.0 | AGP 9.3 minimum |
| Kotlin Gradle Plugin | 2.4.21 | AGP 9.3.1 compatibility range includes this version |
| KSP | 2.3.10 | documented with Kotlin 2.4.21; supports AGP 9 built-in Kotlin fixes |
| Compose compiler plugin | 2.4.21 | aligned with Kotlin plugin |
| Compose BOM | 2026.09.00 | architecture snapshot |
| Room | 2.8.5 | KSP generation |
| SQLCipher Android | 4.20.0 | Room 2 `SupportOpenHelperFactory` integration; verification gate |
| AndroidX SQLite | 2.7.0 | SQLCipher integration candidate |
| DataStore | 1.2.1 | locale preference |

AGP 9 built-in Kotlin is explicitly disabled in `gradle.properties` to allow the pinned standalone Kotlin Gradle Plugin 2.4.21 and Compose compiler plugin to be used consistently. This is a transitional compatibility choice, not an architectural dependency; migrate to AGP built-in Kotlin only after confirming the exact AGP/Kotlin/KSP/Compose toolchain in CI.

## Build

```bash
./gradlew --version
./gradlew :app:assembleOfflineSideloadDebug
./gradlew :app:testOfflineSideloadDebugUnitTest
./gradlew :app:lintOfflineSideloadDebug
./gradlew :app:connectedOfflineSideloadDebugAndroidTest
./gradlew cyclonedxBom
```

Windows:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleOfflineSideloadDebug
.\gradlew.bat :app:testOfflineSideloadDebugUnitTest
.\gradlew.bat :app:lintOfflineSideloadDebug
.\gradlew.bat cyclonedxBom
```

The wrapper bootstrap requires `curl` or `wget` (Linux/macOS) or PowerShell (Windows), and network access for the initial Gradle distribution and dependencies. The pinned SHA-256 matches Gradle’s official checksum reference. Android instrumentation requires an attached device or configured emulator.

Gradle dependency locking is enabled for all configurations, and dynamic dependency versions are rejected. This archive does **not** contain generated per-configuration lockfiles or `gradle/verification-metadata.xml`: dependency resolution could not complete in the authoring environment. Generate and review those files during the first successful network-enabled CI/build, commit them, and switch dependency verification to strict mode before release.

## Database and key recovery policy

- User database: app-private database storage; SQLCipher encrypted.
- Key envelope: `noBackupFilesDir/database-key.envelope`; excluded from Android backup by storage location. App manifest also disables backup and declares explicit full-backup, cloud-backup and device-transfer exclusions for app-private data.
- Wrapping key: non-exportable AES-GCM key in Android Keystore.
- First launch with no DB and no envelope: create a random 256-bit passphrase and persist the encrypted envelope atomically.
- Existing DB with missing envelope: stop with an explicit unrecoverable-key error; never generate a replacement key and never delete the database automatically.
- Invalid envelope, missing Keystore key, authentication failure or native SQLCipher load failure: fail closed; no plaintext fallback.
- Room schema: version 3; 24 private-state tables; no destructive migration fallback. Migration 1→2 adds `pending_approval.sourceGrantId`; migration 2→3 adds `action_execution.proposalId` and its unique index. The Room JSON snapshot is processor-generated on the first successful Gradle/KSP build and must be committed before any release branch; it is not fabricated here. Migration tests must be executed on an Android build runner.
- Sensitive user data must not be entered until the SQLCipher/Keystore instrumentation suite passes on supported API/ABI targets.

## Phase 1 boundaries

## Phase 2 boundaries

The Timeline and Canvas are now real Room-backed feature modules, but this is the first functional slice of Phase 2, not the completion of the entire Phase 2 roadmap. Not yet included: recurring habit rules, all-day/recurring event editing, Android calendar integration, notification scheduling and exact-alarm permission flows, a dedicated history screen, multi-step Canvas step editing, Canvas attachments/undo, prayer-time cards from an approved calculation engine, agent-generated decision cards, and notification preferences. The Timeline is intentionally useful without any LLM or validated prayer engine; it does not invent prayer times or agent suggestions.

Phase 3 now implements the persistent task runtime, authorization decision service, approval/grant lifecycle, atomic execution gate, Room outbox transitions and startup reconciliation. It does not yet include production OS tool adapters, a model-driven `AgentStepProcessor`, tool registry population, decision-card UI, or WorkManager scheduling. No external action type is currently registered by the app, so this tranche cannot mutate Calendar/Contacts/files or other Android resources by itself. The processor and tool-handler interfaces are extension points, not simulated integrations.

Still not implemented: corpus importer, signed packs, RAG retrieval, model manager, LiteRT-LM, OCR, ASR, prayer calculations, Hifz evaluation, notifications, recurring habits, or any religious verdict. These remain later implementation tranches and release gates. The Hifz persistent grant is intentionally the only grant type in Phase 3, for `hifz.adjust_review_interval` only, exact plan/revision/schedule fingerprint, bounded delta, 30-day maximum lifetime and active policy version `hifz-interval.v1`. Grant-authoring UI and a user-confirmation workflow to issue a grant are not wired into the application yet.

## Continuity and checkpoints

- Project folder: `/Timeline_Agentique/`
- Checkpoints: `/Timeline_Agentique/Checkpoints/`
- Every subsequent meaningful delivery must include an immutable checkpoint, ZIP and SHA-256 in the project folder. Never overwrite the previous checkpoint.

## Phase 3 boundaries

- `AgentOrchestrator` runs one bounded step per invocation; it does not spin indefinitely or keep a worker alive while waiting for the user.
- Room is authoritative for tasks, checkpoints, leases, approvals, grants, outbox and execution state. WorkManager is not wired yet.
- Every lease mutation is owner + fencing-token checked. Lease rows are retained after release so the token remains monotonic.
- An `ActionProposal` is immutable for its canonical parameter hash, action type and target. A one-time approval is bound to that exact proposal. Sensitive/high-impact/external/financial/destructive/irreversible actions need strong confirmation.
- The only persistent grant is Hifz review interval adjustment, and only for a matching plan, base revision, schedule profile, bounded absolute delta, expiry, non-revoked grant and current grant-policy version.
- The gateway calls the handler only after the atomic `CLAIMED → EXECUTING` transaction succeeds. Handler exceptions/cancellation are uncertainty, not permission to retry.
- A known action result requeues the task only if the reporting claim still matches the current fencing token. A stale worker may record its execution result but cannot mutate a newer worker's task state.
- The HMAC audit chain detects modified/reordered records and missing historical Keystore keys. It does not provide an independent anti-truncation anchor: deletion of the entire tail (or entire table) cannot be proven from the local chain alone. Do not call it tamper-proof.
- `agentRuntimeReady` is set only after full audit verification and recovery. App-level task, approval and gateway entry points check this gate. Initialization is mutex-protected and cached once per process so Activity recreation cannot reconcile a still-live `EXECUTING` operation as a crashed one. A new process performs recovery again.

## Phase 2 implementation status

| Area | Implementation | Verification in this authoring environment |
|---|---|---|
| Timeline feature module | Implemented; Room-backed date window, filters, add event/task/habit, complete/cancel | Kotlin ViewModel and domain compilation; targeted local tests invoked |
| Canvas feature module | Implemented; persistent draft create/edit/archive and immutable snapshots with compare-and-set versioning | Kotlin ViewModel/repository compilation; draft codec tests invoked |
| Localization | English + Arabic UI strings and app locale metadata | XML/resource-key checks |
| Room DAO | Midnight overlap query and atomic Canvas session/snapshot operations | Instrumented tests added, not run here |
| Android Compose compile | Not verified | Android SDK and Gradle bootstrap unavailable |

The locally invoked Kotlin test harness executed seven targeted unit-test methods successfully. This was not a Gradle/JUnit runner execution and does not replace CI. Android build, KSP/Room code generation, SQLCipher instrumented tests and actual RTL rendering must be verified on a network-enabled Android build runner/device.


## Phase 3 implementation verification

| Check | Result | Scope / limitation |
|---|---|---|
| Pure Kotlin authorization/runtime suite | **34/34 passed** | Local Kotlin compiler + lightweight harness; not Gradle/JUnit and not an Android build. |
| Secret pattern scan | Passed | `scripts/secret-scan.py`; not a full SAST or secret-provider review. |
| XML and resource parsing | Passed | 21 XML files parsed; not Android resource linking. |
| Version catalog parse | Passed | TOML syntax and declared version catalog; dependency resolution not performed. |
| Offline manifest check | Passed | Source manifest has no `INTERNET`; merged manifest remains a CI check. |
| Migration SQL smoke test | Passed | SQLite in-memory check of the two ALTER/index steps; not Room MigrationTestHelper or real legacy DB. |
| Room entity inventory | Passed | 24 entity registrations and DB version 3 checked statically. |
| Room/KSP/SQLCipher Android compile | **Not run** | Gradle bootstrap DNS failure and no Android SDK in authoring environment. |
| Room/Keystore instrumentation suite | **Not run** | Instrumentation tests are present; must run on API 36 emulator and supported physical devices. |

The 34 passing tests cover proposal hashing and approval, confirmation freshness/strength, Hifz grant scope/expiry/policy, claim/start one-shot behavior, tool gateway execution gating, cancellation/unknown outcomes, task-state transitions and verified-outcome requeue. The Room instrumentation suite adds persistence, fencing, transaction/concurrency and audit-chain cases, but remains unexecuted here.
