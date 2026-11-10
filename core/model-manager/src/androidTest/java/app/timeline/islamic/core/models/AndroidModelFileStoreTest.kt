package app.timeline.islamic.core.models

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidModelFileStoreTest {
    @Test
    fun commitAtomicallyConsumesStagingAndRemovalSyncsDirectoryHierarchy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testParent = newPrivateTestParent(context)
        try {
            val bytes = "android-os-atomic-rename-fixture".toByteArray()
            val spec = modelSpec(bytes)
            val store = AndroidModelFileStore(testParent)
            store.prepare(spec)
            store.createInstallOwnershipMarker(spec, "a".repeat(64))
            val staging = store.createStagingFile(spec)
            staging.writeBytes(bytes)
            val destination = store.finalFile(spec)

            store.commit(staging, destination)

            assertFalse("atomic rename must consume the staging path", staging.exists())
            assertArrayEquals(bytes, destination.readBytes())
            assertTrue("model-owned files and empty directories must be removable", store.deleteOwnedFiles(spec, "a".repeat(64)))
            assertFalse(destination.exists())
        } finally {
            testParent.deleteRecursively()
        }
    }

    @Test
    fun commitRejectsAnExistingDestinationWithoutOverwritingIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testParent = newPrivateTestParent(context)
        try {
            val bytes = "verified-staging-bytes".toByteArray()
            val unrelated = "unregistered-existing-artifact".toByteArray()
            val spec = modelSpec(bytes)
            val store = AndroidModelFileStore(testParent)
            store.prepare(spec)
            val staging = store.createStagingFile(spec)
            staging.writeBytes(bytes)
            val destination = store.finalFile(spec)
            destination.writeBytes(unrelated)

            val rejected = runCatching { store.commit(staging, destination) }.isFailure

            assertTrue("commit must reject a destination that already exists", rejected)
            assertArrayEquals("existing destination bytes must remain untouched", unrelated, destination.readBytes())
            assertArrayEquals("failed commit must leave the verified staging bytes available for rollback", bytes, staging.readBytes())
        } finally {
            testParent.deleteRecursively()
        }
    }

    @Test
    fun deleteRejectsAnOwnershipMarkerForAnotherRegistryRecord() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testParent = newPrivateTestParent(context)
        try {
            val bytes = "owned-by-one-record".toByteArray()
            val unrelated = "must-not-be-deleted-by-another-record".toByteArray()
            val owner = "a".repeat(64)
            val otherOwner = "b".repeat(64)
            val spec = modelSpec(bytes)
            val store = AndroidModelFileStore(testParent)
            store.prepare(spec)
            store.createInstallOwnershipMarker(spec, owner)
            val staging = store.createStagingFile(spec)
            staging.writeBytes(bytes)
            val destination = store.finalFile(spec)
            destination.writeBytes(unrelated)

            assertFalse("a different registry row must not delete these bytes", store.deleteOwnedFiles(spec, otherOwner))
            assertArrayEquals(bytes, staging.readBytes())
            assertArrayEquals(unrelated, destination.readBytes())
            assertTrue("the exact owner may delete its model-owned files", store.deleteOwnedFiles(spec, owner))
        } finally {
            testParent.deleteRecursively()
        }
    }

    private fun newPrivateTestParent(context: Context): File =
        File(context.cacheDir, "model-file-store-test-${UUID.randomUUID()}").also {
            check(it.mkdirs()) { "Unable to create isolated model-file-store test directory" }
        }

    private fun modelSpec(bytes: ByteArray) = ModelArtifactSpec(
        id = "instrumented-model",
        displayName = "Instrumented model",
        description = "Android filesystem test fixture",
        descriptionArabic = "بيانات اختبار نظام الملفات",
        task = ModelTask.EMBEDDING,
        repository = "test/model-file-store",
        revision = "0123456789abcdef0123456789abcdef01234567",
        artifactFileName = "instrumented-model.litertlm",
        expectedBytes = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        licenseId = ModelCatalog.APACHE_2_0,
        licenseUrl = "https://example.invalid/license",
        minimumDeviceRamBytes = null
    )
}
