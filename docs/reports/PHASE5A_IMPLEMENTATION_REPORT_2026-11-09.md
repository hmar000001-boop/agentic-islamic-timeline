# Phase 5A implementation report — 2026-11-09

## Executive summary

Phase 5A is implemented in source version `0.6.0-phase5a-model-manager-saf-import` (versionCode 21), incrementally applied to the CI-green Phase 4 baseline `0.5.16-phase4c-revoked-grant-reconciliation-test-fix`.

This is a **model artifact management and verified installation foundation**, not an inference release. It provides a closed catalog, user-driven SAF import, exact byte/hash checks, app-private storage, Room lifecycle metadata, recovery/quarantine, resource preflight and an Arabic/English Models screen. No LiteRT-LM runtime, Gemma generator, EmbeddingGemma adapter, OCR/ASR engine or model weights are bundled.

The source tree was recovered from the exact 0.5.16 complete project archive before applying Phase 5A; Phase 0–4 code and CI history are preserved. The earlier local Phase 5A notes were used as requirements/specification, not treated as an already verified source archive.

## Implemented source changes

### New modules

- `:core:model-manager`: immutable catalog, lifecycle/status model, model registry adapter, streaming importer orchestration, durable per-attempt file-ownership markers, non-destructive recovery, tamper revalidation and resource preflight.
- `:feature:model-management`: localized Models UI and model lifecycle status presentation.

Both use the existing public typed AGP DSL, compile SDK API 37.0, min SDK 26 and Java 17.

### Catalog

Three catalog entries are pinned by full revision, artifact name, exact bytes and SHA-256:

| ID | Bytes | RAM floor | License |
|---|---:|---:|---|
| Gemma 4 E2B IT | 2,588,147,712 | 8 GiB (publisher metadata) | Apache-2.0 |
| Gemma 4 E4B IT | 3,659,530,240 | 12 GiB (publisher metadata) | Apache-2.0 |
| EmbeddingGemma 2 Text 270M | 164,626,432 | Not asserted | Apache-2.0 |

Full pins are in `core/model-manager/src/main/java/app/timeline/islamic/core/models/ModelCatalog.kt`. The importer does not fetch from these repositories; users select an exact file themselves.

### Import/storage

- SAF is limited to `content://`, with lazy `ContentResolver.openInputStream()` and no persisted URI permission.
- `64 KiB` bounded streaming; max-size rejection during read; exact final byte count; incremental SHA-256; coroutine cancellation checks per chunk and during full-file rehash.
- Exclusive `.partial` creation and final-path reservation, file descriptor sync, atomic same-directory `Os.rename`, and parent-directory sync. The final path is created with exclusive-create before rename so POSIX rename cannot overwrite an unrelated pre-existing artifact.
- Paths are derived only from catalog ID/revision/filename below `noBackupFilesDir/on-device-models`.
- Failed rollback deletes only attempt-owned staging/committed paths. A durable sidecar owner marker binds an interrupted staging artifact to the deterministic Room record ID. Recovery removes a `.partial` file only if that marker matches and no final artifact exists. Invalid/pre-existing final files, mismatched markers and unregistered staging files are preserved and quarantined; there is no broad filename-pattern cleanup. Destination collision tests prove rollback does not delete a pre-existing file that appears immediately before commit. If rename succeeds but parent-directory fsync fails, the `INSTALLING` row and bytes are retained for subsequent exact size/hash recovery rather than guessed away.
- If cleanup fails, a quarantined/removal-pending record is retained; the Room row is deleted only after exact model-owned bytes are gone.

### Persistence/recovery

- Reuses the existing SQLCipher Room `installed_model` table and DAO, with no schema migration (Room remains v5).
- Compare-and-set transitions cover `INSTALLING`, `INSTALLED`, `QUARANTINED`, and `REMOVAL_PENDING`.
- Unknown stored status strings are normalized to `QUARANTINED` before inventory is returned.
- Recovery promotes only a final artifact that passes exact hash/size verification, quarantines installed tampering and retries pending removal.
- Full verification is lazy when Models is opened/refreshed; Timeline startup does not hash multi-gigabyte models.

### Resource checks/UI

- Probe reads total/available RAM, low-memory flag, process PSS, memory class, private free storage and thermal status.
- Storage is checked before import. RAM/thermal are estimates for a future runtime load, not a compatibility guarantee.
- Positive preflight result is named `RUNTIME_PROBE_REQUIRED`; `runtimeReady` is always false.
- Models navigation and UI are wired to `MainActivity` and localized Arabic/English. An unreadable encrypted inventory is rendered as unavailable and hides import controls.
- Imported artifacts are explicitly labelled verified but not active. No generator/embedding code is registered.

## Validation actually run in the authoring environment

- ModelManager Kotlin source and test compile via Kotlin compiler 1.9.0 against minimal Android/Room API shims.
- **28/28** `ModelManagerTest` methods executed in a standalone JUnit-compatible harness, including destination-race preservation, post-rename durability recovery, cancellation rollback, owner-marker mismatch and preservation of ambiguous final/partial files. The three `AndroidModelFileStoreTest` methods were also compiled/executed with local Android API shims (3/3); this is not a substitute for the real API 36 instrumentation run.
- `scripts/test-model-manager-contract.py` passed, including a structural check that its GitHub Actions step is a separate `run:` step.
- Translation tests: 6/6; English/Arabic resource parity verified across five modules.
- API 36 setup regressions: 10/10.
- AGP/Kotlin regression tests: 11/11; built-in Kotlin/typed DSL verifies 17 Android modules.
- Android SDK metadata fixture tests: 10/10.
- App module compile/version contract: 10 checks passed.
- Compose/API compile SDK guard: 17 modules use API 37.0; targetSdk and emulator remain API 36.
- Persistence instrumentation-source contract: 14 checks passed.
- Room schema/migration v5, dense-index contract (8 checks), Phase 4C foundation, dependency-lock policy and secret scan passed.
- 27 XML resources, TOML catalog and GitHub Actions YAML parsed; emulator shell script passed `bash -n`.

## Not run / next verification

The authoring container has no `ANDROID_HOME`/`ANDROID_SDK_ROOT`, installed Gradle 9.5 distribution or repository resolution. Consequently the following are **not claimed as passing** here:

- Android Gradle configuration/build, KSP/Room code generation or real Gradle unit tests;
- app and new-feature Compose/Kotlin compilation and Android lint;
- GitHub Actions Phase 5A unit-test, assemble/lint and connected instrumentation targets, including three new `AndroidModelFileStoreTest` tests that exercise real Android `Os.rename` and directory fsync;
- real SAF document-provider access on Android;
- actual LiteRT-LM initialization, inference, vector outputs, memory/thermal behavior or Galaxy S24 FE compatibility.

The user's statement that CI is green closes Phase 4's previous validation gap. Phase 5A itself requires its first hosted Actions run after this source push.

## Explicitly deferred

Phase 5B must select and pin stable LiteRT-LM Android runtime/native dependencies, add actual generator and embedding adapters, verify EmbeddingGemma vector/task contracts, connect to the existing encrypted dense index only after golden tests, keep tool calls manual through `ToolExecutionGateway`, and benchmark on the Galaxy S24 FE. OCR and audio are also deferred. No production readiness claim is made.
