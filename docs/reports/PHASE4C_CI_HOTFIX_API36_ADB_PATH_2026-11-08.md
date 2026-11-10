# Phase 4C CI hotfix — API 36 emulator `adb` discovery

**Source version:** `0.5.9-phase4c-api36-adb-path-fix`
**Base version:** `0.5.8-phase4c-english-resource-lint-fix`
**Date:** 2026-11-08

## 1. Reported failure

The supplied GitHub Actions log showed the SDK Manager completing and the API 36 system image being selected, followed by:

```text
.../avdmanger create avd ...
/home/runner/...sh: line 6: adb: command not found
Process completed with exit code 127.
```

The failing command was `adb wait-for-device`. Exit code 127 means the shell could not resolve the command. The workflow had already requested the SDK package `platform-tools`, but it neither verified the SDK executable's location nor guaranteed that `$ANDROID_HOME/platform-tools` was on `PATH` in the emulator step. The supplied output does not show an emulator boot attempt, so it does not establish a KVM, system-image, or Android boot failure.

## 2. Changes

### SDK installation and executable discovery

The existing SDK installation continues to request `platform-tools`. Immediately after installation, CI now:

- resolves the expected executable as `$ANDROID_HOME/platform-tools/adb`;
- fails with the exact expected path and a directory listing if it is absent or not executable;
- appends `$ANDROID_HOME/platform-tools` to `GITHUB_PATH` so later GitHub Actions steps have it on `PATH`;
- writes the absolute path to `ADB` in `GITHUB_ENV`;
- invokes `"$ADB" version` to prove the selected executable starts before proceeding.

### API 36 emulator startup

The inline startup block is now `scripts/start-api36-emulator.sh`. It uses explicit paths for both `adb` and the emulator, verifies `adb`, `emulator`, and `avdmanager` before creating the AVD, starts the adb server, and preserves the API 36 Google APIs x86_64 / Pixel 6 CI profile.

Startup waits are bounded: up to 180 seconds for the device to appear in adb and up to 600 seconds for `sys.boot_completed=1`. When either wait fails, CI prints `adb devices -l`, the emulator process diagnostic, and the last 200 lines of `/tmp/timeline-emulator.log`. This makes a future emulator/KVM/boot failure distinguishable from an SDK PATH issue.

### Regression coverage

Added `scripts/test-api36-emulator-setup.py`, run before Gradle. It verifies the workflow installs and exposes the SDK `platform-tools` directory, the emulator step calls the dedicated startup script, the helper uses the absolute SDK paths instead of a bare `adb`, diagnostics are present, and an absent `platform-tools/adb` fails early with an actionable diagnostic.

The existing API 37.0 compile SDK / API 36 runtime emulator contract is preserved. `scripts/verify-compose-compile-sdk.py` now checks for the AVD name in the dedicated startup script instead of expecting the former inline AVD command to remain in the workflow YAML.

## 3. Version and scope

- App `versionCode`: 13.
- App `versionName`: `0.5.9-phase4c-api36-adb-path-fix`.
- Preserved compileSdk 37.0, targetSdk 36, minSdk 26, and API 36 emulator image.
- No application runtime logic, user interface, Room schema, encryption, corpus trust, retrieval, or authorization behavior changed.

## 4. Verification

The following local checks are expected and were run for this source tranche:

- API 36 emulator setup regression tests (6 tests, including a mocked successful startup flow).
- Bash syntax check for `scripts/start-api36-emulator.sh`.
- App compile/version contract, string translation tests and verifier, API 37.0 SDK tests, Gradle bootstrap regression, secret scan, Room schema guard, dense-index contract, AGP built-in Kotlin policy tests/verifier, dependency lock/version policy, Phase 4C foundation guard, and Compose compile SDK verifier.
- Workflow YAML parse, XML/TOML validation, ZIP integrity, checksum, patch-application equivalence and executable-mode checks are run at packaging time where applicable.

This authoring environment has no Android SDK or installed Gradle distribution. It cannot execute the actual GitHub-hosted AVD, Gradle/KSP build, Android lint, or connected instrumentation suite. The fix addresses the exact `adb: command not found` failure and adds better diagnostics, but only the next GitHub Actions run can confirm successful API 36 boot and downstream tests.

## 5. Continuation

Continue from this 0.5.9 source. If the next run advances past SDK discovery but fails to boot, use the newly uploaded `/tmp/timeline-emulator.log` tail and `adb devices -l` output to diagnose the actual emulator failure rather than changing application code speculatively.
