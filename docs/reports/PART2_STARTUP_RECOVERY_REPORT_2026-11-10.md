# Part 2 implementation report — process startup and attachment recovery

**Date:** 2026-11-10  
**Baseline archive:** `timeline-agentic-part0-part1.zip`  
**Baseline SHA-256:** `1d5b5b239dd1083d3bc5ec025c709a7c7d807d8ba63f9a921c2d997c990f8514`  
**Scope:** close the Part 1 gap where encrypted attachment recovery existed but was not automatically invoked or represented in app state.  
**Room:** schema v7 retained; no migration.

## Changes

### 1. Process-scoped recovery coordinator

Added `AttachmentRecoveryCoordinator` in `:core:persistence`:

- Exposes read-only `StateFlow` states: `NotStarted`, `Running`, `Ready(report)` and `Failed(failureCode)`.
- Serializes overlapping callers with a coroutine `Mutex`.
- Caches both success and failure during the process. A failed attempt is not silently retried by incidental observers; retry is an explicit operation.
- Cancellation resets the state to `NotStarted` and propagates rather than being misreported as a completed storage failure.
- Only a non-sensitive exception class code is exposed. Exception messages, file paths and attachment IDs are not placed in observable state.

### 2. Application lifecycle integration

- `TimelineApplication.onCreate()` starts attachment reconciliation in an application-owned `SupervisorJob + Dispatchers.IO` scope. It is no longer dependent on `MainActivity` creation or lifetime.
- Agent audit verification/task recovery now starts from the same application-owned scope. The `agentRuntimeReady` fail-closed gate, audit verification and recovery implementation are unchanged; Activity recreation no longer cancels the initial attempt.
- The raw attachment repository is private to `TimelineApplication`.
- App-level import/list/read/delete methods require the coordinator to be `Ready`; no caller through these methods can use attachment storage before successful recovery.

### 3. User-visible recovery status

- Compose observes the recovery state.
- A failed scan displays a generic localized message and an explicit Retry action.
- Missing ciphertext referenced by Room metadata is reported as a count only. The corresponding metadata is preserved; the UI does not reveal IDs, private paths, exception messages or attachment contents.
- A recovery failure disables attachment operations only; it does not block the Timeline, Canvas, corpus or domain workspaces.
- Added matching default/English and Arabic resources.

### 4. Test and CI coverage

Added four coordinator unit tests:

1. Successful initialization executes once and retains its report.
2. Failed initialization remains visible and is retried only after explicit retry.
3. Coroutine cancellation resets state and propagates.
4. Concurrent initialization shares one successful reconciliation.

Added `scripts/verify-part2-startup-recovery.py` and a corresponding CI step. It checks process-scoped startup, API readiness gates, UI status/retry, localization parity, test presence and unchanged Room v7.

## Verification actually run

| Check | Result | Detail |
|---|---|---|
| Part 2 static startup/recovery guard | PASS | 32 source/UI/state/schema assertions |
| Full repository Python static suite | 28 PASS | All non-environment static checks passed, including Part 0/1, migration chain, translations, security scan, CI contracts and test signatures |
| Android SDK platform preflight | BLOCKED | `ANDROID_HOME` / `ANDROID_SDK_ROOT` are not configured |
| Room schema export verifier | BLOCKED | No generated schema JSON is present; Room KSP cannot be run without the Gradle/Android toolchain. No schema was fabricated |
| New coordinator Kotlin harness | PASS, 4/4 | Compiled coordinator and its test bodies with installed Kotlin 1.9.0 + local coroutines runtime and API/JUnit assertion shims; this is not the project's Gradle/JUnit execution |
| JUnit 4 signature guard | PASS | 116 expression-bodied test methods explicitly declare `Unit` across 46 source files |
| Secret scan | PASS | No configured secret pattern found |
| ZIP/delta verification | PASS | Patch ZIP integrity and entry list verified; 5 added + 8 modified + 0 deleted; overlaying the patch onto the exact Parts 0/1 baseline reproduces the full 316-file working tree byte-for-byte. The manifest records the patch SHA-256. |
| Pinned Gradle/AGP/Kotlin build and real Room/SQLCipher/Keystore instrumentation | NOT RUN | No installed Gradle executable/distribution, Android SDK or emulator in this environment |

The 28 passing Python scripts are source/static regression checks. They do not establish Android compilation, KSP generation, Compose resource linking, Android Keystore operation, SQLCipher startup or device behavior.

## Acceptance tests still required

Run the exact patch on a network-enabled Android build host and verify:

- All unit tests, especially `:core:persistence:testDebugUnitTest` and `:app:testOfflineSideloadDebugUnitTest`.
- `:app:assembleOfflineSideloadDebug`, app/persistence lint, and generated Room v7 schema guard.
- `:core:persistence:connectedDebugAndroidTest` and `:app:connectedOfflineSideloadDebugAndroidTest` on API 36.
- App process kill/restart; Activity recreation during a scan; orphan ciphertext cleanup; missing referenced file preservation; transient scan failure followed by user retry; and cancellation while the process is terminating.
- Confirm attachment APIs reject `NotStarted`, `Running` and `Failed`, and permit access only after `Ready`.
- Confirm agent execution remains denied until audit verification and task recovery succeed.

## Out of scope

No attachment picker/import screen, viewer, share/export feature, retention-policy enforcement, backup migration, lifecycle service, model/agent feature, source corpus or domain logic was added. These require separate product/security contracts. No dependencies, database entities/indexes/migrations, permissions, backend, analytics or app version metadata changed.

**Part 2 source status:** implemented; host Gradle and Android runtime acceptance remain outstanding. Part 3 has not started.

## CI follow-up — Android canonical-path failure (2026-11-10)

A subsequent API 36 hosted run compiled and packaged the application and Android test APKs, then exposed one common runtime failure in six tests:

- `:core:security`: 2 failures in `EncryptedAttachmentKeystoreTest`.
- `:core:persistence`: 4 failures in `RoomDocumentAttachmentRepositoryTest`.
- All six failed in `EncryptedAttachmentStore.ensurePrivateDirectory()` with `Attachment storage directory is not a canonical private directory`.

### Root cause

The earlier guard required `directory.canonicalFile == directory.absoluteFile`. Android can legitimately expose an app-private path through a platform symlink alias (for example `/data/user/0` resolving to `/data/data`). The comparison rejected that valid alias even though the target directory remained inside the application's private data area. This was a path-validation defect, not an AES-GCM, Keystore, SQLCipher, Room, compilation or emulator-startup failure.

### Correction applied

- Canonicalize the resolved attachment/cache directory for file operations without requiring its absolute spelling to equal its canonical spelling.
- Reject a symbolic link at the managed directory itself.
- For the `Context` constructor, treat `noBackupFilesDir` and `cacheDir` as trusted Android-private boundaries and reject symbolic links in every app-managed child component below those boundaries.
- Perform the symlink check both before and after `mkdirs()` so a redirected child cannot cause directory creation outside the expected app-managed path.
- Keep the existing canonical-parent check for each attachment file, the opaque ID validation, AES-GCM authentication, size/hash validation and no-backup/cache placement.

### Regression coverage added

- A canonicalized parent alias is accepted while a symlinked managed storage directory is rejected.
- A symlink below the trusted private parent is rejected before child-directory creation.
- A symlinked plaintext verification cache is rejected.
- An Android instrumentation test exercises the `Context` constructor against the real app `noBackupFilesDir` and cache path and confirms ciphertext remains under `noBackupFilesDir/attachments/v1`.
- The Part 1 static CI guard now checks the canonical-alias and symlink-boundary contracts.

### Verification after correction

| Check | Result |
|---|---|
| Kotlin compile of store + JVM tests + Keystore instrumentation source against Android/AndroidX API-shaped stubs | PASS |
| JVM attachment-store regression harness | **12/12 passed** (including the new path/symlink cases) |
| Repository static Python checks | **28 passed, 0 unexpected failures** |
| Android SDK preflight | BLOCKED in this local environment: no `ANDROID_HOME` / `ANDROID_SDK_ROOT` |
| Room schema export verifier | BLOCKED in this local environment: KSP has not run here; no schema was fabricated |
| Hosted API 36 Android instrumentation after this correction | **NOT YET RUN** |

The correction is therefore source- and JVM-harness-verified, but Part 2 is not accepted until the same hosted `connectedAndroidTest` command is rerun on API 36. The rerun must show the Keystore and Room repository tests passing. No Room schema change or migration is part of this fix.

