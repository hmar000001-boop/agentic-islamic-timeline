# Phase 4C CI hotfix — API 36 QEMU host dependency

**Date:** 2026-11-08  
**Baseline:** `0.5.11-phase4c-api36-adb-test-isolation-fix`  
**Resulting source:** `0.5.12-phase4c-api36-libpulse-fix`  
**Scope:** GitHub Actions Linux emulator host dependency and fail-fast validation; no Android application behavior changed.

## 1. Hosted failure

The 2026-11-08 GitHub Actions log showed:

```text
/usr/local/lib/android/sdk/emulator/qemu/linux-x86_64/qemu-system-x86_64:
  error while loading shared libraries: libpulse.so.0:
  cannot open shared object file: No such file or directory
Process completed with exit code 127.
```

ADB was found and ran successfully. The emulator launcher then attempted to load its x86_64 QEMU child, and the Linux dynamic loader refused to start QEMU because the PulseAudio client shared object was absent. `-no-audio` is not sufficient to remove a binary's dynamic-link-time dependency: it disables emulator audio behavior but does not make a required ELF shared object optional.

## 2. Root cause

This failure is a host operating-system dependency issue on the GitHub Actions Ubuntu runner. It is separate from the previous fixes for ADB path discovery, the mock ADB environment leak, AVD creation, KVM permissions and Android boot waiting. The process fails before an Android guest exists, so changing the AVD, API image, ADB polling or guest boot timeout would not address it.

Ubuntu publishes `libpulse0` for the current 26.04 LTS suite as well as 24.04 LTS. It provides PulseAudio client libraries; installing this package does not require running a PulseAudio daemon. See the Ubuntu package index: https://packages.ubuntu.com/resolute/libpulse0.

## 3. Correction

### Workflow host dependency

`.github/workflows/android-ci.yml` now installs the required Ubuntu package before SDK/emulator use:

```bash
sudo apt-get update
sudo apt-get install --yes --no-install-recommends libpulse0
dpkg-query -W -f='${Status}\n' libpulse0 | grep -qx 'install ok installed'
```

The package is a runner dependency only. The app remains offline, and no audio feature or PulseAudio service has been added.

### Startup fail-fast preflight

`scripts/start-api36-emulator.sh` now verifies the expected x86_64 QEMU file and runs `ldd` against that exact executable before invoking the emulator. It parses unresolved shared objects (`not found`), lists them in the error, and exits before AVD creation or emulator launch. It also reports missing `ldd` or an inspection failure explicitly. This makes future host-library omissions actionable rather than waiting for QEMU to exit during startup.

The test fixture supplies a mocked QEMU binary and mocked `ldd`. An explicit failure test reports `libpulse.so.0` missing and asserts the emulator was never launched. The success test's mock linker output resolves the same shared object and then completes through the mocked ADB boot flow.

## 4. Production behavior preserved

The API 36 emulator remains `system-images;android-36;google_apis;x86_64`, named `timeline-ci-api36`, with Pixel 6 AVD profile. KVM rules, ADB selection, emulator flags, early-exit detection, 180-second device-registration limit and 600-second `sys.boot_completed` limit remain intact. No timeout was increased to conceal the loader failure.

No app runtime audio or network capability, Compose behavior, Room schema/migrations, SQLCipher/Keystore integration, corpus trust, retrieval behavior, target SDK or emulator API level changed. App versionCode is 16 and versionName is `0.5.12-phase4c-api36-libpulse-fix`.

## 5. Verification

Verified locally:

- API 36 emulator setup regression tests: **8/8 passed**, with a job-like inherited `ADB` environment variable present.
- Missing-QEMU-library test: detected `libpulse.so.0`, returned the actionable preflight error and confirmed the emulator executable was not launched.
- Successful mock startup: fake QEMU dependencies resolved, mock ADB reported registration and boot completion, and startup completed.
- Workflow contract: apt installation and package-installed verification for `libpulse0` present.
- Shell syntax, app compile/version contract, translations, built-in Kotlin/AGP, SDK metadata tests, Room v5/dense-index contracts, Phase 4C foundation, Gradle lock policy and secret scan passed.
- ZIP integrity, checksum and exact patch application against pristine 0.5.11 verified.

The authoring container does not have the GitHub-hosted runner, this project's installed Android SDK emulator or a KVM-backed host. Therefore it cannot prove that the real runner's QEMU process launches, that the API 36 guest reaches `sys.boot_completed=1`, or that connected instrumentation completes. Those remain explicit hosted CI verification gates.

## 6. Files changed

- `.github/workflows/android-ci.yml`: install and verify Ubuntu `libpulse0` before emulator use.
- `scripts/start-api36-emulator.sh`: validate QEMU executable and shared-library dependencies with `ldd`; fail with an explicit missing-library list.
- `scripts/test-api36-emulator-setup.py`: workflow package contract, mocked QEMU/ldd, missing library test, successful startup regression.
- `app/build.gradle.kts`: app versionCode/name bump only.
- `scripts/verify-app-module-compile-contract.py`: updated expected version.
- `CHANGELOG.md`, `README.md`, this report and the checkpoint: continuity records.

