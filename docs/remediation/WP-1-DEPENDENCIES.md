# WP-1 — dependency alignment and framework API compatibility

**Date:** 2026-11-10  
**Base:** `wp0-complete-2026-11-10`  
**Target checkpoint:** `wp1-dependencies-upgraded-2026-11-10`  
**Scope:** dependency version management and the minimal LangGraph4j 1.9 checkpoint-interface adapter compatibility required to compile.

## Version decisions

| Component | Phase 4 input | WP-1 target | Reason / maturity |
|---|---:|---:|---|
| Java | 21 | 21 | Keep the established toolchain. |
| Apache Maven distribution | 3.10.0 via custom repository bootstrap | 3.10.0 | Distribution version unchanged in WP-1; WP-2 hardens the custom launchers. |
| Spring Boot | 4.1.0 | 4.1.2 | Latest stable line verified 2026-11-10; do not select 4.2.0-RC2. |
| Spring AI | 2.0.1 | 2.0.2 | Stable 2.0 maintenance release; do not select 2.1.0-RC1. |
| LangGraph4j | 1.8.25 | 1.9.9 | Latest published 1.9 release verified 2026-11-10. 1.9 is the newer feature line; 1.8.x remains LTS. |
| LangChain4j stable modules | 1.22.0 | 1.23.0 | Current stable BOM/core/OpenAI release. |
| LangChain4j Agentic | 1.22.0-beta32 | 1.23.0-beta33 | BOM-managed experimental beta; remains explicitly non-stable. |

Sources checked on 2026-11-10:

- LangGraph4j release history: https://github.com/langgraph4j/langgraph4j/releases
- LangGraph4j 1.9 migration guide: https://langgraph4j.github.io/langgraph4j/1.9/whats-new-v1.9/
- LangGraph4j PostgreSQL saver V2 compatibility: https://langgraph4j.github.io/langgraph4j/1.9/core/postgres-saver/SAVER_V2/
- LangChain4j getting started and BOM policy: https://docs.langchain4j.dev/get-started/
- Spring Boot current stable versions: https://docs.spring.io/spring-boot/
- Spring AI 2.0.2 release: https://spring.io/blog/2026/10/23/spring-ai-2-1-0-RC1-available-now/

The root POM imports `spring-ai-bom`, `langchain4j-bom` and `langgraph4j-bom`. Module POMs omit dependency versions when those BOMs manage the artifact. LangChain4j's published BOM sets `langchain4j-agentic` to the `1.23.0-beta33` line; the module README retains the warning that Agentic APIs are experimental.

## LangGraph4j 1.9 compatibility choices

### Deliberately preserved

- Existing graph topology, nodes, edges, conditional routes and state channels.
- `GraphInput.args(...)` for new invocations and `GraphInput.resume(...)` for approval resumes; these are already the preferred 1.9 API.
- `CompileConfig.releaseThread(false)`. LangGraph4j 1.9 releases a successfully completed thread by default. The existing explicit `false` preserves the application's existing same-thread update/reinvoke lifecycle.
- The existing PostgreSQL `PostgresSaver` V1 implementation and its V1 schema.
- `ObjectStreamStateSerializer` and current checkpoint state type.

### Required compatibility adjustment

LangGraph4j 1.9 expands `BaseCheckpointSaver` with release-with-message, release-on-error, interruption registration, tag lookup and subgraph-saver registration APIs. The project's `SynchronizedMemorySaver` implements this interface directly while wrapping `MemorySaver`. It now forwards all expanded operations to the delegate under the wrapper's synchronized boundary. `SynchronizedMemorySaverCompatibilityTest` covers message-aware release/tag lookup, error release, interruption metadata forwarding and subgraph-saver registration. This is interface adaptation; it does not introduce new graph decisions or memory semantics.

### Why not switch to PostgreSQL V2 here

LangGraph4j recommends `PostgresSaverV2` for new applications, but documents that its schema is not compatible with V1. Its V2 schema stores live threads and released thread tags differently. Switching `PostgresSaver` to `PostgresSaverV2` in the same dependency-only work package would silently combine a library upgrade with a persistence migration and would violate the requested sequencing. V1 remains the compatibility choice for WP-1.

A later, separately reviewed checkpoint-migration package must: (1) back up and identify existing V1 data; (2) choose retain-V1 versus explicit V1-to-V2 migration; (3) migrate or provision schema without mixed-version use; (4) verify interruption/resume and normal-completion release semantics; (5) verify restore after process restart; and (6) document rollback. No implicit V1-to-V2 migration is claimed.

## Scope guard — behavior not changed

WP-1 does not intentionally change memory structures, lifecycle or utility policies; semantic consolidation; evidence authority; retrieval adapters/ranking; prompts; model provider settings; action policy/approval boundaries; graph topology or node behavior. The only Java source change is the `BaseCheckpointSaver` adapter forwarding described above.

## Validation record

- [x] Root and module Maven POMs parse as XML.
- [x] All dependency declarations managed by imported BOMs are versionless in child POMs.
- [x] No stale active dependency pins remain in the root/module POMs.
- [x] `SynchronizedMemorySaver` source matches the LangGraph4j 1.9.9 `BaseCheckpointSaver` method contract as inspected from the tagged upstream source.
- [x] Existing `.releaseThread(false)`, V1 `PostgresSaver`, `GraphInput.args()` and `GraphInput.resume()` remain in place.
- [x] Added focused `SynchronizedMemorySaverCompatibilityTest` coverage for the expanded 1.9 saver methods; test source is authored but not executed here.
- [ ] Full Maven reactor `./mvnw -B -ntp -Dapp.llm.provider=simulated clean verify`.
- [ ] Runtime / Spring application-context startup using the upgraded dependency graph.
- [ ] PostgreSQL-backed V1 integration run against LangGraph4j 1.9.9.
- [ ] End-to-end interruption → process restart → authenticated approval resume.
- [ ] Resolve and archive the effective dependency tree (`mvn dependency:tree`) on a network-enabled runner.
- [ ] Run a vulnerability scan against the resolved graph (for example, OWASP Dependency-Check or the organization-approved SCA tool); triage findings against exact resolved coordinates and reachability. Do not add Jackson or other transitive overrides based only on an artifact-page summary.
- [ ] Hosted GitHub Actions run on the WP-1 commit.

The unchecked items are not claimed as passed. The execution environment could not resolve the Maven distribution host (`downloads.apache.org`) during the prior wrapper attempt; therefore Maven/JUnit, dependency-tree/SCA and database verification must be completed on a network-enabled runner. No conclusion about the vulnerability status of the effective transitive graph is made in this checkpoint.
