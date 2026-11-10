# Phase 5C CI hotfix — Android directory fsync API

**Date:** 2026-11-09
**Baseline:** `0.6.2-phase5c-dense-retrieval` (versionCode 23)
**Scope:** one Android SDK compile failure in `:core:model-manager`

## CI finding

The supplied Gradle log reaches `:core:model-manager:compileDebugKotlin` after the app, authorization, agent-runtime, persistence, corpus, retrieval and other module compilation/test tasks have progressed. Compilation stops at `AndroidModelFileStore.kt:215`:

```text
Unresolved reference 'O_DIRECTORY'.
```

The source used:

```kotlin
Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_DIRECTORY, 0)
```

`O_DIRECTORY` is a native POSIX/Linux open flag, but it is not listed in Android's public `android.system.OsConstants` reference. The SDK therefore cannot compile this ordinary application reference. Android does expose `Os.open`, `Os.lstat`, `Os.fstat`, `Os.fsync`, `Os.close`, `OsConstants.O_RDONLY` and `OsConstants.S_ISDIR` from API 21 onward; the app's minSdk is 26.

## Correction

`syncDirectory` now:

1. Retains the absolute, non-canonicalized path so `lstat` can inspect the directory entry itself rather than silently resolving a final-component symlink.
2. Calls `Os.lstat` and rejects a non-directory/symlink path.
3. Opens it with public `OsConstants.O_RDONLY` only.
4. Calls `Os.fstat` on the returned descriptor and verifies it is a directory.
5. Calls `Os.lstat` again and verifies the path still names the same directory using `st_dev` and `st_ino`.
6. Calls `Os.fsync` on the descriptor.
7. Always closes the descriptor in `finally`.

This preserves the intended durability guarantee. It does not remove fsync, weaken failure handling, inline a hidden constant's numeric value, or substitute a non-durable `FileDescriptor.sync` on a different object.

## Regression prevention

Added `scripts/test-android-model-directory-sync-contract.py` and a dedicated GitHub Actions step. It asserts the code does not reference `O_DIRECTORY`, uses the public open/lstat/fstat/S_ISDIR APIs, verifies descriptor/path identity, retains fsync, and closes in `finally`.

The existing API 36 `AndroidModelFileStoreTest.commitAtomicallyConsumesStagingAndRemovalSyncsDirectoryHierarchy` already performs real Android calls through `prepare`, ownership marker creation, staging, atomic commit and model removal. It will exercise the new `syncDirectory` path during connected instrumentation.

## Local validation performed

- New Python source contract: passed.
- Exact production `AndroidModelFileStore.kt` compiled with Kotlin 1.9 against a minimal Android `Os` shim that intentionally exposes only the public methods/constants used by the correction (and deliberately does not define `O_DIRECTORY`).
- Filesystem harness completed `prepare`, ownership-marker creation, staging creation, commit and `deleteOwnedFiles`; it observed eight directory fsync operations and eight matching descriptor closes.
- This validates source/API shape and control flow only. The shim does not reproduce Android's native syscalls or prove filesystem durability on a physical device.

## Remaining verification

Rerun the same GitHub Actions unit-test command and then the connected API 36 instrumentation job. Required confirmation is that `:core:model-manager:compileDebugKotlin` passes and `AndroidModelFileStoreTest` succeeds on the actual API 36 emulator. No Android SDK/KVM runner is available in this authoring environment, so no real AGP/KSP/Android instrumentation pass is claimed.

## Scope and compatibility

No Room schema, model catalog, model bytes, install ownership protocol, atomic rename behavior, model lifecycle state machine, encrypted dense index, inference runtime, retrieval routing, authorization policy or app permission changed. The app remains at `0.6.2-phase5c-dense-retrieval` / versionCode 23; this is a build fix for that same release baseline.
