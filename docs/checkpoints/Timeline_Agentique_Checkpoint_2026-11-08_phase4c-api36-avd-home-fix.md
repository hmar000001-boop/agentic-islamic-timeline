# Timeline Agentique checkpoint — Phase 4C API 36 AVD home fix

**Checkpoint date:** 2026-11-08

**Current source:** `0.5.14-phase4c-api36-avd-home-fix`

**Application version:** versionCode 18, versionName `0.5.14-phase4c-api36-avd-home-fix`

**Baseline:** 0.5.13 `phase4c-api36-headless-qemu-preflight-fix`

## Current state

Continue from the exact 0.5.14 source archive. Do not restart Phase 4 or regenerate earlier implementation.

The latest GitHub Actions log progressed beyond the earlier ADB lookup, libpulse and headless-QEMU dependency failures. It reported that QEMU dependencies were verified and printed Android Emulator 37.2.13. Emulator startup then failed before ADB registration because the emulator could not find `timeline-ci-api36.ini` under `$HOME/.android/avd`.

## Changes in 0.5.14

- `scripts/start-api36-emulator.sh` now pins a canonical Android user/config root to `$HOME/.android` and AVD root to `$HOME/.android/avd`.
- Exports compatible modern/legacy values: `ANDROID_USER_HOME`, `ANDROID_EMULATOR_HOME`, `ANDROID_AVD_HOME`, `ANDROID_SDK_HOME`; clears inherited `ANDROID_PREFS_ROOT`.
- `avdmanager create avd` receives an explicit AVD data directory (`--path $HOME/.android/avd/timeline-ci-api36.avd`).
- Startup requires both `timeline-ci-api36.ini` and `timeline-ci-api36.avd/config.ini` before launching emulator.
- If files are missing, diagnostics list effective paths, AVD home contents and `avdmanager list avd`; emulator is not launched.
- `scripts/test-api36-emulator-setup.py` now injects conflicting environment path values, verifies manager and emulator see the same normalized homes, verifies created registration/config files, and performs a second run where a successful-but-no-op manager must be rejected before a second emulator launch. Prior headless-QEMU and ADB regression coverage remains.
- `app/build.gradle.kts` version bumped to 18 / 0.5.14.

## Verification done locally

- API 36 setup tests: 10/10 pass.
- Bash syntax and Python compilation for touched scripts pass.
- Full Phase 4C static guard suite and packaging checks are being run for this checkpoint.
- No real Android SDK/KVM emulator or GitHub-hosted execution is available in this environment; actual AVD creation, QEMU launch, API 36 guest boot and connected instrumentation must be confirmed by GitHub Actions.

## Next actions

1. Run all existing repository checks, especially API 36 setup, translations, AGP/Kotlin, SDK 37.0 metadata, Room v5/dense-index, app compile contract, Phase 4C foundation, Gradle lock policy and secret scan.
2. Package full source ZIP and patch against 0.5.13, verify exact patch reproduction and SHA-256.
3. Push 0.5.14 and inspect the next Actions output. The expected new checkpoint line is `Verified API 36 AVD registration and configuration under .../.android/avd.` before emulator launch.
4. If the postcondition fails, the script now emits exact resolved paths and `avdmanager list avd`; use that evidence to fix the remaining real SDK-tool behavior rather than assuming success.

## Product constraints remain unchanged

Native Android Kotlin/Compose; offline/local-first; no backend/accounts/analytics/ads/subscriptions; Sunni content across four madhhabs with no default madhhab; evidence-first RAG; strict approval for state-changing/high-impact actions; SQLCipher/Keystore and signed corpus-pack safeguards. Phase 4C remains active, and the immediate engineering task is to stabilize hosted emulator CI before moving to Phase 5.
