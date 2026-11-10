# Timeline Agentique checkpoint — Room v7 schema guard fix

**Date:** 2026-11-09
**Application version:** `0.7.0-phase6-domain-engines` (`versionCode = 25`, unchanged)
**Scope:** CI verifier correction only
**Prior failing task:** `python3 scripts/check-phase4-schema.py`

## Root cause

The verifier searched only for `database.execSQL(...)`; production migration code uses `db.execSQL(...)`. It failed while extracting the publisher keyring table DDL, before executing the migration simulation. This was a false negative in the test tool, not a demonstrated Room or SQLCipher schema defect.

## Corrective change

- Added a single Kotlin execSQL string extractor supporting both `db` and `database` receiver names.
- Added parser regression examples for both receiver names and escaped quotes.
- Kept the schema and migration assertions strict.
- Added full ordered migration-chain checks (1→2→3→4→5→6→7).
- Added SQLCipher factory assertions for `SupportOpenHelperFactory`, Keystore-managed passphrase, and `openHelperFactory(factory)`.
- Required the SQLCipher Android test source to exercise the 6→7 migration.
- No changes to migration SQL, Room entities, encrypted payload format, app runtime, or versionCode.

## Local checks

- `check-phase4-schema.py`: PASS.
- Phase 5C retrieval contracts: 33/33.
- Phase 5C readBatch SQL fixture: PASS.
- Phase 6 workspace contract: 12/12.
- Phase 6 domain contract: 56/56.
- Room dense-index contract: 12/12.
- Persistence Android-test source contract: 14/14.
- Python syntax for all scripts: PASS.

## Resume instructions

1. Apply/check the incremental patch against the exact current Git checkout.
2. Rerun the same GitHub Actions workflow.
3. Confirm `Validate Room v7, encrypted dense-index and domain-workspace migrations` passes.
4. Confirm Gradle/KSP exports exactly one valid `7.json` and the following `Verify generated Room v7 schema snapshot` step passes.
5. Run API 36 SQLCipher/Room connected instrumentation. Do not mark encrypted migration acceptance complete until it passes.

## Limitations

SQLite fixture execution and static SQLCipher wiring checks passed locally; real Android Room/SQLCipher migration instrumentation was not run in this environment. Source provenance remains the recovered 0.7.0 archive, not a byte-identical verified copy of the user's active checkout.
