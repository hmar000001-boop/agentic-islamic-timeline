# Verification, test strategy and known limitations

## Current verification status — WP-2 source revision (2026-11-10)

WP-1 updates the dependency BOMs and adapts the synchronized in-memory checkpoint wrapper to the LangGraph4j 1.9 interface. The complete Maven reactor and all upgraded runtime integrations remain unverified in this environment.

Verified after WP-1 source changes:

- OpenJDK 21 is available and remains the project toolchain.
- Every root and module Maven POM parses as XML.
- The imported BOM coordinates and target versions match the versions recorded in `docs/remediation/WP-1-DEPENDENCIES.md`.
- The custom `SynchronizedMemorySaver` methods were checked against the tagged LangGraph4j 1.9.9 `BaseCheckpointSaver` source contract; a local stub-based Java syntax check is recorded separately from a real dependency compilation.
- `GraphInput.args()`, `GraphInput.resume()`, the V1 `PostgresSaver` selection and `.releaseThread(false)` are preserved.
- Added `SynchronizedMemorySaverCompatibilityTest` for release-with-message, failure release, interruption metadata, tag lookup and subgraph-saver delegation; it still needs execution in the real Maven test run.
- No memory implementation, memory policy, prompt, action policy, graph node or edge was changed in WP-1.

Not verified after WP-1:

- Full Maven reactor `./mvnw -B -ntp -Dapp.llm.provider=simulated clean verify`.
- Real compilation against downloaded LangGraph4j 1.9.9, LangChain4j 1.23.0 / Agentic beta33, Spring Boot 4.1.2 and Spring AI 2.0.2 artifacts.
- JUnit suite and Spring application-context startup with the upgraded dependency graph.
- PostgreSQL V1 saver integration and authenticated approval resume across a process restart.
- A hosted GitHub Actions run on the WP-1 commit.

The previous Phase 4 source-level checks remain useful historical evidence, but they predate the WP-1 dependency refresh and do not substitute for validating the upgraded dependency graph. In this execution environment Maven bootstrap previously stopped before Maven startup because `downloads.apache.org` could not be resolved. A green hosted CI run is still required. A dependency-tree/SCA run is also outstanding; do not infer that the upgraded transitive dependency graph is vulnerability-free.

Do not interpret POM parsing, source inspection or a stub-based syntax check as proof that the upgraded dependency graph compiles or that integration tests pass.

WP-2 closes the source-level Phase 0 reproducibility work while preserving application behavior. The complete Maven reactor still requires a network-enabled hosted run.

Verified after WP-2 source changes:

- The custom POSIX and Windows launcher source pins Apache Maven 3.10.0 by HTTPS URL and validates the expected 128-character SHA-512 before using a downloaded archive. The Windows hand-off file contains only ASCII version/hash tokens; `cmd.exe` reconstructs the Maven path using the original user-home environment variable, including Unicode directory names.
- `pom.xml` now enforces Maven exactly at `[3.10.0]` and JDK at `[21,22)`.
- The offline POSIX bootstrap fixture passes cold-cache install, exact-version validation, one-download cache reuse and checksum-tampering rejection.
- The reproducible-build script performs two clean full-reactor verifications and compares packaged JAR/WAR SHA-256 values; the script is syntax-checked but cannot be executed without Maven.
- The test-inventory reporter is syntax-checked and exercised against a synthetic Surefire XML fixture.
- CI now includes Linux full-reactor/reproducibility checks, Windows full-reactor/bootstrap checks, and the PostgreSQL backend test job. Actions are pinned to reviewed commit SHAs.
- `.gitattributes` normalizes POSIX shell, Windows command and project configuration line endings.
- No Java source, graph behavior, database schema, memory policy, prompt, action policy or framework comparison semantics changed in WP-2.

Not verified after WP-2:

- Real Apache Maven bootstrap and full Maven reactor on Linux/Windows.
- Two clean real builds producing byte-identical packaged artifacts.
- JUnit/Spring integration execution on the upgraded WP-1 dependency graph.
- PostgreSQL test execution.
- Dependency tree convergence and CVE/SCA scan.
- Hosted GitHub Actions outcome.

The wrapper bootstrap test uses a locally generated fixture distribution and fake downloader; it proves launcher logic, not the authenticity of an actual remote download or Maven's compatibility with this project. The PowerShell launcher is added to Windows CI but was not executed locally because this environment has no PowerShell runtime.

## Historical Phase 2 tests added or retained

### Shared domain contracts

- Action effects must be explicitly declared; an empty effect list is rejected rather than assumed read-only.
- A non-`READ` action requires human approval by default, including a low-risk internal `WRITE`.
- External, high/critical-risk, destructive, irreversible, financial and external-sharing actions require approval.
- A low-risk internal `READ` action can execute without definition-level approval.
- Evidence freshness is explicit; model confidence cannot authorize a verified finish.
- Workspace admission is bounded; action invocations are immutable and require a subject and idempotency key.

### Primary LangGraph4j path

- Scripted model loop, catalogue behavior, fallback/fault paths and LLM stack selection.
- Cassette record/replay and replay misses.
- Planning subgraph and parallel retrieval fan-out/fan-in.
- Approval execution boundary, stale-authority revalidation, authenticated thread owner check, typed checkpointed approval request (action/risk/effects/arguments/rationale/subject/intention), interrupt/resume using a matching approval ID, stale-ID rejection and denial-without-execution.
- Workspace re-admission of tool output before reflection.
- Duplicate graph-thread creation and model-call accounting behavior.

### Experimental LangChain4j Agentic path

The module is intentionally unchanged in Phase 2. Its earlier source/test observations remain historical; the Phase 2 packaging environment did not run its JUnit suite. This module remains an experimental comparison implementation, not the primary runtime.

## Remaining architecture and production limitations

1. **Authentication boundary:** There is no REST/API security layer in this phase. `authenticatedSubjectId` must be passed only by a trusted caller derived from a verified security principal. Never trust a request-body or model-provided subject ID.
2. **Multi-instance coordination:** The fixed striped locks serialize same-thread operations only inside one JVM. They do not coordinate multiple application instances. Use a single writer until Phase 8 adds distributed concurrency controls.
3. **PostgreSQL checkpoint semantics:** PostgreSQL V1 saver wiring remains in place after the WP-1 LangGraph4j 1.9.9 upgrade, but is not locally integration-tested against the upgraded artifact. Persistence of approval interruption metadata and cross-process approval resume require an explicit integration test. Do not infer restart-safe approval resumption merely from checkpoint persistence. LangGraph4j V2 is available but uses an incompatible schema; a V2 migration is intentionally deferred to a separate compatibility task.
4. **Memory lifecycle and consolidation:** Typed durable-capable stores, lifecycle controls and deterministic semantic consolidation assessment are implemented. Automatic extraction from episodes, review UI, policy-driven forgetting, durable intention storage and a retention/deletion scheduler are not delivered. The graph checkpoint does not replace those stores.
5. **Retrieval:** The default remains Spring AI `SimpleVectorStore` with hash embeddings. Durable hybrid retrieval, metadata isolation, reranking, source freshness and retrieval evaluation remain later phases.
6. **LLM features:** Primary chat uses LangChain4j asynchronous `ChatModel` calls and typed Java DTO parsing/validation. JSON Schema constrained output, dynamic `ToolProvider`/MCP, typed streaming and model routing remain later work.
7. **Budgets:** Model-call accounting counts logical LangChain4j gateway invocations (including the structured-output repair call), not every underlying HTTP retry, token, monetary cost or provider-side operation.
8. **Action infrastructure:** The current catalogue and action gateway are deterministic local simulator components. Real inventory/order systems, transactional outbox, externally durable idempotency, audit retention and compensation are not present.
9. **Security hardening:** Prompt-injection isolation, tenant partitioning, secret management, retention controls, rate limits, deadlines and operational audit are incomplete.
10. **Recovery and learning:** Recovery is not yet a separately compiled isolated child graph with typed resolution compilation. Memory utility uses an explicit smoothed heuristic over durable evidence-backed per-use outcomes; it is not calibrated or paper-prescribed, and the current graph does not automatically produce meaningful-use/causal-attribution events.

## CI acceptance gates

Before accepting Phase 2 as integrated:

- Run `./mvnw -B -ntp clean verify` on a runner able to resolve Maven Central and the configured Apache Maven distribution.
- Run the PostgreSQL-backed LangGraph4j job using `compose.phase2.yml` or an equivalent PostgreSQL service.
- Confirm `RetailAgentApprovalResumeTest` pauses before executing the action, exposes the exact typed action request, rejects a different authenticated subject and stale approval ID, resumes successfully for the owner with the matching ID, and denies without invoking the gateway.
- Verify all Agentic module files remain unchanged from the Phase 1 delivery.

Before production:

- Implement the authenticated API boundary and derive customer identity only from verified credentials.
- Test checkpoint and approval resume through process restart and across application instances.
- Add distributed locking/optimistic concurrency, durable action idempotency and a transactional outbox for real side effects.
- Add durable memory/intention stores, tenant-isolated retrieval and audit.
- Calibrate uncertainty on held-out labelled tasks and run mechanism ablations with deterministic scenario data.


## CI failure follow-up (2026-11-06)

A GitHub Actions run reported two unresolved `CLARIFY` references in `RetailGraphNodes.java` (lines 305 and 633 in that checkout). Both the full `clean verify` job and PostgreSQL checkpoint test job failed at the same Java compilation step. The Phase 3 corrective source now defines the missing prompt constant at the node-operation boundary and adds a focused regression test for the clarification route. A local Maven rerun could not be performed because the wrapper distribution host `downloads.apache.org` is not resolvable from this environment; therefore this correction is not yet marked CI-verified.
