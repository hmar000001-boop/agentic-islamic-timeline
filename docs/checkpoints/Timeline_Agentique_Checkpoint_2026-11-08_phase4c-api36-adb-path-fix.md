# Timeline Agentique — checkpoint Phase 4C / 0.5.9 API 36 adb path fix

**Date:** 2026-11-08
**Source version:** `0.5.9-phase4c-api36-adb-path-fix`
**Base:** `0.5.8-phase4c-english-resource-lint-fix`

## Current state

The project continues from the 0.5.8 Phase 4C source. The latest GitHub Actions failure occurred in `Start API 36 emulator` at bare `adb wait-for-device`:

```text
adb: command not found
exit code 127
```

SDK Manager had already been asked to install `platform-tools`; the workflow had not guaranteed the platform-tools directory on PATH. The shown log does not indicate that the emulator was launched or that Android boot failed.

## Applied fix

- SDK installation step checks `$ANDROID_HOME/platform-tools/adb` exists and is executable.
- Exports `$ANDROID_HOME/platform-tools` to later steps using `GITHUB_PATH`.
- Exports the exact `ADB` executable path using `GITHUB_ENV` and checks `adb version` immediately.
- Replaces the workflow's inline emulator launch with `scripts/start-api36-emulator.sh`.
- The script uses explicit SDK paths, recreates the same `timeline-ci-api36` Pixel 6 / Android 36 Google APIs x86_64 AVD, starts adb, and waits up to 180 seconds for device registration and 600 seconds for boot completion.
- Failure diagnostics include adb device enumeration, emulator process information and the last 200 lines of the emulator log.
- Adds 6 static/regression tests in `scripts/test-api36-emulator-setup.py` and runs them in CI before Gradle.
- Updates the existing Compose compile/runtime API guard to read the AVD name from the helper script.

## Version

- App versionCode: 13.
- App versionName: `0.5.9-phase4c-api36-adb-path-fix`.
- No application logic, database schema, encryption configuration, trust policy, or model/retrieval behavior changed.

## Verification status

The 6 new emulator setup regression tests passed, including missing-tool failure handling and a mocked successful startup flow. Bash syntax, app version contract, translation tests, API 37.0 SDK tests, Gradle bootstrap, secret scan, Room schema/dense-index checks, AGP built-in Kotlin checks, dependency reproducibility, Phase 4C guard and Compose API guard were also run. The authoring environment has no Android SDK or Gradle distribution, so no actual AVD boot, Gradle build, lint or Android instrumentation test was executed here.

## Next action

Push this exact 0.5.9 source and rerun GitHub Actions. Confirm the workflow passes SDK discovery, API 36 boot and connected instrumentation. If the emulator now fails after it is launched, inspect the new emulator-log tail and adb device listing. Do not restart Phase 4 or change application code for another infrastructure-only failure without evidence.
