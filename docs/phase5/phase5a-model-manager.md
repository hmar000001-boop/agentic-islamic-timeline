# Phase 5A — Offline model inventory and verified SAF installation

**Version:** `0.6.0-phase5a-model-manager-saf-import` (`versionCode = 21`)
**Baseline:** CI-green Phase 4 source `0.5.16`
**Room schema:** v5, unchanged
**Runtime status:** no LiteRT-LM inference engine is initialized by this phase

## Goals

Phase 5A creates a safe, offline lifecycle for optional model artifacts. It does not enable AI inference. The app remains useful without an imported artifact, and model bytes are never treated as active merely because a checksum passed.

The tranche delivers:

- an immutable, application-owned catalog for exact LiteRT-LM artifacts;
- user-selected `content://` SAF import only;
- bounded streaming, exact byte-count and SHA-256 verification;
- app-private no-backup storage with exclusive staging and final-path reservation and atomic commit;
- Room v5 lifecycle metadata and interrupted-operation recovery;
- quarantine on unknown status, metadata mismatch or file tampering;
- exact-file removal with retryable `REMOVAL_PENDING` state;
- RAM/storage/thermal resource estimates that never claim runtime readiness;
- Arabic/English Models screen reachable from app navigation;
- CI source-contract coverage and Kotlin model-manager tests.

## Immutable catalog

Catalog IDs, repository names, immutable revisions, artifact filenames, byte counts, SHA-256 values and license IDs are hard-coded in `ModelCatalog`. SAF input cannot choose or override any of them. The provider's filename/display name never participates in a destination path.

| Catalog ID | Artifact | Bytes | RAM floor (source metadata only) | License |
|---|---|---:|---:|---|
| `gemma-4-e2b-it` | `gemma-4-E2B-it.litertlm` | 2,588,147,712 | 8 GiB | Apache-2.0 |
| `gemma-4-e4b-it` | `gemma-4-E4B-it.litertlm` | 3,659,530,240 | 12 GiB | Apache-2.0 |
| `embeddinggemma-2-text-270m` | `embeddinggemma-2-text-270m.litertlm` | 164,626,432 | Not asserted | Apache-2.0 |

The exact 40-character revisions and 64-character SHA-256 values are in `core/model-manager/.../ModelCatalog.kt`. The Gemma RAM floors mirror Google's Android model-allowlist metadata and are not a compatibility promise. No minimum RAM is inferred for EmbeddingGemma when the catalog source does not assert one. The app does not fetch any of these repositories; the repository field is provenance/catalog metadata only.

## Import flow

1. The user opens **Models** and selects a catalog item.
2. The app opens Android's document picker and receives one selected URI.
3. `SafModelArtifactImporter` accepts only `content://` and lazily opens its input stream. It does not call `takePersistableUriPermission`.
4. `ModelManager` verifies the ID belongs to the built-in catalog and inserts an `INSTALLING` Room record.
5. `AndroidModelFileStore` derives the destination from the catalog ID and immutable revision under `noBackupFilesDir/on-device-models`; it rejects symlink/path escapes and refuses to overwrite an existing unregistered final artifact.
6. A staging file is created with exclusive-create semantics. Bytes stream in 64 KiB chunks. The importer rejects any byte beyond the exact catalog size and verifies the final byte count and incremental SHA-256.
7. The file descriptor is synced. Only then is a same-directory `Os.rename` used to atomically publish the artifact; the containing directory is synced.
8. The committed path is re-hashed. The Room row transitions from `INSTALLING` to `INSTALLED` with a compare-and-set update.

No HTTP client, download manager, model URL fetch, remote inference endpoint, account, telemetry or broad storage permission is added. A failed import cleans only files created by that import attempt. Before creating staging bytes, it writes and syncs a sidecar ownership marker containing the matching registry record ID. Recovery may remove an interrupted staging file only when that marker matches and no final artifact exists. A pre-existing or invalid final artifact, a marker mismatch, and an unregistered `.partial` file are quarantined/preserved rather than deleted by filename pattern. If cleanup itself fails, the registry is retained as `QUARANTINED` for operator-visible recovery.

## Storage and trust boundary

- Weight files: `noBackupFilesDir/on-device-models/<catalog-id>/<40-char-revision>/<catalog-filename>`.
- Staging file: same directory and filesystem, with `.partial` suffix.
- Metadata: existing SQLCipher-protected Room table `installed_model`.
- Model weight files themselves are app-private sandbox files; they are not individually SQLCipher-encrypted in Phase 5A. They are excluded from backup by their `noBackupFilesDir` location (the app also has backup disabled).
- Symlinks, canonical path escapes, provider-supplied path names and recursive deletion are rejected.
- Removal never traverses corpus, dense-index, attachments or general user-data paths.
- A model's content and outputs are untrusted. They cannot approve actions, create grants, claim an execution or call a side-effect handler.

## Lifecycle and recovery

```text
INSTALLING ── verified exact artifact ──> INSTALLED
     │                                   │
     ├── failed cleanup ───────────────> QUARANTINED
     │                                   │
     └── failed/incomplete import        └── tamper/metadata mismatch
          └── cleanup → delete row              └── operator removal

INSTALLED ── user remove ──> REMOVAL_PENDING ── all exact bytes deleted ──> row deleted
                                      │
                                      └── deletion failure: retain row and retry on recovery
```

Registry status transitions are compare-and-set updates. Unknown persisted lifecycle strings are normalized to `QUARANTINED` before inventory is exposed. Recovery:

- promotes an `INSTALLING` row only if the deterministic final artifact has the exact catalog size and SHA-256;
- otherwise removes only the exact staging/final paths owned by that interrupted install, or retains quarantine if cleanup cannot finish;
- re-hashes every `INSTALLED` artifact and quarantines missing/tampered bytes;
- retries `REMOVAL_PENDING`, deleting the Room row only after owned files are gone;
- does not automatically delete unregistered `.partial` files; deterministic filenames alone are not proof of ownership. A matching per-attempt marker is required to remove interrupted staging bytes.

Normal Timeline startup does not run model recovery or re-hash multi-gigabyte model files. The recovery scan is lazy and happens when the user opens or refreshes Models. A future runtime must independently verify/open the artifact at load time to address time-of-check/time-of-use risks.

## Resource preflight

`AndroidDeviceResourceProbe` collects:

- total physical RAM and Android's low-memory flag;
- current process PSS;
- `memoryClass`;
- available bytes on the private no-backup filesystem;
- Android thermal status (where available).

The result is a resource estimate, not a load test. Possible results are `BLOCKED_LOW_MEMORY`, `BLOCKED_DEVICE_RAM`, `BLOCKED_STORAGE`, `BLOCKED_THERMAL`, or `RUNTIME_PROBE_REQUIRED`. The last result explicitly does **not** mean ready/loaded. Storage is checked before import; RAM/thermal preflight is relevant to a future engine load. Real memory use, load success, latency, thermal stability and unload behavior must be measured on the Galaxy S24 FE in Phase 5B.

## UI and graceful degradation

The four-item bottom navigation includes Timeline, Canvas, Knowledge and Models. The Models screen is Arabic/English. It distinguishes `Not installed`, `File verified · runtime not active`, `Quarantined`, and `Removal pending`. If encrypted inventory cannot be read, it displays an unknown/error state and suppresses import/remove controls. Import and removal operations refresh the inventory; deletion failures remain visible/retryable. Preflight feedback is localized.

Timeline and existing FTS5 lexical retrieval do not depend on an installed model. There is no dummy generator, dummy embedding adapter or fabricated vector. No dense index is activated by Phase 5A.

## Deferred to Phase 5B

- select and pin a stable Android LiteRT-LM runtime and native libraries;
- real model open/load/unload lifecycle and resource caps;
- Gemma generation adapter and explicit manual tool-call display/approval;
- EmbeddingGemma runtime adapter, vector dimension/task-prefix verification and golden tests;
- connection to the existing encrypted dense-index builder only after vector contract is verified;
- memory/thermal/latency and repeated-load benchmarks on the Galaxy S24 FE;
- offline OCR and speech engine evaluation.

Autonomous model tool execution remains disabled; all state changes stay behind the existing authorization gateway and user confirmation rules.

## Validation

Local checks performed for this source tranche:

- `scripts/test-model-manager-contract.py`;
- `scripts/test-string-translations.py` and `scripts/verify-string-translations.py`;
- `scripts/verify-app-module-compile-contract.py`;
- `scripts/verify-compose-compile-sdk.py` (17 Android modules, API 37.0 compile; target/emulator API 36);
- existing Phase 4 schema, dense-index, corpus, persistence-test, AGP/Kotlin, dependency-lock, API 36 setup and secret-scan scripts;
- standalone Kotlin 1.9 compiler harness against minimal Android/Room API shims; all 28 `ModelManagerTest` methods passed.

This authoring environment has no Android SDK, Gradle 9.5 distribution or repository resolution. Therefore the real Android Gradle build, KSP/Room generation, official `:core:model-manager:testDebugUnitTest`, app/feature lint, Compose compilation, connected Android instrumentation and SAF file-provider behavior require GitHub Actions/device verification. Phase 4 CI is green according to the user's 0.5.16 run; Phase 5A is not yet hosted-CI validated.
