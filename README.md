# Timeline Agentique

## Governance and release evidence

See [`docs/governance/`](docs/governance/README.md) for the source-rights register, scholarly review record, threat model, change-evidence policy and explicit release gates. A CI-generated offline sideload debug APK is a validation artifact, not a signed production release.

**Current source:** `0.7.0-phase6-domain-engines` (versionCode 25)
**Current phase:** Phase 6 domain-engine foundation implemented in source; actual Gradle/KSP build, SQLCipher/API 36 instrumentation, user-device testing and scholarly rule-pack acceptance remain open. Phase 5C hosted/device acceptance is also pending from version 0.6.3.
**Previous baseline:** Phase 0–4 implementation from 0.5.16; preserved incrementally.

### 0.7.0 CI hotfix — Room v7 migration SQL verifier receiver (2026-11-09)

The hosted workflow failed in `check-phase4-schema.py` before executing its SQLite migration simulation because the checker looked only for `database.execSQL(...)`, while the actual `AppDatabaseMigrations.kt` methods use the parameter name `db`. The checker now parses both valid receiver spellings, tests escaped Kotlin string literals, validates the complete ordered Room 1→2→3→4→5→6→7 chain and confirms that the app factory uses SQLCipher with the Keystore-managed passphrase. No Room migration or app code changed. Local SQLite fixture and source-contract checks pass; real SQLCipher/Room API 36 instrumentation remains a CI/device acceptance gate. See `docs/reports/ROOM_V7_SCHEMA_GUARD_FIX_2026-11-09.md`.

### 0.7.0 CI hotfix — cross-module thermal status smart cast (2026-11-09)

The hosted Phase 6 unit-test command now passes `:core:model-manager:testDebugUnitTest`, confirming the prior JUnit 4 initialization fix. Compilation then exposed a Kotlin smart-cast error when the app read nullable `ModelResourceSnapshot.thermalStatus` directly across a module boundary. Both application runtime preflight and Model Manager now snapshot the nullable value once into a local immutable variable and use that stable value for the threshold decision. Added null-status unit coverage and a CI source guard. Also cleaned the non-fatal compiler warnings from the supplied run (publisher keyring null assertions, deprecated Material3 Divider, prayer-test null assertion, migration override parameter names, and authorization lease assertion). No domain behaviour, schema, dependency, offline policy or app version metadata changed.

### 0.7.0 CI hotfix — JUnit 4 Model Manager test signature

Hosted CI reached the Model Manager unit-test task but JUnit 4 rejected `ModelManagerTest` during class initialization. The cancellation test's expression body returned Kotlin `Unit` because its final expression was a nullable `Job?.cancel()` call; JUnit 4 requires JVM `void`. It now uses a block body, preserving all test behavior, and a dedicated CI source guard protects the signature contract. No production code or app version metadata changed.

### 0.6.2 CI hotfix — Android directory fsync API

The first hosted Phase 5C unit-test build reached `:core:model-manager:compileDebugKotlin` and failed because `OsConstants.O_DIRECTORY` is not exported by Android's public SDK. The directory-sync code now opens with public `O_RDONLY`, verifies the directory path and opened descriptor with `lstat`/`fstat` and `S_ISDIR`, checks device/inode identity, then retains `fsync` and guaranteed close. A separate CI contract rejects hidden constants and missing durability checks. See `docs/reports/PHASE5C_CI_HOTFIX_ANDROID_DIRECTORY_SYNC_2026-11-09.md`.

## Phase 5C — dense retrieval completion candidate (0.6.3)

- Connects the verified LiteRT-LM EmbeddingGemma 2 query adapter to the existing SQLCipher Room dense-index store and `LocalCorpusRetriever`; FTS5/RRF remains active and is not removed or replaced.
- Dense generations are bound to the exact corpus manifest SHA-256, model artifact SHA-256, model revision, output dimension and an explicit embedding preprocessing profile. A model/hash match alone is insufficient.
- Room schema is now v6 (27 entities) in the Phase 5C baseline; Phase 6 advances it to v7 (28 entities). Migration 5→6 adds the corpus-manifest and embedding-profile columns and invalidates all v5 dense generations/vectors because they lack those compatibility fingerprints. Signed corpus packs, FTS5 tables, authorization, user data and publisher trust state are preserved.
- Index building reads only currently cleared, unexpired, reviewed corpus passages within the model's pre-prefix character budget. Before embedding, the SQLite source rechecks each passage's stable evidence identity, original-text SHA-256 and canonical normalized text; it stores only quantized vectors plus stable evidence keys in encrypted Room. The vector rows contain no source text.
- Query scanning is exact cosine over stable-key ordered pages (2,048 vectors/page) with a bounded top-k heap. It reads only eligible stable keys while scoring and loads original citation-bearing source text only for the final top-k; hashes, normalizer output, pack identity, language and madhhab are rechecked before return.
- Dense search is available only when the exact installed EmbeddingGemma artifact and an ACTIVE generation match all fingerprints. No model, no compatible generation or any dense integrity/runtime failure leaves FTS5/RRF available. RRF remains a ranking utility, not a confidence/probability score.
- Query embeddings are normalized once per request before the vector scan; only cleared, approved, in-date sources are scored. Out-of-scope language/madhhab rows and currently ineligible sources are skipped, while malformed/missing provenance fails the dense channel closed.
- The corpus screen exposes explicit per-pack dense-index build/rebuild and local evidence search, displaying original source passage, locator, edition, language, contributing channels and a clearly non-probabilistic RRF ranking score. Search is disabled until a verified publisher keyring is ready.
- Dense search respects generation runtime lifecycle and RAM/storage/thermal preflight. It never silently unloads a user-loaded generation model; lexical FTS5 remains available when dense inference is blocked.
- A standalone Kotlin/JVM harness compiled the production retrieval and index-builder sources and executed 18 focused unit tests (18/18). A separate harness compiled the production EmbeddingGemma adapter against API-shaped LiteRT-LM stubs and passed 11/11 inference policy tests. Static Phase 5C source contracts pass 33/33; Room migration/schema guard and persistence contracts pass 12/12. These checks do not substitute for Gradle/KSP/Compose, real SQLCipher or API 36 connected tests.


## Phase 6 — deterministic Islamic domain engines (0.7.0)

- Adds seven separate offline domain libraries: Prayer, Hifz, Travel/Qibla, Halal evidence, Zakat arithmetic, Inheritance intake and Community snapshot import. Their engines are deterministic Kotlin; no model is used as a religious authority.
- Adds `:core:domain-api` and encrypted SQLCipher Room `domain_workspace` drafts. Drafts use a bounded deterministic payload codec, SHA-256 integrity, revision compare-and-set updates and explicit deletion. Room advances to v7 (28 entities) with additive migration 6→7.
- Prayer calculations require explicit coordinates, IANA timezone, twilight angles, Asr factor and high-latitude policy; the supplied profile is custom and has not been independently validated against a named calculation authority. High-latitude night fractions use elapsed instants across DST transitions.
- Hifz uses stable verse/edition references and user-rated spaced review. It does not provide Quran text, voice recognition or tajwid grading. References are not resolved against an installed verified Mushaf in this tranche.
- Travel supports an explicit route segment with timezone/DST-safe local-time resolution and Qibla bearing. Qasr/jam' guidance remains unavailable without an independently reviewed madhhab-specific ruleset.
- Halal collects ingredient and certification evidence but never returns a halal/haram verdict. User-submitted certificate metadata is not independent source verification.
- Zakat currently exposes a cash/liability arithmetic preview with manually entered valuation, nisab, rate and haul dates/duration. Other asset classes and reviewed madhhab-specific rules are not implemented. Even below-threshold and not-yet-complete-haul outcomes remain an explicitly unreviewed arithmetic preview, not a religious status.
- Inheritance captures jurisdiction, explicit madhhab and family/estate facts, and permits only a confirmed provisional arithmetic remainder. No shares or distribution solver is enabled; the user must describe and attest to all potentially relevant heir categories, including those not represented by dedicated fields. A reviewed rule set is still absent.
- Community imports versioned local event snapshots with provenance/freshness checks. The `TL-COMMUNITY-V1` header carries a source-claimed capture timestamp separately from import time; the user chooses the age threshold for stale status. Source names/references and timestamps are user-supplied metadata, not cryptographic authentication or proof of event accuracy. This is not a live directory, presence service, messaging or remote notification system.
- A bilingual Compose domain hub is wired into app navigation. Some result details remain technical English; Arabic/English resource completeness and Compose compilation must be verified in CI.
- Local pure-Kotlin compilation, custom harness and test-shaped JVM runner results are recorded in the Phase 6 report. They are not a substitute for Gradle/AGP/KSP, SQLCipher/Room instrumented tests or device validation. No `7.json` Room schema snapshot is fabricated; CI must generate and validate it using KSP.

## Phase 5B — LiteRT-LM runtime (0.6.1)

- Exact dependency pin: `com.google.ai.edge.litertlm:litertlm-android:0.19.1`; no dynamic `latest.release` selector.
- `:core:inference-runtime` isolates native inference from the app and Model Manager. The app still targets Java 17 bytecode; the CI Gradle JVM uses JDK 21 for the runtime artifact family.
- Text generation loads only a verified catalog-installed Gemma 4 artifact, uses LiteRT-LM CPU backend, one single-thread native owner, a bounded 4,096-token KV cache, per-request output cap, one-shot conversations and cancellation/close handling.
- No tools are registered and `automaticToolCalling=false`; model-emitted tool calls are rejected. State-changing actions are not exposed through this runtime.
- EmbeddingGemma 2 uses the LiteRT-LM retrieval prefixes (`task: search result | text:` for indexed text and `task: search query | text:` for queries), batches of at most 24 and normalized 768-dimensional vectors. The native engine input is capped at 8,192 tokens; the raw text character budget reserves room for the longest task prefix (8,164 characters), with runtime tokenization still enforcing the native token cap.
- The Models screen exposes explicit CPU load, runtime smoke test and unload. Generation and embedding engines are serialized so two large model weights are not held resident together.
- No Internet permission, model download path, cloud inference or GPU/NPU backend was added. Phase 5C advances Room from v5 to v6 with exact corpus/model fingerprints; lexical FTS5/RRF remains the fallback when dense retrieval is unavailable.
- CI now runs inference-runtime JVM tests, API 36 API-shape instrumentation, lint and the static Phase 5B contract. Hosted Gradle/native validation and Galaxy S24 FE measurements are still required.

## Phase 5A — implemented foundation

- Added an on-device Model Manager backed by the existing SQLCipher Room `installed_model` table; no database schema migration was required.
- Added three immutable, digest-pinned LiteRT-LM catalog entries: Gemma 4 E2B IT, Gemma 4 E4B IT and EmbeddingGemma 2 Text 270M. Each catalog item fixes the repository, immutable revision, exact artifact filename, exact byte length, SHA-256 and Apache-2.0 license identifier.
- Added a user-initiated Android Storage Access Framework (`content://`) importer. It streams into a `.partial` file, enforces the exact maximum and final byte count, computes SHA-256 incrementally, fsyncs the file, then renames the verified artifact into app-private storage.
- Model files live under `noBackupFilesDir/on-device-models`; model paths are derived only from catalog identifiers and revisions, never from provider-supplied filenames. Metadata is in SQLCipher Room; weight files are app-private sandbox files excluded from backup but are not individually SQLCipher-encrypted in Phase 5A. No model URL is fetched by the app, no URI permission is retained, and no network permission was added.
- Added lifecycle states `INSTALLING`, `INSTALLED`, `QUARANTINED` and `REMOVAL_PENDING`, compare-and-set Room transitions, interrupted-install/removal recovery, tamper detection and quarantine. Failed deletion keeps `REMOVAL_PENDING` until every model-owned file is gone; recovery cannot delete the registry row ahead of bytes. Model removal does not touch corpus packs, SQLCipher user data or dense-index generations.
- Added device preflight measurements for total/available RAM, Android low-memory status, process PSS, available private storage, memory class and thermal status. These are resource estimates only: preflight never reports a model as loaded or runtime-ready.
- Added the Models screen and Arabic/English resources. A file imported and hash-verified is explicitly labelled as **not active** until real LiteRT-LM initialization and an on-device load test succeed. If inventory cannot be read, the UI shows an unknown state and does not offer another import.
- The timeline and FTS5 lexical retrieval remain available without any installed model. No new app runtime requirement depends on Phase 5.

## Phase 5B — implemented runtime foundation; validation pending

- LiteRT-LM Android `0.19.1` is pinned and isolated in `:core:inference-runtime`; the Gradle JVM is selected as JDK 21 while Android bytecode targets remain Java 17.
- Gemma 4 E2B/E4B load through the CPU backend only, with a 4-thread owner dispatcher, 4,096-token KV cache, bounded prompt/output, per-request conversation and explicit cancellation/unload. No tool provider is registered and automatic tool calling remains disabled.
- EmbeddingGemma 2 implements the Phase 4 `LocalTextEmbeddingModel` contract with LiteRT-LM's asymmetric search-result/search-query prefixes, max batch 24, requested normalized 768-d vectors and strict finite/dimension/norm checks.
- `TimelineApplication` exposes explicit model loading, smoke test, unload, dense-index construction and one-off query embedding service methods. Generation and embedding are serialized so two multi-GB model engines are not resident together.
- In the 0.6.1 Phase 5B baseline, FTS5/RRF was the only active retrieval route. Phase 5C (0.6.3) now adds an optional, fingerprint-checked dense channel; FTS5 continues to run and remains the fallback.
- Phase 5B CI now includes inference unit tests, API-shape instrumentation, lint and static checks, but this authoring environment cannot execute the real Gradle/Android SDK/native runtime build. The first hosted CI run for 0.6.1 and an actual Galaxy S24 FE model-load/performance run are still required.

## Next development stages

- Phase 6 is the current 0.7.0 implementation tranche. Phase 5C hosted/device acceptance is tracked separately and must be closed with the user's CI/device results; it does not block incremental Phase 6 source development.
- Once the Phase 6 foundation is reviewed in GitHub Actions, implement remaining domain scope and use the user's CI feedback to correct actual compiler, KSP and instrumentation findings.
- Then perform the requested global completion pass across Phases 1–6 against their original acceptance criteria, including authorization, workflows, accessibility, localization, resilience, data rights, security and device validation. Start Phase 7 only after that gap-closure pass.
- Earlier Phase 5D ideas (agent proposal-generation wiring, optional OCR/audio and model quality evaluation) remain separate follow-up work; they are not silently represented as complete by Phase 5C or Phase 6.

## Phase 5C implementation and acceptance boundary

See [`docs/phase5/phase5c-dense-retrieval.md`](docs/phase5/phase5c-dense-retrieval.md) for the architecture, schema migration, exact identity rules, UI flow, safety behaviour, tests and remaining acceptance gates. Phase 5C is implementation-complete in this source tree but is **not release-certified** until GitHub Actions completes Android compilation/unit/lint and API 36 SQLCipher/bundled-SQLite instrumentation, and the exact EmbeddingGemma artifact has been exercised on the Galaxy S24 FE.
