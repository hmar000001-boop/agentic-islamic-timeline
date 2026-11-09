# Timeline Agentique checkpoint — Phase 5B LiteRT-LM runtime

**Date:** 2026-11-09
**Source version:** `0.6.1-phase5b-litertlm-runtime` (versionCode 22)
**Previous baseline:** `0.6.0-phase5a-model-manager-saf-import`
**Phase 4 CI:** user-reported green on 2026-11-09 for 0.5.16.

## Resume state

Continue from this exact 0.6.1 source tree. Do not restart or replace Phase 0–5A. The new module is `core/inference-runtime`; the app version is 22 / `0.6.1-phase5b-litertlm-runtime`.

## Implemented in 0.6.1

- `com.google.ai.edge.litertlm:litertlm-android:0.19.1` is pinned in the version catalog and isolated in `:core:inference-runtime`.
- JDK 21 is selected for the Gradle JVM in CI; Android source/target bytecode remains Java 17.
- `LiteRtCpuGenerationRuntime` loads a verified installed Gemma 4 catalog artifact on CPU only. It owns a single-thread native dispatcher, bounds KV cache to 4096 tokens, validates prompts, bounds output, creates a fresh conversation for each request, registers no tools, disables automatic tool calling, rejects tool-call messages, handles cancellation and explicitly closes conversations/engines.
- Native engine initialization cancellation is safe: a candidate is recorded before `initialize()`, and a successfully initialized handle is closed if the caller is cancelled before publishing it. A failed native close retains the engine reference for retry.
- `LiteRtEmbeddingTextModel` implements the existing `LocalTextEmbeddingModel` interface. It uses LiteRT-LM's asymmetric document/query prefixes, batch size 24, max 8192 input chars/tokens, normalized 768-dimensional output, and rejects malformed vectors. It closes engine after each bounded operation; cancellation after native init closes the engine before rethrowing.
- Models screen has explicit CPU load, smoke-test and unload actions.
- App service methods serialize generation and embeddings, build a staged dense index using existing Phase 4 builder/store and return one query vector. Retrieval is not auto-switched; FTS5/RRF remains active.
- No Internet permission/backend/network downloader/Room schema migration. Model weights remain private no-backup but are not individually SQLCipher-encrypted.

## Source files to know

- `core/inference-runtime/build.gradle.kts`
- `core/inference-runtime/src/main/java/app/timeline/islamic/core/inference/GenerationRuntimeContracts.kt`
- `core/inference-runtime/src/main/java/app/timeline/islamic/core/inference/LiteRtCpuGenerationRuntime.kt`
- `core/inference-runtime/src/main/java/app/timeline/islamic/core/inference/LiteRtEmbeddingTextModel.kt`
- `core/inference-runtime/src/test/java/app/timeline/islamic/core/inference/InferenceRuntimePolicyTest.kt`
- `core/inference-runtime/src/androidTest/java/app/timeline/islamic/core/inference/LiteRtApiContractTest.kt`
- `scripts/test-inference-runtime-contract.py`
- `.github/workflows/android-ci.yml`
- `docs/phase5/phase5b-litertlm-runtime.md`
- `docs/adr/ADR-006-litertlm-cpu-and-embedding-runtime.md`

## Local verification at checkpoint

- Phase 5B static contract: 31 checks passed.
- Inference policy methods: 11/11 in a standalone Kotlin 1.9 harness with API shims.
- API configuration-shape methods: 3/3 in the same harness with API shims.
- Existing project contracts all passed: translation, Model Manager, persistence Android-test dependencies/recovery, app compile/version, Gradle bootstrap, built-in Kotlin/AGP, dependency lock policy, Room v5/schema, dense index, Phase4C fallback, SDK metadata, API36 emulator setup, secret scan, and XML/TOML/YAML parsing.
- Kotlin harness is not the actual Gradle/AAR/Android compiler. No claim of hosted build, native load or S24 FE runtime pass.

## CI tasks added

- `:core:inference-runtime:testDebugUnitTest`
- `:core:inference-runtime:lint`
- `:core:inference-runtime:connectedDebugAndroidTest`
- `scripts/test-inference-runtime-contract.py`
- JDK 21 selection before Gradle tasks.

The next hosted run must execute the full existing workflow, especially the new inference-runtime unit, lint and API36 connected instrumentation tasks. Review official Gradle/KSP/Compose errors before claiming Phase 5B CI green.

## Remaining work / next steps

1. Push 0.6.1 and run the complete GitHub Actions workflow; fix real AAR/AGP/native packaging or Kotlin errors.
2. On API 36, run the three LiteRT API-shape instrumentation tests.
3. On Samsung Galaxy S24 FE, import pinned Gemma 4 E2B and test native load, smoke response, streaming, cancellation, unload, reload and process restart with RAM/thermal logging. Try E4B only if measured preflight/headroom allows.
4. Import EmbeddingGemma and verify 768d outputs, normalization, document/query prefixes and golden vectors on Arabic and English inputs.
5. Validate dense-index build, encrypted persistence, model/index fingerprint and evidence-preserving query ranking before enabling query-time vector search.
6. Only then wire model generation into proposal generation; model output must not execute tools or bypass `ToolExecutionGateway` and user approval.
7. OCR/audio and production corpus licensing/scholarly release gates remain separate.

## Versioning and artifact policy

- Next source version is 0.6.1; if further fixes are necessary, increment app version/versionCode and create a new checkpoint.
- Preserve previous ZIPs/checksums. Use the incremental patch from 0.6.0 for this tranche.
- Project Library root: `/Timeline_Agentique/`; Phase 5 folder exists. The previous uploads were rate-limited; verify the quota before attempting to upload the 0.6.1 package.
