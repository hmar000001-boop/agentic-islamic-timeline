# Timeline Agentique checkpoint — Phase 5C source implementation

**Date:** 2026-11-09
**Source version:** `0.6.3-phase5c-dense-retrieval`
**App versionCode:** `24`
**Phase status:** implementation complete in this recovered source tree; acceptance pending
**Next sequence:** accept Phase 5C → Phase 6 → global phase-completion pass → Phase 7

## Scope completed in source

### 1. Room v6 and safe migration

- `AppDatabase` is version 6 and declares 27 Room entities.
- The encrypted database factory registers migrations 1→2→3→4→5→6.
- Migration 5→6 adds `corpusManifestSha256` and `embeddingProfileSha256` to `dense_index_generation`.
- Since v5 generations cannot prove either identity, the migration deletes only the derived `dense_index_vector` and `dense_index_generation` rows. It does not drop/recreate the database and does not clear timeline, agent, approval, audit, publisher-trust, installed-corpus, model-manager or unrelated tables.
- A SQLCipher Android instrumentation test constructs a v5 database fixture, applies the real migration, and checks both the invalidated dense derivations and preserved unrelated data. The test source is present; execution awaits API 36 CI.
- No Room schema JSON is fabricated. KSP must export the real v6 snapshot during hosted Gradle build; CI now asserts exactly one schema v6 JSON, 27 entities and both fingerprint columns.

### 2. Canonical embedding profile identity

The profile fingerprint covers model ID/revision/artifact digest, output dimension, maximum input, document and query task prefixes, normalization and quantization policy. Query and document embeddings use asymmetric task prefixes. The raw input character budget reserves the longest 28-character prefix, with a separate native 8,192-token bound. Any change to the effective profile invalidates a stored dense generation.

### 3. Index building and source evidence integrity

- The builder is explicitly user-triggered and only uses the installed catalog-pinned EmbeddingGemma artifact and revalidated signed corpus pack.
- It passes the model's exact maximum raw-input character count to the passage source.
- The bundled SQLite source admits only eligible, currently licensed, non-expired, scholarly-approved passages with verified provenance and supported normalizer version.
- Before embedding, source identity, stable key, original-text SHA-256 and normalized text are recomputed and compared to the installed signed SQLite rows. A mismatch aborts staging; it is not silently indexed.
- The SQL source query is executed against fixture databases by `test-phase5c-readbatch-sql.py` and is also covered by new Android instrumentation tests.

### 4. Dense retrieval and hybrid ranking

- `StoredDenseIndexRetriever` is the concrete dense provider registered through the application retrieval path.
- Query vectors are normalized once. Every active generation is scanned exhaustively in stable-key ordered pages of no more than 2,048 vectors; this is exact cosine retrieval, not ANN.
- The implementation checks generation count, page order, missing/duplicate rows, active-state and corpus/profile fingerprints, query language and evidence eligibility.
- The scan stores only score plus stable key in a bounded top-K heap. It does not load all passage text. Full immutable evidence text and source metadata are hydrated only for the final top-K results and are revalidated before return.
- Existing FTS5 lexical channels remain available. Dense and lexical hits flow through RRF. RRF is a ranking utility, not confidence or probability.
- Missing model/index, resource preflight failure, generation model already active, or dense-provider integrity failure leaves the lexical path available and records the fallback reason. The app does not silently unload another model to force dense retrieval.

### 5. Application/UI wiring

- `TimelineApplication.retrieveCorpus()` supplies the trusted publisher keyring, current corpus, model lifecycle coordination and dense provider.
- Corpus UI exposes per-pack manual build/rebuild and local evidence search; search requires verified publisher trust readiness.
- Results expose original text, source/edition/language/locator, contributing channels and RRF ranking score.
- No backend, network retrieval, automatic tool execution, default madhhab or new state-changing authority path was introduced.

## Validation performed in this authoring environment

| Validation | Result | Boundary |
|---|---:|---|
| Production retrieval/index-builder Kotlin sources + tests | 18/18 passed | Standalone JVM harness with coroutine runtime; not Gradle |
| Production EmbeddingGemma adapter + policy tests | 11/11 passed | API-shaped LiteRT-LM stubs; not native LiteRT-LM |
| Bundled SQLite adapter Kotlin compilation | Passed | API-shaped AndroidX SQLite stubs; not AndroidX native database execution |
| Phase 5C dense source contract | 33/33 passed | Static/source contract |
| Room v6 migration/schema guard | Passed | Python SQLite/schema contract, not Room KSP or SQLCipher |
| Dense-index persistence contract | 12/12 passed | Static/source contract |
| Production `readBatch()` SQL fixture execution | Passed | Python SQLite execution of query extracted from production source |
| Repository Python checks | 20/22 passed | Two intentionally excluded environment/hosted gates below |
| Android SDK platform and API 36 setup contract tests | 10/10 each passed | Tests validate verifier/launcher behavior, not installed SDK/device |
| English/Arabic translations | 6/6 tests; five resource modules verified | Static resource verification |
| Security/secret scan, Gradle lock policy, module policy, app compile contracts | Passed | Static checks |
| XML/TOML/workflow YAML/shell syntax | Passed | Parse/syntax checks |

The retrieval harness covers exhaustive paginated cosine ranking, deterministic top-K, top-K-only text hydration, generation count mismatch, profile and corpus fingerprint mismatch, language filtering, query normalization, hybrid RRF, lexical fallback, and staged index abort behavior.

## Acceptance gates still open (do not mark production-ready yet)

1. **Actual Gradle build and Kotlin/Compose compile:** not run here. The pinned Gradle distribution could not be fetched because DNS resolution for `services.gradle.org` failed; the environment has no Android SDK.
2. **Room KSP schema v6:** not generated locally. CI now runs `verify-room-schema-export.py` after app assemble/lint and requires the real generated snapshot. Review and commit the generated JSON after hosted CI.
3. **SQLCipher/Room migration instrumentation:** test source exists, but it was not executed against real Android Room + SQLCipher on API 36.
4. **AndroidX bundled SQLite instrumentation:** test source exists; the adapter compiles against API-shaped stubs and its SQL passes Python SQLite fixtures, but real AndroidX bundled SQLite tests have not run.
5. **GitHub Actions:** no new hosted run for version 0.6.3 is available in this authoring session. Run the complete workflow, including unit, lint, KSP schema export and connected API 36 tests.
6. **Galaxy S24 FE:** native EmbeddingGemma loading, retrieval relevance, latency, peak memory, thermal throttling, battery and sustained-use behavior have not been measured.

## Source provenance caveat

The 0.6.2 archive available to this session was a reconstructed recovery archive, not a verified byte-identical copy of the user's pushed Git checkout. This 0.6.3 output is based on that recovered archive. The patch generated alongside the ZIP is guaranteed against that recovered archive only. Before merging into a different checkout, apply/check the patch against the user's exact source and reconcile any context differences; do not overwrite newer user changes.

## Next sequence

- First: run the 0.6.3 workflow and resolve any real Kotlin/KSP/Android-test failures; commit the generated schema snapshot.
- Second: validate model load and dense retrieval on the Galaxy S24 FE.
- Once Phase 5C acceptance is complete, proceed to Phase 6.
- After Phase 6, perform the planned cross-phase gap closure (Phases 1–5, plus Phase 6) against the original acceptance matrix, then execute Phase 7 release gates.
