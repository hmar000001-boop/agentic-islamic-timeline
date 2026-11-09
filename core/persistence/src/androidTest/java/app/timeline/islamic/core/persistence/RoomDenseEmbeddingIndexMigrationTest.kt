package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real v5→v6 migration against SQLCipher and proves non-derived user data survives. */
@RunWith(AndroidJUnit4::class)
class RoomDenseEmbeddingIndexMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun v5MigrationInvalidatesOnlyUnverifiableDenseRowsAndPreservesUserData() {
        System.loadLibrary("sqlcipher")
        val name = "phase5c-v5-v6-${System.nanoTime()}.db"
        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE timeline_item (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                db.execSQL("INSERT INTO timeline_item(id, title) VALUES ('preserve-me', 'User timeline item')")
                db.execSQL("CREATE TABLE installed_corpus_pack (packId TEXT NOT NULL PRIMARY KEY, version TEXT NOT NULL)")
                db.execSQL("INSERT INTO installed_corpus_pack VALUES ('trusted-pack','1.0.0')")
                db.execSQL("CREATE TABLE agent_task (taskId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL)")
                db.execSQL("INSERT INTO agent_task VALUES ('pending-task','WAITING_FOR_APPROVAL')")
                db.execSQL("""CREATE TABLE dense_index_generation (
                    generationId TEXT NOT NULL PRIMARY KEY, packId TEXT NOT NULL, packVersion TEXT NOT NULL,
                    modelId TEXT NOT NULL, modelVersion TEXT NOT NULL, modelSha256 TEXT NOT NULL,
                    dimension INTEGER NOT NULL, vectorCount INTEGER NOT NULL, encodedBytes INTEGER NOT NULL,
                    maximumEncodedBytes INTEGER NOT NULL, state TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("""CREATE TABLE dense_index_vector (
                    generationId TEXT NOT NULL, stableEvidenceKey TEXT NOT NULL, vector BLOB NOT NULL,
                    PRIMARY KEY(generationId, stableEvidenceKey)
                )""".trimIndent())
                db.execSQL("INSERT INTO dense_index_generation VALUES ('old-gen','pack','1.0.0','model','1','${"a".repeat(64)}',768,1,768,1048576,'ACTIVE',1000)")
                db.execSQL("INSERT INTO dense_index_vector VALUES ('old-gen','${"b".repeat(64)}', X'010203')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("This fixture must remain at schema v5 until the migration is applied explicitly")
        }
        val helper = SupportOpenHelperFactory(passphrase.copyOf()).create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        )
        try {
            val db = helper.writableDatabase
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM dense_index_generation"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM dense_index_vector"))

            AppDatabaseMigrations.MIGRATION_5_6.migrate(db)

            assertEquals(0, scalar(db, "SELECT COUNT(*) FROM dense_index_generation"))
            assertEquals(0, scalar(db, "SELECT COUNT(*) FROM dense_index_vector"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM timeline_item WHERE id = 'preserve-me'"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM installed_corpus_pack WHERE packId = 'trusted-pack'"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM agent_task WHERE taskId = 'pending-task' AND state = 'WAITING_FOR_APPROVAL'"))
            db.query("PRAGMA table_info(dense_index_generation)").use { cursor ->
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue("corpusManifestSha256" in columns)
                assertTrue("embeddingProfileSha256" in columns)
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
            File(context.getDatabasePath(name).path + "-wal").delete()
            File(context.getDatabasePath(name).path + "-shm").delete()
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Int = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }
}
