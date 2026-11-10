# Paper-to-code crosswalk — WP-0 baseline

**Purpose:** trace claims to code and tests without attributing local engineering choices to the papers.  
**Baseline:** Phase 4 input archive, 2026-11-10.

## 1. CoALA architecture-level crosswalk

| CoALA concept | Current implementation | What the repository may claim | Limitation / proof still required |
|---|---|---|---|
| Working memory | `retail-agent-langgraph4j/.../agent/RetailState.java`; `retail-agent-core/.../memory/domain/WorkingMemorySnapshot.java` | Graph state is the active LangGraph working state; bounded subject/execution snapshots are a separate API. | Must verify serialization/restart and avoid conflating graph checkpoints with long-term memory. Agentic `AgenticScope` remains its own runtime mechanism. |
| Episodic memory | `EpisodicMemoryRecord`, `CoalaMemoryService`, `CoalaMemoryRepository`; JDBC and in-memory adapters | Subject-scoped episodes can retain events, outcomes, provenance, evidence links and lifecycle/retention data. | Live PostgreSQL, process-restart durability, lifecycle/deletion policy and graph-created trusted learning remain open. |
| Semantic memory | `SemanticMemoryRecord`; `CoalaMemoryService`; repository adapters | Explicit subject-predicate-value facts support evidence, authority, validity and supersession metadata. | Do not assume all facts are verified; policy-specific authority and safe transactional replacement require tests. |
| Procedural memory | `ProceduralMemoryRecord`; validation/lifecycle methods in `CoalaMemoryService` | Procedures can be represented as candidates with applicability, preconditions, steps, postconditions and evidence-backed validation. | This is not a general skill-governance system or GraSP implementation. Repeated validation/retirement semantics require integration testing. |
| Action space | `retail-agent-langgraph4j/.../agent/Tools.java`; shared action definitions/evaluator in `retail-agent-core` | The retail runtime has a closed, deterministic action whitelist; model proposals do not directly execute tools. | Real merchant systems, external side effects, transactional outbox and distributed idempotency are outside the current bounded simulator. |
| Decision procedure | `RetailAgentGraph.java`, `RetailGraphNodes.java`, planning/evaluation/selection types in `retail-agent-core` | The runtime repeatedly proposes, evaluates, selects, executes/observes and can return to planning/retrieval. | Do not reduce this to a rigid one-pass pipeline; verify every route and loop in tests/traces. |
| Reflection/learning | `RetailGraphNodes` learn/reflect stages; `CoalaMemoryService`; utility and attribution domain records | The code has reflection/outcome and evidence-attribution concepts. | Phase 4 graph currently records workspace admission only; meaningful-use events/verified per-memory attribution are not created by normal graph execution. |

## 2. Fan & Lan mechanism-level crosswalk

The labels below retain the paper's grouping and are not a claim that the implementation reproduces the historical architectures.

| Bundle | Paper-level coupling retained by the project | Current code paths | Baseline status | Tests/evidence that exist | Missing evidence / guardrail |
|---|---|---|---|---|---|
| **B1 — activation / latency / utility** | Memory activation/value interacts with access latency, downstream value and maintenance cost. | `AdaptiveMemoryPolicy`, `MemoryUtilityProfile`, utility estimator and retrieval candidate construction. | **Partial, local heuristic.** | Core mechanism tests; Phase 4 standalone smoke assertions; source-level utility aggregation. | No learned latency predictor, calibrated activation model or causal feedback from ordinary graph use. Compare against relevance/top-k baseline; do not call ACT-R. |
| **B2 — typed impasse / substate / compilation** | Typed failure leads to protected parent state and bounded child recovery; typed resolutions may be validated before reusable compilation. | `TypedRecoveryPolicy`, `RecoverySubstate`, `RetailGraphNodes` recovery route. | **Partial.** | Recovery and graph-path tests in the current suite. | No resolution compiler or proven transfer into reusable control knowledge. `eligibleForCompilation=false` must remain explicit; do not call Soar chunking. |
| **B3 — content competition / workspace / broadcast learning** | Typed candidates compete for a bounded shared workspace; admission/broadcast affects downstream access and future utility. | `BoundedWorkspace`, `WorkspaceCandidate`, workspace event/recipient trace, learn-related policy. | **Partial.** | Core workspace budget/selection tests and graph integration behavior. | Scoring is a local heuristic; runtime does not yet produce meaningful-use feedback. Do not equate message passing alone with cognitive broadcast. |
| **B4 — intention / reconsideration / method authority** | Persistent commitment and explicit reconsideration authority separate goal stability from method change. | `IntentionRecord`, `IntentionGovernor`, graph planning/recovery nodes. | **Partial.** | Intention governance and graph scenario tests. | No separately validated meta-level diagnosis/controller with empirically demonstrated method switching. Do not call a complete BDI/MIDCA implementation. |
| **B5 — skill governance / GraSP comparison** | The paper treats GraSP/skill governance as a comparison point in its synthesis, not an additional residual bundle in this project's gap list. | Deterministic action registry/whitelist only; no GraSP implementation. | **Baseline-only / not implemented.** | Existing action policy tests. | Do not mark B5 as implemented. It is not a sixth open residual bundle; retain as a baseline for any later skill-governance experiment. |
| **B6 — uncertainty / resources / interruption / stopping** | Uncertainty and resource limits govern admission, planning, authorization, execution monitoring and stop/escalation decisions. | `UncertaintyEstimate`, `AgentLimits`, proposal evaluator/governor, graph execution/approval boundary. | **Partial, uncalibrated.** | Non-finite confidence rejection and bounded call/action/iteration tests. | Model self-confidence is provenance, not probability. No calibrated estimator, complete cross-stage propagation or held-out selective-risk results. |

## 3. Seven questions required before claiming a mechanism

For each mechanism, the code review must answer all of the following with exact methods/tests:

1. **State substrate:** what authoritative state is consumed?
2. **Control locus:** which policy/node/governor has transition authority?
3. **Trigger:** what actual event causes the mechanism to run?
4. **Transition:** what materially changes in runtime behavior?
5. **Persistence:** what survives node, graph, process and session boundaries?
6. **Failure semantics:** what happens on timeout, invalid evidence, contradiction, serialization failure and duplicate delivery?
7. **Learning effect:** how does a verified outcome change future decisions, and what experiment establishes that this effect helps?

If any answer is only a field, prompt statement or trace label, mark the mechanism partial/illustrative.

## 4. Source-level scope note

The project must not claim that a shared framework-neutral `CandidateEvaluator` or `BoundedWorkspace` is specified by CoALA or Fan & Lan. These are engineering realizations used to make the experiment executable. Paper claims and code design must be written in separate columns in later design notes.
