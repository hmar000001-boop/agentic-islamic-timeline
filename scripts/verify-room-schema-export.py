#!/usr/bin/env python3
"""Verify that Room KSP produced the real, current schema v6 snapshot."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_ROOT = ROOT / "core/persistence/schemas"
SCHEMAS = sorted(path for path in SCHEMA_ROOT.rglob("6.json") if path.is_file() and path.stat().st_size > 0)
if len(SCHEMAS) != 1:
    raise SystemExit(f"Room KSP must export exactly one schema v6 JSON beneath {SCHEMA_ROOT}; found {len(SCHEMAS)}")
SCHEMA = SCHEMAS[0]
try:
    document = json.loads(SCHEMA.read_text(encoding="utf-8"))
except json.JSONDecodeError as exc:
    raise SystemExit(f"Room schema v6 JSON is invalid: {exc}")
database = document.get("database")
if not isinstance(database, dict) or database.get("version") != 6:
    raise SystemExit("Room schema export does not describe database version 6")
entities = database.get("entities")
if not isinstance(entities, list) or len(entities) != 27:
    raise SystemExit(f"Expected 27 Room entities in schema v6; found {len(entities) if isinstance(entities, list) else 'invalid'}")
by_table = {item.get("tableName"): item for item in entities if isinstance(item, dict)}
generation = by_table.get("dense_index_generation")
if generation is None:
    raise SystemExit("Room schema v6 is missing dense_index_generation")
columns = {field.get("columnName") for field in generation.get("fields", []) if isinstance(field, dict)}
required = {"corpusManifestSha256", "embeddingProfileSha256"}
if not required.issubset(columns):
    raise SystemExit(f"Room schema v6 dense generation is missing fingerprint fields: {sorted(required - columns)}")
if "dense_index_vector" not in by_table:
    raise SystemExit("Room schema v6 is missing dense_index_vector")
print("Room KSP schema export passed: schema v6, all 27 entities and both dense-index fingerprint columns.")
