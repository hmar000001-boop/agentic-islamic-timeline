## 0.6.3-phase5c-dense-retrieval — 2026-11-09

- Completed the Phase 5C source implementation on the recovered 0.6.2 baseline; incremented app versionCode to 24.
- Added Room schema v6 and a non-destructive v5→v6 migration. The migration adds exact signed-manifest and embedding-profile fingerprints, and deletes only legacy derived dense rows that cannot prove compatibility. Timeline, task, approval, audit, publisher trust, corpus registry and all unrelated tables are preserved.
- Made embedding identity canonical over model ID/revision/artifact SHA-256, dimension, max input, document/query task prefixes and normalization/quantization policy. Query and document embeddings use the runtime's separate asymmetric instructions.
- Implemented the concrete SQLCipher Room dense retriever: 2,048-row stable-key pages, exhaustive exact cosine scoring, bounded top-K, generation count integrity check, cancellation checks and deterministic tie ordering.
- Added metadata-only eligibility checks during the vector scan; full original passage text is loaded only for final top-K hits. The signed installed corpus pack remains the only evidence source; language, madhhab, license, scholarly-review, expiry, stable key and content/normalization checks remain authoritative.
- Wired dense retrieval into `TimelineApplication.retrieveCorpus()` and the corpus-management screen. FTS5 lexical results remain active and fuse through existing RRF; missing model/index or dense runtime/integrity/preflight failures are explicitly traced while lexical retrieval remains available. RRF is not presented as probability/confidence.
- Added per-pack manual dense-index build/rebuild and search UI, with source/edition/locator/language and contributing-channel provenance. UI search requires a verified publisher keyring. Dense work respects memory/thermal preflight and does not silently unload an active generation model.
- Added focused cosine/pagination/fingerprint/filter/count/hybrid/top-K hydration tests, SQLCipher v5→v6 migration instrumentation and bundled-SQLite evidence-lookup/index-source instrumentation. The local Kotlin/JVM harness compiled the production retrieval and index-builder sources and passed 18/18 tests. A separate API-shaped LiteRT-LM adapter harness compiled the production EmbeddingGemma adapter and passed 11/11 policy tests. Static Phase 5C contracts pass 33/33; Room schema/migration guard and persistence contracts pass 12/12.
- Index-source hardening: only currently licensed, unexpired, scholarly-approved, provenance-shaped passages within the model input budget enter embedding. Before embedding, the source verifies the stable evidence key, original-text SHA-256 and normalized text against the signed SQLite rows. Invalid or changed source data aborts the staging generation; it cannot be silently embedded.
- EmbeddingGemma's 8,192-token native limit is distinct from the raw character budget. The latter reserves the 28-character document prefix, resulting in an 8,164-character passage/query payload cap; the adapter tests that neither prefixed string exceeds the 8,192-character input envelope.
- **Not yet certified:** actual Gradle/KSP/Compose compilation, hosted CI unit/lint, API 36 SQLCipher/bundled-SQLite instrumentation and Galaxy S24 FE native EmbeddingGemma loading/latency/RAM/thermal benchmarks. These remain required before marking Phase 5C accepted or production-ready.

## 0.6.2 CI hotfix — Android public directory-sync API (2026-11-09)

- Fixed the hosted Kotlin compilation error in `AndroidModelFileStore.kt`: `android.system.OsConstants.O_DIRECTORY` is not part of the public Android SDK API and therefore cannot be referenced by an ordinary application module.
- Kept directory-entry durability. `syncDirectory` now uses the public `Os.open(path, OsConstants.O_RDONLY, 0)`, verifies the path with `Os.lstat`, verifies the opened descriptor with `Os.fstat`, checks `S_ISDIR`, and compares `st_dev`/`st_ino` against a second `lstat` before calling `Os.fsync`. The descriptor is always closed in `finally`.
- This also rejects a symlink or directory replacement detected between path inspection and descriptor verification; it does not silently remove directory fsync or substitute an unverified Java file operation.
- Added `scripts/test-android-model-directory-sync-contract.py` and a dedicated CI step so future changes cannot reintroduce hidden Android constants or remove the fsync/descriptor-identity checks.
- The existing `AndroidModelFileStoreTest.commitAtomicallyConsumesStagingAndRemovalSyncsDirectoryHierarchy` continues to exercise prepare, marker creation, atomic commit, and owned-file removal on the real Android filesystem during API 36 connected instrumentation.
- Local validation: the exact production Kotlin file compiles against a minimal public-API-shaped Android `Os` shim and a filesystem harness completed prepare/commit/removal with eight directory fsyncs and matching descriptor closes; the static contract passed. Actual Android Gradle/KSP compile and API 36 instrumentation must be rerun in GitHub Actions.
- App version metadata remains `0.6.2-phase5c-dense-retrieval` / versionCode 23 because this is a CI/build-compatibility correction to the same Phase 5C source baseline, not a new feature release.

## 0.6.2-phase5c-dense-retrieval — 2026-11-09 (recovered-source status correction)

- This version's original checkpoint notes claimed the dense path, Room v6 migration and app wiring were complete. A subsequent code-level audit of the recoverable 0.6.2 archive did **not** substantiate those claims: the recovered `AppDatabase` remained v5, no `MIGRATION_5_6` was registered, and no concrete dense retriever was connected to the application. The archive provenance also indicates a reconstruction rather than a certified copy of the user's exact Git checkout.
- Therefore, treat recovered 0.6.2 as the Phase 5B/runtime and Android directory-sync hotfix baseline, not as an accepted Phase 5C release. The actual source implementation of dense retrieval is delivered by 0.6.3 below; acceptance still requires hosted Android CI and device validation.

## 0.6.1-phase5b-litertlm-runtime — 2026-11-09

- Continued from 0.6.0 without replacing Phase 0–5A. Added `:core:inference-runtime` and pinned `com.google.ai.edge.litertlm:litertlm-android:0.19.1`; app Android bytecode remains Java 17 while CI selects JDK 21 for the runtime dependency family.
- Added a single-owner, CPU-only LiteRT-LM text-generation runtime. It verifies installed bytes through `ModelManager.resolveInstalledFile`, bounds the KV cache to 4,096 tokens, bounds prompts/output, uses one-shot conversations, handles cancellation, and closes native resources explicitly.
- Tool safety: no ToolProvider is registered, `automaticToolCalling` is false, and any model-emitted tool call fails closed. Model output cannot invoke application actions; the existing authorization gateway remains authoritative.
- Added an EmbeddingGemma 2 adapter for the existing `LocalTextEmbeddingModel` interface. It uses LiteRT-LM's documented asymmetric retrieval prefixes, batches no more than 24 texts, requests normalized 768-dimensional vectors, and rejects dimension, finite-value or L2-norm mismatches.
- Connected explicit CPU load, runtime smoke-test and unload controls to the Models screen. The app serializes generation and embedding runtimes to avoid co-resident multi-GB model weights. Embedding index building is exposed explicitly; lexical FTS5/RRF retrieval is not silently changed.
- Added JVM policy tests, API 36 LiteRT-LM configuration-shape instrumentation, a standalone Phase 5B contract script, unit/lint/instrumentation CI tasks, and the JDK 21 workflow selection. Expanded Android module count to 18.
- No app Internet permission, cloud download, GPU/NPU path, Room schema migration, production authorization change or SQLCipher behavior change. Model weights remain private/no-backup files, not individually SQLCipher-encrypted.
- App versionCode 22, versionName `0.6.1-phase5b-litertlm-runtime`. Actual hosted Gradle/KSP/Compose/native model load and Galaxy S24 FE performance validation remain release gates; Phase 5B is not yet CI-green or device-validated.

## 0.6.0-phase5a-model-manager-saf-import — 2026-11-09

- Started Phase 5 incrementally from the CI-green 0.5.16 baseline. Added `:core:model-manager` and `:feature:model-management` without replacing Phase 0–4 modules or altering the Room v5 schema.
- Added an immutable catalog for Gemma 4 E2B IT, Gemma 4 E4B IT and EmbeddingGemma 2 Text 270M. The entries pin LiteRT Community repository, 40-character revision, expected `.litertlm` filename, exact byte length, verified SHA-256 and Apache-2.0 license. The Gemma runtime minimum-RAM metadata is recorded from Google's published Android model allowlist; it is not a promise that a device can load the model.
- Added user-initiated SAF `content://` stream import, 64 KiB bounded streaming, maximum/final size enforcement, incremental SHA-256, `FileDescriptor.sync()`, staging and same-filesystem rename. Imports require the catalog's exact bytes; no network client, download endpoint, dynamic model selector or persisted URI permission was introduced.
- Stored model artifacts beneath `noBackupFilesDir/on-device-models`, using only catalog-derived paths. Added compare-and-set Room model inventory methods against the existing encrypted `installed_model` table; no schema migration was required.
- Added recovery for interrupted `INSTALLING` and `REMOVAL_PENDING` operations, post-install hash revalidation, quarantine for missing/tampered artifacts, and model-only removal. Corpus, dense-index and user data are outside this removal path.
- Added resource preflight for RAM, low-memory state, process PSS, memory class, free private storage and thermal state. The result can only be `RUNTIME_PROBE_REQUIRED` or a blocked assessment; it never implies a model was loaded. The UI labels imported models as verified files, not active inference runtimes.
- Added Models navigation and Arabic/English resource sets, plus unit/static CI guards. FTS5 and all Phase 4 behavior remain available when no model is installed.
- Explicitly deferred LiteRT-LM engine integration, Gemma generation, EmbeddingGemma adapter/vector-index activation, OCR/audio engines and Galaxy S24 FE runtime benchmarks to Phase 5B. 0.6.0 does not contain an inference runtime or claim device compatibility.
- Added 24 model-manager unit tests covering rollback ownership, destination races, commit durability recovery and import cancellation; also added two API 36 instrumentation checks of the real Android file store and a dedicated CI source-contract step; added the model-manager unit-test and both Phase 5 module lint tasks to CI. The Kotlin core and 24 test methods compile/run in a local standalone harness, but official Gradle/AGP/KSP/Compose validation awaits the hosted run.
- Bumped app versionCode to 21 and versionName to `0.6.0-phase5a-model-manager-saf-import`. No application Internet permission, Room schema, corpus trust policy, authorization policy or offline flavor behavior changed.

## 0.5.16-phase4c-revoked-grant-reconciliation-test-fix — 2026-11-09

- Corrected the Room instrumentation test `revokedScopedGrantIsRecheckedAtExecutorStart`. The previous test asserted that startup reconciliation returned `0`, then immediately asserted that a second call returned `1` while the same execution remained `CLAIMED`. This was internally contradictory: `reconcileExecutingExecutions()` intentionally reconciles all `CLAIMED` records because the external handler is only called after the atomic `CLAIMED` → `EXECUTING` transition.
- Preserved production authorization and recovery behavior. After a grant is revoked, `beginExecution()` re-reads the persisted grant in the same Room transaction that would transition the execution and outbox to `EXECUTING`; `HifzIntervalGrant.matches()` denies a row with `revokedAt != null`. The failed start leaves the claim `CLAIMED`, proving the handler was not allowed to start.
- Strengthened the instrumentation test to exercise `ToolExecutionGateway` with a counted fake handler and assert `ClaimRejected` plus zero handler calls. It also verifies no executor-start task/audit event, both execution and outbox remain `CLAIMED` before recovery, one recovery pass moves both to `ABORTED_BEFORE_EXECUTION`, interrupts the task and expires its lease, and a second recovery pass returns `0`.
- Extended `scripts/test-persistence-androidtest-contract.py` with regression guards for the gateway-level no-side-effect invariant and idempotent recovery assertions. No production authorization, Room, schema, migration, audit, outbox or task-state code changed.
- Bumped app versionCode to 20 and versionName to `0.5.16-phase4c-revoked-grant-reconciliation-test-fix`.
- Local source/static contracts and project checks are run for this tranche. The real Android instrumentation test must be rerun on GitHub Actions; no claim of a hosted pass is made.

## 0.5.15-phase4c-persistence-androidtest-compile-fix — 2026-11-09

- Fixed `:core:persistence:compileDebugAndroidTestKotlin`: added `:core:authorization` as an `androidTestImplementation` dependency because `AgentAuthorizationRoomTest` directly exercises `AuthorizationEngine`. The production `implementation`/`api` graph is unchanged.
- Fixed `RoomAuditLogTest.missingHistoricalKeystoreKeyFailsClosedAndDoesNotResetChain`: promoted the Android `Context` from a setup-local variable to a fixture property so the test can reuse the same application context when constructing its second `KeystoreAuditMacProvider`.
- Added a CI source-set contract regression test and run it before Gradle tasks.
- Bumped app versionCode to 19 and versionName to `0.5.15-phase4c-persistence-androidtest-compile-fix`. No Room schema, migration, production runtime behavior, encryption policy, or feature behavior changed.

## 0.5.14-phase4c-api36-avd-home-fix — 2026-11-08
- Fixed the API 36 startup failure in which the emulator launcher reported `Unknown AVD name [timeline-ci-api36]` and could not find its registration file under `$HOME/.android/avd`. The startup script previously relied on inherited/default Android configuration paths and did not verify that the AVD manager's output was discoverable by the emulator.
- Pinned `ANDROID_USER_HOME` and `ANDROID_EMULATOR_HOME` to `$HOME/.android`, `ANDROID_AVD_HOME` to `$HOME/.android/avd`, and the legacy `ANDROID_SDK_HOME` to `$HOME`; cleared the deprecated conflicting `ANDROID_PREFS_ROOT`. All values are exported before either `avdmanager` or the emulator is invoked, so modern and legacy tool lookup paths converge.
- Passed an explicit `--path` to `avdmanager create avd`. Added a fail-closed postcondition that requires both `timeline-ci-api36.ini` and `timeline-ci-api36.avd/config.ini` at the canonical AVD home before QEMU can launch. Failure diagnostics include the resolved home paths, directory contents, and `avdmanager list avd`.
- Extended API 36 regression coverage with deliberately conflicting inherited Android home variables. The mocked manager creates the AVD registration/configuration at the exported location; the emulator mock verifies it receives the same settings. Existing QEMU library checks, ADB isolation, process cleanup, registration timeout (180 seconds), and guest boot timeout (600 seconds) remain unchanged.
- Bumped application versionCode to 18 and versionName to `0.5.14-phase4c-api36-avd-home-fix`. No app runtime features, UI behavior, Room schema, encryption, corpus trust policy, or retrieval behavior changed.
- Local API 36 emulator setup regression suite passes 10/10; actual GitHub-hosted AVD creation, API 36 boot and connected instrumentation remain pending Actions verification.

## 0.5.13-phase4c-api36-headless-qemu-preflight-fix — 2026-11-08
- Corrected the API 36 emulator dependency preflight after the 0.5.12 log showed dozens of unresolved Qt/Android-emulator libraries. The previous check inspected `qemu-system-x86_64` (the GUI backend) even though CI starts the emulator with `-no-window`, which selects `qemu-system-x86_64-headless`.
- The preflight now validates the exact headless QEMU backend and supplies the SDK-bundled Linux search paths used by the Android Emulator launcher: `emulator/lib64`, `gles_swiftshader`, `vulkan`, and `libstdc++`. This lets `ldd` resolve Android Emulator-owned objects such as `libc++`, `libabseil_dll`, and `libandroid-emu-*` while still detecting genuinely missing Ubuntu host libraries.
- Added regression assertions for the headless binary selection, launcher-equivalent library search path, absence of GUI Qt paths from the headless preflight, missing host-library failure, and successful mocked ADB startup.
- Kept the existing `libpulse0` runner package check as a defensive host-runtime dependency; no PulseAudio service or app audio capability is introduced. API 36 image, Pixel 6 AVD, `-no-window`, SwiftShader, KVM behavior, 180-second registration deadline and 600-second guest boot deadline remain unchanged.
- Bumped app versionCode to 17 and versionName to `0.5.13-phase4c-api36-headless-qemu-preflight-fix`.
- Local API 36 regression suite passes 9/9. Static checks, ZIP integrity, SHA-256 and exact patch reproduction pass. Real hosted QEMU launch, Android boot and instrumentation remain pending GitHub Actions confirmation.

## 0.5.12-phase4c-api36-libpulse-fix — 2026-11-08
- Fixed the hosted API 36 emulator startup failure reported by QEMU: `qemu-system-x86_64` could not load `libpulse.so.0` and exited with code 127. The GitHub Actions job now installs Ubuntu's `libpulse0` runtime package (`--no-install-recommends`); this installs the PulseAudio client shared library only, not a PulseAudio server and not audio functionality in the application.
- Added a preflight to `scripts/start-api36-emulator.sh` that inspects the exact x86_64 QEMU executable with `ldd` before creating the AVD or launching the emulator. Missing shared objects are listed explicitly and the script exits early with the package installation guidance; it does not suppress loader errors or silently proceed.
- Extended `scripts/test-api36-emulator-setup.py` to check that CI installs `libpulse0`, that a missing QEMU shared library is detected before emulator launch, and that successful startup passes with mocked ADB/emulator/QEMU dependencies. Existing ADB isolation test coverage remains intact.
- Preserved API 36 Google APIs x86_64 system image, Pixel 6 AVD, KVM configuration, explicit ADB path, early emulator-exit detection, 180-second device registration limit and 600-second Android boot limit. No application runtime/audio feature, backend, data, Room schema, encryption or corpus policy was changed.
- Bumped app versionCode to 16 and versionName to `0.5.12-phase4c-api36-libpulse-fix`.
- Local API 36 startup regression suite passes 8/8 with an inherited `ADB` variable set. Static source, policy, Room, localization, dependency-lock, secret-scan and archive checks pass. Actual GitHub-hosted QEMU launch, API 36 boot and instrumentation remain to be verified by the next Actions run.

## 0.5.11-phase4c-api36-adb-test-isolation-fix — 2026-11-08
- Fixed the remaining GitHub Actions timeout in `test_startup_script_completes_with_mocked_sdk_tools`. The workflow exports a job-wide `ADB` path through `GITHUB_ENV`; the test copied the runner environment and changed `ANDROID_HOME` but did not override `ADB`. Because the production startup script correctly honors an explicitly supplied `ADB`, the test unknowingly invoked the runner's real ADB against a fake emulator that cannot register with that server. The real `adb get-state` therefore remained unsuccessful while the script correctly waited within its 180-second registration deadline; the test's 20-second subprocess timeout fired first.
- The successful-startup fixture now explicitly sets `ADB` to the temporary SDK's mock executable. The mock records every invocation, and the test asserts that version, server startup, device registration, boot-property, animation-setting and device-list commands all reached that mock. This makes the test deterministic under GitHub's job-level environment and prevents a silent fallback to host ADB.
- No production emulator startup code was changed. Early-exit detection, 180-second registration timeout, 600-second Android boot timeout, API 36 system image, Pixel 6 AVD, KVM setup and connected instrumentation tasks remain unchanged. No app runtime, UI, Room schema, encryption, corpus trust or retrieval behavior changed.
- Bumped app versionCode to 15 and versionName to `0.5.11-phase4c-api36-adb-test-isolation-fix`.
- API 36 setup regression suite passes 6/6 with a deliberately pre-set inherited `ADB` variable, reproducing the GitHub Actions environment condition. A hosted Actions run is still required to verify actual API 36 AVD boot and connected instrumentation.

## 0.5.10-phase4c-api36-emulator-test-fix — 2026-11-08
- Fixed the two failing API 36 emulator setup regression tests reported by GitHub Actions.
- The missing-ADB test now overrides the job-level `ADB` variable with the intentionally absent path under its temporary SDK. Previously the inherited valid runner path caused the script to proceed to the next missing prerequisite, making the test assert the wrong diagnostic.
- The successful-startup fake emulator now stays alive while mocked ADB reports device registration and boot completion. The test terminates the detached process in a `finally` cleanup path, including on subprocess timeout.
- Kept production emulator early-exit detection, 180-second device-registration timeout, 600-second boot timeout, API 36 image, Pixel 6 AVD, KVM setup, and instrumentation tasks unchanged. No app runtime, Room schema, encryption, corpus trust, or retrieval behavior changed.
- Bumped app versionCode to 14 and versionName to `0.5.10-phase4c-api36-emulator-test-fix`.
- API 36 setup regression suite passes 6/6 locally. A GitHub-hosted run remains required to confirm real AVD boot and connected instrumentation tests.

## 0.5.9-phase4c-api36-adb-path-fix — 2026-11-08
- Fixed the API 36 GitHub Actions startup failure (`adb: command not found`, exit 127). SDK Manager already installed `platform-tools`; the workflow had not guaranteed that `$ANDROID_HOME/platform-tools` was present on `PATH`.
- CI now verifies the executable at `$ANDROID_HOME/platform-tools/adb`, exports the directory using `GITHUB_PATH`, and exports the absolute executable using `GITHUB_ENV`.
- Moved AVD startup into `scripts/start-api36-emulator.sh`; the script uses explicit SDK paths for `adb` and `emulator`, checks required executables before creating the AVD, starts the adb server, and uses bounded waits for device registration (180 seconds) and Android boot completion (600 seconds).
- On emulator failure, CI prints `adb devices -l`, process diagnostics and the last 200 lines of `/tmp/timeline-emulator.log` to distinguish a tooling-path issue from an actual emulator/KVM/boot failure.
- Added regression tests that verify the workflow's SDK install/PATH contract, prohibit bare `adb` use in startup, and confirm missing `platform-tools/adb` fails early with an actionable message.
- Bumped app versionCode to 13 and versionName to `0.5.9-phase4c-api36-adb-path-fix`. No app runtime logic, Compose UI, Room schema, encryption policy, corpus trust, retrieval behavior, compileSdk 37.0, targetSdk 36, or emulator API level changed.
- Static regression tests and shell syntax checks can be run locally; a hosted GitHub Actions run remains necessary to verify actual API 36 emulator boot and connected Android instrumentation tests.

## 0.5.8-phase4c-english-resource-lint-fix — 2026-11-08
- Fixed the three blocking `MissingTranslation` lint errors reported by the hosted `:app:lintOfflineSideloadDebug` task. The app's explicit `values-en/strings.xml` was missing `app_nav_timeline`, `app_nav_canvas`, and `app_nav_corpus`; added the existing English labels `Timeline`, `Canvas`, and `Knowledge`.
- Added a complete explicit `values-en/strings.xml` for `feature:corpus-management`, matching its existing default English resource set and Arabic translation rather than relying on fallback for this new feature.
- Added `scripts/verify-string-translations.py` and regression tests. CI now verifies that every translatable Android string resource has matching English and Arabic keys and matching formatted placeholders. It rejects missing/extra resources, missing locale files, and format-argument mismatches; it does not suppress Android lint.
- Kept English as the default resource language, Arabic as the second launch language, compileSdk 37.0, targetSdk 36, minSdk 26, API 36 emulator, Room schema v5, encryption, corpus trust, retrieval and application behavior unchanged.
- App versionCode is 12 and versionName is `0.5.8-phase4c-english-resource-lint-fix`. The next hosted CI run must confirm app lint now passes and reveal any other lint findings; this authoring workspace cannot run Android Gradle lint.

## 0.5.7-phase4c-app-compile-fix — 2026-11-08
- Fixed the next real GitHub Actions app Kotlin compilation errors after `:core:persistence:compileDebugKotlin` and `:core:persistence:testDebugUnitTest` succeeded.
- Removed the explicit `androidx.compose.foundation.layout.weight` import from `MainActivity`; `Modifier.weight` is a `ColumnScope` member extension and is resolved inside the `Column` content receiver. Importing it as a top-level extension caused Kotlin to bind to an inaccessible internal `RowColumnParentData?.weight` symbol.
- Added the missing `core.agent.TaskLease` and `core.agent.ExecutionClaimResult` imports to `TimelineApplication`, matching their actual declarations in `core:agent-api`.
- Changed `core:persistence` to expose `androidx.room:room-runtime` via `api(...)` rather than `implementation(...)`, because its public `AppDatabase` class extends `androidx.room.RoomDatabase` and the consuming app must see that public supertype on its compile classpath. Room KTX remains an implementation dependency.
- Added `scripts/verify-app-module-compile-contract.py` and wired it into CI before Gradle compilation. It checks the Compose receiver import, exact agent API imports/contracts, Room runtime API exposure, and version bump.
- No business behavior, database schema, encryption configuration, runtime target, or Phase 4 corpus trust policy changed. App versionCode is 11 and versionName is `0.5.7-phase4c-app-compile-fix`.
- Local static contract checks are available; a fresh GitHub Actions run is still required to verify the real app compile and downstream build tasks.

## 0.5.6-phase4c-room-dense-index-compile-fix — 2026-11-08
- Fixed the real GitHub Actions Kotlin compilation errors in `RoomDenseEmbeddingIndexStore`: `activateGeneration` now explicitly implements the interface's `Unit` return contract instead of leaking the integer row-count returned by the final DAO deletion call.
- Added the missing `STATE_ACTIVE = "ACTIVE"` constant used by `vectorsFor`; the value matches the `DenseIndexDao` activation and active-generation query predicates.
- Kept generation activation inside one `RoomDatabase.withTransaction` transaction. Retire-old → activate-staging compare-and-set → delete-retired remains atomic; no partial active generation can become visible.
- Added `scripts/verify-room-dense-index-contract.py` and a CI step covering the API return type, adapter override, staging/active state names, DAO predicates, and transactional activation.
- Existing `RoomDenseEmbeddingIndexStoreTest` exercises initial activation, replacement activation, active-vector visibility, retired-vector cleanup and abandoned-staging recovery.
- A local Kotlin compile harness compiled the actual production `RoomDenseEmbeddingIndexStore.kt` against minimal Room/domain/DAO API shims. The official GitHub Actions Kotlin compile and Android Room/SQLCipher instrumentation must still be rerun; no full Android build is claimed from the local harness.
- Bumped app versionCode to 10 and versionName to `0.5.6-phase4c-room-dense-index-compile-fix`. No database schema change or application feature behavior outside the dense-index adapter was introduced.

## 0.5.5-phase4c-embedding-index-test-fixture-fix — 2026-11-08
- Fixed the three real GitHub Actions failures in `:core:retrieval:testDebugUnitTest` reported after API 37.0 installation and AGP/Kotlin configuration succeeded.
- Root cause: the new `LocalEmbeddingIndexBuilder` correctly rejects missing or zero-byte SQLite index files before opening a staging generation, but three unit tests supplied `File.createTempFile(...)` files without writing any bytes. The completed-build test therefore failed before the builder ran; failure/oversized-batch tests saw `abortCount == 0` because staging had never begun.
- Updated the tests to use an explicit non-empty fixture placeholder consumed only by the in-memory `FixtureSource`. The production `EmbeddingPassageSource` remains responsible for opening/validating the real installed SQLite database.
- Strengthened failure tests to assert the actual exception and expected message for wrong embedding dimensions and oversized source batches, plus verify begin/abort/activation ordering.
- Added `emptyIndexFileIsRejectedBeforeCreatingAStagingGeneration` to preserve the production precondition and prove an empty database is rejected before store mutation.
- Added an actionable error message for missing/empty corpus index files. No relaxation of production validation, embedding behavior, SQLCipher storage, model policy, or retrieval was made.
- CI supplied evidence that all app/core/feature production Kotlin compilation passed through the earlier tasks and KSP began; only the three `LocalEmbeddingIndexBuilderTest` cases failed. This correction specifically addresses those failures. Rerun the full hosted workflow to verify remaining tasks.

## 0.5.4-phase4c-sdk-metadata-validation-fix — 2026-11-08
- Fixed the CI pre-Gradle Android SDK validator to match the real Platform 37.0 `source.properties` format: `AndroidVersion.ApiLevel=37.0` with `AndroidVersion.ExtensionLevel=22`, rather than assuming an integer API level plus a separate minor-level key.
- Normalizes dotted and split API representations to a strict `(major, minor)` tuple; accepts only API 37.0, rejects API 37.1, wrong major levels, malformed values, conflicting dotted/separate minor values, missing/empty `android.jar`, missing package metadata, and SDK platform symlink escapes.
- Updated SDK validator fixtures to reproduce the exact platform metadata observed in GitHub Actions and expanded coverage to 8 tests.
- No SDK package request, compile SDK, target SDK, emulator, dependency, or Android application behavior changed. CI continues to install `platforms;android-37.0`, compile against API 37.0, and run API 36 runtime tests.
- The actual `sdkmanager` installation succeeded in GitHub Actions; only the project validator failed. This correction addresses that validator mismatch. A new hosted CI run is required to verify subsequent Gradle/KSP and unit-test stages.

## 0.5.3-phase4c-android-sdk-37.0 — 2026-11-08
- Fixed the GitHub Actions SDK Manager failure caused by requesting the nonexistent package path `platforms;android-37`; the published API 37 platform package is `platforms;android-37.0`.
- Migrated all 15 Android modules from the integer-only compileSdk setter to AGP's typed minor-API DSL: `compileSdk { version = release(37) { minorApiLevel = 0 } }`. This encodes the exact platform identity and avoids an ad-hoc SDK directory symlink.
- Added a pre-Gradle SDK verifier that checks `platforms/android-37.0/android.jar`, `source.properties`, API level 37, minor API level 0, and that the resolved platform stays inside the SDK platforms directory.
- Added six SDK-layout regression tests for correct installation, missing package, old package-name-only layout, wrong API/minor level, missing android.jar and symlink escape.
- Kept `targetSdk = 36`, `minSdk = 26`, API 36 emulator and Build Tools 36.0.0 unchanged. No app runtime behavior or database schema changed.
- Local Python guards, Room schema checks, Phase 4C checks, AGP built-in Kotlin checks, bootstrap regression and secret scan passed. Real Gradle/KSP/Android device CI must still be rerun on GitHub Actions.

# Changelog

## 0.5.1-phase4c-buildconfig-nullability-fix — 2026-11-08
- Fixed the Kotlin DSL compilation failure reported by GitHub Actions at `app/build.gradle.kts`: AGP's public `Variant.buildConfigFields` is nullable. The app now uses `checkNotNull(variant.buildConfigFields)` with an actionable configuration error before adding publisher-root BuildConfig values.
- Kept BuildConfig generation explicitly enabled and preserved both publisher-root values; no permissive fallback or silent omission was added.
- Added regression assertions to `scripts/test-built-in-kotlin.py` and `scripts/verify-built-in-kotlin.py` for the nullable Variant API guard.
- Bumped app `versionCode` to 6 and `versionName` to `0.5.1-phase4c-buildconfig-nullability-fix`.
- Local static checks pass. The actual Gradle/KGP/KSP/Room build and Android tests remain unexecuted in this authoring environment; rerun GitHub Actions to reveal the next build-stage result.

## 0.5.0-phase4c-agp9-built-in-kotlin — 2026-11-08
- Continued incrementally from the Phase 4B/0.4.3 source; preserved Timeline, Canvas, Phase 3 agent/authorization runtime, signed corpus packs, publisher trust and retrieval.
- Completed the AGP 9 built-in Kotlin migration: removed the external `org.jetbrains.kotlin.android` plugin and both temporary AGP opt-out flags; all Android modules use typed public `ApplicationExtension`/`LibraryExtension` DSL; Compose compiler uses the dedicated `org.jetbrains.kotlin.plugin.compose` plugin.
- Migrated BuildConfig publisher-root values to `ApplicationAndroidComponentsExtension.onVariants` and `variant.buildConfigFields`, removed project use of deprecated `buildConfigField()` and legacy Android variant APIs, and kept Kotlin 2.4.21 / KSP 2.3.13 / AGP 9.3.1 / Gradle 9.5.0 explicitly pinned.
- Fixed CI's dependency-resolution conflict by no longer applying `failOnDynamicVersions()` manually and explicitly deactivating dependency locking on AGP `*RuntimeClasspath` and CycloneDX BOM configurations. Root KGP/KSP buildscript classpath and all other resolvable project configurations remain locked; catalog/direct dynamic selectors are statically rejected. Explicit `--no-configuration-cache` is added to CI Gradle commands until the AGP navigation/dependency-report task serialization issue is independently resolved.
- Phase 4C adds the app-wired corpus-management feature, SAF keyring/pack import and update, lifecycle/revalidation UI, encrypted Room v5 dense-index generations/vectors and streaming bounded local embedding-index builder.
- Dense vector bytes remain inside SQLCipher Room; no sidecar plaintext vector database or source passage text is written. The builder caps a single generation at 128 MiB and the store caps total active/staging vector payload at 256 MiB. Inactive/quarantined/superseded pack generations are cleaned after trust/lifecycle changes.
- Dense retrieval remains disabled until a real, reviewed compatible on-device embedding model is provisioned. No model weights, fake model adapter, placeholder vectors, remote embeddings or rights-cleared scholarly corpus are included. FTS5 remains the functioning lexical path.
- Added AGP built-in Kotlin/public DSL, dependency-lock-policy and Phase 4C UI/encryption guards, plus Room SQLCipher dense-generation instrumentation source coverage. Actual Android Gradle/KSP, SQLCipher instrumentation, emulator and physical-device tests are still NOT RUN in the authoring environment.

## 0.4.3-phase4b-agp-kotlin-dsl-compatibility-fix — 2026-11-08
- Fixed the GitHub Actions Gradle configuration failure: AGP 9.3.1 enables the new DSL by default, which is incompatible with the project's applied `org.jetbrains.kotlin.android` plugin while the build also opts out of built-in Kotlin.
- Kept the existing external Kotlin Gradle Plugin 2.4.21 / Compose compiler / KSP toolchain and set both required AGP 9 compatibility properties: `android.builtInKotlin=false` and `android.newDsl=false`. This is a temporary AGP 9 profile; AGP 10 removes these opt-outs and requires a coordinated built-in Kotlin and KSP migration.
- Added `scripts/verify-agp-kotlin-mode.py` and wired it into CI before Gradle configuration. It verifies both properties, the pinned AGP major, the version-catalog Kotlin Android plugin, and consistent plugin application across Android modules.
- No application/runtime/Room/corpus/retrieval/authorization code changed. Static configuration tests pass locally; a real Gradle build still requires the GitHub runner because the authoring container has no installed Gradle or Android SDK.

## 0.4.0-phase4-knowledge-foundation — 2026-11-08
- Continued incrementally from the immutable Phase 3.0.1 source; retained Timeline, Canvas, authorization, agent runtime and Room schema v3.
- Added `:core:knowledge-api` source/evidence/retrieval contracts, explicit four-madhhab filtering with no implicit default, Arabic/English search normalization, source eligibility gates, exact original-text hashing, and deterministic edition/locator/language evidence keys.
- Added `:core:corpus` canonical signed-manifest codec, Ed25519 verification adapter using Bouncy Castle lightweight API, publisher keyring contract, bounded ZIP importer, signed-index SHA-256 validation, fail-closed key trust, atomic app-private installation, and crash-orphan cleanup.
- Added `:core:retrieval` bundled SQLite FTS5/BM25 index validation and search, independent per-pack lexical channels, optional local dense-retriever contract, rank-only RRF fusion, provenance-aware deduplication, and explicit retrieval diagnostics.
- Added `RoomCorpusPackRegistry` on the existing `installed_corpus_pack` table; no Room schema migration (schema remains v3 / 24 entities).
- Added canonical manifest, Ed25519 RFC 8032 vector, source-eligibility, stable-evidence-identity, RRF and local retrieval fallback tests; 20 focused JVM test methods passed under the local Kotlin/JUnit/coroutine shim harness.
- Added six executable corpus-installer filesystem tests under a lightweight Context shim, covering signature/key rejection, path rejection, registry rollback and metadata retention; Android instrumentation source now contains 12 test methods across corpus/retrieval.
- Hardened pack validation to recompute every original passage SHA-256, normalized text and stable source/edition/locator/language evidence key before installation; validates external-content FTS5 postings and orphan source rows.
- Hardened retrieval to reject inconsistent stable identities/normalized text, filter future-dated license/review metadata before candidate limiting, isolate dense channel IDs and report dense-provider failure while retaining eligible lexical fallback; RRF tie ordering is input-order independent.
- Local verification includes Kotlin syntax compilation against temporary API shims, 10 focused Kotlin harness assertions, 20 shim-run JVM test methods, six shim-executed installer filesystem tests, SQLite schema/FTS5/foreign-key/integrity/time-filter smoke checks, and SHA/hash/identity fixture checks. This does not substitute for Gradle, AndroidX runtime, the resolved Bouncy Castle dependency, Android instrumentation or device tests; these remain unrun in this authoring environment.
- Release blockers: production publisher public keys are not pinned; there are no signed/rights-cleared scholarly corpus packs included; pack update/revocation management, SAF UI, local vector retrieval, reranking, citation UI and scholarly governance tooling remain outstanding.

## 0.3.1-ci-fix — 2026-11-07
- Fixed the Android CI JDK selection step: `GITHUB_ENV` now receives only a valid `JAVA_HOME=VALUE` record; the previous bare-path line caused the runner to fail with `Invalid format '/usr/lib/jvm/temurin-17-jdk-amd64'` after Java 17 had already launched successfully.
- Added explicit checks that `JAVA_HOME_17_X64` is set and that its `bin/java` executable exists before exporting it.
- Verified the workflow YAML structure and shell syntax locally; full GitHub Actions / Android Gradle execution still requires the hosted runner and SDK/dependency access.

## 0.1.0-phase1 — 2026-11-06
- Materialized the actual Gradle multi-module Android project and package structure (previous workspace contained only project documentation).
- Added Room schema v1 with 24 user-state tables, DAOs and Timeline repository adapter.
- Added SQLCipher Room 2 integration candidate and Android Keystore AES-GCM database-key envelope with fail-closed recovery rules.
- Added Arabic/English resources and DataStore locale preference contract.
- Added offline/Play flavors (neither currently requests INTERNET), tests, CI and documentation.
- Build and Android instrumentation remain unexecuted here because Android SDK/Gradle/dependency resolution are unavailable in the authoring environment.
- Added task lease/event and Canvas step entities; isolated Keystore test aliases to prevent test interaction with the application database key.
- Added explicit Android backup and device-transfer exclusion resources; expanded authorization DAO with execution receipt access.


## 0.2.0-phase2 — 2026-11-06
- Added dedicated `:feature:timeline` and `:feature:canvas` modules; wired them into the existing application without replacing the Phase 1 project.
- Replaced the placeholder app shell with a Room-backed daily Timeline, local-date/week navigation, filters, event/task/habit entry, and completion/cancellation for user-owned tasks and habits.
- Corrected daily retrieval to include events overlapping midnight; added DST-aware date window boundaries.
- Added persistent Canvas workspace create/edit/archive, versioned draft encoding, compare-and-set updates, atomic session/snapshot writes, and snapshot hashes.
- Added English/Arabic feature resources and Android per-app locale metadata.
- Added date-window, filter, Canvas codec unit tests and instrumented midnight-window / Canvas snapshot tests; expanded CI to execute new unit-test modules.
- Local Kotlin-only compilation and seven targeted test methods passed using a local harness. Full Android Gradle/KSP build and device tests remain unverified because the container has no Android SDK and DNS resolution for services.gradle.org fails.


## 0.3.0-phase3 — 2026-11-07
- Added `:core:agent-runtime` bounded one-step orchestration, persistent checkpoints, state transitions and startup recovery coordination.
- Added owner/fencing-token-checked Room leases with monotonically increasing fencing tokens; released rows are retained rather than deleted.
- Implemented `AuthorizationEngine` with canonical parameter SHA-256 binding, single-proposal approvals, confirmation freshness/lifetime checks and strong confirmation for high-impact or irreversible actions.
- Added the only Phase 3 persistent grant: Hifz review-interval adjustment, exact plan/revision/schedule scope, bounded delta, revocation/expiry and active policy-version checking.
- Added `ToolExecutionGateway`, atomic `CLAIMED → EXECUTING` checks and durable execution/outbox outcomes. Claims cannot be started after lease/approval/grant expiry; cancellation attempts to persist `OUTCOME_UNKNOWN` in a non-cancellable section.
- Execution outcomes are now reported with the original `ExecutionClaim`. A known result requeues only when the same fencing token still owns the task; stale workers cannot change a newer worker's state.
- Added Keystore-backed HMAC-SHA256 audit-chain writes in the same Room transaction as authorization mutations, plus audit verification before app-level agent entry points are enabled.
- Added Room migrations 1→2 and 2→3; Room schema version is now 3 (24 entities). The generated schema JSON remains pending the first successful KSP build.
- Added unit tests for delayed executor start, grant policy-version invalidation, cancellation, verified outcome requeue, and instrumented Room tests for leases, approvals, concurrency, audit integrity and crash reconciliation.
- Local pure-Kotlin test harness: 34/34 selected tests passed; SQLite migration SQL smoke test passed. Full Android Gradle build and instrumentation remain unexecuted. Android Gradle build and instrumentation remain not run because there is no Android SDK and DNS cannot resolve `services.gradle.org`.

- Phase 3 hardening: process-scoped, mutex-protected agent recovery prevents Activity recreation from reconciling live work a second time.
- Moved Hifz grant policy constants into `core:agent-api`, removing the persistence adapter dependency on the concrete authorization engine.
- Added future-dated grant rejection, additional lease timestamp checks, and an atomic lease-release assertion when proposals enter approval wait.
- Updated local verification: 34/34 selected pure-Kotlin tests pass; Android/Room/Keystore tests are still awaiting a network-enabled Android runner.

## 0.4.1-phase4b-publisher-trust-lifecycle — 2026-11-08
- Continued from Phase 4A `0.4.0`; retained the complete Android application, Timeline/Canvas, Phase 3 authorization/agent runtime, signed Corpus Pack v1 and lexical retrieval modules.
- Added the canonical root-signed publisher keyring format and manager. Keyring version, generated timestamp, sorted publisher keys, raw Ed25519 public keys, activation timestamps and permanent revocation timestamps are parsed from exact canonical UTF-8 bytes; the pinned root verifies the detached 64-byte signature.
- Added monotonic keyring version compare-and-set persistence in `publisher_keyring_state` within SQLCipher Room; serialized manager refresh/install so concurrent coroutine updates cannot publish a stale in-memory key snapshot after a newer update.
- Enforced key history continuity: existing publisher key IDs/public keys/activation times cannot be rebound or removed, and an established revocation cannot be undone. A missing/invalid keyring clears in-memory trust and fails closed.
- Added strict two-entry signed keyring archive importer (`keyring.txt`, `keyring.sig`) and Storage Access Framework `content://` stream adapters for keyring and corpus-pack install/update.
- Added Room schema v4 and migration 3→4 for encrypted publisher-keyring state. Registered the migration in the SQLCipher Room factory and added an executable CI schema/migration guard.
- Added transactional corpus version replacement, `SUPERSEDED` inventory, downgrade/stale-current rejection, startup signature/index/license/path revalidation, quarantine/restore, `REMOVAL_PENDING` recovery and symlink-safe deletion. Room adapter now restricts legal state transitions and only deletes records after a pending-removal state.
- Added keyring, SAF archive, persistence lifecycle, and corpus installer update tests. Phase 4A+4B selected JVM test suite passed 29/29 using the local Kotlin/JUnit/coroutine shim harness; installer lifecycle integration harness passed; Ed25519 RFC 8032 vector passed with Bouncy Castle; Room v3→v4 SQL/schema guard passed.
- Android Gradle build, KSP Room schema generation, actual AndroidX Room/SQLCipher instrumentation, API 26+ runtime compatibility, emulator and physical-device tests were not run in the authoring environment. The keyring/persistence instrumentation source compiled against API shims but did not execute on Android.
- Release gates remain: pin a reviewed production publisher root key (none is supplied), root-key ceremony/rotation plan, signed rights-cleared and scholarly-reviewed corpus packs, app-level SAF picker/management UI and startup dependency wiring, and hardware/device migration/recovery validation. Local monotonic keyring version has no independent hardware anti-rollback anchor against restoration of an older complete database image.

## 0.4.2-phase4b-gradle-bootstrap-permission-fix — 2026-11-08
- Fixed the custom Gradle 9.5.0 bootstrap failure reported by GitHub Actions: Python ZIP extraction did not preserve the Unix executable bit on `gradle-9.5.0/bin/gradle`, causing `Permission denied` (exit 126).
- The bootstrap now restores recorded Unix modes from the checksum-verified official distribution, explicitly sets the Gradle launcher executable after extraction, and repairs an existing cached launcher before testing `-x`.
- Added `scripts/test-gradle-bootstrap.py`, which creates a realistic non-executable cached launcher and verifies that `./gradlew --version` can execute it without network access; CI runs this regression before distribution verification.
- No application, Room schema, corpus, retrieval, authorization or agent-runtime behavior changed. Full GitHub Actions Gradle build remains pending this hotfix run.

## 0.5.2-phase4c-compose-api37 — 2026-11-08

- Updated all 15 Android modules to `compileSdk = 37` to satisfy Jetpack Compose 1.12.1 AAR metadata selected by Compose BOM `2026.09.00`.
- Kept the app `targetSdk = 36`, `minSdk = 26`, API 36 CI emulator, and Android 36 runtime compatibility test lane unchanged.
- Updated GitHub Actions to install both Android SDK Platform 37 (compile) and Platform 36 (runtime/emulator), retaining Build Tools 36.0.0.
- Added `scripts/verify-compose-compile-sdk.py` and a CI step to enforce the compile/target/emulator split across all 15 Android modules.
- No application behavior, permission, API target, data schema, or Phase 4 corpus logic changed.
- CI log diagnosis: Kotlin compilation and Room KSP began; the build then failed at `:app:checkOfflineSideloadDebugAarMetadata` because 11 Compose 1.12.1 AARs require compileSdk 37. This is a compile-time metadata issue, not a Kotlin source failure.
