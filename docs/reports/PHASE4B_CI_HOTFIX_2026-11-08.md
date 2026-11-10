# Phase 4B CI hotfix — Gradle launcher permission

**Date:** 2026-11-08  
**Source baseline:** `0.4.1-phase4b-publisher-trust-lifecycle`  
**Hotfix version:** `0.4.2-phase4b-gradle-bootstrap-permission-fix`

## Reported failure

GitHub Actions successfully downloaded the pinned Gradle 9.5.0 distribution, then failed at `./gradlew --version`:

```text
./gradlew: line 27: /home/runner/.gradle/agentic-wrapper/gradle-9.5.0/gradle-9.5.0/bin/gradle: Permission denied
Process completed with exit code 126.
```

This occurs before Gradle configuration, dependency resolution, Android compilation, KSP or tests.

## Root cause

The custom Linux/macOS `gradlew` downloads the official ZIP and previously used Python's `zipfile.extractall()`. Python's ZIP extraction does not restore the Unix executable mode recorded for the Gradle launcher. The extracted `bin/gradle` therefore existed but was not executable. The script subsequently attempted to execute that path directly, resulting in shell exit code 126.

## Fix

1. Restore POSIX mode bits from each entry's `external_attr` while extracting the checksum-verified official distribution.
2. Explicitly set `bin/gradle` to mode `0755` after extraction.
3. Before the `-x` cache test, repair an already-extracted launcher that exists but is not executable. This handles the exact failing runner cache without requiring another download.
4. Add a path traversal check while extracting distribution entries.
5. Add `scripts/test-gradle-bootstrap.py`. It builds a temporary Gradle cache with a valid launcher in mode `0644`, executes `./gradlew --version`, verifies successful launch, and confirms the executable bit was repaired.
6. Run that regression test in GitHub Actions before the real `./gradlew --version` distribution verification.

The official distribution SHA-256 remains unchanged and is still checked before extraction. The JDK setup, Android application sources, database schema and application runtime behavior are unchanged.

## Local verification actually run

- `bash -n gradlew`: passed.
- `python3 -m py_compile scripts/test-gradle-bootstrap.py`: passed.
- `python3 scripts/test-gradle-bootstrap.py`: passed; reproduces the mode-0644 cache failure condition.
- `python3 scripts/check-phase4-schema.py`: passed (Room v4 / 3→4 migration and lifecycle guards).
- `python3 scripts/secret-scan.py`: passed.
- GitHub Actions YAML parse and regression-step presence check: passed.
- ZIP integrity and SHA-256: passed after packaging.

## Not claimed

The actual Gradle distribution was not launched in this authoring environment, and no Android Gradle build, KSP task, unit-test task, emulator test or device test was run. The regression test uses a harmless fake launcher to verify the bootstrap permission repair deterministically. A rerun on GitHub Actions is still required to confirm the real downloaded distribution proceeds into Gradle.
