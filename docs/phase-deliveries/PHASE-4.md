# Phase 4 delivery — controlled consolidation and durable memory utility

## Phase boundary

Phase 4 closes two explicit Phase 3 gaps without introducing autonomous trust promotion:

1. deterministic assessments for semantic consolidation candidates (clear, duplicate, contradiction), considering subject/predicate identity and temporal overlap;
2. durable per-memory utility profiles derived from the already-persisted use and evidence-backed attribution ledgers, consumed by the primary LangGraph4j retrieval selector.

The experimental `retail-agent-langchain4j/` module remains untouched. Its earlier in-process adaptive-memory mechanism is preserved as a comparison track.

## Delivered

- Added `SemanticConsolidationAssessment` and `CoalaMemoryService.assessSemanticCandidate(...)`.
- Candidate assessment compares normalized subject, predicate and value. It only considers active facts whose validity intervals overlap; different values become a contradiction, same values become a duplicate, and non-overlapping temporal facts remain distinct.
- Semantic activation now refuses duplicate/contradictory candidates. An explicit supersession path remains available through `replaceSemanticFact`; overlapping active competitors other than the superseded fact are rejected.
- Added `MemoryUtilityProfile`, including total exposure, meaningful use, and relation-specific attributed outcome counts.
- Added subject-, memory-ID- and memory-kind-scoped utility aggregation to the memory repository contract and both in-memory/PostgreSQL adapters. The aggregate is computed from `coala_memory_uses` and `coala_memory_attributions`; no second, drift-prone score table is introduced.
- Added the Beta(1,1)-smoothed support rate plus a downstream-value posterior blend whose prior mean is the candidate's intrinsic value and whose prior strength is four. Evidence-backed outcomes therefore move candidate value gradually toward observed support/contradiction; exposure-only events never increase utility.
- Primary LangGraph4j retrieval now blends this durable utility into each episodic, semantic and procedural memory candidate. Its selector is invocation-local; stale process-local scores can no longer override persisted feedback.
- Added PostgreSQL indexes for memory-item usage aggregation and active semantic consolidation lookup (`V004__memory_utility_indexes.sql`), wired after V003 schema bootstrap.
- Added unit tests for duplicate/contradictory/temporal semantic candidates, activation refusal, evidence-backed utility counts, smoothing and subject isolation.
- Added a Spring integration test that exercises evidence persistence, semantic conflict checks and utility aggregation through whichever adapter the job configures; it asserts the concrete adapter type so a PostgreSQL job cannot silently use the in-memory fallback.
- Corrected the PostgreSQL CI job to configure both the graph checkpoint backend and the CoALA memory/evidence JDBC backends, including the shared PostgreSQL URL and schema bootstrap.

## Explicit safety and interpretation boundaries

- Consolidation assessment is read-only. It neither creates candidate facts nor activates, supersedes, archives or deletes memory.
- `CANDIDATE` remains the required state for proposed facts. A `CLEAR` result only means no active same-subject/predicate fact overlaps the candidate's validity interval; it is not evidence that the candidate is true. Evidence validation and an explicit activation call are still required.
- A task-level outcome is never copied to every memory in the workspace. Only a use-specific `SUPPORTED` or `CONTRADICTED` attribution, accepted by the existing evidence-authority and freshness checks, influences the learned value.
- Workspace admission remains exposure only. The graph does not infer that an LLM relied on a memory from prompt inclusion or task success. A trusted runtime component must record a meaningful use stage before attribution is accepted.
- The scoring blend is a local experimental heuristic, not a formula prescribed by CoALA or Fan & Lan.

## Verification status

- All shared-core production sources compile with `javac --release 21 -Xlint:all`.
- A standalone Java smoke harness passes the temporal contradiction guard, evidence-backed utility calculation, exposure-only exclusion and subject-isolation checks.
- The JDBC adapter and new backend integration test source compile against temporary minimal API stubs; this checks Java source compatibility only, not compatibility with the pinned dependency graph or runtime behavior.
- Maven/JUnit execution is blocked before Maven startup because this environment cannot resolve `downloads.apache.org`; the full reactor and the newly added JUnit tests are not claimed as executed here.
- The PostgreSQL CI job now applies V003/V004 and runs the backend-aware Spring integration test, but it has not been executed successfully from this packaging environment. PostgreSQL query execution and concurrent multi-process activation therefore remain pending hosted CI verification.
- The Phase 3 CI correction remains source-integrated but is not newly verified by a successful CI result in this package.

## Next phase candidates

- Structured, trusted runtime events for `CITED_AS_EVIDENCE`, `USED_IN_PLAN` and `USED_IN_ACTION`; do not infer them from free-form model text.
- A review interface/queue that presents consolidation assessments and candidate provenance to an authorized human.
- Retrieval evaluation with fixed datasets and measured relevance/latency; this is distinct from adding more heuristic score terms.
