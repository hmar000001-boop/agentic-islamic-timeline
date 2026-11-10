# Retail Agent — CoALA / Fan & Lan mechanism study

A single Maven reactor containing two independent implementations of the same retail-agent example:

- **`retail-agent-langgraph4j`** — the primary LangGraph4j orchestration path, now using LangChain4j for production chat and the shared Java core.
- **`retail-agent-langchain4j`** — a separate LangChain4j Agentic implementation using a custom `Planner` and the same shared Java policies.
- **`retail-agent-core`** — framework-neutral domain objects and control policies shared by both implementations.

The purpose is to study the *same application semantics* through two orchestration approaches, not to claim that either framework implements CoALA or Fan & Lan automatically.

## Requirements

- JDK 21
- Repository Maven bootstrap launcher (pins Apache Maven 3.10.0 and verifies its Apache-published SHA-512; no system Maven installation is required)
- Network access on the first build to download Maven and resolve project dependencies

The repository includes custom POSIX (`mvnw`) and Windows (`mvnw.cmd`) bootstrap launchers. They download the pinned Apache Maven binary distribution into the Maven user cache, verify the distribution with the Apache-published SHA-512 checksum, and then invoke the build. They are **not** the Apache Maven Wrapper implementation and do not use a wrapper JAR. The current packaging environment cannot resolve `downloads.apache.org`, so a real Maven execution remains an external acceptance gate; the bootstrap logic is separately exercised with a local fixture archive.

## Build and tests

From this directory:

```bash
./mvnw -B -ntp clean verify
```

Exercise the bootstrap logic offline with a generated fixture archive:

```bash
./scripts/test-wrapper-bootstrap.sh
```

Run the full clean verification twice and compare packaged JAR/WAR hashes:

```bash
./scripts/verify-reproducible-build.sh
```

Run one module and its shared dependency:

```bash
./mvnw -B -ntp -pl retail-agent-langchain4j -am test
./mvnw -B -ntp -pl retail-agent-langgraph4j -am test
```

Run the LangChain4j Agentic demo with its deterministic simulator:

```bash
./mvnw -pl retail-agent-langchain4j -am exec:java \
  -Dexec.mainClass=com.example.retail.langchain.RetailAgenticApplication
```

The module pins the Maven Exec plugin in its build so the main class is launched with the reactor dependency classpath.

Run the LangChain4j implementation against OpenAI:

```bash
OPENAI_API_KEY=... RETAIL_LLM_PROVIDER=openai \
  ./mvnw -pl retail-agent-langchain4j -am exec:java \
  -Dexec.mainClass=com.example.retail.langchain.RetailAgenticApplication
```

`OPENAI_MODEL` optionally overrides the default model name (`gpt-4o-mini`). The simulated provider is the default and needs no key.

The LangGraph4j module remains a Spring Boot application. Its deterministic provider and in-memory checkpoint saver are selected by default in `application.yml`; tests exercise the graph directly. Start its application context with:

```bash
./mvnw -pl retail-agent-langgraph4j -am spring-boot:run
```

For live OpenAI chat calls, set `APP_LLM_PROVIDER=real` and `OPENAI_API_KEY`; LangChain4j is the provider adapter. Scripted/replay/fault injection remain available for deterministic tests. Cassette recording is opt-in with `APP_LLM_RECORD=true` and should be treated as sensitive local data. See [`retail-agent-langgraph4j/README.md`](retail-agent-langgraph4j/README.md).

## Example task

> Find waterproof trail-running shoes under $150 and recommend only an item whose current stock is verified.

The catalogue includes:

- `p-103`, Peak Trail GTX — waterproof trail-running shoe, $149.00, 4 units in stock.
- `p-101`, Trail Runner X — trail-running shoe, $89.00, 12 units in stock, but its catalogue description does not establish waterproofing.

A search result alone is not completion evidence. The Java verifier requires a matching product, current price/stock observation, positive stock, and compliance with the parsed request constraints.

## Control flow

Both implementations separate the model's **proposal/interpretation authority** from runtime **transition authority**.

1. Retrieve episodic, semantic, validated procedural-memory and catalogue candidates in parallel.
2. Compete for bounded workspace admission; record broadcast recipients.
3. Ask the LLM for candidate actions (proposal only).
4. Independently evaluate the proposals using deterministic Java rules.
5. Select one authorized action, reject all candidates and replan, or terminate safely.
6. Execute only a whitelisted grounding action.
7. Incorporate the observation; an LLM reflection is advisory, while Java verifies completion.
8. Continue, recover in a bounded typed substate, learn from the outcome, or answer.

The **inner planning loop** is distinct from the **outer decision cycle**. Rejection can return to retrieval/proposal before executing an action. After an action, the observation can start another decision cycle. LangGraph4j trace events include `cycle` and `plan` coordinates so repeated visits remain distinguishable even if the state channel removes duplicate strings.

## What is shared and what is intentionally different

Shared:

- Retail domain and deterministic catalogue source of truth.
- Proposal record, independent evaluator, evidence verifier.
- Intention/governor model, uncertainty metadata, resource limits.
- Adaptive candidate selection and bounded workspace policies. The primary LangGraph4j path now derives memory utility from durable, per-use evidence attributions; semantic consolidation assessments detect duplicates and contradictions without auto-promoting candidates. The frozen experimental Agentic module retains its original memory path.
- Typed recovery record and policy.

Resource ceilings are aligned through `AgentLimits` defaults: 3 outer action cycles, 3 planning rejections, 4 failures, 3 tool calls, 8 model calls, 2 recovery attempts, 180 memory tokens and a 260-token workspace. LangGraph4j also has a compile-time recursion ceiling derived from its application limits as a final guard.

Different:

- LangGraph4j owns graph state and node/edge transitions. `Proposal` and the core state records are serializable because LangGraph4j snapshots state between nodes.
- LangChain4j Agentic owns the custom `Planner` and `AgenticScope`. Only JSON-friendly JDK values are written into scope; the planner cursor is explicit and `executionState()` / `restoreExecutionState()` support planner-state restoration.
- The LangGraph4j retrieval node uses Spring AI's vector store and local hash embeddings by default; the LangChain4j retrieval adapter uses deterministic lexical catalogue search. The explicit `search_catalog` grounding action uses the shared deterministic lexical catalogue search in both. **Initial memory retrieval is therefore not yet a controlled variable in a rigorous framework benchmark.** Use the same retriever adapter in both before drawing performance conclusions.
- LangGraph4j includes scripted, record/replay, and fault-injection model infrastructure. LangChain4j currently has a scripted `ChatModel` for deterministic control-flow tests; matching cassette and fault-injection parity is a follow-up task.

## Paper alignment and scope

Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/FAN_LAN_MECHANISM_MATRIX.md`](docs/FAN_LAN_MECHANISM_MATRIX.md) before interpreting the names in code.

- CoALA is the architecture-level framework: working and long-term memory, internal/external actions, and a repeated decision procedure with planning and execution.
- Fan & Lan provide a mechanism-level review and propose residual control-coupling hypotheses B1, B2, B3, B4 and B6. They do **not** prescribe implementing every bundle in every agent.
- B5 is treated as closed within the paper's reviewed corpus through GraSP; it is a closest-baseline / comparison point, not a sixth unresolved bundle.
- The runtime contains **partial, illustrative mechanisms**, not complete faithful reconstructions of ACT-R, Soar, LIDA, BDI, MIDCA, EPIC or Sigma.
- The heuristic scoring functions in this repository are engineering choices for an experiment. They are not equations or algorithms specified by the papers.
- No mechanism is accepted merely because a field exists. Tests and traces must show the trigger, control effect, state transition, persistence and outcome update.

## Persistence and production boundaries

This is an executable mechanism-study workspace, not yet a production deployment:

- The primary LangGraph4j module has independent PostgreSQL checkpoint and CoALA memory/evidence repositories. Local defaults remain in-memory for development/tests; the `production` profile rejects either in-memory backend. CoALA memory schema bootstrap applies V003 plus Phase 4's V004 lookup indexes. See [`docs/COALA_MEMORY.md`](docs/COALA_MEMORY.md) for repository boundaries, utility scoring and limitations.
- The experimental Agentic module is intentionally unchanged. It has no `AgenticScopeStore` configured; its custom planner exposes execution state for restoration, but that alone is not durable persistence.
- B2 recovery preserves the committed goal and records a typed child/recovery resolution; it does **not** yet compile a resolution into reusable production rules. `eligibleForCompilation` remains false.
- B6 uncertainty is explicitly marked uncalibrated. Model self-confidence is recorded as provenance only and never treated as calibrated probability.
- The retail catalogue is an in-memory fixture; `check_stock` reports that fixture's state, not a live retailer inventory service.
- The primary graph includes a checkpointed approval interrupt/resume path and subject-binding check. The shared action contract requires approval for non-READ effects by default. API authentication, cross-instance locking, multi-tenant isolation, transactional external-side-effect idempotency and durable audit storage remain unimplemented.

## Dependency pins — WP-1 target (2026-11-10)

The current build uses centrally managed, explicitly versioned release trains:

- Java 21 (unchanged)
- Apache Maven distribution 3.10.0 via the repository bootstrap launcher (unchanged)
- Spring Boot parent 4.1.2 (stable)
- Spring AI BOM 2.0.2 (stable)
- LangGraph4j BOM 1.9.9 (current 1.9 release)
- LangChain4j BOM 1.23.0 (stable core/OpenAI modules)
- LangChain4j Agentic 1.23.0-beta33 (BOM-managed beta module; experimental/pre-release)

The root POM imports the Spring AI, LangGraph4j and LangChain4j BOMs. Child modules do not restate versions for dependencies managed by those BOMs. The Agentic module remains isolated from the framework-neutral core and remains experimental; the LangChain4j maintainers explicitly warn that some modules in the 1.23.0 BOM still use the `1.23.0-beta33` prerelease line.

WP-1 deliberately retains the existing LangGraph4j PostgreSQL V1 saver and schema, and keeps `CompileConfig.releaseThread(false)`. This avoids mixing a dependency refresh with a checkpoint-schema migration or graph lifecycle change. LangGraph4j 1.9 offers a V2 saver with a schema incompatible with V1; any V2 migration must be a separately reviewed, backed-up and integration-tested work package. The 1.9 runtime's default release behavior is explicitly overridden to preserve this project's existing same-thread lifecycle.

## Remediation scope and research traceability

WP-0 freezes the objective, non-goals, paper-to-code crosswalk and known comparison confounders. Read [`docs/remediation/PROJECT_SCOPE.md`](docs/remediation/PROJECT_SCOPE.md), [`docs/remediation/PAPER_TO_CODE_CROSSWALK.md`](docs/remediation/PAPER_TO_CODE_CROSSWALK.md) and [`docs/remediation/COMPARISON_CONFOUNDERS.md`](docs/remediation/COMPARISON_CONFOUNDERS.md) before changing runtime behavior.

## References

- CoALA, Sumers et al., *Cognitive Architectures for Language Agents*: <https://arxiv.org/abs/2309.02427>
- Fan & Lan, *From Cognitive Architectures to Language Agents: A Mechanism-Level Review of Lineage, Convergence, and Migration Gaps*: <https://arxiv.org/abs/2607.23942>
- LangChain4j Agentic documentation: <https://docs.langchain4j.dev/tutorials/agents/>
- Previous code-level review: [`docs/reports/Retail_Agent_CoALA_Fan_Lan_Implementation_Audit.docx`](docs/reports/Retail_Agent_CoALA_Fan_Lan_Implementation_Audit.docx)

## Reproducible build (Phase 0)

The repository pins the Maven distribution and verifies its SHA-512 before first use. A JDK 21 installation is required.

```bash
./mvnw --version
./mvnw -B -ntp -Dapp.llm.provider=simulated clean verify
```

On Windows, use `mvnw.cmd` with the same arguments. Both launchers cache the pinned Apache Maven distribution under the Maven user home, validate its SHA-512 and reported version, and do not commit Maven binaries to the repository. The launchers are custom repository bootstrap scripts, not the official Apache Maven Wrapper. CI exercises both the Linux and Windows entry points with simulated providers. The experimental `retail-agent-langchain4j` Agentic module is retained as-is and is not part of the primary-runtime redesign.
