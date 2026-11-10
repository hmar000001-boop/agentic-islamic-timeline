# Timeline Agentique — Phase 4C Android SDK metadata validator checkpoint

**Date:** 2026-11-08  
**Checkpoint:** `PHASE4C-CI-API37-METADATA-002`  
**Source:** `0.5.4-phase4c-sdk-metadata-validation-fix`  
**Project:** `/mnt/data/ta-work`  
**Library project:** `/Timeline_Agentique/`

## Resume rule

Continue from 0.5.4. Preserve the full Phase 1–4C application, Timeline/Canvas, agent runtime, authorization, encrypted persistence, corpus trust lifecycle, FTS5/RRF retrieval, corpus management UI, and encrypted vector-index foundation. Never start the project again from a blank template.

## Failure and exact cause

GitHub Actions successfully installed `platforms;android-37.0`. It then failed in `scripts/verify-android-sdk-platform.py` because the validator expected `AndroidVersion.ApiLevel=37`, while the actual SDK package reports `AndroidVersion.ApiLevel=37.0` and `AndroidVersion.ExtensionLevel=22`; it does not need a separate `AndroidVersion.MinorApiLevel` property.

## Implemented correction

The verifier parses dotted or split API metadata into a normalized `(major, minor)` pair and accepts only `(37, 0)`. A split optional minor property must agree with any dotted minor. The verifier still requires the exact `platforms/android-37.0` directory, non-empty `android.jar`, readable `source.properties`, and canonical path within the SDK `platforms` directory.

The tests use the actual runner metadata fixture and validate split-format compatibility, malformed/wrong versions, conflicts, missing metadata/artifacts, old package-name rejection, and symlink escape.

## Local checks

- SDK metadata verifier tests: 10/10.
- Compose/API 37.0 guard: pass (15 modules, targetSdk 36, API 36 emulator).
- Built-in Kotlin and typed AGP DSL: 11/11.
- Gradle reproducibility/dependency lock policy: pass.
- Room v5 / Phase 4C encrypted index migration guard: pass.
- Phase 4C lifecycle/SAF/UI integration guard: pass.
- Gradle launcher permission regression: pass.
- Secret scan: pass.
- Workflow YAML, dependency catalog TOML, 23 XML resources: parse successfully.

No real Gradle/KSP/Android unit/instrumentation build was run in this environment.

## Next action

Push 0.5.4 and rerun the identical Android CI workflow. The SDK install should remain successful and the verifier should accept `AndroidVersion.ApiLevel=37.0`. Diagnose only the next actual failure if one appears. After the Android build/tests are stable, resume Phase 5. The application runtime remains targetSdk 36 and uses an API 36 emulator test lane.
