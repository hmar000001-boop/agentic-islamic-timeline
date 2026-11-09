# Phase 5B — LiteRT-LM CPU inference runtime

**Source baseline:** `0.6.1-phase5b-litertlm-runtime` (versionCode 22)
**Date:** 2026-11-09
**Status:** implementation present; hosted Android build, native model load and Galaxy S24 FE validation pending.

## 1. Scope

Phase 5B continues from the Phase 5A Model Manager in 0.6.0. It adds a pinned Android inference dependency and adapters for text generation and text embeddings. It does not replace the Timeline/Canvas architecture, change the Room v5 schema, add network access, or activate a new default retrieval path.

The app's model artifacts still come from the user-initiated SAF importer in Phase 5A. The runtime accepts only a catalog model ID, resolves it through `ModelManager.resolveInstalledFile`, and relies on the Model Manager's file identity/size/digest checks before native access.

## 2. Dependency and toolchain

- Maven coordinate: `com.google.ai.edge.litertlm:litertlm-android:0.19.1`.
- Exact version pin in `gradle/libs.versions.toml`; no `latest.release` or dynamic version.
- Dependency is isolated to `:core:inference-runtime`.
- GitHub Actions selects the preinstalled JDK 21 for the Gradle process. Android application/library source and target compatibility remain Java 17; `targetSdk` remains 36; compile SDK remains API 37.0; API 36 remains the instrumentation emulator.
- Runtime adapters use the v0.19.1 Kotlin API shapes: `Engine`/`Conversation`, `sendMessageAsync(...): Flow<Message>`, `EmbeddingEngine`, `computeEmbeddingBatchAsync`, `EmbeddingEngineConfig`, `EmbeddingOptions` and CPU backend.

Primary upstream API references:

- [LiteRT-LM v0.19.1 Conversation.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.19.1/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
- [LiteRT-LM v0.19.1 Config.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.19.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
- [LiteRT-LM v0.19.1 EmbeddingEngine.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.19.1/kotlin/java/com/google/ai/edge/litertlm/EmbeddingEngine.kt)
- [LiteRT-LM v0.19.1 EmbeddingConfig.kt](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.19.1/kotlin/java/com/google/ai/edge/litertlm/EmbeddingConfig.kt)
- [LiteRT-LM Android API guide](https://developers.google.com/edge/litert-lm/android)

## 3. Generation runtime

`LiteRtCpuGenerationRuntime` owns one native engine on a dedicated single-thread executor. Engine construction, initialization, conversation operations, cancellation, close and unload are confined to that owner dispatcher. Model initialization is not performed from the UI thread.

Configuration and limits:

| Item | Value |
|---|---:|
| Backend | CPU only |
| CPU threads | 4 |
| Engine KV/context budget | 4,096 tokens |
| Max prompt | 16,384 characters |
| Max output | 256 tokens |
| Stream accumulation hard limit | 12,000 characters |
| Snapshot's last output | 4,000 characters, in memory only |
| Conversation lifetime | One request; no history persistence |

Every request creates a fresh `Conversation`. `tools` is empty and `automaticToolCalling=false`. A model-emitted tool call causes the request to fail closed; the runtime cannot invoke the application's `ToolExecutionGateway`, execute app actions or change application state. The runtime adds an instruction not to fabricate source citations, but prompt wording is not treated as a security boundary.

Cancellation stops the native process and closes the conversation. Native engine close is explicit. Initialization cancellation retains a reference to any successfully initialized candidate so it can be closed rather than orphaned. If native close fails, the engine reference is retained to allow a retry.

The Models screen exposes explicit CPU load, text smoke test and unload. The app does not load a model automatically on launch.

## 4. EmbeddingGemma adapter

`LiteRtEmbeddingTextModel` implements `LocalTextEmbeddingModel` from `core:knowledge-api` and is only opened around a bounded index or query operation. The app serializes embedding and generation operations with one application-level mutex; a multi-GB generation engine is unloaded before the embedding engine is opened.

Configuration:

| Item | Value |
|---|---:|
| Backend | CPU |
| Model | pinned EmbeddingGemma 2 Text 270M LiteRT-LM artifact |
| Output dimension | 768 |
| Normalization | requested from runtime and checked |
| Maximum text length | 8,192 characters |
| Batch size | 24 |

The LiteRT-LM embedding guide documents asymmetric prefixes:

- Indexed passage: `task: search result | text: {passage}`
- Search query: `task: search query | text: {query}`

The adapter trims the input before adding its prefix, enforces non-empty/maximum input, and rejects response count mismatch, dimension mismatch, NaN/infinity, or a normalized-vector L2 norm outside 0.90–1.10. It requests `EmbeddingOptions(normalize = true, outputSize = 768)`.

The existing `LocalEmbeddingIndexBuilder` remains responsible for staging and activating the encrypted dense index. It batches passages and stores quantized vectors in the existing SQLCipher Room schema. The adapter does not activate a retrieval route on its own. `TimelineApplication` exposes explicit index-build and query-vector methods, but query-time vector search is not yet wired as the default. FTS5/RRF remains authoritative and works without any model.

## 5. Resource and lifecycle policy

- `ModelManager` checks exact catalog entry/revision and verifies bytes at the private file boundary before use.
- Runtime preflight checks Android low-memory status, reported total RAM, minimum catalog RAM floor, 512 MiB private storage reserve and severe thermal status.
- The RAM thresholds are conservative preflight gates, not proof that LiteRT-LM can initialize. Advertised device RAM must not be treated as available process memory.
- Only one large inference engine is resident at a time; embedding engines are opened and closed per operation.
- Inference caches live under the app cache directory. Model artifacts remain under app-private `noBackupFilesDir` as implemented in Phase 5A.
- No Internet permission, cloud endpoint, remote model download, GPU/NPU dependency or telemetry was introduced.
- Model weight files are private/no-backup but are **not individually SQLCipher-encrypted**. Registry metadata and derived dense vectors remain in the SQLCipher database.

## 6. Current app boundary

| Capability | 0.6.1 status |
|---|---|
| Model import/verify/recovery | Phase 5A implemented |
| LiteRT-LM dependency pin | Implemented |
| CPU Gemma generation adapter | Implemented; native load unverified on device |
| No-tool conversation boundary | Implemented |
| EmbeddingGemma adapter | Implemented; golden-vector/device output unverified |
| Explicit dense index build service | Implemented |
| Query vector service | Implemented |
| Dense-vector retrieval as default | Not enabled; FTS5/RRF retained |
| Generation inside agent orchestration | Not enabled; smoke-test only |
| OCR/audio engines | Not implemented |
| S24 FE model benchmarks | Not run |

## 7. Validation performed for 0.6.1

- 31 static Phase 5B source/offline/lifecycle/CI contract checks.
- 11 inference policy tests compiled and executed in a standalone Kotlin 1.9 harness against narrow LiteRT-LM/Android/Room API shims. This verifies Kotlin source and policy behavior, not the official Maven AAR or Android Gradle toolchain.
- Three Android instrumentation API-shape tests are added to CI and compile against the actual pinned AAR when hosted Gradle executes.
- Existing project checks pass locally: translation parity, Model Manager contract, persistence instrumentation-source contract, app compile/version contract, built-in Kotlin/AGP contract, dependency lock policy, Room schema/dense index, Phase 4C fallback, API 37 SDK metadata, API 36 emulator setup and secret scan.
- XML, TOML and YAML parsing passed.

Not run in this authoring environment: actual Gradle 9.5.0/AGP/KSP/Compose build; official LiteRT-LM AAR compile; API 36 connected instrumentation; native `.litertlm` model initialization; S24 FE RAM/thermal/latency tests; model unload/reload soak tests; golden Arabic/English embeddings.

## 8. Phase 5C gates

1. Run the full GitHub Actions workflow on 0.6.1 and fix actual AAR/AGP/native compile or packaging issues.
2. Execute `LiteRtApiContractTest` on API 36.
3. Import the pinned Gemma 4 E2B artifact and verify initialization, prompt response, cancellation, unload, repeat-load and process restart on the Galaxy S24 FE.
4. Test E4B separately only if preflight and sustained memory/thermal measurements support it. No compatibility claim is inferred from E2B.
5. Import EmbeddingGemma and compare real vectors against upstream golden examples. Verify 768-d outputs, normalization and asymmetric Arabic/English query/document behavior.
6. Build a dense generation for a cleared/test corpus pack and verify staged activation, rollback, encrypted persistence and index fingerprint. Keep FTS5/RRF as fallback.
7. Wire query-time vector retrieval only after index/model fingerprints, ranking behavior and citation provenance tests pass.
8. Integrate generation into agent proposal generation only with source/evidence boundaries, no tool execution, and existing explicit authorization for state-changing actions.
9. Treat licensing/scholarly approval for actual Quran/Hadith/Tafsir/fiqh corpora as a separate release gate.
