# Phase 4C CI hotfix — select and validate headless QEMU correctly

**Date:** 2026-11-08

**Baseline:** `0.5.12-phase4c-api36-libpulse-fix`

**Resulting source:** `0.5.13-phase4c-api36-headless-qemu-preflight-fix`

**Scope:** GitHub Actions API 36 Linux emulator startup preflight; no Android application behavior changed.


## 1. Hosted failure

The 0.5.12 hosted run stopped in `scripts/start-api36-emulator.sh` before AVD creation:

```text
Android emulator QEMU is missing required Linux host shared libraries:
  libQt6CoreAndroidEmu.so.6
  libQt6GuiAndroidEmu.so.6
  libQt6NetworkAndroidEmu.so.6
  libabseil_dll.so
  libandroid-emu-agents.so
  libandroid-emu-base-logging.so
  libandroid-emu-curl.so
  libandroid-emu-metrics.so
  libandroid-emu-protos.so
  libandroid-emu-tracing.so
  libandroid-webrtc.so
  libc++.so
  libemugl_common.so
  libglib2_linux-x86_64.so
  libprotobuf.so.28.4.0
  libtcmalloc_minimal.so.4
```

The list mixed GUI-only Qt libraries and Android Emulator SDK-owned libraries. Treating every one as an Ubuntu package dependency was incorrect.

## 2. Root cause

The CI invocation uses `-no-window`. Android Emulator's launcher parses `-no-window` as headless mode and selects the QEMU executable with the `-headless` suffix. The preflight in 0.5.12 instead inspected `qemu-system-x86_64`, the GUI backend.

The preflight also invoked `ldd` without the Android SDK's bundled library search directories. The Android Emulator launcher normally configures its engine to search the SDK `lib64` directory, the GLES backend selected by `-gpu swiftshader_indirect`, Vulkan ICD libraries, and the bundled `libstdc++` directory. For headless mode, it does not add the GUI Qt library directory. Running `ldd` without those paths therefore made SDK-owned shared objects appear unresolved even though the emulator launcher is designed to load them from the SDK installation.

This is a false-positive dependency check. It is distinct from the original missing `libpulse.so.0` host dependency and from ADB/AVD/guest-boot failures.

## 3. Source-level verification

The Android Emulator launcher source confirms:

- `-no-window` sets `isHeadless = true` (source around lines 655–663).
- The headless QEMU executable path uses `qemu-system-%s-headless` (around lines 1304–1317).
- The launcher adds `<emulator>/lib64`, the selected GLES backend, `vulkan`, and (on Linux unless system libraries are requested) bundled `libstdc++` directories to its search path (around lines 1339–1414).
- The bundled Qt search path is only added when the emulator is not headless (around lines 1123–1130).

Primary AOSP source: https://android.googlesource.com/platform/external/qemu/+/emu-master-dev/android/emulator/main-emulator.cpp

The currently published Linux emulator prebuilts include `qemu-system-x86_64-headless` alongside the GUI binary: https://android.googlesource.com/platform/prebuilts/android-emulator/+/refs/heads/main/linux-x86_64/qemu/linux-x86_64

## 4. Correction in 0.5.13

### Select the exact QEMU executable used by CI

`scripts/start-api36-emulator.sh` now validates:

```text
$ANDROID_HOME/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless
```

This matches the actual `-no-window` emulator invocation.

### Mirror the launcher search path for `ldd`

Before dependency inspection, the script builds a local `QEMU_LIBRARY_PATH` in the same order as the launcher for this job:

1. `$ANDROID_HOME/emulator/lib64`
2. `$ANDROID_HOME/emulator/lib64/gles_swiftshader`
3. `$ANDROID_HOME/emulator/lib64/vulkan`
4. `$ANDROID_HOME/emulator/lib64/libstdc++`
5. Existing host `LD_LIBRARY_PATH`, when present.

`ldd` receives this value for the inspection command only. The script does not globally overwrite the CI job's library path. It still fails closed if `ldd` reports any `not found` entries. The diagnostic now prints the exact search path and distinguishes an incomplete SDK emulator package from a missing Ubuntu host library.

The `libpulse0` runner dependency from 0.5.12 is retained defensively. It remains a host-only package; no PulseAudio service or app audio capability is added.

## 5. Regression coverage

`scripts/test-api36-emulator-setup.py` now has nine tests, including:

- The startup contract points at `qemu-system-x86_64-headless`.
- The `ldd` invocation receives the launcher-equivalent library search path.
- The headless preflight includes the `lib64`, SwiftShader GLES, Vulkan and bundled `libstdc++` paths, but not a GUI Qt path.
- A genuinely missing host library still stops startup before the emulator executable is launched.
- The successful mocked ADB startup continues to complete, with the existing ADB isolation and process cleanup checks.

## 6. Verification performed

- API 36 startup regression tests: **9/9 passed**.
- Translation tests: **6/6 passed**; English/Arabic resource parity verified across four Android resource modules.
- AGP built-in Kotlin policy tests: **11/11 passed**; policy verifier passed across 15 Android modules.
- Android SDK metadata fixture tests: **10/10 passed**.
- Room dense-index persistence contract: **8/8 passed**.
- App compile/version contract: **9/9 passed** (versionCode 17 / versionName 0.5.13).
- Room v5/schema/migration and Phase 4C foundation guards passed.
- Compose compile SDK guard passed: all 15 modules API 37.0; targetSdk and emulator remain API 36.
- Gradle dependency lock policy, Gradle bootstrap regression and secret scan passed.
- Shell syntax and resource/config parse checks passed.
- ZIP integrity, SHA-256 and patch reproduction against a clean 0.5.12 extraction passed.

The workspace has no installed copy of this project's Android SDK emulator, no KVM-backed host, and no GitHub-hosted runner. Consequently this increment does **not** claim that the actual headless QEMU binary has been launched, that the API 36 guest reaches `sys.boot_completed=1`, or that connected instrumentation has completed. Those remain Actions gates.

## 7. Files changed

- `scripts/start-api36-emulator.sh`: use the headless QEMU binary, mirror Android Emulator library paths for `ldd`, and emit actionable diagnostics.
- `scripts/test-api36-emulator-setup.py`: assert correct backend/library path selection and retain the missing-library and successful-startup regressions.
- `app/build.gradle.kts`: versionCode 17 and versionName `0.5.13-phase4c-api36-headless-qemu-preflight-fix`.
- `scripts/verify-app-module-compile-contract.py`: update expected application version.
- `CHANGELOG.md`, `README.md`, this report and the new checkpoint: continuity records.

No timeline/Canvas feature, authorization rule, data model, Room schema/migration, SQLCipher/Keystore behavior, corpus format/trust policy, retrieval ranking, network permission, target SDK, compile SDK or Android emulator API level was changed.
