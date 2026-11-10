# Timeline Agentique — checkpoint 0.5.10

**Timestamp:** 2026-11-08  
**Branch/workstream:** Phase 4C CI stabilization  
**Baseline:** `0.5.9-phase4c-api36-adb-path-fix`  
**Current source:** `0.5.10-phase4c-api36-emulator-test-fix`  
**App versionCode:** 14

## Resume point

Continue from the complete 0.5.10 source tree. Do not restart Phases 1–4C. The current change fixes two false failures in the API 36 emulator setup regression suite; it does not alter the Android app runtime.

## Exact CI failure and fix

- Missing-ADB test: CI exports a valid `ADB` variable job-wide. The test inherited it and accidentally tested the following missing-emulator condition. It now explicitly points `ADB` at the missing `platform-tools/adb` under its temporary SDK.
- Successful mock startup: the fake emulator exited immediately, while production correctly rejects an emulator process that exits before ADB registration. The fake now stays alive, and the test terminates it in a `finally` cleanup path.

## Verified locally

- API 36 setup regression tests: 6/6 passed.
- Production startup script is unchanged.
- App versionCode 14 / versionName `0.5.10-phase4c-api36-emulator-test-fix`.

## Remaining hosted validation

The next GitHub Actions run must confirm:

1. SDK install and API 36 AVD creation.
2. Emulator process remains live, ADB sees the device and `sys.boot_completed=1`.
3. All connected instrumentation tasks complete.

No local Android emulator/SDK or GitHub-hosted runner is available in the authoring environment; do not mark these as passed until the hosted log confirms them.

## Product/architecture continuity

All Timeline, Canvas, authorization, encrypted Room v5, corpus signing/trust, offline retrieval, Arabic/English resources, compileSdk 37.0 / targetSdk 36 and no-backend architecture remain as in 0.5.9. No schema or app feature behaviour changed in this hotfix.
