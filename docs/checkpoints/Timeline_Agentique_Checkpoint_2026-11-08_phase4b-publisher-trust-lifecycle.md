# Timeline agentique — Phase 4B continuity checkpoint

**Date:** 2026-11-08  
**Checkpoint ID:** `PHASE4B-PUBLISHER-TRUST-LIFECYCLE-001`  
**Source version:** `0.4.1-phase4b-publisher-trust-lifecycle`  
**Project root in authoring workspace:** `/mnt/data/ta-work`  
**Project Library folder:** `/Timeline_Agentique/`

## Resume rule

Continue from the complete Phase 4A `0.4.0` project plus the Phase 4B incremental modifications in this source tree. Do not recreate the app, remove Phase 1/2 Timeline/Canvas, rewrite the Phase 3 runtime/authorization or restart Phase 4A. Preserve prior immutable ZIPs and checkpoints.

## Baseline retained

- Existing modules include `app`, `core:common`, `core:timeline-api`, `core:agent-api`, `core:authorization`, `core:agent-runtime`, `core:security`, `core:localization`, `core:persistence`, `core:knowledge-api`, `core:corpus`, `core:retrieval`, `feature:timeline`, `feature:canvas`.
- Native Android/Kotlin/Compose; minSdk 26, compile/target SDK 36; JDK/bytecode 17 target.
- Local-only/offline product, no app backend/account/analytics/tracking/ads/billing. Offline manifest guard remains in CI.
- Timeline/Canvas, Phase 3 Room runtime/authorization, Phase 4A signed Corpus Pack v1 and FTS5 retrieval remain in place.

## Phase 4B additions

- Publisher keyring canonical format/parser and detached root Ed25519 signature verification.
- `PublisherKeyringManager` injected root key; no product default/fake root. Increasing version, nondecreasing generation timestamp, no key ID/public key/activation rebinding or key-history removal, immutable revocation. Refresh/install serialized by a mutex; exact stored bytes reverified and in-memory trust cleared on failure.
- `PublisherKeyringStore.writeIfCurrentVersion` contract; `RoomPublisherKeyringStore` exact payload/signature BLOB inside SQLCipher Room, transaction CAS.
- Room schema v4, 25 entities; migration 3→4 adds `publisher_keyring_state`, registered in encrypted DB factory. KSP-generated schema `4.json` is still missing until a real Android Gradle/KSP run.
- Bounded root-signed two-file keyring ZIP; SAF `content://` stream adapters for keyring and corpus pack install/update.
- Pack update requires strict version increase and expected active version; transactionally supersedes the previous installed version and installs new one. Downgrade/stale expected version rejected.
- Startup revalidation checks manifest signature/hash, active publisher time window, index hash/metadata, license profile and canonical private filesystem paths; bad records quarantined, valid records may be restored after trust update.
- Removal state `REMOVAL_PENDING` before deletion; path/symlink-safe deletion and interrupted removal recovery. Install/update/removal/orphan cleanup share one process-wide mutation mutex; long file operations use `Dispatchers.IO`. Room registry validates state transitions and row deletion only after pending state.
- `CorpusPackRuntime` ordered startup coordinator and Phase 4 schema guard in CI.

## Verification state

- Phase 4A + 4B JVM suite: **29/29 passed** under local Kotlin/JUnit/coroutine shim harness (not Gradle).
- Pack lifecycle integration harness: passed install, 1.0.0→1.1.0 update, downgrade rejection, signature tamper/quarantine/restore and removal flows (not Room/Android runtime).
- Ed25519 RFC 8032 vector passed with local Bouncy Castle 1.80.
- Room 3→4 SQL/schema guard passed using Python/SQLite and validates the exact migration SQL/table shape and BLOB read/write.
- Persistence adapters, corpus code and instrumentation source compile checks passed against temporary AndroidX/Room/JUnit shims. This is not actual Android instrumentation.
- Full Gradle build, dependency resolution, KSP Room schema generation, SQLCipher instrumentation, API 26–36 emulator and Galaxy S24 FE tests: **NOT RUN**. This environment has no Android SDK or installed Gradle and outbound Gradle dependency access failed previously.

## Release limitations

- No real publisher root key is pinned; no production imports should be enabled until a reviewed root public key and offline key custody/rotation process are supplied.
- Runtime coordinator and SAF bridge exist but are not wired into `TimelineApplication` or a user-facing management UI.
- Keyring monotonicity is database-local; no independent hardware anti-rollback anchor exists against restoring an older full DB image.
- No signed rights-cleared scholarly corpus content, no dense vector index/provider, reranker, citation UI or religious verdict workflow.
- Legal/copyright and scholarly review remain hard release gates.

## Next work — Phase 4C

1. Implement disabled-by-default publisher trust configuration and wire safe startup initialization without inventing or embedding a placeholder production root key.
2. Add user-facing corpus/keyring SAF import and pack inventory/update/quarantine/removal UI with clear trust-state explanations.
3. Add corpus management/revalidation instrumentation tests on real Room/SQLCipher API 26–36; create/review KSP `4.json`, dependency lockfiles and verification metadata.
4. Implement versioned local embedding generation/index contracts and benchmark candidate models/runtime on supported Android hardware. Dense retrieval must remain optional and must never bypass licensing/review/madhhab eligibility or source-citation validation.

Continue from the Phase 4B source and this checkpoint.

## 2026-11-08 Gradle bootstrap permission hotfix (0.4.2)

- Reported GitHub Actions failure at `./gradlew --version`: downloaded Gradle 9.5.0 `bin/gradle` existed but execution failed with `Permission denied` / exit 126.
- Root cause: Python `zipfile.extractall()` did not restore the Unix executable bit; the custom wrapper then directly executed a mode-0644 launcher.
- Fix in `gradlew`: restore ZIP POSIX file modes from `external_attr`; explicitly chmod the launcher to 0755 after extraction; repair an existing non-executable cached launcher before checking `-x`; reject extraction paths that escape the cache root.
- Added `scripts/test-gradle-bootstrap.py` with a mode-0644 cached fake launcher to reproduce and prevent regression. CI runs this test before `./gradlew --version`.
- Local verification: shell syntax, Python compile, regression test, Phase 4 schema guard, secret scan and workflow YAML check all passed.
- No Android Gradle build or actual Gradle launch was performed locally. Next action: rerun GitHub Actions and diagnose any later, independent Gradle/dependency/AGP/KSP/Room failures from their logs.
