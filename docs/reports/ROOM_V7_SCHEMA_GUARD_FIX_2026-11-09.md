# Room v7 schema guard fix — 2026-11-09

## CI failure

The hosted workflow stopped at:

```text
python3 scripts/check-phase4-schema.py
Could not extract migration SQL for publisher_keyring_state
```

This was a verifier/parser defect, not a Room migration or SQLCipher runtime failure. `AppDatabaseMigrations.kt` consistently invokes `SupportSQLiteDatabase.execSQL` through the local parameter named `db`:

```kotlin
override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS publisher_keyring_state (...)")
}
```

The Python verifier searched only for `database.execSQL(...)`. It therefore rejected the first migration DDL before SQLite schema simulation began. The error did not show that the publisher keyring table was malformed or that Room/SQLCipher could not migrate it.

## Correction

`scripts/check-phase4-schema.py` now uses one Kotlin-string-aware SQL extractor that accepts the actual `db.execSQL(...)` receiver and remains compatible with the historical `database.execSQL(...)` spelling. It decodes ordinary Kotlin escapes and has inline regression fixtures for both receiver forms and escaped quotes.

The verifier has also been tightened rather than relaxed:

- checks the complete 1→2→3→4→5→6→7 migration registration, in order;
- checks that the encrypted Room factory creates `SupportOpenHelperFactory` with the Keystore-managed passphrase and calls `openHelperFactory(factory)`;
- checks the Room v7 version and all 28 registered entities;
- executes the extracted SQL for publisher keyring and dense-index schema creation, dense migration 5→6, and additive workspace migration 6→7 against an in-memory SQLite fixture;
- verifies expected columns, indices, foreign-key cascade, dense-generation invalidation and preservation of unrelated user rows;
- verifies that the SQLCipher migration instrumentation source uses `SupportOpenHelperFactory` and explicitly exercises `MIGRATION_6_7`.

No migration SQL, entity, schema version, app runtime behavior, dependency or versionCode was changed by this hotfix.

## Verification performed

Passed in the local source workspace:

- `python3 -m py_compile scripts/check-phase4-schema.py`
- `python3 scripts/check-phase4-schema.py`
- Phase 5C retrieval source contract: 33/33
- Phase 5C production `readBatch()` SQL fixture
- Phase 6 domain workspace contract: 12/12
- Phase 6 domain-engine source contract: 56/56
- Room dense-index contract: 12/12
- Persistence Android-test source contract: 14/14
- `python3 -m compileall -q scripts`
- repository scan found no remaining verifier dependency on the literal `database.execSQL` receiver.

The verifier uses Python's SQLite to execute the SQL extracted directly from Kotlin source. That validates parser correctness and DDL/data-preservation semantics, but it is not a substitute for the real SQLCipher/Room Android instrumentation test.

## Remaining acceptance gates

- Rerun the GitHub Actions workflow. The reported `check-phase4-schema.py` step must pass on the hosted runner.
- Let Gradle/KSP generate the real Room `7.json`; do not synthesize it. `verify-room-schema-export.py` remains a later CI gate.
- Run the API 36 SQLCipher/Room connected instrumentation tests.
- Review the generated schema and keep the existing 1→2→3→4→5→6→7 migration path.

## Delivery provenance

This fix is incremental against the recovered Phase 6 0.7.0 thermal-hotfix archive used in this session. That archive was not certified byte-identical to the user's current Git checkout. Check the patch against the exact checkout before applying; do not overwrite unrelated repository work.
