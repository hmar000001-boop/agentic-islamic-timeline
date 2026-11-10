# Comparison confounders — frozen WP-0 register

**Rule:** no result may be attributed to LangGraph4j versus LangChain4j Agentic until relevant confounders are either aligned or explicitly controlled in the experiment.

| ID | Confounder observed in Phase 4 | Why it invalidates a naïve comparison | WP-0 decision | Later work needed |
|---|---|---|---|---|
| C-01 | Initial retrieval differs: primary LangGraph4j uses Spring AI `SimpleVectorStore` with `HashEmbeddingModel`; Agentic retrieval uses deterministic lexical search. | Candidate quality and rank differ before orchestration policy acts. | Record as known; do not change during WP-0/WP-1. | Shared deterministic `CatalogRetriever` port/adapter and identical data/query/top-k. |
| C-02 | Memory lifecycle and persistence differ: primary uses Phase 3–4 CoALA repositories; Agentic retains its older in-scope memory path. | State durability, selection feedback and recall context differ. | Preserve historical Agentic source/behavior for this work package. | Create a matched comparison profile or explicitly label the Agentic run as a historical baseline; do not compare as parity. |
| C-03 | LangGraph4j has PostgreSQL checkpoint and CoALA JDBC wiring; Agentic does not configure `AgenticScopeStore`. | Restart/recovery capability differs. | No persistence redesign in WP-0/WP-1. | Shared persistence contract or benchmark dimensions that exclude persistence claims. |
| C-04 | LangGraph4j has scripted, cassette record/replay and fault-injection support; Agentic has a scripted ChatModel but not identical cassette/fault controls. | Test determinism, failure injection and retry conditions are asymmetric. | Preserve; record. | Equivalent scripted scenarios, fault schedules and replay contracts. |
| C-05 | Prompts, parser/repair path, LLM gateway and response validation may differ by runtime. | Model-visible task framing and invalid-output handling can dominate observed outcomes. | No prompt/model tuning in WP-0/WP-1. | Freeze equivalent prompts, structured-output contracts, repair limits and model configuration for the matched benchmark. |
| C-06 | Resource limits are described as aligned but invocation accounting differs (logical gateway calls vs underlying provider requests/retries). | Equal numeric budgets may not represent equal real work/cost. | Retain current limits; do not claim budget parity is fully proven. | Shared counters or report separate logical calls, HTTP attempts, tokens, latency and cost. |
| C-07 | Runtime framework lifecycle differs; LangGraph4j has graph checkpoints/interrupts, Agentic path uses custom Planner and `AgenticScope`. | The frameworks expose different lifecycle semantics by design. | Treat this as a measured independent variable, not automatically a defect. | Define which capabilities are controlled and which are the subject of comparison. |
| C-08 | Latest dependency migration may alter library behavior/API even when application source is unchanged. | A before/after result may reflect library change, not prior remediation. | WP-1 records exact library versions and dependency tree; no performance claims from version-upgrade run. | Re-run correctness conformance; benchmark only after dependencies are frozen. |

## Comparison protocol minimum

Before a comparative result is published, capture for each run:

- exact source commit and SHA-256 of fixture corpus;
- Java, Maven, framework, model-adapter and transitive dependency versions;
- exact prompt/templates and model/provider parameters;
- same initial goal, product/catalogue facts, evidence and action permissions;
- retrieval implementation, index snapshot, ranking/top-k and filters;
- memory contents, lifecycle and starting persisted state;
- action/tool allowlist, result fixture and idempotency behavior;
- model-call, HTTP-attempt, token, elapsed-time and memory-token accounting;
- seeded simulator/fault schedule;
- trace of proposal, evaluation, selection, authorization, execution, observation, recovery, answer verification and learning.

Report correctness, task success, evidence validity, unsafe-action rate, goal drift, token/work cost and failure recovery separately. Do not collapse all into a single success percentage.
