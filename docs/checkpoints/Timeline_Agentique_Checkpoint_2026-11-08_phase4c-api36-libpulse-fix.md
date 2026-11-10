# Timeline Agentique — checkpoint 0.5.12

**Timestamp:** 2026-11-08  
**Branch/workstream:** Phase 4C CI stabilization  
**Baseline:** `0.5.11-phase4c-api36-adb-test-isolation-fix`  
**Current source:** `0.5.12-phase4c-api36-libpulse-fix`  
**App versionCode:** 16

## Resume point

Continue from the complete 0.5.12 source tree. Do not restart Phases 1–4C. This increment addresses the current GitHub Actions API 36 emulator startup failure where QEMU cannot load the host library `libpulse.so.0`.

## Hosted failure and diagnosis

The 2026-11-08 Actions log showed ADB working, then:

```text
qemu-system-x86_64: error while loading shared libraries:
libpulse.so.0: cannot open shared object file: No such file or directory
exit code 127
```

The emulator launcher could not start its QEMU child because the Ubuntu runner lacked a required PulseAudio client shared library. This occurs before the virtual Android device boots. The `-no-audio` argument does not remove a dynamic ELF dependency. It is not an ADB, KVM, AVD image or Android guest boot timeout failure.

## Fix in 0.5.12

- GitHub Actions installs `libpulse0` with `apt-get` and verifies the package is installed before SDK/emulator work.
- The startup script verifies `emulator/qemu/linux-x86_64/qemu-system-x86_64` exists and runs `ldd` before launching QEMU. Missing shared objects are listed and cause an immediate actionable failure.
- Regression test includes a mocked QEMU and mocked `ldd`; a missing `libpulse.so.0` test confirms failure occurs before emulator launch.
- Successful mock startup continues to verify ADB isolation and successful virtual-device registration/boot state.
- Production API 36 image, Pixel 6 AVD, KVM, 180-second registration timeout and 600-second boot timeout are unchanged.

## Local verification

- API 36 emulator setup tests: **8/8 passed** with inherited `ADB` set.
- Explicit missing `libpulse.so.0` test passed; no emulator launch occurred.
- Mocked successful QEMU/ADB startup test passed.
- App version/compile contract, translation checks, AGP/built-in Kotlin tests, SDK metadata tests, Room v5/dense-index, Phase 4C, dependency locks and secret scan passed.
- Shell syntax and archive/patch/checksum verification passed.

## Remaining hosted validation

After pushing 0.5.12, Actions must confirm:

1. `libpulse0` is installed on the actual hosted image.
2. The real QEMU `ldd` preflight reports no unresolved libraries.
3. The API 36 Pixel 6 AVD process registers with ADB and reaches `sys.boot_completed=1`.
4. App/core connected instrumentation tests complete.

Do not mark real QEMU launch, emulator boot or connected instrumentation as passed before the hosted logs confirm each gate. The authoring environment has no GitHub-hosted runner or KVM-backed Android emulator runtime.

## Product/architecture continuity

Timeline, Canvas, authorization/HITL, encrypted Room v5, corpus publisher signing/trust, offline retrieval, Arabic/English resources, compileSdk 37.0, targetSdk 36 and Android-only no-backend design remain unchanged. No data migration or application behavior changed.
