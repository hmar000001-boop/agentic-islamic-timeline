# Project scope freeze — WP-0

**Baseline:** Phase 4 source archive received 2026-11-10  
**Status:** frozen for WP-0/WP-1; changes require a recorded scope decision

## 1. Research and engineering objective

This repository is a controlled Java study of two orchestration implementations for the **same bounded retail-agent scenario**:

1. `retail-agent-core`: framework-neutral domain types, deterministic policies, evidence checks, scenario fixtures and contracts;
2. `retail-agent-langgraph4j`: the LangGraph4j graph runtime, using LangChain4j for LLM calls;
3. `retail-agent-langchain4j`: the separate LangChain4j Agentic/custom-`Planner` runtime.

The comparison asks how different orchestration frameworks express and execute the same application semantics, and how selected cognitive-architecture mechanisms can be represented, instrumented and experimentally assessed. Framework code is not itself evidence that CoALA or a mechanism bundle has been implemented.

The common scenario is evidence-gated retail recommendation: find waterproof trail-running shoes under a stated price limit and recommend only an item whose stock has been positively verified against the catalogue fixture. A search result alone is not stock evidence.

## 2. Invariants across all remediation work packages

- Preserve the three-module Maven reactor and the framework-neutral core boundary.
- Preserve one scenario, catalogue source of truth, proposal/evaluation/authorization contracts and action semantics across the two runtime tracks.
- Model output is a proposal, not transition authority, evidence authority or permission to execute an action.
- Deterministic Java policy validates proposals, evidence, action effects, approval requirements and verified completion.
- A non-`READ` action requires explicit approval by default; high-impact, destructive, irreversible, financial and externally sharing actions must not become autonomously executable through this remediation.
- Retrieval, evidence and memory are subject-scoped; runtime identity must come from a trusted caller boundary, not a model or untrusted request field.
- A graph checkpoint is not a substitute for durable long-term memory, and a durable memory row is not a substitute for graph execution state.
- All heuristics are labeled as project choices, not formulas prescribed by the papers.
- No new production, benchmark superiority, calibration or causal-learning claim is made without the relevant integration or empirical test.

## 3. Explicit non-goals for WP-0 and WP-1

The following must not be changed in these two work packages:

- memory schemas, memory retrieval/scoring, semantic activation/replacement rules or outcome attribution;
- graph topology, node ordering, transition predicates, action policy, approval flow or application-level state model;
- prompts, model selection, token/action budgets, fixture semantics or answer verification;
- retrieval parity between tracks (record it as a comparison confounder; address in its own work package);
- implementation of new B1/B2/B3/B4/B6 mechanisms or B5/GraSP;
- production API/authentication, multi-tenant deployment, external retail integrations or production release claims.

WP-1 may make **strict dependency compatibility edits only** (imports, serializers, saver implementation, configuration necessary for the upgraded library). Any such edit must preserve application-level state transitions and be described with before/after tests. Do not bundle behavioral redesign into dependency migration.

## 4. Research interpretation guardrails

### CoALA

Use CoALA as an architecture-level lens for memory systems, action spaces and the iterative agent decision process. The current graph is one engineering realization, not a paper-mandated fixed pipeline. Retrieval, reasoning/proposal and action selection can recur and interleave; do not describe the project as a rigid one-pass `retrieve → plan → act` system.

### Fan & Lan (2026)

Keep the paper's distinction between the residual mechanism bundles B1, B2, B3, B4 and B6 and the B5/GraSP comparison baseline. Do not call B5 a sixth residual gap. Do not claim this repository implements ACT-R, Soar, LIDA, BDI, MIDCA, EPIC, Sigma or GraSP.

For every B1/B2/B3/B4/B6 statement, separate:

1. the paper's reported mechanism/coupling;
2. the local engineering design chosen here;
3. the source-code path;
4. the trigger and observed control transition;
5. the persistence and failure boundary;
6. the test/trace supporting the claim;
7. the missing parts and falsifying experiment.

A named field, trace string or helper method is not sufficient evidence that a mechanism operates. The evidence must show that the trigger controls a transition and that the resulting state is preserved and affects later behavior where claimed.

## 5. Acceptance vocabulary

Every feature and phase is assessed separately at these levels:

- **Implemented:** source/configuration exists.
- **Unit verified:** isolated tests execute and pass.
- **Integration verified:** framework, database and runtime interaction executes and passes.
- **Acceptance verified:** the complete phase-specific criteria pass in CI from a clean checkout, including relevant failure/restart/security conditions.

Do not use “100% done”, “production-ready” or “paper-faithful” when the applicable acceptance criteria are not met.

## 6. Authoritative project decisions

- Java 21 remains the source/target baseline for this remediation.
- LangGraph4j owns the primary graph runtime; LangChain4j supplies chat-model adapters and the separate Agentic comparison runtime.
- Keep Spring Boot/Spring AI only where their current concrete responsibilities remain. WP-1 may version-align them but must not redesign retrieval.
- Keep the deterministic simulator as the default for builds/tests; live-provider credentials are not required for CI.
- Retain the Phase 4 archive as an immutable input checkpoint. The Git baseline tag and integrity manifest identify its exact contents.

## 7. Source papers and framework references

- Sumers et al., *Cognitive Architectures for Language Agents* (CoALA), arXiv:2309.02427: <https://arxiv.org/abs/2309.02427>
- Fan & Lan, *From Cognitive Architectures to Language Agents: A Mechanism-Level Review of Lineage, Convergence, and Migration Gaps*, arXiv:2607.23942: <https://arxiv.org/abs/2607.23942>
- LangGraph4j 1.9 release notes: <https://langgraph4j.github.io/langgraph4j/1.9/whats-new-v1.9/>
- LangGraph4j PostgreSQL saver documentation: <https://langgraph4j.github.io/langgraph4j/1.9/core/postgres-saver/>
- LangChain4j version/BOM guidance: <https://docs.langchain4j.dev/get-started/>
- LangChain4j Agentic documentation: <https://docs.langchain4j.dev/tutorials/agents/>

These links are references to be checked against the exact paper/library revision during each future mechanism or dependency change. They do not replace a source-level review.
