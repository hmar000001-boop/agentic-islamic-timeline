# Phase 4C CI hotfix — API 36 emulator regression fixtures (0.5.10)

**Date:** 2026-11-08  
**Baseline:** `0.5.9-phase4c-api36-adb-path-fix`  
**Version:** `0.5.10-phase4c-api36-emulator-test-fix`

## Triggering CI evidence

The API 36 setup regression step ran six tests; four passed and two failed:

1. `test_missing_adb_fails_early_with_actionable_diagnostic` received the emulator-executable diagnostic instead of the expected `platform-tools/adb` diagnostic.
2. `test_startup_script_completes_with_mocked_sdk_tools` failed because the mock emulator exited before ADB registration.

## Root causes

### Missing-ADB test inherited the runner's valid ADB override

The CI workflow exports `ADB` at job scope through `GITHUB_ENV`. The test copied `os.environ` into its isolated environment but did not override `ADB`. Consequently, the startup script used the runner's real `$ANDROID_HOME/platform-tools/adb`; it passed the missing-ADB check and reached the next prerequisite, where the deliberately incomplete temporary SDK had no emulator executable. The production diagnostic order was correct; the fixture was not isolated.

**Correction:** The test now explicitly sets `ADB` to the nonexistent `platform-tools/adb` path beneath its temporary SDK. It therefore tests the intended failure regardless of inherited runner variables.

### Successful-startup mock exited immediately

The fake emulator returned success after writing its PID file. The production startup script intentionally checks that the emulator process remains alive while ADB registers the device and Android finishes booting. The immediate mock exit was correctly interpreted as an emulator failure.

**Correction:** The mocked emulator remains alive after startup, emulating the lifetime of the real emulator. The test always sends termination to the detached mock in a `finally` block, including when the subprocess times out or an assertion fails.

## Production behaviour preserved

No production startup timeout, process-health check, SDK path, KVM setup, AVD definition, emulator arguments, API level, workflow install step, or instrumentation-test command was weakened or bypassed. The fix is confined to regression-test isolation and mock process lifecycle.

## Verification performed in the authoring environment

- `python3 scripts/test-api36-emulator-setup.py`: **6/6 passed**.
- Full offline/static contract suite: results recorded in the delivery summary and reproducible in GitHub Actions.
- Shell syntax, Python compilation, YAML/XML/TOML parsing, archive integrity and patch-application checks are included in release verification.

The authoring environment does not provide the project's Android SDK/Gradle runtime, so it cannot prove a real GitHub-hosted API 36 AVD boot or connected instrumentation run. Those remain hosted-CI checks and are not represented as passed here.
