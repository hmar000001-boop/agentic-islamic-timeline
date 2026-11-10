#!/usr/bin/env python3
"""Static contract for Phase 6's additive, SQLCipher-backed domain workspace migration."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text(encoding="utf-8")
db = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt")
entities = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/Entities.kt")
migrations = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabaseMigrations.kt")
factory = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/EncryptedDatabaseFactory.kt")
dao = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt")
store = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomDomainWorkspaceRepository.kt")
android_test = read("core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomDomainWorkspaceTest.kt")
workflow = read(".github/workflows/android-ci.yml")
checks = {
    "Room schema v7": "version = 7" in db,
    "28 registered Room entities": db.count("::class") == 28,
    "domain_workspace registered": "DomainWorkspaceEntity::class" in db,
    "domain workspace entity payload/hash/revision": all(x in entities for x in ["tableName = \"domain_workspace\"", "payloadSha256", "schemaVersion", "revision"]),
    "additive 6->7 migration": "Migration(6, 7)" in migrations and "CREATE TABLE IF NOT EXISTS domain_workspace" in migrations,
    "both workspace indexes": "index_domain_workspace_domain_updatedAtEpochMillis" in migrations and "index_domain_workspace_status" in migrations,
    "migration installed in database factory": "AppDatabaseMigrations.MIGRATION_6_7" in factory,
    "DAO compare-and-set update": "revision = revision + 1" in dao and "revision = :expectedRevision" in dao,
    "store verifies payload hash": "DomainWorkspaceRecord(" in store and "payloadSha256 = hash" in store,
    "no destructive migration": "fallbackToDestructiveMigration" not in factory and "DROP TABLE domain_workspace" not in migrations,
    "SQLCipher migration and repository instrumentation exist": "SupportOpenHelperFactory" in android_test and "MIGRATION_6_7.migrate(db)" in android_test,
    "Android instrumentation task is wired into CI": ":core:persistence:connectedDebugAndroidTest" in workflow,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " " + label)
if not all(checks.values()): raise SystemExit(1)
print(f"Phase 6 domain workspace contract passed: {len(checks)}/{len(checks)}")
