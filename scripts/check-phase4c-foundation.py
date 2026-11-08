#!/usr/bin/env python3
"""Static guard for Phase 4C app wiring and SQLCipher-only local vector persistence."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

app = (ROOT / "app/src/main/java/app/timeline/islamic/TimelineApplication.kt").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/app/timeline/islamic/MainActivity.kt").read_text(encoding="utf-8")
feature = (ROOT / "feature/corpus-management/src/main/java/app/timeline/islamic/feature/corpus/CorpusManagementFeature.kt").read_text(encoding="utf-8")
dense_store = (ROOT / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexStore.kt").read_text(encoding="utf-8")
builder = (ROOT / "core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/LocalEmbeddingIndexBuilder.kt").read_text(encoding="utf-8")
contracts = (ROOT / "core/knowledge-api/src/main/java/app/timeline/islamic/core/knowledge/CorpusContracts.kt").read_text(encoding="utf-8")

require('OpenDocument()' in activity and 'ZIP_MIME_TYPES' in activity, "SAF OpenDocument import callbacks are missing")
require('AppDestination.CORPUS' in activity and 'CorpusManagementFeature(' in activity, "Corpus management screen is not wired into app navigation")
for name in ("importPublisherKeyring", "importCorpusPack", "updateCorpusPack", "removeCorpusPack", "loadCorpusManagementState"):
    require(f"fun {name}" in app or f"suspend fun {name}" in app, f"App-level corpus lifecycle operation is missing: {name}")
require("cleanupDenseIndexesForInactivePacks()" in app, "Dense index generations are not cleaned after publisher revalidation/quarantine")
require("publisherRootConfiguration.root" in app and "PinnedPublisherRoot" in app, "Publisher root remains not explicitly configured through the pinned-root contract")
require("database.withTransaction" in dense_store, "Dense generation writes are not transactionally stored in encrypted Room")
require("DenseIndexVectorEntity" in dense_store and "originalText" not in dense_store, "Dense index stores text or omits vector-only Room persistence")
require("128L * 1024L * 1024L" in dense_store and "256L * 1024L * 1024L" in dense_store, "Dense vector generation/total byte budgets are missing")
require("const val MAXIMUM_ENCODED_BYTES_LIMIT = 128L * 1024L * 1024L" in builder, "Builder's maximum generation budget must match Room's 128 MiB cap")
require("withContext(NonCancellable + Dispatchers.IO)" in builder, "Cancellation/failure does not abort staged generations in a non-cancellable cleanup section")
require("LocalTextEmbeddingModel" in contracts and "suspend fun embedBatch" in contracts, "On-device embedding model interface is missing")
require("EmbeddingsUnavailable" in feature or "embeddings_unavailable" in feature or "embeddings_unavailable" in (ROOT / "feature/corpus-management/src/main/res/values/strings.xml").read_text(encoding="utf-8"), "UI does not disclose that dense embeddings/model are unavailable")
require("No network" in contracts or "local-only" in contracts or "remote" in contracts, "Knowledge contracts do not explicitly restrict embedding provider to local operation")

for locale_dir in ("values", "values-ar"):
    strings = ROOT / f"feature/corpus-management/src/main/res/{locale_dir}/strings.xml"
    require(strings.is_file(), f"Corpus management resources missing for {locale_dir}")
    if strings.is_file():
        try:
            ET.parse(strings)
        except ET.ParseError as exc:
            errors.append(f"Invalid corpus management XML {strings}: {exc}")

# Offline flavor manifest must not request INTERNET. App manifests and flavor overlays are scanned.
for manifest in ROOT.glob("app/src/**/AndroidManifest.xml"):
    try:
        root = ET.parse(manifest).getroot()
    except ET.ParseError as exc:
        errors.append(f"Invalid Android manifest {manifest}: {exc}")
        continue
    for node in root.iter():
        if node.tag.endswith("uses-permission") and node.attrib.get("{http://schemas.android.com/apk/res/android}name") == "android.permission.INTERNET":
            errors.append(f"Offline application source declares INTERNET permission: {manifest.relative_to(ROOT)}")

instrumented = ROOT / "core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexStoreTest.kt"
require(instrumented.is_file() and "SupportOpenHelperFactory" in instrumented.read_text(encoding="utf-8"), "SQLCipher-backed Room dense-generation instrumentation source is missing")

# The Phase 4C foundation intentionally contains no real model implementation or model weights.
for path in (ROOT / "core").rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    if path.name != "CorpusContracts.kt":
        require(not re.search(r"(?:class|object)\s+\w*Embedding\w*Model\b", text), f"Unexpected concrete embedding model implementation; require reviewed local model before dense retrieval: {path.relative_to(ROOT)}")

if errors:
    print("Phase 4C foundation verification FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print("Phase 4C guard passed: SAF corpus/keyring UI wiring, publisher-root gating, inactive-pack dense cleanup, bounded local embeddings, SQLCipher-only vector persistence and Arabic/English UI resources.")
