# ADR-003 — Human approval before state-changing actions

- Status: accepted
- Date: 2026-11-06

## Decision

A state-changing action is denied unless explicit authorization exists. Default behavior is `ASK_EACH_TIME`. Persistent grants, when later implemented, must be narrowly scoped, bounded, expiring and revocable. Sensitive, external-sharing, financial, destructive and irreversible operations always require per-action confirmation.

## Consequences

Model output cannot authorize execution. The authorization module is isolated from UI and LLM/runtime dependencies. No concrete Android tool executor is exposed in Phase 1.
