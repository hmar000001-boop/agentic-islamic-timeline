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
}
