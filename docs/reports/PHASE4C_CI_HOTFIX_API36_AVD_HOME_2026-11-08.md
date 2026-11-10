# Phase 4C CI hotfix — pin API 36 AVD creation and lookup paths

**Date:** 2026-11-08

**Baseline:** `0.5.13-phase4c-api36-headless-qemu-preflight-fix`

**Resulting source:** `0.5.14-phase4c-api36-avd-home-fix`

**Scope:** GitHub Actions API 36 Android Virtual Device creation and emulator discovery. No application runtime behavior changed.

## 1. Hosted failure

The 0.5.13 hosted run passed the headless QEMU shared-library preflight, printed the Android Emulator 37.2.13 version, created/attempted to create the Pixel 6 AVD, and started the emulator process. The process exited before registering with ADB:

```text
ERROR | Unknown AVD name [timeline-ci-api36], use -list-avds to see valid list.
ERROR | HOME is defined but there is no file timeline-ci-api36.ini in $HOME/.android/avd
```

This failure occurs before guest boot. It is not a QEMU loader error, ADB PATH error, KVM permission error, or Android `sys.boot_completed` timeout.

## 2. Root-cause analysis and uncertainty

`avdmanager` and the emulator resolve AVD registration through Android user/AVD configuration directories. Modern SDK tooling uses `ANDROID_USER_HOME` and `ANDROID_AVD_HOME`; older SDK tooling may use `ANDROID_SDK_HOME` as the parent under which `.android` is created. The emulator has its own AVD search order. The prior script relied on inherited/default environment values, invoked `avdmanager create avd`, and immediately launched the emulator without checking where the root AVD registration file or data `config.ini` had been written.

The supplied hosted log proves that `timeline-ci-api36.ini` was not present in the emulator's selected `$HOME/.android/avd` lookup directory. It does not include an `avdmanager list avd` result or the path chosen by `avdmanager`, so the exact alternate location cannot be established from this log alone. The correction therefore makes the location deterministic instead of asserting a guessed historical path mismatch.

Android's documented variables and SDK CLI contracts:

- Environment variables: https://developer.android.com/tools/variables
- `avdmanager` CLI, including `create avd --path`: https://developer.android.com/tools/avdmanager
- SDK tooling AVD manager implementation: https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-master-dev/sdklib/src/main/java/com/android/sdklib/tool/AvdManagerCli.java
- Emulator AVD search implementation: https://android.googlesource.com/platform/external/qemu/+/emu-35-3-release/android/emulator/main-emulator.cpp

## 3. Correction in 0.5.14

### Canonicalize all compatible Android home variables

Before invoking either `avdmanager` or the emulator, the startup script now exports:

```text
ANDROID_USER_HOME=$HOME/.android
ANDROID_EMULATOR_HOME=$HOME/.android
ANDROID_AVD_HOME=$HOME/.android/avd
ANDROID_SDK_HOME=$HOME
```

It unsets deprecated `ANDROID_PREFS_ROOT` so an inherited alternate preferences root cannot redirect older tooling. All these values are chosen to resolve to the same `.android` and `.android/avd` roots under the current CI runner's `HOME`.

### Specify the AVD content path explicitly

The `avdmanager create avd` command now receives:

```text
--path $HOME/.android/avd/timeline-ci-api36.avd
```

### Verify the complete AVD before launch

After `avdmanager` exits, the script requires both non-empty files:

```text
$HOME/.android/avd/timeline-ci-api36.ini
$HOME/.android/avd/timeline-ci-api36.avd/config.ini
```

If either is absent, startup fails before launching QEMU and prints the canonical paths, directory listing, and `avdmanager list avd` output. The emulator is started only after both registration and content configuration exist.

## 4. Regression coverage

`scripts/test-api36-emulator-setup.py` now verifies:

- Compatible values are exported for modern and legacy SDK/Emulator tooling.
- A deliberately conflicting set of inherited `ANDROID_USER_HOME`, `ANDROID_EMULATOR_HOME`, `ANDROID_AVD_HOME`, `ANDROID_SDK_HOME`, and `ANDROID_PREFS_ROOT` values is normalized.
- The mocked `avdmanager` receives the canonical variables and explicit `--path`, and creates the root `.ini` plus AVD `config.ini` at the expected paths.
- The mock emulator receives the exact same canonical variables and launch target.
- QEMU headless dependency preflight, missing-host-library behavior, ADB executable isolation, emulator process lifetime, and Android boot success contracts remain covered.

The test suite has **10 tests**.

## 5. Verification performed

- API 36 emulator setup regression suite: **10/10 passed**.
- Translation unit tests and English/Arabic parity verifier passed.
- AGP built-in Kotlin policy tests and migration verifier passed.
- Android SDK API 37.0 metadata fixture tests passed.
- App compile/version contract passed for versionCode 18 / versionName `0.5.14-phase4c-api36-avd-home-fix`.
- Room v5 schema/migration checks, dense-index persistence contract, and Phase 4C foundation checks passed.
- Compose compile SDK/target SDK split, Gradle dependency-lock policy, Gradle bootstrap tests, and secret scan passed.
- Shell syntax and XML/TOML/YAML configuration parsing passed.
- Archive integrity, SHA-256, and patch reproduction against a clean 0.5.13 archive passed.

The current environment has no installed Android SDK or KVM-backed Linux emulator, so it cannot verify the behavior of the real installed `avdmanager` binary, actual QEMU launch, API 36 guest boot, or connected instrumentation. These remain hosted GitHub Actions verification gates. The preflight no longer assumes AVD creation succeeded: it checks the expected output files and will provide targeted diagnostics if the real manager still writes elsewhere or fails.

## 6. Files changed

- `scripts/start-api36-emulator.sh`: canonical Android configuration roots, explicit AVD content directory, pre-launch registration/config validation, actionable AVD diagnostics.
- `scripts/test-api36-emulator-setup.py`: conflicting-environment and shared-location regression assertions.
- `app/build.gradle.kts`: versionCode 18 and versionName `0.5.14-phase4c-api36-avd-home-fix`.
- `scripts/verify-app-module-compile-contract.py`: expected version update.
- `CHANGELOG.md`, `README.md`, this report, and the new checkpoint.

No Timeline/Canvas feature, authorization policy, Room schema/migration, SQLCipher/Keystore behavior, corpus format/trust policy, retrieval ranking, network permission, compile SDK, target SDK, or emulator API level was changed.
