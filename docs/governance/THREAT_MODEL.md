# Threat model — offline Android timeline

## Scope and assumptions

This model covers the Android application, its private local database, files, imported source/model artifacts, agent proposals and the offline sideload flavor. It assumes an attacker may obtain an app-private file through device compromise, exploit an import path, tamper with local storage, interrupt the process during writes, or induce incorrect outputs with malicious content. It does not claim protection against a fully compromised/rooted operating system, a malicious OS/vendor, coercion, physical observation of an unlocked screen, or vulnerabilities in Android/cryptographic providers.

## Assets

- Personal timeline, location-derived and prayer context, preferences, plans, notes and attachments.
- SQLCipher database and Keystore-wrapped database key.
- Attachment encryption keys, ciphertext, metadata, hashes and temporary plaintext buffers/files.
- Imported Qur'an/hadith/tafsir/fiqh corpora, citations, rights manifests and review records.
- Model weights, model catalog identity, checksums and inference inputs/outputs.
- Agent task state, proposals, permissions, approval decisions, audit records and action receipts.
- App signing material, CI credentials, dependency lockfiles and build artifacts.

## Trust boundaries

1. External picker/content provider → import stream → app-private storage.
2. App-private encrypted files ↔ SQLCipher metadata database.
3. Android Keystore ↔ encrypted database and attachment payloads.
4. Publisher-provided corpus/model artifact → manifest/hash/license validation → installed local artifact.
5. Retrieved source evidence → inference model → candidate explanation/proposal → authorization engine → side-effect handler.
6. CI checkout/dependencies/build outputs → downloadable validation artifact.

## Threats and required controls

| Threat | Required control | Residual risk / validation |
|---|---|---|
| Plaintext attachment written to shared/external storage | Keep encrypted originals in `noBackupFilesDir`; short-lived plaintext only in private temporary storage after authenticated decryption | Viewer/export flows still need explicit review; test crash cleanup and backup rules |
| Ciphertext substitution between attachment IDs | Bind attachment ID/version as AES-GCM AAD | Verify wrong-ID and swapped-file tests |
| Ciphertext tampering/truncation | AES-GCM tag validation; do not expose plaintext to consumer until tag and stored SHA/size checks pass | Verify malformed header, truncation, modified tag and corrupted metadata |
| Oversized or hostile imports | Bounded streaming, no full-file buffering, MIME/ID validation, no untrusted path joins | Verify limit boundary, huge streams and provider read failures |
| Crash leaves incomplete or orphan files | Write temporary file, sync, atomic rename; reconcile orphan ciphertext against Room records | Verify process-kill points on device and idempotent reconciliation |
| Database row refers to missing or substituted file | Compare stored relative path, ID, size and SHA with verified file; report unavailable, never silently recreate | Verify corrupted metadata and missing-file behavior |
| Backup copies keys/ciphertext inconsistently | Private no-backup storage; explicit cloud/device-transfer exclusions and documented recovery policy | Backup/device-transfer test remains pending |
| Keystore key invalidation or missing envelope | Fail closed; no replacement key for existing encrypted data and no plaintext fallback | Device tests across API levels and lock-state changes remain pending |
| Agent/model induces unsafe action | Model has no direct side-effect capability; authorization engine enforces human confirmation and scoped grants | End-to-end UI/handler tests remain pending; not changed in Parts 0/1 |
| Unlicensed or unreviewed religious content | Source-rights registry and version/hash-bound scholarly approval gate | Corpus audit and reviewer sign-off remain pending |
| CI artifact confused with release APK | Upload explicitly named offline debug validation APK with checksum; no signing secrets | CI artifact inspection and release pipeline separation remain required |

## Security invariants

- No plaintext attachment is persisted as the canonical object.
- Authenticated decryption must finish before content is made available to a consumer.
- A failed import must not create a Room row; cleanup failure must be visible and recoverable.
- A failed key lookup or tag verification must not trigger plaintext fallback or automatic re-keying.
- External sharing/export is a separate user-authorized operation and is not implied by import or read access.
- Model output is untrusted input and cannot grant itself permissions.

## Review triggers

Re-review on storage format changes, Room migrations, new attachment viewers/exporters, backup policy changes, cryptographic dependency/runtime changes, model-tool integration, a security incident or any change to authorization boundaries.
