# Timeline Agentique checkpoint — Phase 5A Model Manager

**Date:** 2026-11-09
**Source version:** `0.6.0-phase5a-model-manager-saf-import`
**Version code:** 21
**Previous baseline:** `0.5.16-phase4c-revoked-grant-reconciliation-test-fix`
**Current phase:** Phase 5A implemented; hosted Android validation pending

## Continuity

The complete CI-green 0.5.16 project was used as the base. All previous Phase 0–4 modules, Room v5 schema/migrations, encryption, authorization gateway, corpus trust, retrieval, Timeline and Canvas source were retained. No replacement/restart of the product occurred. The local Phase 5A design notes were treated as specification only; the Phase 5A source was applied and reconstructed against the exact 0.5.16 archive to keep the delivery complete and reproducible.

## Delivered

- `core/model-manager` module with closed digest-pinned Gemma 4 E2B/E4B and EmbeddingGemma 2 model catalog.
- SAF user-selected `content://` import with 64 KiB stream buffer, exact byte count and SHA-256, exclusive staging and final-path reservation, fsync and atomic same-filesystem rename. A durable sidecar owner marker binds staging to the Room record. Recovery preserves/quarantines invalid final artifacts, marker mismatches and unregistered `.partial` files instead of deleting by filename pattern; post-rename sync uncertainty is recovered from `INSTALLING` after hash verification; long stream/rehash loops observe cancellation.
- App-private `noBackupFilesDir/on-device-models` layout, catalog-derived paths, symlink/canonical path checks, exact owned-file deletion and interrupted-operation recovery.
- Existing SQLCipher Room `installed_model` table reused; no schema migration (v5 remains).
- Model lifecycle `INSTALLING`, `INSTALLED`, `QUARANTINED`, `REMOVAL_PENDING`, CAS transitions, tamper hash recheck, unknown-status quarantine, retryable removal.
- RAM/low-memory/PSS/memory-class/storage/thermal resource probe; no result ever claims runtime readiness.
- `feature/model-management` with Arabic/English Models screen, user document picker and status/preflight feedback.
- App Models navigation, lazy model verification/recovery (not on normal Timeline startup), storage check before import.
- Dedicated CI model-manager contract step, model-manager unit-test task and lint tasks.

## Critical architecture boundaries

- No network permission/client/downloader; no model downloads or remote inference.
- Model bytes are private no-backup sandbox files, but are not individually SQLCipher-encrypted in this tranche; metadata is stored in the encrypted Room database.
- No LiteRT-LM runtime is included or initialized. An imported model is labelled **verified file, runtime not active**.
- No concrete Gemma generator, EmbeddingGemma adapter, dense-index activation, OCR or audio engine.
- Model output never authorizes actions; tool execution remains behind Phase 3's manual approval and gateway.

## Local verification

- Standalone Kotlin compile of core model manager and its tests against Android/Room API shims: passed.
- `ModelManagerTest`: **28/28** method cases passed in standalone JUnit-compatible runner. Added three API 36 instrumentation tests for actual Android atomic rename, final-path collision preservation and directory fsync; their source methods also ran 3/3 with local API shims, but the real Android instrumentation awaits hosted CI.
- Phase 5A source contract: passed.
- Existing regressions: API 36 setup 10/10; translation 6/6; AGP/Kotlin 11/11; SDK metadata 10/10; persistence test source 14/14; Room dense index 8/8; app compile/version 10 checks; Compose/API SDK guard 17 modules; Phase 4 schema/Phase4C/dependency-lock/secret scan passed.
- XML/TOML/YAML parsing, shell syntax passed.

## Required next action

Push 0.6.0 and run GitHub Actions. Specifically validate `:core:model-manager:testDebugUnitTest`, `:core:model-manager:connectedDebugAndroidTest`, all app/module compile and lint tasks, offline merged-manifest audit, and the existing API 36 connected instrumentation targets. Real Android SAF behavior and Galaxy S24 FE measurements remain device gates.

## Phase 5B next tranche

1. Confirm the maintained LiteRT-LM Android runtime and pin exact runtime/native artifact versions and licenses.
2. Add a load/unload adapter that verifies the artifact from the same opened file descriptor handed to the runtime; enforce process memory and thermal budgets.
3. Validate Gemma E2B/E4B model load and generation on the Galaxy S24 FE; do not infer compatibility from the 8/12 GiB catalog floors.
4. Add EmbeddingGemma 2 runtime adapter; record actual output dimensions, normalization and task prefixes with golden tests.
5. Connect embeddings to the existing encrypted Room dense index only after golden tests and size/cleanup budgets pass; preserve FTS5 fallback.
6. Keep model tool calls manual, with the authorization gateway as the only side-effect route.
7. Evaluate offline OCR/audio separately after licensing, model/runtime and resource profiles are reviewed.

**Do not mark Phase 5 complete or production-ready at this checkpoint.**
