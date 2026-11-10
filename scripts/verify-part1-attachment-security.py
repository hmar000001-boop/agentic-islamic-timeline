#!/usr/bin/env python3
"""Static guard for attachment encryption and Room lifecycle wiring."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
store = (root / "core/security/src/main/java/app/timeline/islamic/core/security/EncryptedAttachmentStore.kt").read_text(encoding="utf-8")
repo = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomDocumentAttachmentRepository.kt").read_text(encoding="utf-8")
dao = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt").read_text(encoding="utf-8")
app = (root / "app/src/main/java/app/timeline/islamic/TimelineApplication.kt").read_text(encoding="utf-8")
keystore_test = (root / "core/security/src/androidTest/java/app/timeline/islamic/core/security/EncryptedAttachmentKeystoreTest.kt").read_text(encoding="utf-8")
unit_test = (root / "core/security/src/test/java/app/timeline/islamic/core/security/EncryptedAttachmentStoreTest.kt").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
checks = {
    "AES-GCM authenticated encryption": "AES/GCM/NoPadding" in store and "GCMParameterSpec" in store,
    "Keystore-generated randomized IV": "init(Cipher.ENCRYPT_MODE, key)" in store and "val iv = cipher.iv" in store and "cipher.iv" in store and "init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec" not in store,
    "Android Keystore AES key": "AndroidKeyStore" in store and "KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT" in store,
    "no replacement key while ciphertext exists": "getOrCreateKey(allowCreate = !hasEncryptedPayloadFiles())" in store and "keyProvider.getOrCreateKey(allowCreate = false)" in store,
    "attachment ID is authenticated as AAD": "updateAAD(associatedData(id))" in store,
    "original files use no-backup storage": "noBackupFilesDir" in store and "ATTACHMENT_DIRECTORY = \"attachments\"" in store,
    "temporary plaintext is isolated in app cache": "context.applicationContext.cacheDir" in store and "DECRYPT_TEMP_DIRECTORY = \"timeline-attachment-decrypt\"" in store,
    "streaming bounded import and ciphertext size check": "maximumPlaintextBytes - total" in store and "ByteArray(IO_BUFFER_BYTES)" in store and "maximumEncryptedBytes" in store,
    "durable temporary-file write before rename": "output.fd.sync()" in store and "temporary.renameTo(destination)" in store,
    "GCM finalization precedes plaintext callback": store.index("val finalBytes = cipher.doFinal()") < store.index("return FileInputStream(plaintextTemp).use(consumer)"),
    "plaintext temp cleanup": "finally" in store and "plaintextTemp.delete()" in store,
    "hash and size cross-check": "constantTimeHexEquals(actualHash, expectedSha256)" in store and "total != expectedPlaintextBytes" in store,
    "Room metadata CRUD": all(x in dao for x in ("suspend fun putAttachment", "suspend fun attachment(", "suspend fun attachments(", "suspend fun deleteAttachment(")),
    "import rollback and orphan recovery": "failure.addSuppressed" in repo and "encryptedStore.reconcile(existingIds)" in repo,
    "application repository access": "RoomDocumentAttachmentRepository(database, EncryptedAttachmentStore(this))" in app,
    "provider-generated IV regression test": "samePlaintextUsesDistinctProviderGeneratedGcmIvs" in unit_test,
    "Android Keystore lifecycle test": "missingKeystoreKeyIsNotRecreatedWhileCiphertextExists" in keystore_test and "keystoreKeySurvivesStoreRecreationAndAuthenticatesContent" in keystore_test,
    "Android Keystore instrumentation is in CI": ":core:security:connectedDebugAndroidTest" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Part 1 attachment security checks failed: " + ", ".join(failed))
# Schema must not be advanced for these additive DAO/repository methods.
database = (root / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt").read_text(encoding="utf-8")
if "version = 7" not in database:
    raise SystemExit("Part 1 unexpectedly changed the Room schema version")
print(f"Part 1 attachment static checks passed ({len(checks)} contracts); Room remains v7.")
