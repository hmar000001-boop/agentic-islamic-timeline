# Phase 4A implementation report — knowledge and retrieval foundation

**Date:** 2026-11-08  
**Version:** `0.4.0-phase4-knowledge-foundation`  
**Baseline:** Phase 3.0.1 CI hotfix archive  
**Project:** Timeline agentique Android, continued in-place

## Delivery scope

Phase 4A adds the first real offline knowledge-pack and lexical retrieval foundation without recreating the application or modifying prior Phase 1–3 behavior.

Three modules are added:

- `:core:knowledge-api` — typed language/source/license/review/madhhab contracts, immutable evidence records, eligibility rules, Arabic/English normalization and deterministic citation identity.
- `:core:corpus` — canonical signed-manifest parser, Ed25519 verifier adapter, publisher-key trust contract, bounded ZIP importer, signed SQLite digest check, FTS/metadata validation, atomic private installation and orphan cleanup.
- `:core:retrieval` — AndroidX bundled SQLite FTS5 search, source eligibility predicates, independent per-pack lexical channels, optional local dense-provider interface, provenance-aware deduplication and rank-only Reciprocal Rank Fusion.

`:core:persistence` gains `RoomCorpusPackRegistry`, reusing the existing `installed_corpus_pack` table. Room remains schema version 3 with 24 entities; there is no Phase 4 Room migration.

## Data and trust boundaries

- The app accepts only a ZIP with exactly `manifest.txt`, `manifest.sig` and `corpus.sqlite` at its root. It rejects duplicate, nested, unexpected, oversized and truncated entries.
- The Ed25519 signature binds the exact canonical UTF-8 manifest bytes; the manifest binds the SQLite SHA-256. Keys must come from an app-controlled publisher keyring. The app does not accept a publisher key supplied by the pack.
- The imported index is validated before atomic rename into app-private storage. Registry commit failure rolls the directory back. Signed manifest and signature are retained for future revalidation.
- Corpus source records must have redistribution clearance and approved scholarly review. Legal/provenance and reviewer metadata is mandatory. This is an eligibility gate, not proof that a source is actually licensed; legal and scholarly governance remain external release gates.
- Original source text remains exact and immutable. The importer recomputes original-text SHA-256, deterministic source/edition/locator/language identity and normalized search form for every passage. Retrieval repeats these checks for returned evidence.
- Source and optional metadata sizes are bounded. The index validator checks SQLite identity, schema version, integrity, foreign keys, orphan records, FTS5 external-content consistency, pack metadata, counts, languages and temporal eligibility.
- FTS5 uses AndroidX bundled SQLite rather than depending on the framework SQLite version shipped by a particular device. Corpus indexes are separate from the encrypted Room user-state database.
- Each pack's BM25 ordering is an independent lexical channel. Raw BM25 scores from different SQLite databases are never compared. RRF uses one-based ranks, bounded/consistent channel weights and stable evidence identity. It never presents an RRF score as probability or confidence.
- A dense provider is only an interface at this stage. Provider errors are surfaced in the retrieval trace while eligible lexical fallback is retained. No vector model/index or reranker is claimed.

## Verification executed in this environment

| Check | Result | Scope / limitation |
|---|---:|---|
| Phase 4 production Kotlin source compilation | PASS | Kotlin 1.9 against temporary Android/AndroidX SQLite/coroutine/Bouncy Castle API shims; not the real dependency compile |
| Focused JVM unit methods | **20/20 PASS** | Local reflection runner with lightweight JUnit and coroutine shims; not Gradle/JUnit |
| Corpus installer filesystem methods | **6/6 PASS** | Actual installer code using a lightweight Android `Context` shim and fake signature/index validators; not Android instrumentation |
| Android instrumentation source compilation | PASS | Three Android test source files / 12 test methods compile against API shims; tests have not run on Android |
| Room registry adapter source compile | PASS | Compiled against a DAO/entity contract shim; Room/KSP not executed |
| SQLite schema smoke | PASS | Python SQLite 3.46.1; identifiers, integrity, foreign keys, Arabic FTS5, query-time eligibility and deliberate FTS external-content corruption detection |
| Static inventory | PASS | 14 Gradle modules resolve to build files; version catalog TOML parses; 21 XML files parse; source manifests contain no INTERNET permission |
| Secret scan | PASS | `scripts/secret-scan.py`; not a complete SAST/security audit |
| Archive integrity and SHA-256 | Pending packaging | To be finalized for the release archive |

## Not run / not proven

This authoring environment has Kotlin 1.9 / JDK 21 but no installed Gradle distribution or Android SDK. Gradle distribution bootstrap cannot resolve `services.gradle.org`. Therefore the following are **NOT RUN**:

- AGP/Kotlin 2.4.21/KSP 2.3.10 dependency resolution and Android Gradle compilation;
- real AndroidX SQLite bundled driver execution and FTS5 instrumentation;
- Room compiler/KSP generation and generated schema verification;
- actual Bouncy Castle 1.86 jar integration and Android API 26+ Ed25519 verification;
- official Gradle/JUnit tasks, emulator tests, SQLCipher/Keystore instrumentation or Galaxy S24 FE device tests;
- GitHub Actions CI after Phase 4 integration.

Shim-backed checks are useful source-level and algorithm checks, but they do not replace the above platform tests. No APK/AAB or successful hosted CI run is claimed.

## Remaining Phase 4 work and release gates

1. Pin a production publisher public-key set and define signed key rotation/revocation. Revalidate already-installed packs when trust policy changes.
2. Build the corpus pack authoring/curation/signing pipeline with reproducible builds, provenance evidence, rights scope and scholarly review records.
3. Implement safe pack replacement/update semantics; wire orphan cleanup into app startup; add SAF import and pack-management UI plus dependency injection.
4. Implement local embeddings and a versioned dense index, benchmark on representative devices, and add a reranker only if a quality/latency benchmark justifies it.
5. Add user-visible evidence/citation cards to Timeline and Canvas, including source, edition, exact locator, review/license status, retrieval trace and correction path.
6. Execute full Gradle CI, Android instrumentation, migration/security tests and physical-device acceptance.
7. Do not enable religious guidance or verdict functionality until source rights, scholarly review, corpus coverage and retrieval quality have formal sign-off.

**Status:** Phase 4A foundation is implemented. Phase 4 is still in progress and this archive is not a production release candidate. No Quran, hadith, tafsir or fiqh content and no production publisher private key are included.
