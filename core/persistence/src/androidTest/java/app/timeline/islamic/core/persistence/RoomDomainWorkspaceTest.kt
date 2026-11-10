package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.domain.DomainPayloadCodec
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDomainWorkspaceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun encryptedRoomWorkspaceSupportsHashVerifiedCasAndRejectsTampering(): Unit = runBlocking {
        System.loadLibrary("sqlcipher")
        val name = "phase6-domain-workspace-${System.nanoTime()}.db"
        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .build()
        try {
            val repository = RoomDomainWorkspaceRepository(database)
            val payload = DomainPayloadCodec.encode(mapOf("profile" to "user-defined", "location" to "Toronto"))
            val created = repository.create("prayer-1", "prayer", 1, "DRAFT", payload, 1_800_000_000_000L)
            assertEquals(1L, created.revision)
            assertEquals(payload.toList(), repository.read("prayer-1")!!.payload.toList())
            assertFalse(repository.update("prayer-1", 99L, "DRAFT", payload, 1_800_000_000_001L))
            val updatedPayload = DomainPayloadCodec.encode(mapOf("profile" to "user-defined", "location" to "Shawinigan"))
            assertTrue(repository.update("prayer-1", 1L, "DRAFT", updatedPayload, 1_800_000_000_001L))
            assertEquals(2L, repository.read("prayer-1")!!.revision)
            assertEquals("Shawinigan", DomainPayloadCodec.decode(repository.read("prayer-1")!!.payload)["location"])
            assertFalse(repository.delete("prayer-1", 1L))
            assertEquals(1, repository.list("prayer", 10).size)

            database.domainWorkspaceDao().updateIfRevisionMatches(
                "prayer-1", 2L, "DRAFT", byteArrayOf(99), "0".repeat(64), 1_800_000_000_002L
            )
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.read("prayer-1") } }
        } finally {
            database.close()
            context.deleteDatabase(name)
            File(context.getDatabasePath(name).path + "-wal").delete()
            File(context.getDatabasePath(name).path + "-shm").delete()
        }
    }

    @Test fun additiveV6ToV7MigrationPreservesExistingEncryptedDatabaseRows() {
        System.loadLibrary("sqlcipher")
        val name = "phase6-migration-6-7-${System.nanoTime()}.db"
        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE timeline_item (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                db.execSQL("INSERT INTO timeline_item VALUES ('timeline-1','Preserve timeline')")
                db.execSQL("CREATE TABLE dense_index_generation (generationId TEXT NOT NULL PRIMARY KEY, corpusManifestSha256 TEXT NOT NULL, embeddingProfileSha256 TEXT NOT NULL)")
                db.execSQL("INSERT INTO dense_index_generation VALUES ('gen-1','${"a".repeat(64)}','${"b".repeat(64)}')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("The fixture must remain at version 6")
        }
        val helper = SupportOpenHelperFactory(passphrase.copyOf()).create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        )
        try {
            val db = helper.writableDatabase
            AppDatabaseMigrations.MIGRATION_6_7.migrate(db)
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM timeline_item WHERE id='timeline-1'"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM dense_index_generation WHERE generationId='gen-1'"))
            assertEquals(0, scalar(db, "SELECT COUNT(*) FROM domain_workspace"))
            val columns = db.query("PRAGMA table_info(domain_workspace)").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertEquals(setOf("id", "domain", "schemaVersion", "revision", "status", "payload", "payloadSha256", "createdAtEpochMillis", "updatedAtEpochMillis"), columns)
            val indexes = db.query("PRAGMA index_list(domain_workspace)").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("index_domain_workspace_domain_updatedAtEpochMillis" in indexes)
            assertTrue("index_domain_workspace_status" in indexes)
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
