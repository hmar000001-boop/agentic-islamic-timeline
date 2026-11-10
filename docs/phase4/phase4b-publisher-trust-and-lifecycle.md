# Phase 4B — publisher trust and corpus-pack lifecycle

**Source version:** `0.4.1-phase4b-publisher-trust-lifecycle`  
**Baseline:** immutable Phase 4A `0.4.0`  
**Scope:** trusted publisher keyring, SAF stream import, pack update/revalidation/removal, Room schema v4.

## 1. Trust model

The corpus package is untrusted input. The signed corpus manifest binds the index-file SHA-256; the manifest signature is checked against an active publisher key. Publisher keys are in turn admitted only through a root-signed keyring verified against a pinned application root public key.

`PinnedPublisherRoot` is an explicit constructor dependency. There is intentionally no example/default/generated public root key in the product. A release composition must pin and review a real root key before publisher-keyring import or corpus-pack installation is enabled. This source archive does not ship any private signing key or signed religious corpus.

The Ed25519 implementation uses Bouncy Castle's lightweight `org.bouncycastle.math.ec.rfc8032.Ed25519` API so the design does not depend on Android provider availability. A Bouncy Castle RFC 8032 vector was executed locally. Actual Gradle dependency resolution, Android DEX and API 26–36 runtime validation remain CI/device gates.

## 2. Canonical keyring format

The detached signature is exactly 64 bytes and covers every byte of the UTF-8 payload, including the final LF. Payloads are LF-only, have no UTF-8 BOM, no trailing blank line, sorted unique key IDs and canonical lowercase hexadecimal key bytes.

Grammar:

```text
format=timeline-publisher-keyring
version=<positive integer>
generated_at_epoch_ms=<positive integer>
key_count=<1..256>
<key-id>|<64 lowercase hex chars>|<not-before epoch ms>|<revoked-at epoch ms or ->
...
```

Publisher public keys are raw 32-byte Ed25519 encodings. Key IDs follow `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`. A revocation timestamp may not precede the activation timestamp. Keyring payload size is capped at 256 KiB.

The downloadable/SAF archive contains exactly two non-directory entries: `keyring.txt` and `keyring.sig`. Compressed archive size is capped at 512 KiB; expanded payload is capped at 300 KiB. Unexpected paths, duplicate entries, traversal names and entry overflow are rejected. The exact payload and signature are passed to `PublisherKeyringManager`; no archive entry is extracted to shared storage.

## 3. Keyring manager invariants

- The root signature must verify before a candidate keyring is considered.
- The keyring `version` must be greater than the stored version. Its `generated_at_epoch_ms` cannot move backwards and cannot be in the future.
- Existing publisher key IDs cannot be removed or rebound to a different raw public key or activation timestamp.
- An established revocation timestamp is immutable; a later keyring cannot un-revoke that key.
- A key is usable only when it is within its activation/revocation interval at both the pack's signed creation time and the current time.
- The exact signed payload and detached signature are persisted, then reverified on refresh. Invalid/missing persisted trust clears in-memory trust (fail closed).
- `refresh()` and `install()` share a coroutine `Mutex`; their database read/verify/CAS/publish sequences cannot interleave and publish an older in-memory map after a newer update.
- Persistence uses compare-and-set on the expected current keyring version inside one Room transaction. This rejects concurrent writes even if another process or store client is introduced.

The store is `RoomPublisherKeyringStore` and writes to `publisher_keyring_state`, which is inside the existing SQLCipher database. Publisher key data is not kept in plaintext DataStore/preferences.

## 4. Room migration 3 → 4

`AppDatabase` now registers 25 entities and schema version 4. Migration `MIGRATION_3_4` creates:

```sql
CREATE TABLE IF NOT EXISTS publisher_keyring_state (
  id TEXT NOT NULL PRIMARY KEY,
  version INTEGER NOT NULL,
  payload BLOB NOT NULL,
  signature BLOB NOT NULL,
  installedAtEpochMillis INTEGER NOT NULL
)
```

There is no destructive fallback. The migration is registered in `EncryptedDatabaseFactory`. `scripts/check-phase4-schema.py` asserts entity/schema/migration registration and exercises the exact table SQL in SQLite, including BLOB persistence. This static guard is not a replacement for Room `MigrationTestHelper`, generated Room schema JSON or real SQLCipher migration/reopen tests. KSP-generated `4.json` remains pending the first successful Android Gradle build.

## 5. Pack update lifecycle

The Room registry inventory uses these states:

- `INSTALLED`: active and eligible for retrieval after revalidation.
- `QUARANTINED`: retained on disk but excluded from the active registry/retrieval set pending a clean trust/integrity pass.
- `SUPERSEDED`: prior version retained after a successful update; not active.
- `REMOVAL_PENDING`: explicit removal intent recorded before deletion, enabling crash recovery.

The update API requires an expected active version and a strictly greater semantic version. It validates the candidate archive before activation, writes the exact signed manifest/signature beside the immutable index, and performs an atomic directory rename under app-private storage. Then the registry transaction checks that exactly one active version still matches the expected version, changes that row to `SUPERSEDED`, and inserts the new `INSTALLED` row. A stale expected version or a database conflict rejects the update; if the Room commit fails after the filesystem rename, the new unregistered directory is rolled back. Old versions are not deleted by an update.

## 6. Startup revalidation and recovery

`CorpusPackRuntime` orders these operations:

1. Refresh/reverify the signed publisher keyring (or clear trust on failure).
2. Complete `REMOVAL_PENDING` deletions from a previous interrupted process.
3. Remove unregistered staging/orphan directories without following links.
4. Revalidate all `INSTALLED` and `QUARANTINED` pack records.

Revalidation verifies canonical manifest encoding and hash, publisher signature/key activation and revocation, index SHA-256, license profile, index metadata, private canonical paths and absence of symlinked path components/files. An invalid `INSTALLED` row is transitioned to `QUARANTINED`, so `listInstalled()` excludes it. A later valid revalidation may restore a previously quarantined row. `revalidateAfterTrustUpdate()` permits rechecking after an approved keyring import.

Removal first changes a row to `REMOVAL_PENDING`; filesystem deletion refuses symbolic links and any canonical path outside the app-private corpus root. Only after deletion (or confirmation that the corpus root is absent) is the pending registry record deleted. The Room adapter restricts legal status transitions and its DAO delete is conditional on `REMOVAL_PENDING`.

## 7. SAF integration boundary

`SafCorpusPackImporter` and `SafPublisherKeyringImporter` accept a `content://` URI from a user-selected document provider and stream the content directly into the corresponding bounded importer. They do not request a persistent URI permission because the operation is immediate and does not retain the URI. The app still needs an explicit picker/management UI and the runtime composition that supplies the pinned root key.

## 8. Residual risks and release gates

- No real publisher root public key is pinned. Do not enable import in a production build until an independently reviewed root and a documented offline key-custody/rotation procedure are supplied.
- No signed Quran, hadith, tafsir or fiqh packs are included. Copyright/redistribution rights and scholarly approval remain separate mandatory release gates.
- The monotonic keyring version protects against ordinary stale writes and concurrent in-process updates but has no independent hardware anti-rollback anchor. An attacker able to restore an older complete app database image may restore an older signed keyring snapshot. The current user database backup policy is intended to exclude private state, but the source code must not claim this as hardware rollback resistance.
- Root-key rotation is not an in-band publisher-key rotation. Replacing the root is a reviewed app update / trust migration and must be handled with a separate recovery and overlap plan.
- Actual Android Gradle/KSP/Room/SQLCipher/AndroidX runtime, API 26–36 emulator and Galaxy S24 FE tests remain unexecuted here.
- `CorpusPackRuntime` and SAF bridges are extension components; app-level startup wiring and pack/keyring management UI are not yet connected.
