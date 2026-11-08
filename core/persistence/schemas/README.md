# Room schema snapshots

Room schema export is configured through KSP (`room.schemaLocation`). The database is currently **schema version 5 with 27 registered entities**. The archive intentionally does not fabricate `5.json`: generate it with the real Room KSP processor on a network-enabled Android Gradle runner, review the exported schema, and commit it with the migration tests before release.

Historical snapshots `1.json` through `4.json` must be retained if/when they are generated. Migrations are explicit and non-destructive: 1→2, 2→3, 3→4 and 4→5. Never replace a prior schema snapshot or add a destructive fallback to make a migration test pass.
