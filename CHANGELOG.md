# Changelog

## WP-2 — Phase 0 reproducibility (2026-11-10)

- Clarified that `mvnw` / `mvnw.cmd` are custom repository bootstrap launchers, not the official Apache Maven Wrapper. Preserved the Apache-published SHA-512 distribution check.
- Hardened the POSIX and PowerShell bootstrap path, enforced exactly Maven 3.10.0, and added offline cold-cache/checksum/cache-reuse tests.
- Added a two-clean-build JAR/WAR reproducibility script and a Surefire/Failsafe inventory report tool.
- Expanded CI to Linux, Windows and PostgreSQL jobs; pinned GitHub Actions by immutable commit SHA.
- Added normalized cross-platform line-ending attributes. No application Java, memory or graph behavior changed.
- Full Maven/Windows/PostgreSQL hosted CI acceptance remains pending because this environment cannot resolve the Apache distribution host and has no PowerShell runtime.


## Phase 4 — controlled consolidation and durable memory utility

- Added read-only, deterministic semantic consolidation assessment for duplicate and contradictory active facts, including case/whitespace normalization and temporal validity overlap.
- Semantic activation now rejects overlapping duplicates and contradictions; explicit replacement remains subject-scoped and rejects competing active facts.
- Added durable per-memory utility profiles aggregated from subject-scoped usage and evidence-backed attribution events in the existing repositories.
- Primary LangGraph4j retrieval now uses conservatively smoothed evidence-backed downstream utility; exposure-only events do not influence the learned value and in-process candidate scores no longer override persisted feedback.
- Added PostgreSQL lookup indexes, a backend-aware Spring integration test and phase-specific documentation. The PostgreSQL CI job now configures both checkpoint and CoALA memory/evidence JDBC backends rather than testing only checkpoints. The frozen LangChain4j Agentic comparison module remains untouched.

## Phase 3 — CoALA memory and evidence boundaries

- Added framework-neutral typed working, episodic, semantic and procedural memory records, explicit lifecycle transitions, provenance, validity/retention and bounded working-memory snapshots.
- Added per-memory usage and evidence-backed outcome-attribution records. Workspace exposure is distinct from downstream use; task-level success no longer updates every admitted memory's utility score.
- Added subject-scoped repository ports and in-memory adapters, plus PostgreSQL JDBC repositories and `V003__coala_memory.sql` for evidence, four memory dimensions, memory use and attribution.
- Added evidence existence/freshness/authority checks for verified episodes, semantic activation, procedure validation and causal attribution. Evidence IDs are immutable within a subject scope.
- Added replay-safe, idempotent memory-use, episodic append, evidence insertion and per-use attribution recording; conflicting ID reuse is rejected and memory kind is included in deterministic use identity.
- Made procedure validation evidence-event based (no count inflation on replay); a newly verified failure archives an active reusable procedure rather than leaving it silently selectable.
- Added compare-and-set updates for episodic, semantic and procedural state changes, bounded procedure-validation retry, and row locking for PostgreSQL semantic replacement.
- Added PostgreSQL schema checks for lifecycle/outcome/enumeration values, confidence, validity/expiry intervals and validation counts; semantic matching now uses literal substring matching consistent with the in-memory adapter.
- Wired four independent retrieval branches (episodic, semantic, validated procedural and catalogue) into the primary LangGraph4j graph with separate state channels.
- Added core lifecycle, subject isolation, evidence, optimistic concurrency, idempotency and attribution tests; documented limitations and PostgreSQL setup in `docs/COALA_MEMORY.md`.
- Kept `retail-agent-langchain4j/` byte-for-byte unchanged as the experimental Agentic comparison module.

## Phase 2 — Primary LangGraph4j runtime

- Split orchestration into the application facade, graph topology/thread lifecycle, and graph node operations.
- Added an inner planning subgraph and parallel retrieval fan-out/fan-in with distinct channels.
- Made LangChain4j the native asynchronous production chat integration for the primary graph; retained Spring AI for the existing vector-store adapter and simulator compatibility.
- Preserved opt-in cassette recording/replay and scripted/fault-injection execution.
- Added memory and PostgreSQL checkpoint saver configuration; production profile requires PostgreSQL.
- Added checkpointed human approval interrupt/resume with authenticated-subject binding, a typed exact-action approval request, approval-ID-bound resume, pending-state checks, execution-time action snapshot re-validation and simulator gateway defence in depth.
- Added approval/lifecycle/cassette tests and rejected non-finite uncertainty probabilities.
- Strengthened the authority contract so every non-READ effect requires approval by default; empty/unspecified effects are rejected instead of being treated as read-only; added immediate execution-boundary registry revalidation.
- Added an end-to-end graph test for interrupt, owner-bound checkpoint update, resume and denial, plus simulator idempotency collision/retry tests.
- Made simulator idempotency atomic per key, used a canonical length-prefixed SHA-256 request fingerprint, rejected key reuse with a different payload, and rebound cached outcomes to the current retry invocation ID.
- Corrected the checkpoint YAML property nesting and removed stale Spring AI real-chat wiring.

## Unreleased

- Fixed the Phase 3 CI compilation failure caused by the graph/node split moving clarification handling into `RetailGraphNodes` without moving its `CLARIFY` prompt constant. The constant is now declared at the node-operation boundary and both the exhausted-planning clarification branch and deterministic fallback use it.
- Added `RetailGraphNodesClarificationTest` to exercise the exhausted-planning/no-evidence path and assert that it emits the `clarify` route, a non-empty clarification prompt and the expected trace event.

- Fixed `CoreMechanismTest.adaptiveMemoryRefreshPreservesOutcomeLearnedActivationAndValue` to compare outcome-learned floating-point values with a `1e-9` tolerance instead of exact decimal equality. The production adaptive-memory policy is unchanged; the observed `0.7799999999999999` value is the normal IEEE-754 representation of the expected `0.78` result.
- Fixed LangGraph4j Spring binding for `AgentSettings` by keeping its canonical record constructor unambiguous; updated the hand-built test fixture to use the full canonical settings tuple.
- Made repeated LangGraph4j node visits observable by annotating trace events with outer-cycle and planning-round coordinates. This distinguishes an actual second retrieve/propose/evaluate/select pass from deduplicated identical trace labels.
- Corrected the total-provider-outage test to assert that the fallback recommendation matches a positively verified stock observation, rather than requiring `p-103` for a generic trail-running query that does not require waterproofing.

## CI dependency resolution correction

- At the time of this historical CI correction, LangChain4j core and OpenAI were pinned to `1.22.0`, and the separately versioned experimental `langchain4j-agentic` module was pinned to `1.22.0-beta32`. These were the Phase 3-era pins, not a statement about later publications.

- Added an explicit LangChain4j `propose` trace event around the actual `ProposalAgent` invocation. The model-call reservation remains a separate `budget:model:proposal` event, so resource governance is not confused with proposal execution. Strengthened the runtime test to require exactly two proposal events across its two decision cycles.
- Updated verification documentation to reflect the latest CI run: core 9/9 and LangGraph4j 18/18 passed; LangChain4j compiled and ran 4 tests with one trace-assertion failure, now addressed in source and awaiting a fresh CI run.

## WP-0 — baseline and scope freeze (2026-11-10)

- Recorded the exact Phase 4 source archive and extracted-file manifest hashes; preserved the incoming archive as an immutable checkpoint.
- Froze the controlled-comparison objective, architectural invariants and explicit non-goals before dependency or runtime changes.
- Added a paper-to-code crosswalk for CoALA and Fan & Lan B1–B6, keeping paper statements separate from local heuristics and marking incomplete/illustrative mechanisms honestly.
- Added a comparison-confounder register covering retrieval, memory/persistence, fault-injection, prompt/model and resource-accounting differences.
- Corrected stale README wording: the `1.22.0-beta32` Agentic coordinate is the Phase 4 baseline pin, not a statement about the latest published version.
- No Java source, dependency declaration, database migration, graph topology, memory policy, prompt or runtime behavior changed in WP-0.

## WP-1 — dependency alignment (2026-11-10)

- Updated Spring Boot to stable `4.1.2`, Spring AI to stable `2.0.2`, LangGraph4j to `1.9.9`, and LangChain4j to `1.23.0`; LangChain4j's BOM manages the experimental Agentic module at `1.23.0-beta33`.
- Imported Spring AI, LangGraph4j and LangChain4j BOMs and removed child-level versions for BOM-managed dependencies. Java 21 and the Apache Maven 3.10.0 distribution remain unchanged (the repository uses custom bootstrap scripts, not the official Maven Wrapper).
- Updated `SynchronizedMemorySaver` to forward the expanded LangGraph4j 1.9 `BaseCheckpointSaver` contract to its delegate while retaining the existing synchronized wrapper; added focused compatibility test source for release, error, interruption, tag and subgraph delegation.
- Kept `PostgresSaver` V1, its schema, graph topology, `GraphInput` calls and `.releaseThread(false)` unchanged. A V2 saver/schema migration is deliberately deferred because LangGraph4j documents V2 as schema-incompatible with V1.
- No memory model, memory policy, retrieval behavior, prompt, action policy, graph node or graph-edge behavior was intentionally changed. Full Maven/JUnit and PostgreSQL verification remain required before WP-1 is accepted.
