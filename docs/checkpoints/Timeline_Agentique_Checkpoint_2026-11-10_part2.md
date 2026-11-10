# Timeline Agentique — Part 2 continuity checkpoint

**Date:** 2026-11-10  
**Baseline:** `timeline-agentic-part0-part1.zip`  
**Baseline SHA-256:** `1d5b5b239dd1083d3bc5ec025c709a7c7d807d8ba63f9a921c2d997c990f8514`  
**Scope:** process startup orchestration and encrypted attachment recovery integration.  
**Room schema:** v7 unchanged.

## Implemented

- Added `AttachmentRecoveryCoordinator` with `NotStarted`, `Running`, `Ready` and `Failed` states exposed through read-only `StateFlow`.
- Recovery is serialized per process. Successful initialization is cached; a failure remains explicit until a user-triggered retry; cancellation resets to `NotStarted` and propagates.
- `TimelineApplication.onCreate()` now starts attachment reconciliation in an application-owned `SupervisorJob + Dispatchers.IO` scope. The scan no longer depends on `MainActivity` lifetime.
- Agent audit verification and task/outbox recovery are also started from the application-owned scope. The existing `agentRuntimeReady` guard remains the authority for agent execution; a recovery failure does not unlock actions.
- The raw attachment repository is private to the application. Import/list/read/delete are exposed through app-level methods that reject use until the recovery state is `Ready`.
- The UI observes recovery state, allows explicit retry after a failure, and reports only the count of metadata rows with missing ciphertext. It does not display IDs, paths or exception messages.
- Added four coordinator unit tests and a static CI guard. English/default and Arabic resource key sets remain aligned.

## Safety and compatibility boundaries

- Reconciliation still preserves metadata rows when their encrypted file is missing. No silent row deletion or content recreation was introduced.
- Orphan encrypted files and abandoned temporary plaintext cache files continue to be handled only by the existing reconciliation policy.
- Recovery errors disable attachment operations but do not block the Timeline, Canvas, corpus or domain workspace.
- No attachment import UI, document previewer, sharing/export path, retention-policy enforcement or cloud backup was introduced.
- No entity, index, database version, migration, dependency, manifest permission, server endpoint, analytics or background service was added.

## CI follow-up — canonical Android path correction

The first hosted API 36 run compiled and packaged the application/test APKs but failed 2 security Keystore tests and 4 Room attachment-repository tests at `EncryptedAttachmentStore.ensurePrivateDirectory()`. The guard compared absolute and canonical path strings and rejected a legitimate Android app-private parent alias. The store now canonicalizes that alias while rejecting symlinks at managed directories and below trusted `noBackupFilesDir` / `cacheDir` boundaries; preflight validation runs before `mkdirs()`. Regression tests cover canonical parent aliases, managed-directory symlinks, trusted-boundary child symlinks and plaintext-cache symlinks. A Context-constructor instrumentation test exercises the real app-private directories.

The store plus test sources compile against API-shaped stubs, 12 JVM harness tests pass, and the repository static suite reports 28 passed / 0 unexpected failures. This does **not** replace the required hosted API 36 rerun. The original failing Keystore and repository instrumentation suites must be rerun before accepting Part 2.

## Verification required

- Run the pinned Gradle/AGP/Kotlin build and `:core:persistence:testDebugUnitTest` on a network-enabled Android build host.
- Run app/persistence lint and all existing unit tests.
- Run `:core:persistence:connectedDebugAndroidTest` and `:app:connectedOfflineSideloadDebugAndroidTest` on API 36 to verify Room/SQLCipher/Keystore interaction and UI resource linking.
- Exercise app process kill/restart, Activity recreation during recovery, app startup with orphan ciphertext, startup with a missing referenced ciphertext, transient scan failure followed by retry, and cancellation during process shutdown.
- Verify that a failed recovery never exposes attachment APIs and that missing metadata rows remain preserved.

**Status:** source implementation completed; actual Gradle, Android instrumentation and physical-device acceptance are still pending. Part 3 has not started.
