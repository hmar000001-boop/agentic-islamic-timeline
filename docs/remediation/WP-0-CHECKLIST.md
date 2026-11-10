# WP-0 execution record — baseline and scope freeze

**Date:** 2026-11-10  
**Input:** `retail-agent-phase-4.zip`  
**Input archive SHA-256:** `b6f4f3192c0f43c5191bfb5c62d4861ef177d345625d1be804749992df72dc93`  
**Extracted source files:** 153  
**Extracted source manifest SHA-256:** `58e7c9b30b0c40d8761ef72bed550e79a8f00dd442160fbe1eeb5454ac70694d`

## Changes made in WP-0

- [x] Preserved the incoming Phase 4 archive unchanged.
- [x] Established a Git baseline from the archive (the supplied ZIP did not contain `.git`).
- [x] Froze the research objective, invariants and non-goals in `PROJECT_SCOPE.md`.
- [x] Added CoALA and B1–B6 paper-to-code crosswalk with partial/illustrative boundaries.
- [x] Added the comparison-confounder register.
- [x] Corrected stale README language so it describes the Agentic coordinate as a Phase 4 baseline pin rather than a claim about current Maven Central.
- [x] Added this work-package execution record.
- [x] No Java source, POM, migration, graph topology, memory policy or runtime behavior was changed.

## Explicit scope freeze

The project remains a controlled study of one bounded retail-agent scenario implemented through a framework-neutral core and two orchestration tracks. It is not a production retail service, not an implementation of an entire cognitive architecture, and not an empirical validation of Fan & Lan's hypotheses.

## Exit status

**WP-0 documentation/source-boundary acceptance: PASS.**  
**WP-0 full phase certification: NOT CLAIMED.** This work package freezes scope and evidence boundaries; it does not close Phases 0–4.

## Git record

The delivery records the exact baseline archive, WP-0 commit and tag. The source archive does not include `.git`; a separate Git bundle is provided with the checkpoints so the history can be imported.
