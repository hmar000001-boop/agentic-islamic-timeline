package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.security.KeystoreAuditMacProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class RoomAuditLogTest {
    private lateinit var db: AppDatabase
    private lateinit var audit: RoomAuditLog

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        audit = RoomAuditLog(db, KeystoreAuditMacProvider(context, "timeline.test.audit.${System.nanoTime()}"))
    }

    @After fun tearDown() { db.close() }

    @Test fun appendsAndVerifiesHmacChainWithoutPersistingPayload() = runBlocking {
        val payload = "private-details-must-not-be-stored".toByteArray()
        assertEquals(1L, audit.append("ACTION_APPROVED", payload, Instant.parse("2026-11-07T18:00:00Z")))
        assertEquals(2L, audit.append("ACTION_EXECUTION", "receipt-hash-only".toByteArray(), Instant.parse("2026-11-07T18:00:01Z")))
        assertTrue(audit.verify() is app.timeline.islamic.core.agent.AuditVerification.Valid)
        val rows = db.auditDao().allAscending()
        assertEquals(sha(payload), rows.first().payloadHash)
        assertFalse(rows.first().recordMac == rows.first().payloadHash)
    }

    @Test fun tamperedRecordMacIsDetected() = runBlocking {
        audit.append("TASK_CREATED", "id-1".toByteArray(), Instant.parse("2026-11-07T18:00:00Z"))
        db.openHelper.writableDatabase.execSQL("UPDATE audit_event SET recordMac = '00' WHERE sequence = 1")
        val result = audit.verify()
        assertTrue(result is app.timeline.islamic.core.agent.AuditVerification.Invalid)
    }

    @Test fun missingHistoricalKeystoreKeyFailsClosedAndDoesNotResetChain() = runBlocking {
        val alias = "timeline.test.audit.missing.${System.nanoTime()}"
        val provider = KeystoreAuditMacProvider(context, alias)
        val localAudit = RoomAuditLog(db, provider)
        localAudit.append("ACTION_APPROVED", "hash-only".toByteArray(), Instant.parse("2026-11-07T18:00:00Z"))
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        val verification = localAudit.verify()
        assertTrue(verification is app.timeline.islamic.core.agent.AuditVerification.Invalid)
        try {
            localAudit.append("SHOULD_NOT_APPEND", "new-root".toByteArray(), Instant.parse("2026-11-07T18:00:01Z"))
            fail("Audit append must fail closed when the historical MAC key is missing")
        } catch (_: IllegalStateException) { }
        assertEquals(1, db.auditDao().allAscending().size)
    }

    private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
