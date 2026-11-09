#!/usr/bin/env python3
"""SQLite-backed guard for Room v7, Phase 5C fingerprints and Phase 6's additive workspace migration."""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "core/persistence/src/main/java/app/timeline/islamic/core/persistence"
read = lambda name: (BASE / name).read_text(encoding="utf-8")
db = read("AppDatabase.kt")
entities = read("Entities.kt")
migrations = read("AppDatabaseMigrations.kt")
factory = read("EncryptedDatabaseFactory.kt")
registry = read("RoomCorpusPackRegistry.kt")
dao = read("Daos.kt")
keyring_manager = (ROOT / "core/corpus/src/main/java/app/timeline/islamic/core/corpus/PublisherKeyringManager.kt").read_text(encoding="utf-8")
installer = (ROOT / "core/corpus/src/main/java/app/timeline/islamic/core/corpus/CorpusPackInstaller.kt").read_text(encoding="utf-8")
dense_store = read("RoomDenseEmbeddingIndexStore.kt")

if not re.search(r"version\s*=\s*7", db):
    raise SystemExit("AppDatabase version must be 7")
if db.count("::class") != 28:
    raise SystemExit(f"Expected 28 registered Room entities, found {db.count('::class')}")
for entity, table in (
    ("PublisherKeyringStateEntity", "publisher_keyring_state"),
    ("DenseIndexGenerationEntity", "dense_index_generation"),
    ("DenseIndexVectorEntity", "dense_index_vector"),
    ("DomainWorkspaceEntity", "domain_workspace"),
):
    table_annotation = re.search(r"@Entity\([\s\S]{0,180}?tableName = \"" + re.escape(table) + r"\"", entities)
    if f"{entity}::class" not in db or table_annotation is None:
        raise SystemExit(f"Room entity/table is missing: {entity}/{table}")
for migration, factory_ref in (
    ("MIGRATION_3_4", "AppDatabaseMigrations.MIGRATION_3_4"),
    ("MIGRATION_4_5", "AppDatabaseMigrations.MIGRATION_4_5"),
    ("MIGRATION_5_6", "AppDatabaseMigrations.MIGRATION_5_6"),
    ("MIGRATION_6_7", "AppDatabaseMigrations.MIGRATION_6_7"),
):
    if f"val {migration} = object : Migration(" not in migrations or factory_ref not in factory:
        raise SystemExit(f"EncryptedDatabaseFactory does not register {migration}")
if "STATUS_SUPERSEDED -> setOf(InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING)" not in registry:
    raise SystemExit("Room corpus registry does not protect superseded-pack lifecycle transitions")
if "status = 'REMOVAL_PENDING'" not in dao:
    raise SystemExit("Corpus registry row deletion is not gated by REMOVAL_PENDING")
if "private val updateMutex = Mutex()" not in keyring_manager:
    raise SystemExit("Publisher keyring refresh/install are not serialized")
if "CorpusPackMutationLock.mutex.withLock" not in installer or "Files.isSymbolicLink(root.toPath())" not in installer:
    raise SystemExit("Corpus pack filesystem mutation is not serialized or root-symlink checked")
if "database.withTransaction" not in dense_store:
    raise SystemExit("Dense index generation writes do not use Room transactions")
if "DenseIndexVectorEntity" not in dense_store or "originalText" in dense_store:
    raise SystemExit("Dense index persistence must store only stable evidence keys and vector bytes, not passage text")
if (BASE / "AndroidSqliteDenseEmbeddingIndexStore.kt").exists():
    raise SystemExit("Plaintext sidecar dense index must not exist; vectors must stay inside SQLCipher Room")

connection = sqlite3.connect(":memory:")
connection.execute("PRAGMA foreign_keys=ON")
# Build the v5 base schema from the exact migration 4→5 SQL.
for table in ("publisher_keyring_state", "dense_index_generation", "dense_index_vector"):
    match = re.search(rf'database\.execSQL\("(CREATE TABLE IF NOT EXISTS {table}[^\"]+)"\)', migrations)
    if not match:
        raise SystemExit(f"Could not extract migration SQL for {table}")
    connection.execute(match.group(1))

publisher_columns = {row[1]: row[2].upper() for row in connection.execute("PRAGMA table_info(publisher_keyring_state)")}
expected_publisher = {"id": "TEXT", "version": "INTEGER", "payload": "BLOB", "signature": "BLOB", "installedAtEpochMillis": "INTEGER"}
if publisher_columns != expected_publisher:
    raise SystemExit(f"Unexpected publisher_keyring_state columns: {publisher_columns}")
connection.execute("INSERT INTO publisher_keyring_state VALUES (?, ?, ?, ?, ?)", ("publisher-root", 1, b"canonical", bytes(64), 1234))
vector_columns = {row[1]: row[2].upper() for row in connection.execute("PRAGMA table_info(dense_index_vector)")}
if vector_columns != {"generationId": "TEXT", "stableEvidenceKey": "TEXT", "vector": "BLOB"}:
    raise SystemExit(f"Unexpected dense_index_vector columns: {vector_columns}")
foreign_keys = list(connection.execute("PRAGMA foreign_key_list(dense_index_vector)"))
if len(foreign_keys) != 1 or foreign_keys[0][2] != "dense_index_generation" or foreign_keys[0][6].upper() != "CASCADE":
    raise SystemExit(f"Dense vector generation foreign key/cascade is incorrect: {foreign_keys}")
for statement in re.findall(r'database\.execSQL\("(CREATE INDEX IF NOT EXISTS [^\"]+)"\)', migrations):
    if "domain_workspace" not in statement:
        connection.execute(statement)
index_names = {row[1] for table in ("dense_index_generation", "dense_index_vector") for row in connection.execute(f"PRAGMA index_list({table})")}
for expected in ("index_dense_index_generation_packId_packVersion_modelId_modelVersion_state", "index_dense_index_generation_state", "index_dense_index_vector_generationId"):
    if expected not in index_names:
        raise SystemExit(f"Room migration index missing: {expected}")
connection.execute("INSERT INTO dense_index_generation VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ("gen-1", "pack", "1.0", "embedder", "1", "a" * 64, 16, 1, 16, 1024, "ACTIVE", 1234))
connection.execute("INSERT INTO dense_index_vector VALUES (?, ?, ?)", ("gen-1", "b" * 64, bytes(range(16))))
if connection.execute("SELECT length(vector) FROM dense_index_vector WHERE generationId='gen-1'").fetchone() != (16,):
    raise SystemExit("Dense vector BLOB write/read failed")
connection.execute("DELETE FROM dense_index_generation WHERE generationId='gen-1'")
if connection.execute("SELECT COUNT(*) FROM dense_index_vector").fetchone() != (0,):
    raise SystemExit("Dense vector foreign-key cascade failed")

# Simulate an existing v5 DB, run real 5→6 statements, then seed v6 derived rows.
connection.execute("CREATE TABLE timeline_item (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL)")
connection.execute("INSERT INTO timeline_item VALUES ('keep-user-data', 'Do not delete me')")
connection.execute("CREATE TABLE installed_corpus_pack (packId TEXT NOT NULL PRIMARY KEY, version TEXT NOT NULL)")
connection.execute("INSERT INTO installed_corpus_pack VALUES ('trusted-pack','1.0.0')")
connection.execute("CREATE TABLE agent_task (taskId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL)")
connection.execute("INSERT INTO agent_task VALUES ('pending-task','WAITING_FOR_APPROVAL')")
connection.execute("INSERT INTO dense_index_generation VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ("old-gen", "pack", "1.0.0", "embedder", "1", "a" * 64, 16, 1, 16, 1024, "ACTIVE", 1234))
connection.execute("INSERT INTO dense_index_vector VALUES (?, ?, ?)", ("old-gen", "c" * 64, bytes(range(16))))
block_5_6 = migrations.split("val MIGRATION_5_6", 1)[1].split("val MIGRATION_6_7", 1)[0]
sql_5_6 = re.findall(r'database\.execSQL\("([^\"]+)"\)', block_5_6)
if len(sql_5_6) != 4:
    raise SystemExit(f"Expected four explicit 5→6 SQL statements, found {len(sql_5_6)}")
for statement in sql_5_6:
    connection.execute(statement)
generation_columns = {row[1]: row[2].upper() for row in connection.execute("PRAGMA table_info(dense_index_generation)")}
if generation_columns.get("corpusManifestSha256") != "TEXT" or generation_columns.get("embeddingProfileSha256") != "TEXT":
    raise SystemExit(f"Room v6 dense generation fingerprints are missing: {generation_columns}")
if connection.execute("SELECT COUNT(*) FROM dense_index_generation").fetchone() != (0,) or connection.execute("SELECT COUNT(*) FROM dense_index_vector").fetchone() != (0,):
    raise SystemExit("5→6 must discard only unprovable derived dense generations and vectors")

# Run exact additive 6→7 statements and prove existing domain data survives.
connection.execute("CREATE TABLE user_preference (id TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
connection.execute("INSERT INTO user_preference VALUES ('keep-pref','dark')")
block_6_7 = migrations.split("val MIGRATION_6_7", 1)[1]
sql_6_7 = re.findall(r'database\.execSQL\("([^\"]+)"\)', block_6_7)
if len(sql_6_7) != 3 or not any("CREATE TABLE IF NOT EXISTS domain_workspace" in statement for statement in sql_6_7):
    raise SystemExit(f"Expected 6→7 table plus two indexes, found {len(sql_6_7)} SQL statements")
for statement in sql_6_7:
    connection.execute(statement)
columns = {row[1]: row[2].upper() for row in connection.execute("PRAGMA table_info(domain_workspace)")}
expected = {"id":"TEXT", "domain":"TEXT", "schemaVersion":"INTEGER", "revision":"INTEGER", "status":"TEXT", "payload":"BLOB", "payloadSha256":"TEXT", "createdAtEpochMillis":"INTEGER", "updatedAtEpochMillis":"INTEGER"}
if columns != expected:
    raise SystemExit(f"Unexpected domain_workspace columns: {columns}")
connection.execute("INSERT INTO domain_workspace VALUES ('draft-1','prayer',1,1,'DRAFT',X'0102','" + "a" * 64 + "',100,100)")
if connection.execute("SELECT COUNT(*) FROM domain_workspace WHERE id='draft-1'").fetchone() != (1,):
    raise SystemExit("domain_workspace insert/read failed")
if connection.execute("SELECT value FROM user_preference WHERE id='keep-pref'").fetchone() != ("dark",):
    raise SystemExit("6→7 migration must preserve unrelated user data")
workspace_indexes = {row[1] for row in connection.execute("PRAGMA index_list(domain_workspace)")}
if not {"index_domain_workspace_domain_updatedAtEpochMillis", "index_domain_workspace_status"}.issubset(workspace_indexes):
    raise SystemExit(f"Domain workspace indexes missing: {workspace_indexes}")
if "DROP TABLE" in migrations or "fallbackToDestructiveMigration" in factory:
    raise SystemExit("Room migrations must not use destructive table/database reset")
connection.close()

instrumented = ROOT / "core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexStoreTest.kt"
if not instrumented.is_file() or "SupportOpenHelperFactory" not in instrumented.read_text(encoding="utf-8"):
    raise SystemExit("SQLCipher Room dense-index instrumentation coverage is missing")
phase6_test = ROOT / "core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomDomainWorkspaceTest.kt"
if not phase6_test.is_file() or "SupportOpenHelperFactory" not in phase6_test.read_text(encoding="utf-8"):
    raise SystemExit("SQLCipher Room domain-workspace instrumentation coverage is missing")
print("Room guard passed: v7 / 28 entities / 1→2→3→4→5→6→7 chain; dense fingerprints; additive SQLCipher workspace table; 5→6 dense invalidation; 6→7 user-data preservation")
