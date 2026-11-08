# Phase 4C CI hotfix — API 36 mocked ADB test isolation

**Date:** 2026-11-08  
**Baseline:** `0.5.10-phase4c-api36-emulator-test-fix`  
**Resulting source:** `0.5.11-phase4c-api36-adb-test-isolation-fix`  
**Scope:** CI regression test fixture only; production emulator startup code unchanged.

## 1. Hosted failure

The 2026-11-08 GitHub Actions run reported:

```text
ERROR: test_startup_script_completes_with_mocked_sdk_tools
subprocess.TimeoutExpired: Command [.../scripts/start-api36-emulator.sh'] timed out after 20 seconds
```

The other five tests passed, including the missing-ADB diagnostic, platform-tools installation contract, startup-script integration, boot-failure diagnostics, and explicit SDK path contract.

## 2. Root cause analysis

The workflow's SDK installation step exports the real runner ADB path as a job-wide variable:

```bash
echo "ADB=$ADB" >> "$GITHUB_ENV"
```

Subsequent workflow steps receive this `ADB` value. The successful-startup test creates a temporary SDK containing a mock `platform-tools/adb`, then uses `env = os.environ.copy()` and overrides `ANDROID_HOME`, `AVDMANAGER`, `PATH`, and the emulator log. It did not override `ADB`.

The production startup script selects the explicit environment override before its SDK fallback:

```bash
ADB="${ADB:-${SDK_ROOT}/platform-tools/adb}"
```

This is correct production behavior and supports deterministic SDK selection in CI. However, it means the test was not using the fake SDK's mock ADB. It invoked the actual runner ADB instead. The mock emulator process is not a real Android emulator and cannot register a virtual device with the runner's ADB server. Consequently, `adb get-state` continued to return a non-success status while the startup script polled for device registration. The script's production bound is 180 seconds, but the Python test's subprocess bound is 20 seconds, so `subprocess.run` raised `TimeoutExpired` before the production deadline could be reached.

This is not evidence of API 36 system-image failure, KVM failure, emulator boot failure, or regression in the startup script. It is a test hermeticity defect caused by a job-level environment variable leaking into a fixture that intended to isolate the SDK.

The difference between local and CI behavior is explained by environment inheritance: when `ADB` is unset locally, the script falls back to the temporary SDK's mock binary and the test completes; under Actions, `ADB` is already exported and takes precedence.

## 3. Correction

The success fixture now explicitly sets:

```python
env["ADB"] = str(adb)
```

The fixture's mock ADB also appends each received argument vector to a temporary call log. After successful startup, the test asserts that the mock received all expected invocations:

- `version`
- `start-server`
- `get-state`
- `shell getprop sys.boot_completed`
- all three animation settings commands
- `devices -l`

This checks not just successful completion but that the test actually exercised the intended fake SDK binary. The existing success test remains time-bounded at 20 seconds. No increase to the test timeout is used to hide the failure.

## 4. Production behavior preserved

`scripts/start-api36-emulator.sh` is unchanged. In particular, this correction does not weaken or bypass:

- executable checks for `platform-tools/adb`, emulator and `avdmanager`;
- early emulator-process exit detection;
- 180-second ADB device-registration deadline;
- 600-second Android `sys.boot_completed` deadline;
- diagnostic output for ADB devices, emulator process and last 200 log lines;
- Pixel 6 / `system-images;android-36;google_apis;x86_64` configuration;
- KVM permissions setup or connected Android instrumentation tasks.

No Kotlin/Compose runtime, app features, Room schema, SQLCipher/Keystore integration, corpus signing policy, retrieval behavior, target SDK or emulator API level changed. App versionCode is 15 and versionName is `0.5.11-phase4c-api36-adb-test-isolation-fix`.

## 5. Verification

Verified in the authoring container:

- API 36 emulator setup regression suite: **6/6 passed** while the parent process deliberately had `ADB=/usr/bin/true` set, simulating a pre-existing job-level `ADB` variable. The success fixture overrides it with the temporary SDK mock and asserts all mock calls.
- Python syntax compilation for the changed test.
- Full static regression checks listed in the release validation record and checkpoint.

This environment does not provide the user's GitHub-hosted runner, Android SDK emulator runtime, or the Samsung device. Therefore actual hosted API 36 boot and connected instrumentation remain unverified until the new source is pushed and Actions completes those steps.

## 6. Files changed

- `scripts/test-api36-emulator-setup.py`: isolate ADB in the successful mock test and assert mock command coverage.
- `app/build.gradle.kts`: app versionCode/name bump only.
- `scripts/verify-app-module-compile-contract.py`: update expected version contract.
- `CHANGELOG.md`, `README.md`: record the correction.
- `docs/reports/PHASE4C_CI_HOTFIX_API36_ADB_TEST_ISOLATION_2026-11-08.md`: this report.
- `docs/checkpoints/Timeline_Agentique_Checkpoint_2026-11-08_phase4c-api36-adb-test-isolation-fix.md`: resume checkpoint.

No change to the production startup script or workflow configuration was necessary.
