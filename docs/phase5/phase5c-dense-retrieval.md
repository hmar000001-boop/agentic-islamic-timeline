# Phase 5C — Fingerprinted local dense retrieval

**Source version:** `0.6.3-phase5c-dense-retrieval` (`versionCode = 24`)
**Room schema:** v6
**Implementation status:** complete in the recovered source tree
**Acceptance status:** pending hosted Android CI, API 36 connected tests and Galaxy S24 FE native model validation

## Goal and boundary

Phase 5C connects the already verified local EmbeddingGemma 2 artifact to the existing encrypted Room vector store and the signed SQLite corpus packs. It does not add network retrieval, model-generated facts, automatic tool execution, or a default madhhab. FTS5 remains available as a lexical channel and fallback.

## Identity and stale-index rules

A dense generation is eligible only when all of these match the currently installed, verified inputs:

- `packId` and pack version;
- exact signed corpus-manifest SHA-256;
- embedding model ID and revision;
- model artifact SHA-256;
- output dimension and input bound;
- document task prefix and query task prefix;
- normalization and signed-int8 quantization policy.

`LocalEmbeddingModelDescriptor.embeddingProfileSha256` is a canonical length-prefixed SHA-256 over the complete embedding profile. Changing a model file, revision, dimension, task prefix, max-input bound or normalization policy invalidates the old generation. A mere model ID/revision match is not enough.

## Room v6 migration

`MIGRATION_5_6` adds `corpusManifestSha256` and `embeddingProfileSha256` to `dense_index_generation`. Existing v5 generations have no recorded values for either fingerprint, so their dense vectors and generation rows are deleted. This is deliberate invalidation of derived data, not destructive database recreation. Timeline items, pending approvals, agent tasks, audit rows, publisher keys, installed corpus metadata, model metadata and all other non-dense tables are not deleted.

`EncryptedDatabaseFactory` registers the complete migration chain through v6. The Room schema remains SQLCipher-backed; there is no plaintext sidecar vector database.

## Index build and storage

The explicit user-triggered index build uses the installed catalog-pinned EmbeddingGemma artifact and the installed, revalidated signed corpus pack. It streams eligible passages in bounded batches. The SQLite source enforces cleared/current license, unexpired rights, approved scholarly review, verified provenance, normalizer version and the model's raw-text character budget. Before a row is embedded, it recomputes the stable evidence key, original-text SHA-256 and canonical normalized text; any changed value aborts the staging generation. Document text is embedded with the document task prefix, finite values/dimension/norm are validated, normalized vectors are quantized to signed int8, and rows are staged in SQLCipher Room before atomic activation. Stored rows contain stable evidence keys and vector bytes, not source passage text. Existing storage budgets and abandoned-generation recovery remain in force.

## Query path

1. Require a ready publisher keyring and enumerate currently installed/revalidated packs.
2. Run the existing FTS5/BM25 lexical query per pack.
3. Only when the exact EmbeddingGemma artifact is installed and resource preflight permits it, create a local query embedding using the separate query prefix.
4. For each compatible active generation, read stable-key ordered pages of at most 2,048 vectors. Perform an exhaustive cosine scan; this is exact full scan, not ANN.
5. During scoring, query only eligible stable evidence keys and compact source eligibility metadata. Keep only a bounded top-K heap of `(cosineScore, stableEvidenceKey)`; do not materialize full passage text for every page.
6. Hydrate immutable original passage/source metadata only for the final top-K keys from the same installed signed SQLite pack. Reject missing, duplicate or changed identities.
7. Recheck pack/version, stable evidence identity, original-content hash, normalizer output, license state/expiry, scholarly review, language and madhhab in the existing retrieval orchestrator.
8. Fuse the lexical pack channels and dense model/pack channels with rank-based RRF. A score is a ranking utility, never a confidence or probability.

## Fallback and runtime safety

- Missing embedding model or missing compatible active generation: lexical FTS5 only.
- Dense model initialization, fingerprint, pagination, count, evidence hydration or integrity failure: dense provider is marked failed and the lexical results remain available.
- RAM/storage/thermal preflight failure: lexical search remains available; dense is not initialized.
- Another generation model is active: the app does not silently unload it to perform search. Dense retrieval falls back to lexical until the user explicitly unloads the generation model.
- Dense-index build requires the generation runtime to be explicitly unloaded.
- All embedding inference remains local; no network provider exists. The model runtime has no tool provider and cannot bypass `ToolExecutionGateway`.

## User interface

The Corpus screen exposes an explicit per-pack Build/Rebuild dense index action and a local evidence search field. Search requires verified publisher trust readiness. Results show original source text, source type/title, edition, language, locator, contributing retrieval channels and RRF ranking score. The UI labels the RRF score as non-probabilistic and makes dense fallback/failure visible.

## Verification performed in this authoring environment

- Kotlin/JVM compiler 1.9 compiled the production knowledge contracts, retrieval sources and index builder against the installed coroutine runtime.
- Standalone JVM harness executed **18/18** retrieval/index-builder unit tests, including full-scan pagination, stable top-K, fingerprint mismatch rejection, query-profile identity, query language filtering, missing-row count detection, hybrid RRF integration, lexical fallback, top-K-only text hydration and staged-index abort behavior.
- A separate adapter harness compiled the production EmbeddingGemma source against API-shaped LiteRT-LM stubs and passed **11/11** inference policy tests, including the asymmetric prefix budget. It does not verify the native SDK implementation.
- Phase 5C source contract: **33/33** checks passed.
- Room schema/migration SQLite guard: passed; verifies schema v6, registered migration, dense-row invalidation and preservation of unrelated user data.
- Room dense-index persistence contract: **12/12** passed.
- Earlier Phase 5A/5B and app source-contract checks pass on this workspace. All static Python scripts except environment-dependent SDK verification have been run; the Android SDK is not installed in this authoring environment. No Room KSP schema JSON is fabricated: the real v6 schema snapshot must be generated by KSP during the hosted Gradle build and committed after review.

## Acceptance gates still open

The local environment could not download the pinned Gradle distribution (`services.gradle.org` DNS resolution failed) and has no Android SDK/connected emulator. Room schema export is configured but the repository does not yet contain a generated v6 schema JSON beneath the database-qualified schema folder; do not fabricate it. Therefore this environment has **not** run:

- actual Gradle/Kotlin/Room KSP/Compose compilation;
- complete GitHub Actions unit-test and lint jobs;
- SQLCipher `MIGRATION_5_6` instrumentation on API 36;
- bundled SQLite evidence lookup instrumentation on API 36;
- native LiteRT-LM EmbeddingGemma initialization and generation on the Samsung Galaxy S24 FE;
- Arabic/English retrieval relevance, latency, peak memory, thermal and battery measurements on device.

Phase 5C should be marked **implementation-complete / acceptance-pending**, not CI-green or production-ready, until those gates pass on the exact source revision.
