## 0.5.14 CI correction — unify API 36 AVD creation and lookup paths

The latest hosted run passed the headless QEMU dependency preflight and started the Android Emulator launcher, but the emulator could not find `timeline-ci-api36.ini` under `$HOME/.android/avd`. The startup script now pins `ANDROID_USER_HOME`, `ANDROID_EMULATOR_HOME`, `ANDROID_AVD_HOME`, and the legacy `ANDROID_SDK_HOME` to compatible locations, removes an inherited conflicting `ANDROID_PREFS_ROOT`, and passes an explicit `--path` for the AVD data directory. After `avdmanager` exits, startup verifies both the root `.ini` registration and the AVD `config.ini` before launching the emulator. The regression suite checks conflicting inherited runner variables and that `avdmanager` and the emulator receive the same canonical paths. Real hosted AVD creation, guest boot, and instrumentation still require Actions confirmation.

## 0.5.13 CI correction — select the actual headless QEMU backend

The API 36 job launches the emulator with `-no-window`, which the Android Emulator launcher treats as headless mode. The launcher therefore selects `qemu-system-x86_64-headless`, not the GUI `qemu-system-x86_64` binary. The 0.5.12 preflight inspected the wrong binary and ran `ldd` without the SDK's bundled library search paths, so it reported SDK-owned Qt/Android-emulator libraries as missing. The preflight now checks the headless binary and mirrors the launcher's `lib64`, SwiftShader GLES, Vulkan and bundled `libstdc++` search directories. Genuine missing host dependencies still fail fast. The API 36 regression suite passes 9/9 locally; actual GitHub-hosted QEMU launch, API 36 guest boot and connected instrumentation still require Actions confirmation.

## 0.5.12 CI correction — initial API 36 QEMU host library preflight

The latest hosted emulator step reached the Android Emulator launcher, but QEMU could not load `libpulse.so.0` and exited with code 127 before an Android guest started. The Ubuntu runner now installs the `libpulse0` client runtime package; no PulseAudio server or app audio feature is installed. Before creating the AVD, the startup script checks the x86_64 QEMU binary with `ldd` and fails with the exact unresolved library names if host dependencies are missing. Tests cover the workflow package install, missing-library preflight, and successful mock startup. Real hosted API 36 boot and connected instrumentation still require Actions verification.

## 0.5.10 CI correction — API 36 emulator test fixtures

The next GitHub Actions run exposed two failures in the emulator setup regression tests, not in the emulator startup path itself. The missing-ADB test inherited the runner's valid job-level `ADB` override, so it reached a later missing-emulator check. It now explicitly points to the missing `platform-tools/adb` path in its temporary SDK. The successful-startup fake emulator had exited immediately; it now remains alive while ADB reports a booted device and is terminated in a `finally` cleanup path. Production early-exit detection and boot deadlines remain intact. The regression suite passes 6/6 locally; a hosted run must still verify real API 36 boot and instrumentation.

## 0.5.9 CI correction — API 36 emulator adb discovery


The supplied hosted-runner log showed that SDK Manager completed but the next step failed at bare `adb wait-for-device` with `adb: command not found` (exit 127). CI now verifies `$ANDROID_HOME/platform-tools/adb`, exports `platform-tools` through `GITHUB_PATH`, and passes the explicit executable to a dedicated API 36 startup script. That script uses bounded waits for device registration and Android boot, and prints the device list and emulator log tail if startup fails. This corrects the tool lookup failure; a subsequent hosted run must still confirm that the emulator boots and the instrumentation suite runs.

## 0.5.8 CI correction — English resource lint completeness

The last hosted run successfully assembled `:app:assembleOfflineSideloadDebug` and passed lint for `core:corpus`, `core:retrieval`, and `feature:corpus-management`. App lint then reported three `MissingTranslation` errors: `app_nav_timeline`, `app_nav_canvas`, and `app_nav_corpus` existed in default and Arabic resources but were absent from the app's explicit `values-en/strings.xml`. All three English entries are now present. The corpus-management feature also now has an explicit English resource file matching its default English set. CI runs translation parity tests and a verifier before Gradle, checking both locale key sets and format placeholders without disabling lint.

# Timeline Agentique

**Version:** `0.5.14-phase4c-api36-avd-home-fix`

## 0.5.7 CI correction — app compile classpath and symbols

The latest hosted build passed `:core:persistence:compileDebugKotlin`, Room KSP and persistence unit tests, then exposed app-source compile errors. `MainActivity` no longer imports the `ColumnScope`-member `weight` extension as a top-level symbol; `TimelineApplication` imports `TaskLease` and `ExecutionClaimResult` from `core.agent`; and persistence now exports Room runtime as an API dependency because the public `AppDatabase` type extends `RoomDatabase`. CI runs `scripts/verify-app-module-compile-contract.py` before Gradle. Re-run hosted app compilation to validate the fix.

## 0.5.6 CI correction — Room dense-index adapter compile contract

The next hosted Gradle run passed the API 37.0 SDK verifier, AGP built-in Kotlin configuration, AAR metadata checks, all listed core/feature production compilation except `:core:persistence`, KSP startup, and the previously failing `:core:retrieval` unit tests. Compilation then exposed two errors in `RoomDenseEmbeddingIndexStore`: the expression-bodied `activateGeneration` leaked the DAO's `Int` return value despite the interface requiring `Unit`, and `vectorsFor` referenced an undeclared `STATE_ACTIVE`. The adapter now uses a block-bodied `Unit` override while keeping the complete retire/activate/delete sequence inside the same Room transaction, and defines `STATE_ACTIVE = "ACTIVE"` to match the DAO predicates. CI runs `scripts/verify-room-dense-index-contract.py`; the existing Room instrumentation test exercises activation, replacement and recovery.

## 0.5.5 CI correction — non-empty corpus-index test fixtures

The GitHub Actions run now gets through SDK installation, AGP configuration, Kotlin compilation and into the unit tests. Three failures were isolated to `LocalEmbeddingIndexBuilderTest`: its tests used zero-byte temporary `.sqlite` files, while the production builder intentionally rejects an empty corpus index before creating a staging generation. Tests now write an explicit fixture placeholder for their in-memory source; the empty-file rejection itself has its own regression test. Production validation remains strict.

## 0.5.4 CI correction — parse the real Android SDK Platform 37.0 metadata

The installed Platform 37.0 package reports `AndroidVersion.ApiLevel=37.0` (a dotted value) in `source.properties`; it does not report `AndroidVersion.ApiLevel=37` plus `AndroidVersion.MinorApiLevel=0`. The pre-Gradle verifier now parses and normalizes both representations, accepts exactly major 37/minor 0, rejects mismatched or conflicting values, and retains the `android.jar`, directory-boundary, and package-name checks. Ten regression tests include the exact metadata emitted by the runner.

## 0.5.3 CI correction — Android SDK Platform 37.0

Android SDK Platform 37 is published by the SDK Manager as `platforms;android-37.0`, not `platforms;android-37`. CI installs the exact `37.0` package and verifies `android.jar` plus `source.properties` before Gradle starts. Every Android module uses AGP's typed `compileSdk { version = release(37) { minorApiLevel = 0 } }` DSL, so Gradle resolves the correct platform identity without a filesystem alias. The app remains `targetSdk 36`; the CI runtime emulator remains API 36.

`python3 scripts/test-android-sdk-platform.py` tests the validator against missing, wrong-version, incomplete and symlink-escape SDK layouts. `scripts/verify-android-sdk-platform.py` checks the real installed SDK in CI.

## 0.5.2 CI correction — Compose API 37

The stable Compose BOM `2026.09.00` resolves Compose 1.12.1, whose Android artifacts require `compileSdk 37`. All 15 Android modules now compile against API 37.0. The app's `targetSdk` remains 36 and CI keeps the API 36 emulator for runtime compatibility tests; compile SDK and target SDK serve different purposes. CI installs Android SDK Platforms 36 and 37.0.

`python3 scripts/verify-compose-compile-sdk.py` checks this split and is executed by GitHub Actions.

# Timeline agentique — Android source foundation (Phases 1–4C)

**Current source version:** `0.5.14-phase4c-api36-avd-home-fix`
**Architecture:** `docs/reference/Cahier_architecture_Timeline_agentique_v1.2.md`
**Readiness:** `docs/reference/Rapport_final_readiness_demarrage_Timeline_agentique.md`
**Package ID:** provisional `app.timeline.islamic`
**Status:** incremental implementation source; not a production release.

## 0.5.1 CI correction

The first real AGP 9.3.1 Kotlin DSL configuration attempt found that the public `Variant.buildConfigFields` API is nullable. The app keeps `android.buildFeatures.buildConfig = true` and now checks the property with `checkNotNull` before registering the two publisher-root BuildConfig values. This fails explicitly if BuildConfig generation is unavailable instead of silently omitting trust configuration. The correction is covered by the built-in Kotlin/AGP verification scripts.

The authoring environment still has no installed Gradle distribution or Android SDK, so the actual AGP/KGP/KSP build remains pending the next GitHub Actions run.

## What is in this source archive

- The original Android application and all prior Timeline, Canvas, task runtime, authorization, encrypted persistence, corpus, publisher-trust and retrieval work are preserved. This is not a replacement project.
- Fifteen Gradle modules: `app`; `core:common`, `core:timeline-api`, `core:agent-api`, `core:authorization`, `core:agent-runtime`, `core:security`, `core:localization`, `core:persistence`, `core:knowledge-api`, `core:corpus`, `core:retrieval`; `feature:timeline`, `feature:canvas`, and `feature:corpus-management`.
- Android min API 26, compile API 37 / target API 36, Java 17 bytecode target. Offline sideload and Play-distribution flavors both currently have no INTERNET permission; Play AI Delivery is not integrated.
- SQLCipher-protected Room schema v5 with 27 registered entities and explicit migrations 1→2→3→4→5. Keystore-wrapped random database key, fail-closed recovery, no plaintext fallback.
- Existing daily Timeline, task/habit entry and completion, Canvas create/edit/archive/snapshots, Arabic/English resource sets and RTL-capable layouts.
- Phase 3 persistent one-step agent runtime, owner/fencing-token leases, approvals, narrowly scoped Hifz interval grant, execution gateway, crash reconciliation and Keystore HMAC audit chain. No production OS action adapter is registered by this source tranche.
- Phase 4A signed Corpus Pack v1, canonical Ed25519 manifest, immutable original evidence text, source/edition/provenance/licensing/review metadata, Arabic/English normalization, four-madhhab selection with no default madhhab, SQLite FTS5/BM25 lexical retrieval, rank-only RRF and eligibility/citation rechecks.
- Phase 4B root-signed publisher keyring, monotonic encrypted Room persistence, SAF import/update, pack lifecycle, startup revalidation, quarantine/restore, removal recovery and path/symlink protections.
- Phase 4C corpus-management screen connected to Timeline app navigation; user-driven SAF selection for keyring and packs; signed keyring/pack lifecycle operations; explicit update/removal confirmation; status visibility and startup revalidation.
- Phase 4C bounded local embedding-index construction contract. It streams installed passages in small batches, quantizes normalized vectors to int8, stages and atomically publishes generations, and stores vectors only inside the SQLCipher-protected Room database. Per-generation payload is capped at 128 MiB; total active/staging vector payload is capped at 256 MiB. Inactive/quarantined/superseded packs have their dense generations removed.
- Dense retrieval remains disabled until a separately reviewed, compatible on-device embedding model is installed and registered. There is no fake embedder, no fabricated vector model, no embedded model weights and no remote embedding provider. FTS5 remains the functioning retrieval path.

## Toolchain and AGP 9+ migration

| Component | Version | Configuration |
|---|---:|---|
| Android Gradle Plugin | 9.3.1 | Built-in Kotlin and public typed Android DSL |
| Gradle distribution | 9.5.0 | Custom checksum-verifying bootstrap (`gradlew`) |
| Kotlin compiler | 2.4.21 | AGP built-in Kotlin; no `org.jetbrains.kotlin.android` plugin |
| KSP | 2.3.13 | Pinned processor plugin for Room code generation |
| Compose compiler plugin | 2.4.21 | Same Kotlin compiler release |
| Compose BOM | 2026.09.00 | Exact catalog pin |
| Room | 2.8.5 | KSP processor |
| SQLCipher Android | 4.20.0 | Room encrypted helper; device validation required |
| AndroidX SQLite | 2.7.1 | Bundled SQLite/FTS5 for immutable corpus index files |

The Android modules now use AGP built-in Kotlin. The external `org.jetbrains.kotlin.android` plugin and both transitional AGP opt-out properties are removed. All Android module scripts use the public typed `ApplicationExtension`/`LibraryExtension` DSL. Compose uses the dedicated Kotlin Compose compiler plugin. App BuildConfig values are added through lazy `androidComponents.onVariants` rather than the deprecated eager `buildConfigField()` API. There are no `applicationVariants`, `libraryVariants`, `testVariants` or `unitTestVariants` calls in project build scripts. These are the permanent AGP 9 migration changes required before any AGP 10 upgrade. See the official [built-in Kotlin migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin).

KGP 2.4.21 is pinned through the root buildscript classpath because this build uses a newer compiler than AGP's bundled default. KSP 2.3.13 is pinned explicitly for the Room processor path. KSP/Room code generation still requires a successful Android Gradle run; this environment cannot validate that runtime integration.

## Gradle CI and dependency reproducibility

- `org.gradle.configuration-cache=false` is explicit. The supplied CI logs showed AGP navigation-resource and Gradle dependency-report tasks failing while serializing configuration-cache state. Re-enable only after these tasks pass with configuration cache on the target runner.
- Locking is enabled for the explicitly pinned root KGP/KSP buildscript classpath and ordinary resolvable project configurations. AGP runtime classpaths and CycloneDX BOM configurations explicitly deactivate locking because their plugins install `failOnDynamicVersions`; Gradle forbids combining that strategy with dependency locking on the same Configuration. We do **not** add a second `failOnDynamicVersions()` call. Version-catalog and direct dependency declarations are statically checked for dynamic selectors, and normal compile/processor configurations retain transitive lock state.
- CI runs bootstrap permission regression, AGP built-in Kotlin/DSL checks, reproducibility policy checks, Room migration guards, tests, lint, offline merged-manifest audit, API 36 instrumentation, dependency report and CycloneDX SBOM.
- CI passes `--write-locks` to successful Gradle tasks and uploads generated project `gradle.lockfile` and root `buildscript-gradle.lockfile` artifacts. Lockfiles and Gradle verification metadata must be reviewed and committed from a successful network-enabled CI run; they are not fabricated here.

## Honest verification status

The authoring workspace has JDK 21 and Kotlin compiler 1.9.0, but **no installed Gradle distribution or Android SDK**. The container cannot resolve Gradle/Maven repositories. Therefore a real Android Gradle build, KSP/Room generation, AndroidX/SQLCipher runtime tests, API 36 emulator tests and Galaxy S24 FE tests have **not** been run here. Do not treat local static checks as a passing Android build.

Local checks for this tranche include the AGP built-in Kotlin migration tests, public-DSL/version-policy guards, Room v5 migration/schema/SQLite smoke guard, Gradle bootstrap regression, offline manifest and XML checks, secret scan, source inventories and archive/checksum integrity. Their exact results and limitations are in `docs/reports/PHASE4C_AND_CI_MIGRATION_REPORT_2026-11-08.md`.

## Database and key recovery policy

- User database is app-private and SQLCipher encrypted.
- Random database passphrase is wrapped by a non-exportable AES-GCM Android Keystore key in `noBackupFilesDir/database-key.envelope`.
- Existing database with missing/invalid envelope or missing Keystore key fails closed. The app never generates a replacement key for an existing database, deletes the database automatically or falls back to plaintext.
- Room migrations are explicit and non-destructive: 1→2 adds `pending_approval.sourceGrantId`; 2→3 adds `action_execution.proposalId` and unique index; 3→4 adds encrypted `publisher_keyring_state`; 4→5 adds dense-index generation/vector tables and indices with cascade deletion.
- The Room KSP schema JSON snapshot is not fabricated. Generate and review schema `5.json` on the first successful Gradle/KSP runner and commit it along with migration tests before release.

## Important incomplete areas and release gates

- No production publisher root public key is pinned. The app intentionally disables publisher trust operations until the operator supplies a reviewed root key via controlled build properties. No publisher private key, placeholder root key or corpus data is included.
- No signed, rights-cleared, scholarly-reviewed Qur'an/hadith/tafsir/fiqh corpus pack is included. Corpus rights, provenance, exact editions/translations, and scholarly governance remain mandatory release gates.
- No concrete embedding model/runtime adapter or model weights are included. Phase 4C provides the streaming index builder and encrypted storage only; model acquisition/validation and on-device inference belong to Phase 5.
- No dense ranking is used when a model is absent. Dense retrieval must remain subordinate to hard source eligibility, licensing, review, language, madhhab and citation checks.
- Phase 2 remains a first functional slice: recurring habits, calendar integration, notification scheduling, history UI, full Canvas step editing/attachments/undo, prayer-time calculation cards and other listed roadmap functions are not complete.
- Phase 3 does not yet register production OS tool adapters, a model-driven AgentStepProcessor or WorkManager scheduler. No external action is currently registered by the application.
- HMAC audit chaining has no independent anti-truncation anchor; deletion of the entire tail/table cannot be proven from the local chain alone.
- SQLCipher/Keystore recovery and migration instrumentation, supported-ABI and API-range testing, localization/RTL, battery/performance and physical-device QA remain release gates.

## Build commands

```bash
./gradlew --version
./gradlew :app:assembleOfflineSideloadDebug
./gradlew :app:testOfflineSideloadDebugUnitTest
./gradlew :core:authorization:testDebugUnitTest :core:agent-runtime:testDebugUnitTest
./gradlew :core:persistence:testDebugUnitTest :core:knowledge-api:testDebugUnitTest
./gradlew :core:corpus:testDebugUnitTest :core:retrieval:testDebugUnitTest
./gradlew :feature:timeline:testDebugUnitTest :feature:canvas:testDebugUnitTest
./gradlew :feature:corpus-management:testDebugUnitTest
./gradlew :app:lintOfflineSideloadDebug
./gradlew :app:connectedOfflineSideloadDebugAndroidTest
./gradlew :core:persistence:connectedDebugAndroidTest
```

For initial dependency lock generation on a network-enabled host, run the CI workflow with `--write-locks`, review all generated lockfiles and commit them. Do not manufacture lock entries by hand.

## Continuity and checkpoints

- Project Library folder: `/Timeline_Agentique/`
- Immutable ZIPs and SHA-256 files are versioned; do not overwrite earlier deliveries.
- Every meaningful tranche has a new checkpoint under `docs/checkpoints/` and report under `docs/reports/`.
- Resume from `docs/checkpoints/Timeline_Agentique_Checkpoint_2026-11-08_phase4c-api36-headless-qemu-preflight-fix.md` for the next implementation.
