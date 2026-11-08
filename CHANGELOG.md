## 0.5.4-phase4c-sdk-metadata-validation-fix — 2026-11-08
- Fixed the CI pre-Gradle Android SDK validator to match the real Platform 37.0 `source.properties` format: `AndroidVersion.ApiLevel=37.0` with `AndroidVersion.ExtensionLevel=22`, rather than assuming an integer API level plus a separate minor-level key.
- Normalizes dotted and split API representations to a strict `(major, minor)` tuple; accepts only API 37.0, rejects API 37.1, wrong major levels, malformed values, conflicting dotted/separate minor values, missing/empty `android.jar`, missing package metadata, and SDK platform symlink escapes.
- Updated SDK validator fixtures to reproduce the exact platform metadata observed in GitHub Actions and expanded coverage to 8 tests.
- No SDK package request, compile SDK, target SDK, emulator, dependency, or Android application behavior changed. CI continues to install `platforms;android-37.0`, compile against API 37.0, and run API 36 runtime tests.
- The actual `sdkmanager` installation succeeded in GitHub Actions; only the project validator failed. This correction addresses that validator mismatch. A new hosted CI run is required to verify subsequent Gradle/KSP and unit-test stages.

## 0.5.3-phase4c-android-sdk-37.0 — 2026-11-08
- Fixed the GitHub Actions SDK Manager failure caused by requesting the nonexistent package path `platforms;android-37`; the published API 37 platform package is `platforms;android-37.0`.
- Migrated all 15 Android modules from the integer-only compileSdk setter to AGP's typed minor-API DSL: `compileSdk { version = release(37) { minorApiLevel = 0 } }`. This encodes the exact platform identity and avoids an ad-hoc SDK directory symlink.
- Added a pre-Gradle SDK verifier that checks `platforms/android-37.0/android.jar`, `source.properties`, API level 37, minor API level 0, and that the resolved platform stays inside the SDK platforms directory.
- Added six SDK-layout regression tests for correct installation, missing package, old package-name-only layout, wrong API/minor level, missing android.jar and symlink escape.
- Kept `targetSdk = 36`, `minSdk = 26`, API 36 emulator and Build Tools 36.0.0 unchanged. No app runtime behavior or database schema changed.
- Local Python guards, Room schema checks, Phase 4C checks, AGP built-in Kotlin checks, bootstrap regression and secret scan passed. Real Gradle/KSP/Android device CI must still be rerun on GitHub Actions.

# Changelog

## 0.5.1-phase4c-buildconfig-nullability-fix — 2026-11-08
- Fixed the Kotlin DSL compilation failure reported by GitHub Actions at `app/build.gradle.kts`: AGP's public `Variant.buildConfigFields` is nullable. The app now uses `checkNotNull(variant.buildConfigFields)` with an actionable configuration error before adding publisher-root BuildConfig values.
- Kept BuildConfig generation explicitly enabled and preserved both publisher-root values; no permissive fallback or silent omission was added.
- Added regression assertions to `scripts/test-built-in-kotlin.py` and `scripts/verify-built-in-kotlin.py` for the nullable Variant API guard.
- Bumped app `versionCode` to 6 and `versionName` to `0.5.1-phase4c-buildconfig-nullability-fix`.
- Local static checks pass. The actual Gradle/KGP/KSP/Room build and Android tests remain unexecuted in this authoring environment; rerun GitHub Actions to reveal the next build-stage result.

## 0.5.0-phase4c-agp9-built-in-kotlin — 2026-11-08
- Continued incrementally from the Phase 4B/0.4.3 source; preserved Timeline, Canvas, Phase 3 agent/authorization runtime, signed corpus packs, publisher trust and retrieval.
- Completed the AGP 9 built-in Kotlin migration: removed the external `org.jetbrains.kotlin.android` plugin and both temporary AGP opt-out flags; all Android modules use typed public `ApplicationExtension`/`LibraryExtension` DSL; Compose compiler uses the dedicated `org.jetbrains.kotlin.plugin.compose` plugin.
- Migrated BuildConfig publisher-root values to `ApplicationAndroidComponentsExtension.onVariants` and `variant.buildConfigFields`, removed project use of deprecated `buildConfigField()` and legacy Android variant APIs, and kept Kotlin 2.4.21 / KSP 2.3.13 / AGP 9.3.1 / Gradle 9.5.0 explicitly pinned.
- Fixed CI's dependency-resolution conflict by no longer applying `failOnDynamicVersions()` manually and explicitly deactivating dependency locking on AGP `*RuntimeClasspath` and CycloneDX BOM configurations. Root KGP/KSP buildscript classpath and all other resolvable project configurations remain locked; catalog/direct dynamic selectors are statically rejected. Explicit `--no-configuration-cache` is added to CI Gradle commands until the AGP navigation/dependency-report task serialization issue is independently resolved.
- Phase 4C adds the app-wired corpus-management feature, SAF keyring/pack import and update, lifecycle/revalidation UI, encrypted Room v5 dense-index generations/vectors and streaming bounded local embedding-index builder.
- Dense vector bytes remain inside SQLCipher Room; no sidecar plaintext vector database or source passage text is written. The builder caps a single generation at 128 MiB and the store caps total active/staging vector payload at 256 MiB. Inactive/quarantined/superseded pack generations are cleaned after trust/lifecycle changes.
- Dense retrieval remains disabled until a real, reviewed compatible on-device embedding model is provisioned. No model weights, fake model adapter, placeholder vectors, remote embeddings or rights-cleared scholarly corpus are included. FTS5 remains the functioning lexical path.
- Added AGP built-in Kotlin/public DSL, dependency-lock-policy and Phase 4C UI/encryption guards, plus Room SQLCipher dense-generation instrumentation source coverage. Actual Android Gradle/KSP, SQLCipher instrumentation, emulator and physical-device tests are still NOT RUN in the authoring environment.

## 0.4.3-phase4b-agp-kotlin-dsl-compatibility-fix — 2026-11-08
- Fixed the GitHub Actions Gradle configuration failure: AGP 9.3.1 enables the new DSL by default, which is incompatible with the project's applied `org.jetbrains.kotlin.android` plugin while the build also opts out of built-in Kotlin.
- Kept the existing external Kotlin Gradle Plugin 2.4.21 / Compose compiler / KSP toolchain and set both required AGP 9 compatibility properties: `android.builtInKotlin=false` and `android.newDsl=false`. This is a temporary AGP 9 profile; AGP 10 removes these opt-outs and requires a coordinated built-in Kotlin and KSP migration.
- Added `scripts/verify-agp-kotlin-mode.py` and wired it into CI before Gradle configuration. It verifies both properties, the pinned AGP major, the version-catalog Kotlin Android plugin, and consistent plugin application across Android modules.
- No application/runtime/Room/corpus/retrieval/authorization code changed. Static configuration tests pass locally; a real Gradle build still requires the GitHub runner because the authoring container has no installed Gradle or Android SDK.

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

## 0.4.2-phase4b-gradle-bootstrap-permission-fix — 2026-11-08
- Fixed the custom Gradle 9.5.0 bootstrap failure reported by GitHub Actions: Python ZIP extraction did not preserve the Unix executable bit on `gradle-9.5.0/bin/gradle`, causing `Permission denied` (exit 126).
- The bootstrap now restores recorded Unix modes from the checksum-verified official distribution, explicitly sets the Gradle launcher executable after extraction, and repairs an existing cached launcher before testing `-x`.
- Added `scripts/test-gradle-bootstrap.py`, which creates a realistic non-executable cached launcher and verifies that `./gradlew --version` can execute it without network access; CI runs this regression before distribution verification.
- No application, Room schema, corpus, retrieval, authorization or agent-runtime behavior changed. Full GitHub Actions Gradle build remains pending this hotfix run.

## 0.5.2-phase4c-compose-api37 — 2026-11-08

- Updated all 15 Android modules to `compileSdk = 37` to satisfy Jetpack Compose 1.12.1 AAR metadata selected by Compose BOM `2026.09.00`.
- Kept the app `targetSdk = 36`, `minSdk = 26`, API 36 CI emulator, and Android 36 runtime compatibility test lane unchanged.
- Updated GitHub Actions to install both Android SDK Platform 37 (compile) and Platform 36 (runtime/emulator), retaining Build Tools 36.0.0.
- Added `scripts/verify-compose-compile-sdk.py` and a CI step to enforce the compile/target/emulator split across all 15 Android modules.
- No application behavior, permission, API target, data schema, or Phase 4 corpus logic changed.
- CI log diagnosis: Kotlin compilation and Room KSP began; the build then failed at `:app:checkOfflineSideloadDebugAarMetadata` because 11 Compose 1.12.1 AARs require compileSdk 37. This is a compile-time metadata issue, not a Kotlin source failure.
