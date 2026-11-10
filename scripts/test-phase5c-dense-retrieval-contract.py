#!/usr/bin/env python3
"""Phase 5C end-to-end source contract: Room v7 fingerprints, exhaustive dense scan, RRF and app wiring."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []
checks = 0

def require(condition: bool, label: str) -> None:
    global checks
    checks += 1
    if not condition:
        errors.append(label)

read = lambda path: (ROOT / path).read_text(encoding="utf-8")
app_db = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt")
migrations = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabaseMigrations.kt")
factory = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/EncryptedDatabaseFactory.kt")
entity = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/Entities.kt")
api = read("core/knowledge-api/src/main/java/app/timeline/islamic/core/knowledge/CorpusContracts.kt")
dao = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt")
store = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexStore.kt")
builder = read("core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/LocalEmbeddingIndexBuilder.kt")
retriever = read("core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/StoredDenseIndexRetriever.kt")
corpus_index = read("core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/BundledSqliteCorpusIndex.kt")
local_retriever = read("core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/LocalCorpusRetriever.kt")
fusion = read("core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/RrfFusion.kt")
app = read("app/src/main/java/app/timeline/islamic/TimelineApplication.kt")
embedding = read("core/inference-runtime/src/main/java/app/timeline/islamic/core/inference/LiteRtEmbeddingTextModel.kt")
workflow = read(".github/workflows/android-ci.yml")

require(re.search(r"version\s*=\s*7", app_db) is not None, "Room schema version is 7 after additive Phase 6 workspace migration")
require("MIGRATION_5_6" in migrations and "AppDatabaseMigrations.MIGRATION_5_6" in factory,
        "Room factory registers v5→v6 migration")
require('ALTER TABLE dense_index_generation ADD COLUMN corpusManifestSha256 TEXT NOT NULL DEFAULT \'\'' in migrations and
        'ALTER TABLE dense_index_generation ADD COLUMN embeddingProfileSha256 TEXT NOT NULL DEFAULT \'\'' in migrations,
        "migration adds both signed-corpus and embedding-profile fingerprints")
require('DELETE FROM dense_index_vector' in migrations and 'DELETE FROM dense_index_generation' in migrations and
        'DELETE FROM timeline_item' not in migrations and 'DROP TABLE' not in migrations,
        "migration invalidates only unprovable derived vector rows without destructive DB reset")
require('@ColumnInfo(defaultValue = "\'\'") val corpusManifestSha256: String' in entity and
        '@ColumnInfo(defaultValue = "\'\'") val embeddingProfileSha256: String' in entity,
        "Room entity defaults match the explicit migration columns")
require("profileFingerprint(" in api and "documentPrefix" in api and "queryPrefix" in api and
        "normalizationPolicy" in api and "artifactSha256" in api,
        "embedding profile fingerprint is derived from model bytes, dimensions, task prefixes and normalization")
require("corpusManifestSha256 = pack.manifestSha256" in store and
        "embeddingProfileSha256 = model.embeddingProfileSha256" in store,
        "index generation persists exact corpus and embedding profile fingerprints")
require("activeGeneration(\n        pack: InstalledCorpusPackRecord" in store and
        "it.corpusManifestSha256 == pack.manifestSha256" in store and
        "it.embeddingProfileSha256 == model.embeddingProfileSha256" in store,
        "active index lookup rejects mismatched corpus/model/profile fingerprints")
require("vectorsPage(generationId: String, afterStableEvidenceKey: String, limit: Int)" in dao and
        "ORDER BY stableEvidenceKey COLLATE BINARY ASC LIMIT :limit" in dao,
        "Room vector DAO uses deterministic stable-key keyset pagination")
require("MAX_PAGE_SIZE = 2048" in store and "limit in 1..MAX_PAGE_SIZE" in store and
        "DenseIndexVectorRecord" in store,
        "Room vector reads are bounded to 2,048-row pages")
require("store.vectorsPage" in retriever and "QuantizedEmbeddingCodec.cosine" in retriever and
        "scanned == generation.vectorCount" in retriever,
        "dense retriever exhaustively scans every vector page and ranks by cosine with count validation")
require("PriorityQueue" in retriever and "sortedWith(bestFirst)" in retriever,
        "dense top-K memory is bounded and deterministic")
require("activeGeneration(pack, descriptor)" in retriever and "embeddingProfileSha256" in retriever,
        "dense retriever fails closed for stale profile generations")
require("DenseEvidenceLookup" in corpus_index and "readEligibleEvidenceKeysByStableKeys" in corpus_index and
        "readEvidenceByStableKeys" in corpus_index and "EvidenceEligibility.isEligible" in corpus_index and
        "p.language IN" in corpus_index,
        "dense vector keys are filtered using lightweight metadata and resolve to original eligible source text")
require("readEligibleEvidenceKeysByStableKeys" in retriever and
        "rankedKeys.map { it.stableEvidenceKey }" in retriever and
        "Top dense evidence could not be resolved uniquely" in retriever and
        "Candidate(val score: Double, val stableEvidenceKey: String)" in retriever,
        "dense scan holds only key/score top-K candidates and hydrates original text for final results only")
require("duplicate stable evidence keys" in corpus_index,
        "corpus validation rejects ambiguous stable keys before index generation")
require("maximumInputCharacters: Int" in api and
        "source.readBatch(pack, indexFile, afterRowId, batchSize, descriptor.maximumInputCharacters)" in builder,
        "embedding builder passes the active model's declared input bound to the corpus source")
read_batch = corpus_index[corpus_index.find("override suspend fun readBatch"):corpus_index.find("override suspend fun search")]
require("length(p.original_text) <= ?" in read_batch and
        "p.normalizer_version = ?" in read_batch,
        "dense index construction excludes oversized or stale-normalizer passages at the SQLite source")
require("s.license_status = 'CLEARED'" in read_batch and
        "s.license_expires_at_epoch_ms > ?" in read_batch and
        "s.review_status = 'APPROVED'" in read_batch and
        "s.provenance_sha256" in read_batch,
        "dense index construction enforces current redistribution rights, scholarly approval and provenance")
require("EvidenceIdentity.stableKey(" in read_batch and
        "EvidenceIdentity.contentSha256(originalText)" in read_batch and
        "CorpusTextNormalizer.normalize(originalText).text" in read_batch,
        "dense index builder revalidates stable identity, original-text hash and canonical normalization before embedding")
evidence_test_text = read("core/retrieval/src/androidTest/java/app/timeline/islamic/core/retrieval/BundledSqliteCorpusIndexTest.kt")
require("embeddingBatchReadsOnlyEligiblePassagesWithinTheModelInputLimit" in evidence_test_text and
        "licenseStatus = \"RESTRICTED\"" in evidence_test_text and
        "reviewStatus = \"PENDING\"" in evidence_test_text and
        "originalTextOverride = \"x\".repeat(9_000)" in evidence_test_text and
        "embeddingBatchRejectsPassagesWhoseIdentityOrContentChangedAfterPackValidation" in evidence_test_text,
        "bundled SQLite Android instrumentation covers license, review, model-input, stable-identity and text-integrity filtering")
require("denseRetriever?.let" in local_retriever and "RrfFusion.fuse" in local_retriever and
        "denseProviderFailed" in local_retriever and "HYBRID_FUSION" in local_retriever,
        "dense and per-pack lexical channels fuse through RRF with an explicit lexical fallback trace")
require("fun fuse(" in fusion and "DEFAULT_K = 60" in fusion,
        "RRF remains deterministic and rank-based")
require("StoredDenseIndexRetriever" in app and "retrieveCorpus(request: RetrievalRequest)" in app and
        "LiteRtEmbeddingTextModel.open" in app and "embeddingModel.close()" in app,
        "application exposes a lifecycle-safe on-device dense+lexical retrieval path")
require("A verified publisher keyring is required before corpus retrieval" in app and
        "ModelLifecycleStatus.INSTALLED" in app and "LocalCorpusRetriever" in app,
        "application retrieval requires trusted packs and an installed catalog-pinned model")
require("runtimeLoadBlockReason(spec.minimumDeviceRamBytes, modelResourceProbe.snapshot())" in app and
        "runtimeSnapshot.state != GenerationRuntimeState.UNLOADED" in app and
        "Unload the generation model explicitly before building the dense index" in app,
        "dense operations respect memory/thermal preflight and never silently unload another active native runtime")
feature = read("feature/corpus-management/src/main/java/app/timeline/islamic/feature/corpus/CorpusManagementFeature.kt")
activity = read("app/src/main/java/app/timeline/islamic/MainActivity.kt")
require("onBuildDenseIndex" in feature and "onSearch" in feature and "state.keyringReady" in feature and
        "corpus_search_channels" in feature and "app.retrieveCorpus(" in activity,
        "corpus UI exposes explicit indexing/search while displaying evidence channels and requiring trust readiness")
require("embedQuery(text: String)" in api and "override suspend fun embedQuery" in embedding and
        "QUERY_PREFIX" in embedding and "DOCUMENT_PREFIX" in embedding,
        "query embeddings use the model's distinct asymmetric query prefix")
require('versionCode = 25' in read("app/build.gradle.kts") and
        'versionName = "0.7.0-phase6-domain-engines"' in read("app/build.gradle.kts"),
        "Phase 6 source release identity is 0.7.0 / 25")
require("scripts/test-phase5c-dense-retrieval-contract.py" in workflow and
        "scripts/test-phase5c-readbatch-sql.py" in workflow and
        "scripts/verify-room-schema-export.py" in workflow and "scripts/verify-phase6-domain-workspace.py" in workflow and
        ":core:retrieval:testDebugUnitTest" in workflow and
        ":core:persistence:connectedDebugAndroidTest" in workflow and
        ":core:retrieval:connectedDebugAndroidTest" in workflow,
        "CI contains dense retrieval contract, JVM tests, SQLCipher migration/store, bundled-SQLite instrumentation and Phase 6 domain contracts")

unit_test = ROOT / "core/retrieval/src/test/java/app/timeline/islamic/core/retrieval/StoredDenseIndexRetrieverTest.kt"
room_migration_test = ROOT / "core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexMigrationTest.kt"
evidence_test = ROOT / "core/retrieval/src/androidTest/java/app/timeline/islamic/core/retrieval/BundledSqliteCorpusIndexTest.kt"
require(unit_test.is_file() and len(re.findall(r"@Test", unit_test.read_text(encoding="utf-8"))) >= 6,
        "dense retrieval has focused ranking, pagination, filter, fingerprint and count tests")
require(room_migration_test.is_file() and "MIGRATION_5_6.migrate(db)" in room_migration_test.read_text(encoding="utf-8"),
        "SQLCipher instrumentation executes the real 5→6 migration and checks unrelated-data preservation")
require("denseEvidenceLookupReturnsOnlyMatchingEligibleOriginalPassages" in evidence_test.read_text(encoding="utf-8") and
        "readEligibleEvidenceKeysByStableKeys" in evidence_test.read_text(encoding="utf-8"),
        "bundled SQLite instrumentation exercises key-only filtering and final original-passage hydration")

if errors:
    print("Phase 5C dense retrieval contract FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)
print(f"All {checks} Phase 5C dense retrieval, Room v7, fingerprint, migration, RRF, app-wiring and CI source contracts passed.")
