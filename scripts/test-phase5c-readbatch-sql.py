#!/usr/bin/env python3
"""Execute the production dense-index source query against SQLite fixture rows."""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/BundledSqliteCorpusIndex.kt").read_text(encoding="utf-8")
method = source[source.find("override suspend fun readBatch"):source.find("override suspend fun search")]
match = re.search(r'connection\.prepare\(\s*"""([\s\S]*?)"""\.trimIndent\(\)', method)
if not match:
    raise SystemExit("Could not locate the actual production readBatch SQL statement")
sql = "\n".join(line.strip() for line in match.group(1).strip().splitlines())

connection = sqlite3.connect(":memory:")
connection.executescript("""
CREATE TABLE source (
  source_id TEXT PRIMARY KEY, edition_id TEXT NOT NULL, rights_holder TEXT NOT NULL,
  license_id TEXT NOT NULL, license_status TEXT NOT NULL,
  license_verified_at_epoch_ms INTEGER, license_expires_at_epoch_ms INTEGER,
  review_status TEXT NOT NULL, reviewer_id TEXT, reviewed_at_epoch_ms INTEGER,
  provenance_sha256 TEXT NOT NULL
);
CREATE TABLE passage (
  rowid INTEGER PRIMARY KEY, stable_evidence_key TEXT NOT NULL, original_text TEXT NOT NULL,
  content_sha256 TEXT NOT NULL, locator TEXT NOT NULL, language TEXT NOT NULL,
  normalized_text TEXT NOT NULL, normalizer_version TEXT NOT NULL, source_id TEXT NOT NULL
);
""")
valid = {
    "rights_holder": "rights-holder", "license_id": "license-1", "license_status": "CLEARED",
    "license_verified_at_epoch_ms": 100, "license_expires_at_epoch_ms": None,
    "review_status": "APPROVED", "reviewer_id": "reviewer", "reviewed_at_epoch_ms": 110,
    "provenance_sha256": "a" * 64,
}

def add(row_id: int, **overrides: object) -> None:
    source_id = f"source-{row_id}"
    values = valid | overrides
    connection.execute(
        "INSERT INTO source VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (source_id, "edition-1", values["rights_holder"], values["license_id"],
         values["license_status"], values["license_verified_at_epoch_ms"],
         values["license_expires_at_epoch_ms"], values["review_status"], values["reviewer_id"],
         values["reviewed_at_epoch_ms"], values["provenance_sha256"]),
    )
    connection.execute(
        "INSERT INTO passage VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (row_id, f"{row_id:064x}", "Arabic original passage", "b" * 64,
         "2:255", "ar", "normalized", "timeline-normalizer-v1", source_id),
    )

add(1)
add(2, license_status="RESTRICTED")
add(3, review_status="PENDING")
add(4, license_expires_at_epoch_ms=200)  # expires at the as-of instant: ineligible
add(5, license_verified_at_epoch_ms=201)  # verified in the future
add(6, reviewed_at_epoch_ms=201)  # review in the future
add(7, provenance_sha256="not-a-sha")
add(8)
connection.execute("UPDATE passage SET original_text = ? WHERE rowid = 8", ("x" * 9000,))
add(9)
connection.execute("UPDATE passage SET normalizer_version = ? WHERE rowid = 9", ("old-normalizer",))

args = (0, 8164, "timeline-normalizer-v1", 200, 200, 200, 24)
rows = connection.execute(sql, args).fetchall()
assert [row[0] for row in rows] == [1], f"Expected only eligible, bounded row 1; got {[row[0] for row in rows]}"
assert rows[0][1] == f"{1:064x}", "The source SQL must return the original stable evidence key unchanged"

# Verify keyset pagination does not skip the next eligible row and applies the caller's page bound.
add(10)
add(11)
first_page = connection.execute(sql, (0, 8164, "timeline-normalizer-v1", 200, 200, 200, 1)).fetchall()
second_page = connection.execute(sql, (first_page[-1][0], 8164, "timeline-normalizer-v1", 200, 200, 200, 1)).fetchall()
assert [row[0] for row in first_page] == [1]
assert [row[0] for row in second_page] == [10]
connection.close()
print("Phase 5C production readBatch SQL passed: rights, review, expiry, future timestamps, provenance, normalizer, model input bound and row-id pagination.")
