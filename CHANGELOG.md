# Changelog

## 0.4.0-phase4-knowledge-foundation — 2026-11-08
- Continued incrementally from the immutable Phase 3.0.1 source; retained Timeline, Canvas, authorization, agent runtime and Room schema v3.
- Added `:core:knowledge-api` source/evidence/retrieval contracts, explicit four-madhhab filtering with no implicit default, Arabic/English search normalization, source eligibility gates, exact original-text hashing, and deterministic edition/locator/language evidence keys.
- Added `:core:corpus` canonical signed-manifest codec, Ed25519 verification adapter using Bouncy Castle lightweight API, publisher keyring contract, bounded ZIP importer, signed-index SHA-256 validation, fail-closed key trust, atomic app-private installation, and crash-orphan cleanup.
- Added `:core:retrieval` bundled SQLite FTS5/BM25 index validation and search, independent per-pack lexical channels, optional local dense-retriever contract, rank-only RRF fusion, provenance-aware deduplication, and explicit retrieval diagnostics.
- Added `RoomCorpusPackRegistry` on the existing `installed_corpus_pack` table; no Room schema migration (schema remains v3 / 24 entities).
- Added canonical manifest, Ed25519 RFC 8032 vector, source-eligibility, stable-evidence-identity, RRF and local retrieval fallback tests; 20 focused JVM test methods passed under the local Kotlin/JUnit/coroutine shim harness.
- Added six executable corpus-installer filesystem tests under a lightweight Context shim, covering signature/key rejection, path rejection, registry rollback and metadata retention; Android instrumentation source now contains 12 test methods across corpus/retrieval.
- Hardened pack validation to recompute every original passage SHA-256, normalized text and stable source/edition/locator/language evidence key before installation; validates external-content FTS5 postings and orphan source rows.
- Hardened retrieval to reject inconsistent stable identities/normalized text, filter future-dated license/review metadata before candidate limiting, isolate dense channel IDs and report dense-provider failure while retaining eligible lexical fallback; RRF tie ordering is input-order independent.
- Local verification includes Kotlin syntax compilation against temporary API shims, 10 focused Kotlin harness assertions, 20 shim-run JVM test methods, six shim-executed installer filesystem tests, SQLite schema/FTS5/foreign-key/integrity/time-filter smoke checks, and SHA/hash/identity fixture checks. This does not substitute for Gradle, AndroidX runtime, the resolved Bouncy Castle dependency, Android instrumentation or device tests; these remain unrun in this authoring environment.
- Release blockers: production publisher public keys are not pinned; there are no signed/rights-cleared scholarly corpus packs included; pack update/revocation management, SAF UI, local vector retrieval, reranking, citation UI and scholarly governance tooling remain outstanding.

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

## 0.4.1-phase4b-publisher-trust-lifecycle — 2026-11-08
- Continued from Phase 4A `0.4.0`; retained the complete Android application, Timeline/Canvas, Phase 3 authorization/agent runtime, signed Corpus Pack v1 and lexical retrieval modules.
- Added the canonical root-signed publisher keyring format and manager. Keyring version, generated timestamp, sorted publisher keys, raw Ed25519 public keys, activation timestamps and permanent revocation timestamps are parsed from exact canonical UTF-8 bytes; the pinned root verifies the detached 64-byte signature.
- Added monotonic keyring version compare-and-set persistence in `publisher_keyring_state` within SQLCipher Room; serialized manager refresh/install so concurrent coroutine updates cannot publish a stale in-memory key snapshot after a newer update.
- Enforced key history continuity: existing publisher key IDs/public keys/activation times cannot be rebound or removed, and an established revocation cannot be undone. A missing/invalid keyring clears in-memory trust and fails closed.
- Added strict two-entry signed keyring archive importer (`keyring.txt`, `keyring.sig`) and Storage Access Framework `content://` stream adapters for keyring and corpus-pack install/update.
- Added Room schema v4 and migration 3→4 for encrypted publisher-keyring state. Registered the migration in the SQLCipher Room factory and added an executable CI schema/migration guard.
- Added transactional corpus version replacement, `SUPERSEDED` inventory, downgrade/stale-current rejection, startup signature/index/license/path revalidation, quarantine/restore, `REMOVAL_PENDING` recovery and symlink-safe deletion. Room adapter now restricts legal state transitions and only deletes records after a pending-removal state.
- Added keyring, SAF archive, persistence lifecycle, and corpus installer update tests. Phase 4A+4B selected JVM test suite passed 29/29 using the local Kotlin/JUnit/coroutine shim harness; installer lifecycle integration harness passed; Ed25519 RFC 8032 vector passed with Bouncy Castle; Room v3→v4 SQL/schema guard passed.
- Android Gradle build, KSP Room schema generation, actual AndroidX Room/SQLCipher instrumentation, API 26+ runtime compatibility, emulator and physical-device tests were not run in the authoring environment. The keyring/persistence instrumentation source compiled against API shims but did not execute on Android.
- Release gates remain: pin a reviewed production publisher root key (none is supplied), root-key ceremony/rotation plan, signed rights-cleared and scholarly-reviewed corpus packs, app-level SAF picker/management UI and startup dependency wiring, and hardware/device migration/recovery validation. Local monotonic keyring version has no independent hardware anti-rollback anchor against restoration of an older complete database image.
