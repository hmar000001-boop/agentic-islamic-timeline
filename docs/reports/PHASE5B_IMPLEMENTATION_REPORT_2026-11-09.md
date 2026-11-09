# Phase 5B implementation report — LiteRT-LM runtime

**Delivery:** `0.6.1-phase5b-litertlm-runtime`
**Application version:** versionCode `22`
**Base:** Phase 5A `0.6.0` (which preserves Phase 0–4 / 0.5.16)
**Date:** 2026-11-09
**Status:** implementation and local source-level verification completed; hosted CI, real native model execution and Galaxy S24 FE validation pending.

## Executive summary

Phase 5B adds a pinned Android LiteRT-LM runtime in a separate module, a CPU-only Gemma generation owner, and an EmbeddingGemma 2 adapter for the existing Phase 4 local embedding/index-builder contract. The Models screen now exposes explicit CPU load, smoke-test and unload actions. The application exposes service methods for an installed embedding model to build a staged dense index or return one query vector.

This is an incremental continuation of 0.6.0. It does not replace Phase 5A's SAF installer, catalog, private model storage, ownership markers, Room registry, recovery or preflight. It does not change Room schema v5, app permissions, the corpus trust model or authorization policy.

**Phase 5B does not yet mean the real model has been loaded on the target phone.** The exact API has been checked against upstream LiteRT-LM v0.19.1 source and compile/runtime API-shape tests have been added to the hosted CI workflow. This authoring environment does not contain the Android SDK/KVM emulator or a resolvable Gradle 9.5.0/Maven environment, so the official AAR compilation, native load and S24 FE measurements remain unverified.

## Implemented scope

### Runtime boundary

- Added `:core:inference-runtime` and isolated `litertlm-android:0.19.1` there.
- Added the exact version pin to `gradle/libs.versions.toml`; no dynamic version selector is used.
- Added a CI JDK 21 selection step for the runtime dependency family. Android Java source/target compatibility remains 17.
- All Android modules still use compile SDK 37.0; app target SDK remains 36; emulator remains API 36.
- No GPU or NPU backend is configured. CPU is the initial supported path pending target-device validation.

### Text generation — `LiteRtCpuGenerationRuntime`

- Resolves only a catalog ID through `ModelManager.resolveInstalledFile`; callers cannot supply an arbitrary model path.
- Revalidates exact model file length and digest at the Model Manager boundary immediately before native access.
- Owns the native Engine on one dedicated single-thread coroutine dispatcher/executor.
- Initializes in a background, non-cancellable section and closes a successfully initialized candidate if cancellation arrives before it is published.
- Keeps one engine active; explicitly unloads before switching models.
- Uses CPU with four threads and bounds the KV cache at 4,096 total context tokens.
- Validates non-empty prompt, max 16,384 characters, clamps output to 16–256 tokens, and caps accumulated stream text at 12,000 characters.
- Creates a fresh conversation per request (no persistent message history in this adapter).
- Registers no tools and sets `automaticToolCalling=false`. Any tool-call message is rejected; there is no callback from the model to app actions.
- Cancels native conversation work on coroutine cancellation and closes every per-request conversation.
- Retains an engine handle if native close fails, so a later unload can retry.
- Stores at most 4,000 characters of the explicit smoke-test output in an in-memory StateFlow. Prompt/output history is not written to Room or files.

### Embeddings — `LiteRtEmbeddingTextModel`

- Implements the existing `LocalTextEmbeddingModel` interface from `core:knowledge-api`.
- Uses the pinned catalog EmbeddingGemma 2 Text 270M `.litertlm` artifact, CPU backend, an 8,192 token input limit and at most 24 records per batch.
- Requests `normalize=true` and `outputSize=768`, matching the Phase 4 encrypted dense-index contract.
- Uses LiteRT-LM-specific asymmetric retrieval prefixes: `task: search result | text:` for indexed passages and `task: search query | text:` for queries. Inputs are trimmed before the prefix is prepended.
- Rejects empty/oversized inputs, wrong response counts, wrong dimension, NaN/infinity and L2 norm outside 0.90–1.10.
- Closes the embedding engine after each bounded index/query operation. Cancellation during initialization closes a completed native engine before cancellation is rethrown.

### App and UI wiring

- The Models screen has explicit load CPU, run smoke test and unload controls for installed generation models.
- `TimelineApplication` serializes generation and embedding work with one lifecycle mutex; it unloads generation before opening the embedding engine, avoiding co-resident multi-GB model weights.
- Added explicit app-service methods to build a dense index from an installed EmbeddingGemma artifact and to return one query vector.
- Dense index creation continues through the existing `LocalEmbeddingIndexBuilder` and encrypted `RoomDenseEmbeddingIndexStore`; it remains staged/activated through the existing store contract.
- No automatic retrieval switch was introduced. FTS5 lexical search and RRF remain the authoritative active path until real golden-vector, Arabic/English ranking and query routing tests pass.
- No generation call has been added to autonomous agent action execution. The exposed generation action is an explicit Models-screen smoke test only.

## Security / privacy checks

- No app `INTERNET` permission was added; source manifests and CI merged-manifest audit remain in force.
- No cloud inference, HTTP model download, remote endpoint, account, analytics or tracking code was added.
- Runtime tools are an empty list; automatic tool calling is disabled; tool-call messages fail closed.
- State-changing operations are not available through the model runtime. The existing `ToolExecutionGateway` and human-approval policy remain authoritative.
- Models still reside under app-private `noBackupFilesDir/on-device-models`; the weights themselves are **not individually SQLCipher-encrypted**. Registry metadata and dense embeddings remain inside SQLCipher Room.
- No Room schema migration was added; Room remains v5.
- No model weights are bundled inside the APK; user-initiated SAF import from the pinned catalog remains the installation route.

## Pinned upstream API and model evidence

The code was checked against the exact LiteRT-LM `v0.19.1` source files rather than a moving `main` API:

- `Conversation.kt`: text `sendMessageAsync` returns `Flow<Message>` and supports per-call `maxOutputToken`; `automaticToolCalling=false` returns tool-call messages instead of executing them.
- `Config.kt`: CPU backend `threadCount`, `EngineConfig.maxNumTokens`, and `ConversationConfig.tools` / `automaticToolCalling` / `maxOutputToken` fields.
- `EmbeddingEngine.kt`: `computeEmbeddingBatchAsync(contentsBatch, options)` and suspend `computeEmbeddingAsync` return embedding responses.
- `EmbeddingConfig.kt`: CPU-compatible `EmbeddingEngineConfig`, `maxInputLength`, `EmbeddingOptions.normalize` and `outputSize`.
- `Message.kt`: `Message.toString()` renders its `Contents`, used to accumulate streamed text; tool calls are exposed separately on `Message.toolCalls`.

The pinned Gemma 4 E2B/E4B LiteRT Community repositories publish `.litertlm` bundles and describe Android/LiteRT-LM execution. EmbeddingGemma 2's published model card specifies a native 768-dimensional embedding space; LiteRT-LM's own embedding guide gives the asymmetric search-result and search-query prefixes used here. These metadata/API checks are not a substitute for actual native engine/model initialization on the target device.

## Validation performed locally

### Executed source/contract suites

- `scripts/test-inference-runtime-contract.py`: **31 checks passed**.
- `scripts/test-string-translations.py`: **6/6**.
- `scripts/verify-string-translations.py`: all five app/feature resource modules have English and Arabic key/placeholder parity.
- `scripts/test-model-manager-contract.py`: passed.
- `scripts/test-persistence-androidtest-contract.py`: **14/14**.
- `scripts/verify-app-module-compile-contract.py`: **10/10**.
- `scripts/test-gradle-bootstrap.py`: passed.
- `scripts/test-built-in-kotlin.py`: **11/11**.
- `scripts/verify-built-in-kotlin.py`: all 18 Android modules.
- `scripts/verify-gradle-reproducibility.py`: all 20 catalog versions exact and lock policy consistent.
- `scripts/check-phase4-schema.py`: Room v5, migration, SQLCipher and entity/schema guards passed.
- `scripts/verify-room-dense-index-contract.py`: **8/8**.
- `scripts/check-phase4c-foundation.py`: passed; FTS5/RRF remains fallback with one explicit pinned local embedding provider.
- `scripts/test-android-sdk-platform.py`: **10/10**.
- `scripts/verify-compose-compile-sdk.py`: all 18 modules select API 37.0, target/emulator remain 36.
- `scripts/test-api36-emulator-setup.py`: **10/10**.
- `scripts/secret-scan.py`: passed.
- XML, TOML and GitHub Actions YAML parsing passed.

### Standalone Kotlin harness

The actual Phase 5B production Kotlin files and both inference test source files were compiled with local Kotlin 1.9.0 plus narrow Android/Room/LiteRT-LM/JUnit API shims. The policy test methods ran **11/11**; the three API configuration-shape test methods ran **3/3**. The LiteRT-LM call signatures were separately verified against the pinned upstream v0.19.1 Kotlin source.

This harness catches Kotlin syntax/type interactions at the adapter boundary but is **not** the official LiteRT-LM AAR, AndroidX, AGP/KSP, Compose, D8/R8 or Android native runtime. It is not counted as an Android build or connected instrumentation pass.

## Not yet verified / release gates

- Real Gradle 9.5.0 build with JDK 21, AGP 9.3.1, KSP and the official `litertlm-android:0.19.1` Maven AAR.
- Actual `:core:inference-runtime:testDebugUnitTest`, lint and API 36 connected instrumentation on GitHub Actions.
- Native Gemma 4 E2B/E4B initialization from the pinned model files, first response, token streaming, cancellation, reload, unload and process-restart behavior.
- Native EmbeddingGemma 2 output dimension/normalization and golden vectors for Arabic and English.
- Actual S24 FE memory headroom, latency, sustained thermal behavior, model unload, storage/cache and OOM behavior. Do not infer S24 FE compatibility from the published S26 Ultra benchmark.
- Dense query-time retrieval integration, model/index fingerprint enforcement, multilingual ranking evaluation and citation/evidence regression tests.
- Generation integration into the local agent's proposal generation (without tools or side effects).
- Offline OCR/audio foundations.
- Corpus source redistribution rights, edition/licensing validation and scholarly approval remain separate release gates.

## Next phase slice

Phase 5C should start by running the 0.6.1 hosted build and API 36 instrumentation, then performing model load and inference on the Galaxy S24 FE. Do not automatically promote E2B/E4B or the embedding index to a production-ready feature until the above gates are met.
