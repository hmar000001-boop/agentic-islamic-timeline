# Timeline Agentique — checkpoint 0.5.13

**Timestamp:** 2026-11-08

**Workstream:** Phase 4C CI stabilization

**Baseline:** `0.5.12-phase4c-api36-libpulse-fix`

**Current source:** `0.5.13-phase4c-api36-headless-qemu-preflight-fix`

**App versionCode:** 17


## Resume point

Continue from the complete 0.5.13 source tree. Do not restart Phases 1–4C. The current task is API 36 emulator startup stabilization in GitHub Actions.

## Latest reported hosted failure

The 0.5.12 workflow reached the QEMU preflight, which listed many SDK-owned Qt and Android Emulator libraries as missing (`libQt6*.so.6`, `libandroid-emu-*.so`, `libc++.so`, `libprotobuf.so.*`, etc.) and exited before AVD creation.

## Deep root cause

The workflow starts the emulator with `-no-window`. In Android Emulator's launcher source, this flag sets headless mode and selects `qemu-system-x86_64-headless`; it does not select `qemu-system-x86_64` (GUI backend). The 0.5.12 preflight inspected the GUI binary. It also called `ldd` without the SDK library search directories that the launcher adds for the selected backend. Consequently the check reported bundled SDK libraries as missing, including Qt libraries that are not used in headless mode.

Primary source used for diagnosis: `android/emulator/main-emulator.cpp` in AOSP external/qemu. The source shows the `-no-window` headless flag parsing, headless backend filename, SDK `lib64` / SwiftShader / Vulkan / bundled libstdc++ search paths, and that Qt paths are configured only for non-headless operation.

## Fix implemented in 0.5.13

- Preflight now checks `emulator/qemu/linux-x86_64/qemu-system-x86_64-headless`.
- Constructs `QEMU_LIBRARY_PATH` from the Android Emulator launcher's Linux library directories: SDK `emulator/lib64`, `gles_swiftshader`, `vulkan`, and `libstdc++`; then appends any existing host `LD_LIBRARY_PATH`.
- Runs `ldd` against the headless QEMU binary with that search path supplied only to the inspection process.
- Does not add the GUI `lib64/qt` directory to headless dependency checks.
- Preserves fail-closed handling of actual unresolved system libraries and prints the complete search path for diagnosis.
- Keeps the existing `libpulse0` host package from 0.5.12 as a defensive runtime dependency.
- Preserves the API 36 Google APIs x86_64 image, Pixel 6 AVD, KVM setup, SwiftShader, ADB registration deadline (180 seconds) and Android boot deadline (600 seconds).

## Verification completed locally

- API 36 emulator startup tests: **9/9 passed**.
- Translation tests: **6/6 passed** and resource parity verifier passed.
- AGP/built-in Kotlin tests: **11/11 passed**; all 15 modules verified.
- SDK metadata tests: **10/10 passed**.
- Room dense index contract: **8/8 passed**.
- App compile/version contract: **9/9 passed**.
- Phase 4 Room v5 schema/migration, Phase 4C foundation, Compose API 37.0 / target API 36, dependency lock, Gradle bootstrap and secret scan verifiers passed.
- Shell syntax passed; ZIP and checksum validated; patch reproduced the exact 0.5.13 source when applied to a clean 0.5.12 extraction.

## Remaining hosted gates

After pushing 0.5.13, confirm in GitHub Actions:

1. `ldd` against `qemu-system-x86_64-headless` reports no unresolved libraries using the SDK library search paths.
2. The emulator process remains alive and registers with ADB.
3. Android API 36 reaches `sys.boot_completed=1`.
4. `:app:connectedOfflineSideloadDebugAndroidTest`, `:core:persistence:connectedDebugAndroidTest`, `:core:corpus:connectedDebugAndroidTest`, and `:core:retrieval:connectedDebugAndroidTest` complete.

The authoring container has no installed Android SDK emulator package or KVM-backed runner. Do not claim these hosted checks passed until the user supplies the Actions result.

## Product continuity

The production Android-only Islamic daily Timeline/Canvas application direction remains unchanged: offline/no backend, Sunni with all four madhhabs and no default madhhab, Arabic/English, evidence-first local corpus/retrieval, human approval boundaries, SQLCipher Room v5 and Keystore-protected key management. No Phase 1–4C application runtime behavior, database schema, encryption, source trust, corpus lifecycle or retrieval behavior was modified by this CI correction.
