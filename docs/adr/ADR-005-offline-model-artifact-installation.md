# ADR-005 — Offline model artifact installation and lifecycle

**Status:** Accepted for Phase 5A
**Date:** 2026-11-09

## Context

Phase 4 has an encrypted vector-storage foundation but no real local embedding runtime. Phase 5 must allow users to supply approved on-device model artifacts without introducing mandatory network access, cloud inference, an account, or an uncontrolled model registry.

## Decision

1. Ship a closed in-app catalog. Every item fixes an immutable repository revision, exact artifact filename, exact byte count, SHA-256 and declared license.
2. Accept model bytes only through user-initiated Storage Access Framework `content://` selection. Do not download by URL and do not persist URI grants.
3. Stream to an exclusively created staging file under `noBackupFilesDir/on-device-models`, enforce exact size, hash incrementally, sync, atomically rename on the same filesystem, then record `INSTALLED` with a Room compare-and-set.
4. Reuse Room v5 `installed_model`; no migration is required for this tranche. Lifecycle metadata remains in SQLCipher. Weight files remain app-private, no-backup files, not individually SQLCipher encrypted.
5. Recover interrupted installs/removals and quarantine tampered, unknown or mismatched inventory. Delete registry metadata only after the model-owned files have been deleted.
6. Treat RAM/storage/thermal checks as advisory gates only. Never infer LiteRT-LM compatibility or activation from metadata.
7. Keep generation/embedding engines disabled until a separately reviewed runtime integration and physical-device validation pass.

## Consequences

- Timeline and FTS5 retrieval work with no model installed.
- Import is offline, explicit and auditable, but users must obtain the exact pinned artifact separately.
- Catalog changes require an application update; revision rollback/update orchestration is deferred.
- The app does not yet perform inference, activate dense retrieval or support model-driven actions.
- Phase 5B must revalidate the artifact from the same opened file descriptor used by the runtime to reduce TOCTOU exposure, then measure actual memory, thermal and load behavior on the target device.

## Alternatives rejected

- In-app model download: rejected to preserve no-network/offline product architecture.
- Arbitrary user-supplied model IDs/paths: rejected because it bypasses the trusted catalog and risks path confusion.
- Treating RAM floors as compatibility: rejected; only a real runtime load test can establish device compatibility.
- Marking imported files active: rejected; hash verification authenticates file bytes but does not prove runtime support or successful inference.
