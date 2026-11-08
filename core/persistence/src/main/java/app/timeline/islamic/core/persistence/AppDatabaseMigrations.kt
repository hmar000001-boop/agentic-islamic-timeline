package app.timeline.islamic.core.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Phase 3 adds the grant provenance to an exact approval; existing one-time approvals migrate as null. */
object AppDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE pending_approval ADD COLUMN sourceGrantId TEXT")
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE action_execution ADD COLUMN proposalId TEXT")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_action_execution_proposalId ON action_execution(proposalId)")
        }
    }
    /** Phase 4B stores the exact signed publisher keyring envelope in the encrypted Room database. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS publisher_keyring_state (id TEXT NOT NULL PRIMARY KEY, version INTEGER NOT NULL, payload BLOB NOT NULL, signature BLOB NOT NULL, installedAtEpochMillis INTEGER NOT NULL)")
        }
    }
    /** Phase 4C stores local dense-index generations and int8 vectors inside SQLCipher-protected Room. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS dense_index_generation (generationId TEXT NOT NULL PRIMARY KEY, packId TEXT NOT NULL, packVersion TEXT NOT NULL, modelId TEXT NOT NULL, modelVersion TEXT NOT NULL, modelSha256 TEXT NOT NULL, dimension INTEGER NOT NULL, vectorCount INTEGER NOT NULL, encodedBytes INTEGER NOT NULL, maximumEncodedBytes INTEGER NOT NULL, state TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dense_index_generation_packId_packVersion_modelId_modelVersion_state ON dense_index_generation(packId, packVersion, modelId, modelVersion, state)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dense_index_generation_state ON dense_index_generation(state)")
            database.execSQL("CREATE TABLE IF NOT EXISTS dense_index_vector (generationId TEXT NOT NULL, stableEvidenceKey TEXT NOT NULL, vector BLOB NOT NULL, PRIMARY KEY(generationId, stableEvidenceKey), FOREIGN KEY(generationId) REFERENCES dense_index_generation(generationId) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dense_index_vector_generationId ON dense_index_vector(generationId)")
        }
    }
}
