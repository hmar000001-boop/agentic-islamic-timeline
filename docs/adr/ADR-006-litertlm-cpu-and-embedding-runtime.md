# ADR-006 — Pinned LiteRT-LM CPU runtime and local embedding adapter

- **Status:** Accepted for Phase 5B implementation; hosted/device validation pending.
- **Date:** 2026-11-09
- **Baseline:** `0.6.1-phase5b-litertlm-runtime`

## Context

Phase 5A can install and verify `.litertlm` artifacts, but it does not execute them. Phase 4 already defines `LocalTextEmbeddingModel`, a bounded embedding-index builder and encrypted dense-vector storage. The application must remain offline and functional with no model installed. A model must not autonomously invoke tools or commit application state.

## Decision

1. Pin `com.google.ai.edge.litertlm:litertlm-android:0.19.1`; no dynamic version selector.
2. Isolate the AAR/native runtime in `:core:inference-runtime` rather than leaking it across app modules.
3. Run generation and embedding on CPU first. GPU/NPU support is deferred until separate per-device stability and native dependency validation.
4. Run generation engine/conversation operations on one dedicated single-thread owner dispatcher. Use one-shot conversations; no persistent history in this adapter.
5. Set empty tools and `automaticToolCalling=false`; reject model tool-call messages. Application actions remain exclusively behind the existing `ToolExecutionGateway` and approval policy.
6. Cap the generation KV cache at 4,096 tokens and output at 256 tokens. Bound prompts and accumulated output, and close native handles on cancellation/unload.
7. Implement EmbeddingGemma 2 through `LocalTextEmbeddingModel`, use the LiteRT-LM-specific asymmetric retrieval prefixes, 24-item batches, normalized 768-dimensional vectors, and strict result validation.
8. Serialize generation and embedding at the app boundary. Do not keep both large model engines resident together.
9. Preserve FTS5/RRF as the active retrieval route until golden-vector, multilingual ranking, evidence-provenance and device validation pass.
10. Select JDK 21 for the Gradle runtime in CI; keep Android Java bytecode targets at 17, `targetSdk` 36 and the API 36 emulator.

## Consequences

- The Android runtime dependency and native ABI packaging are isolated and version-pinned.
- No Internet permission, cloud inference, GPU/NPU path, Room schema migration or automatic agent tool call is introduced.
- Runtime initialization can take several seconds and is explicitly triggered by the user, not app launch.
- CPU-only inference avoids a first release dependence on device-specific GPU delegate/native libraries but may be slower.
- E2B/E4B memory, model compatibility, cancellation/reload and S24 FE sustained performance remain empirical gates.
- Model weights remain app-private/no-backup files but not individually SQLCipher-encrypted.
- Dense indexing can be invoked explicitly but vector retrieval remains disabled by default.

## Rejected alternatives

- `latest.release`: rejected because builds must be reproducible and API changes visible.
- GPU/NPU as initial default: rejected pending Galaxy S24 FE specific delegate/native library stability tests.
- Integrating runtime directly into `app`: rejected because it increases the compile/runtime dependency surface and couples Compose/navigation to native code.
- Automatic tool execution: prohibited by the app's existing authorization architecture.
- Automatic vector routing on successful model import: rejected because file validity does not prove embedding compatibility or search quality.
