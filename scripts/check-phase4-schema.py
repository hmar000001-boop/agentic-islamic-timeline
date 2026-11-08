#!/usr/bin/env python3
"""Static/SQLite guard for the Phase 4B Room v3→v4 publisher-keyring migration."""
from pathlib import Path
import re
import sqlite3
import sys

root = Path(__file__).resolve().parents[1]
db = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt").read_text()
entities = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/Entities.kt").read_text()
migrations = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabaseMigrations.kt").read_text()
factory = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/EncryptedDatabaseFactory.kt").read_text()
registry = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomCorpusPackRegistry.kt").read_text()
dao = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt").read_text()
keyring_manager = (root / "core/corpus/src/main/java/app/timeline/islamic/core/corpus/PublisherKeyringManager.kt").read_text()
installer = (root / "core/corpus/src/main/java/app/timeline/islamic/core/corpus/CorpusPackInstaller.kt").read_text()

if not re.search(r"version\s*=\s*4", db):
    raise SystemExit("AppDatabase version must be 4")
if "PublisherKeyringStateEntity::class" not in db:
    raise SystemExit("PublisherKeyringStateEntity is not registered in Room")
if "@Entity(tableName = \"publisher_keyring_state\")" not in entities:
    raise SystemExit("publisher_keyring_state Room entity is missing")
if "val MIGRATION_3_4 = object : Migration(3, 4)" not in migrations:
    raise SystemExit("Room 3→4 migration is missing")
if "AppDatabaseMigrations.MIGRATION_3_4" not in factory:
    raise SystemExit("EncryptedDatabaseFactory does not register migration 3→4")
if "STATUS_SUPERSEDED -> setOf(InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING)" not in registry:
    raise SystemExit("Room corpus registry does not protect superseded-pack lifecycle transitions")
if "status = 'REMOVAL_PENDING'" not in dao:
    raise SystemExit("Corpus registry row deletion is not gated by REMOVAL_PENDING")
if "private val updateMutex = Mutex()" not in keyring_manager:
    raise SystemExit("Publisher keyring refresh/install are not serialized")
if "CorpusPackMutationLock.mutex.withLock" not in installer or "Files.isSymbolicLink(root.toPath())" not in installer:
    raise SystemExit("Corpus pack filesystem mutation is not serialized or root-symlink checked")

sql = re.search(r'database\.execSQL\("(CREATE TABLE IF NOT EXISTS publisher_keyring_state[^\"]+)"\)', migrations)
if not sql:
    raise SystemExit("Could not extract publisher keyring migration SQL")
connection = sqlite3.connect(":memory:")
connection.execute(sql.group(1))
columns = {row[1]: row[2].upper() for row in connection.execute("PRAGMA table_info(publisher_keyring_state)")}
expected = {"id": "TEXT", "version": "INTEGER", "payload": "BLOB", "signature": "BLOB", "installedAtEpochMillis": "INTEGER"}
if columns != expected:
    raise SystemExit(f"Unexpected publisher_keyring_state columns: {columns}")
connection.execute(
    "INSERT INTO publisher_keyring_state VALUES (?, ?, ?, ?, ?)",
    ("publisher-root", 1, b"canonical", bytes(64), 1234),
)
if connection.execute("SELECT version FROM publisher_keyring_state WHERE id='publisher-root'").fetchone() != (1,):
    raise SystemExit("SQLite migration smoke read failed")
print("Phase 4 guard passed: Room v4 / 3→4 migration, SQLCipher keyring state, registry lifecycle gates, serialized filesystem mutation, and SQLite BLOB persistence")
