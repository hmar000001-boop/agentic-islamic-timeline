# Timeline agentique — Phase 4A continuity checkpoint

**Date:** 2026-11-08  
**Checkpoint:** `PHASE4A-KNOWLEDGE-001`  
**Source version:** `0.4.0-phase4-knowledge-foundation`  
**Baseline archive:** `agentic-islamic-timeline-phase3-0.3.1.zip`  
**Project Library:** `/Timeline_Agentique/`

## 1. Resume rule — incremental continuation only

Continue from this Phase 4A source tree. Do not regenerate the application or discard Phase 1/2/3 code, immutable ZIPs, reports or checkpoints. The current source is the Phase 3.0.1 baseline plus Phase 4 knowledge modules.

## 2. Current module inventory

Fourteen Gradle modules are included: `:app`, `:core:common`, `:core:timeline-api`, `:core:agent-api`, `:core:authorization`, `:core:agent-runtime`, `:core:security`, `:core:localization`, `:core:persistence`, `:core:knowledge-api`, `:core:corpus`, `:core:retrieval`, `:feature:timeline`, and `:feature:canvas`.

Phase 4 adds `:core:knowledge-api`, `:core:corpus`, and `:core:retrieval`; `:core:persistence` adds a `RoomCorpusPackRegistry` adapter over the existing installed-pack table. Room stays at schema v3 / 24 entities. Prior Timeline, Canvas, authorization, outbox, agent-task, lease and audit implementations are retained.

## 3. Implemented Phase 4A functionality

### Knowledge contracts

- Arabic/English corpus languages and QURAN/HADITH/TAFSIR/FIQH/REFERENCE types.
- License and scholarly-review states; four madhhab scopes, comparative/all-school passages and no default madhhab.
- Mandatory eligibility requiring cleared redistribution state, rights holder/license identity, positive nonfuture verification/review timestamps, nonexpired license, approved reviewer and provenance SHA-256.
- NFKC/lowercase, Arabic harakat/tatweel removal and versioned Arabic character folding for retrieval only.
- Domain-separated, length-prefixed SHA-256 identity from source ID, edition ID, locator and language. Exact original text has its own UTF-8 SHA-256.

### Signed pack import

- Canonical UTF-8 manifest parser; exact manifest bytes are signed with Ed25519.
- 32-byte raw public keys, 64-byte signatures, key activation/revocation checks and keyring interface.
- Exactly three ZIP entries allowed: `manifest.txt`, `manifest.sig`, `corpus.sqlite`; path traversal, duplicate/unexpected entries, size overflow and truncation rejected.
- Signed SQLite digest and database metadata/schema/count/language validation.
- SQLite integrity, foreign keys, FTS5 availability and external-content integrity, orphan/source/passage validity, all evidence hashes, normalized text and stable identities checked.
- Atomic same-filesystem app-private installation; Room registry is written only after the file move; registry failure rolls back. Exact manifest/signature retained with the index.
- Crash orphan cleanup helper exists but is not wired to app startup.

### Local retrieval

- AndroidX bundled SQLite FTS5, external-content index and hard SQL predicates for rights/review time, languages and madhhab.
- Per-pack BM25 rank channels; no cross-database raw BM25 comparisons.
- Optional local dense provider contract, currently no concrete vector implementation.
- Rank-only RRF; one contribution per channel/evidence, bounded consistent weights, provenance/source/edition/locator/language-aware identity and deterministic tie ordering.
- Returned hits are rechecked for pack identity, source eligibility, stable evidence key, normalized text and exact original hash.
- Retrieval trace exposes lexical/dense channels, failed/unavailable packs, rejected hits and dense-provider failure; RRF score is not confidence and reranking remains false.

## 4. Important source paths

- `core/knowledge-api/src/main/java/app/timeline/islamic/core/knowledge/CorpusContracts.kt`
- `core/knowledge-api/src/main/java/app/timeline/islamic/core/knowledge/CorpusTextNormalizer.kt`
- `core/corpus/src/main/java/app/timeline/islamic/core/corpus/CorpusPackManifestCodec.kt`
- `core/corpus/src/main/java/app/timeline/islamic/core/corpus/PackTrust.kt`
- `core/corpus/src/main/java/app/timeline/islamic/core/corpus/CorpusPackInstaller.kt`
- `core/corpus/src/main/resources/corpus-index-v1.sql`
- `core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/BundledSqliteCorpusIndex.kt`
- `core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/LocalCorpusRetriever.kt`
- `core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/RrfFusion.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomCorpusPackRegistry.kt`
- `docs/phase4/corpus-pack-format-v1.md`
- `docs/phase4/phase4a-implementation.md`

## 5. Checks executed at this checkpoint

- 20 focused Phase 4 JVM test methods passed through local Kotlin/JUnit/coroutine shims.
- 6 corpus installer filesystem tests passed on the actual installer code using a lightweight `Context` shim and fake cryptographic/index dependencies.
- All 12 Android instrumentation test methods across three source files compile syntactically against shims; **none have been executed on Android**.
- Phase 4 main Kotlin code compiles against temporary API shims; `RoomCorpusPackRegistry` compiles against a DAO/entity shim.
- SQLite 3.46.1 schema smoke passes: app ID, user version, DELETE journal, integrity, foreign keys, Arabic FTS5 result, time eligibility filtering and detection of deliberately corrupted external-content postings.
- Secret scan passes; version catalog parses; 21 XML files parse; all 14 included Gradle modules have build files; source manifests omit INTERNET.

## 6. Explicit unverified items

The authoring environment has no installed Gradle distribution or Android SDK; Gradle bootstrap DNS for `services.gradle.org` fails. Thus Android Gradle/AGP/Kotlin/KSP build, official Gradle/JUnit tasks, Room code generation, real AndroidX bundled SQLite execution, actual Bouncy Castle jar integration, Android instrumentation, SQLCipher/Keystore instrumentation, emulator tests, and Galaxy S24 FE tests remain **NOT RUN**. The local shim checks must not be described as those tests passing.

There is no production publisher public keyring, production signed content, private key, legal license evidence, or scholarly review workflow bundled. No signed Quran/Hadith/Tafsir/Fiqh corpus is supplied. No APK/AAB or CI success is claimed.

## 7. Next actions — in sequence

1. Execute the updated GitHub Actions workflow on a network-enabled runner with JDK 17 and SDK Platform 36; fix actual dependency/API/compiler diagnostics.
2. Generate/review Room schema `3.json`, dependency lockfiles and Gradle verification metadata through the real build.
3. Validate the actual Bouncy Castle Ed25519 API and AndroidX SQLite bundled runtime on API 26+ and API 36; run the 12 instrumentation methods on emulator/device.
4. Implement pinned publisher keys, signed rotation/revocation and installed-pack revalidation.
5. Implement reproducible corpus build/signing, update transactions, SAF/import UI and startup orphan recovery.
6. Implement and benchmark local vectors; only then decide whether a reranker is justified.
7. Add Timeline/Canvas evidence cards and correction/report controls. Religious features remain blocked until rights, scholarly and retrieval gates pass.

**Resume from this checkpoint and this archive; do not start from scratch.**
