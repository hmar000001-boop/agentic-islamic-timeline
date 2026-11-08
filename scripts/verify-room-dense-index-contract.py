#!/usr/bin/env python3
"""Guard the Room dense-index adapter against interface/DAO contract drift."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexStore.kt"
API = ROOT / "core/knowledge-api/src/main/java/app/timeline/islamic/core/knowledge/CorpusContracts.kt"
DAO = ROOT / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt"

store = STORE.read_text(encoding="utf-8")
api = API.read_text(encoding="utf-8")
dao = DAO.read_text(encoding="utf-8")

checks = []
checks.append((
    bool(re.search(r"interface\s+DenseEmbeddingIndexStore[\s\S]*?suspend\s+fun\s+activateGeneration\(generationId:\s*String\)\s*(?:\n|\})", api)),
    "DenseEmbeddingIndexStore.activateGeneration contract returns Unit"
))
checks.append((
    bool(re.search(r"override\s+suspend\s+fun\s+activateGeneration\(generationId:\s*String\)\s*:\s*Unit\s*\{", store)),
    "Room override explicitly returns Unit"
))
checks.append((
    bool(re.search(r"const\s+val\s+STATE_ACTIVE\s*=\s*\"ACTIVE\"", store)),
    "Room adapter defines ACTIVE state used by vector visibility"
))
checks.append((
    "state = STATE_STAGING" in store and 'const val STATE_STAGING = "STAGING"' in store,
    "Room adapter uses the DAO-compatible STAGING state"
))
checks.append((
    re.search(r"UPDATE\s+dense_index_generation\s+SET\s+state\s*=\s*'ACTIVE'", dao, re.I) is not None,
    "DenseIndexDao activates rows with ACTIVE state"
))
checks.append((
    re.search(r"state\s*=\s*'ACTIVE'\s+LIMIT\s+1", dao, re.I) is not None,
    "DenseIndexDao queries ACTIVE state"
))
checks.append((
    "if (generation.state != STATE_ACTIVE) return emptyMap()" in store,
    "Vector reads expose only ACTIVE generations"
))
checks.append((
    "database.withTransaction" in store[store.find("override suspend fun activateGeneration"):store.find("override suspend fun abortGeneration")],
    "Generation activation remains one Room transaction"
))

failed = 0
for ok, label in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
    failed += not ok
if failed:
    print(f"{failed} dense-index persistence contract check(s) failed", file=sys.stderr)
    raise SystemExit(1)
print(f"All {len(checks)} dense-index persistence contract checks passed.")
