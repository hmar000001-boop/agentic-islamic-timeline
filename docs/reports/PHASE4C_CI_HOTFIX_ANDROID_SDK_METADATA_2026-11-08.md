# Phase 4C CI hotfix — Android SDK Platform 37.0 metadata format

**Date:** 2026-11-08  
**Base:** `0.5.3-phase4c-compile-sdk37.0`  
**New source:** `0.5.4-phase4c-sdk-metadata-validation-fix`

## Actual CI failure

The SDK Manager installation step completed and installed the `platforms;android-37.0` package, Build Tools, emulator, and API 36 Google APIs system image. The next command, `scripts/verify-android-sdk-platform.py`, failed with:

```text
Android SDK Platform verification FAILED: SDK package android-37.0 reports an unexpected AndroidVersion.ApiLevel: '37.0'
```

The validator incorrectly expected `AndroidVersion.ApiLevel=37` and optionally a separate `AndroidVersion.MinorApiLevel=0`. The installed package's actual `source.properties` format is:

```properties
Pkg.Desc=Android SDK Platform 37.0
Pkg.Revision=1
AndroidVersion.ApiLevel=37.0
AndroidVersion.CodeName=
AndroidVersion.ExtensionLevel=22
AndroidVersion.IsBaseSdk=true
```

The dotted API value is the platform's real identity. The separate `AndroidVersion.MinorApiLevel` property is absent in this package.

## Fix

- Parse `AndroidVersion.ApiLevel` as a strict major/minor tuple, accepting `37.0` as the canonical installed package representation.
- Retain compatibility with a split representation (`ApiLevel=37` and `MinorApiLevel=0`) because SDK metadata consumers may expose it that way.
- Reject all versions except major 37/minor 0; reject malformed, contradictory dotted/split metadata, wrong platform directory, missing `android.jar`, missing `source.properties`, and SDK-root path escapes.
- Change test fixture to the exact metadata observed in the hosted runner, and add malformed-version and missing-metadata coverage. The suite now has 10 tests.
- Update application `versionName`, README, CHANGELOG, and this checkpoint to 0.5.4.

No changes to the SDK Manager package request, compileSdk, targetSdk, minSdk, Build Tools, emulator, Gradle/AGP/Kotlin versions, Room schema, or app behavior.

## Verification performed locally

- API 37.0 SDK verifier tests: **10/10 passed** using the exact GitHub-runner `source.properties` fixture.
- Compose/API compile SDK guard: all **15 modules** select API 37.0; CI still installs the `.0` package, `targetSdk` and emulator remain API 36.
- AGP built-in Kotlin migration tests: **11/11 passed**.
- Dependency locking/reproducibility guard: passed.
- Room v5 / Phase 4C encrypted corpus and dense-index migration guard: passed.
- Phase 4C SAF/lifecycle/embedding guard: passed.
- Gradle bootstrap executable test: passed.
- Secret scan: passed.
- Version catalog TOML, CI workflow YAML, and **23 XML** resources parsed successfully.

## Not run

The local authoring environment has no installed Android SDK or Gradle distribution. The real Android Gradle/KSP build, unit-test tasks, Room/SQLCipher instrumentation, assemble, lint, CycloneDX and emulator tests were not run here. The GitHub Actions SDK installation itself is already confirmed successful by the supplied log; a new hosted CI run is needed to verify this validator correction and expose any subsequent build issue.

## Continuation

Continue from this exact source tree and preserve Phases 1–4C. Do not restart architecture, downgrade compileSdk, alter targetSdk, or change the emulator lane to work around this metadata parser defect. Once the CI baseline is green, resume Phase 5 from the Phase 4C checkpoint.
