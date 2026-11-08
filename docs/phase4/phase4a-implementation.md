# Phase 4A implementation report — knowledge and lexical retrieval foundation

Date: 2026-11-08  
Source baseline: Phase 3.0.1 CI hotfix  
Source version: 0.4.0-phase4-knowledge-foundation

## Continuity

This is an incremental continuation of the Phase 3.0.1 project. All prior Phase 1, Phase 2 and Phase 3 implementation files are retained. Room schema remains version 3 with 24 entities; no destructive migration or new Room entity was added.

## Added modules

- `:core:knowledge-api`: language/source/license/review/madhhab contracts, retrieval request/filter objects, immutable evidence metadata, search normalization and deterministic evidence identity.
- `:core:corpus`: canonical Corpus Pack v1 parser, Ed25519 verifier adapter, publisher keyring interface, bounded ZIP importer, SHA-bound SQLite validation, atomic private installation and orphan cleanup.
- `:core:retrieval`: bundled SQLite FTS5/BM25 validator/searcher, local retrieval orchestration, optional dense-provider interface and RRF fusion.
- `:core:persistence`: `RoomCorpusPackRegistry` adapter reusing the existing installed pack table.

## Important correctness choices

1. Original evidence text is preserved exactly and hashed as UTF-8. Normalized text exists only for retrieval.
2. Stable passage identity is recomputed from source ID, edition ID, locator and language using a domain-separated, length-prefixed SHA-256 input.
3. Import requires both legal redistribution clearance and an approved scholarly review record. The presence of a license URL by itself is not accepted as proof.
4. The Ed25519 message is the exact canonical manifest bytes. The manifest contains the SQLite index hash. ZIP entries are restricted and extracted only to an app-private staging directory.
5. The corpus index uses AndroidX Bundled SQLite so FTS5 availability does not depend on the SQLite build included with a device OS. The Room user-state database remains separate and SQLCipher-backed.
6. A pack database's BM25 values are used only to establish its internal rank order. Every pack is a separate lexical RRF channel; raw BM25 scores across packs are never compared.
7. RRF deduplication binds source, edition, provenance hash, locator, language, stable identity and content hash. Repeated rows from one channel contribute only once, and channel weights are bounded and consistent.
8. `reranked=false` is explicit. No local dense-vector provider or reranker is claimed as present. A dense-provider exception or invalid candidate is recorded in the retrieval trace while an eligible lexical fallback can still be returned.
9. Pack validation runs the FTS5 external-content integrity check before making the connection query-only; this detects postings that diverge from the signed passage table. Retrieval also recomputes the stable evidence identity of each result, not just its original-text hash.
10. Query-time SQL filters reject future-dated license verification or scholarly review timestamps before candidate limiting, preserving valid candidates when a pack is malformed or becomes inconsistent.

## Verification completed in this authoring environment

- Kotlin 1.9 syntax compilation of all Phase 4 main Kotlin sources using temporary API shims for Android, AndroidX SQLite, coroutines and Bouncy Castle. This checks Kotlin syntax and referenced API shapes as stubbed; it is not a real Android or dependency compile.
- Focused executable Kotlin harness: 10 assertions passed for Arabic normalization, FTS-safe query construction, no-default-madhhab semantics, deterministic evidence identity, exact original-text hash semantics, manifest round-trip, rank-only RRF and edition separation. Focused JVM test methods: 20 passed using Kotlin 1.9 plus lightweight local JUnit/coroutine/API shims; these are not Gradle/JUnit-runner results.
- Six corpus-installer filesystem tests were also executed locally against the actual installer code using a lightweight Android `Context` shim and fake signature/SQLite validators. They cover installation plus manifest/signature retention, unexpected ZIP paths, bad signature, failed registry commit rollback, inactive publisher key and revoked publisher key. This does not replace Android instrumentation.
- Android instrumentation source compiled for syntax/API shape against shims and now contains 12 test methods across corpus/retrieval: installation lifecycle/rejection/rollback, FTS5 Arabic retrieval, malformed original-text hash/normalization, corrupted external-content postings, and future-dated review filtering. These instrumented tests have not run on Android.
- SQLite 3.46.1 smoke exercise: the committed SQL schema executed, application ID/user version matched, integrity check returned `ok`, foreign-key check returned no violations, FTS5 returned the normalized Arabic fixture, future-dated scholarly review was excluded by the query-time predicate, and a deliberate external-content postings mismatch was rejected by the FTS integrity check.
- Static parsing/inventory, Gradle module inclusion, version catalog, Android manifest offline permission, and ZIP integrity/hash checks are run at packaging.

## Not verified

- No Android SDK or installed Gradle distribution is available in the authoring container, and Gradle bootstrap cannot resolve `services.gradle.org`. Therefore AGP/Kotlin/KSP compilation, AndroidX SQLite 2.7.1 binary/runtime integration, Room codegen, Gradle/JUnit, full Ed25519 test against the resolved Bouncy Castle 1.86 jar, instrumentation and device performance have not been run.
- The actual publisher keyring is not pinned. There are no signed production packs in this archive.
- No rights-cleared or scholar-approved corpus content is supplied.

## Remaining Phase 4 work

- Pin production publisher public keys and establish signed key rotation/revocation, including revalidation/handling of already-installed packs (signed manifest/signature are now retained in each installed pack directory).
- Add pack replacement/update transaction semantics and a pack authoring/curation/signing toolchain.
- Wire SAF import, registry/recovery, pack lifecycle UI and app dependency injection.
- Implement local embeddings, a versioned dense index and device benchmark; then implement a benchmark-justified reranker if it adds measurable value.
- Add citation/evidence cards to the Timeline and Canvas interaction layer, exposing exact source/edition/locator, license status, scholarly review, retrieval channel/rank trace and correction/report path.
- Android instrumentation source now covers validated pack installation and retained manifest/signature, unexpected ZIP entry rejection, invalid/inactive publisher key rejection, registry-failure filesystem rollback, bundled SQLite Arabic FTS retrieval, content-hash mismatch, and FTS external-content corruption. Full zip-bomb boundaries, orphan recovery, SAF wiring and cross-pack end-to-end retrieval still need dedicated tests and Android CI execution.
- Do not release religious guidance features until licensing, scholarly review, corpus coverage and retrieval quality acceptance are signed off.

**Status:** Phase 4A foundation implemented in source. Phase 4 is still in progress; this is not a production release candidate.
