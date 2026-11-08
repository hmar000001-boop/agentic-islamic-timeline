# Timeline Agentique — checkpoint 0.5.11

**Timestamp:** 2026-11-08  
**Branch/workstream:** Phase 4C CI stabilization  
**Baseline:** `0.5.10-phase4c-api36-emulator-test-fix`  
**Current source:** `0.5.11-phase4c-api36-adb-test-isolation-fix`  
**App versionCode:** 15

## Resume point

Continue from the complete 0.5.11 source tree. Do not restart Phases 1–4C. The latest change fixes the remaining timeout in the successful API 36 emulator setup regression test. Production emulator startup code and Android application behavior are unchanged.

## Exact hosted failure and root cause

The 2026-11-08 Actions log showed `test_startup_script_completes_with_mocked_sdk_tools` raising `subprocess.TimeoutExpired` after 20 seconds. Five other API 36 setup tests passed.

The workflow exports a job-wide `ADB` path to the real Android SDK using `GITHUB_ENV`. The test copied the environment and changed `ANDROID_HOME` to a temporary SDK containing a fake `platform-tools/adb`, but omitted `env["ADB"]`. Since the production startup script correctly gives the explicit `ADB` environment override precedence, the test launched the real runner ADB against a fake emulator process. That fake process cannot register with the real ADB server, so `get-state` did not succeed. The startup script remained within its correctly bounded 180-second device-registration polling loop, while the test's 20-second timeout fired first.

This was a test environment isolation issue, not proof of actual emulator/KVM/image failure. Raising the timeout would not correct it.

## Fix in 0.5.11

- Set `env["ADB"] = str(adb)` in the successful-startup fixture.
- Record all command arguments received by the fake ADB executable.
- Assert that version, server start, device registration, boot property, animation settings and device-list commands all reached the fake binary.
- Retain the 20-second regression-test timeout and the `finally` cleanup of the long-running mock emulator.
- Leave `scripts/start-api36-emulator.sh` unchanged.

## Local verification

- API 36 setup regression tests: **6/6 passed**, with parent environment explicitly set to `ADB=/usr/bin/true` to simulate the job-scoped GitHub Actions variable.
- Changed Python test passes `py_compile`.
- Version/compile contract and other existing Phase 4 static regression checks are expected to be rerun for this archive and must remain green.

## Remaining hosted validation

After pushing 0.5.11, Actions must confirm:

1. Platform-tools installation and SDK validation.
2. All six API 36 setup regression tests pass in the actual job environment.
3. API 36 system image and Pixel 6 AVD creation succeeds.
4. The real emulator registers with ADB and reaches `sys.boot_completed=1`.
5. Connected app/core instrumentation tasks complete.

Do not mark real API 36 boot or instrumentation as passed before the hosted logs confirm them. This authoring environment does not have the user's GitHub runner, installed Android SDK/emulator, or physical device.

## Product/architecture continuity

Timeline, Canvas, authorization/HITL, encrypted Room v5, corpus publisher signing/trust, offline retrieval, Arabic/English resources, compileSdk 37.0 / targetSdk 36 and Android-only no-backend design remain unchanged. No database migration or product feature behavior changed.
