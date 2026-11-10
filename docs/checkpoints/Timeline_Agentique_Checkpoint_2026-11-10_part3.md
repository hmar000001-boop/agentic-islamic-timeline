# Timeline Agentique — Part 3 continuity checkpoint

**Date:** 2026-11-10  
**Baseline:** Part 2 implementation plus `timeline-agentic-part2-ci-fix-patch-only.zip`  
**Scope:** user-facing private attachment workflow (SAF import, list, authenticated preview and confirmed delete).  
**Room schema:** v7 unchanged.  
**Part 0–2 CI state:** user reports hosted CI green after the canonical Android private-path correction; recorded as user-confirmed, not independently inspected from the hosted workflow UI in this session.

## Part 3 implementation delivered in source

- Added `AttachmentImportPolicy` for PDF/plain-text allowlisting, MIME normalization, PDF signature validation and stream rewind.
- Added `AttachmentPreviewLoader` for strict UTF-8 text preview (64 KiB), Android `PdfRenderer` PDF preview (25 MiB, 1,000 pages, 1,440-pixel edge and 4 MP bounds), private cache temp-file deletion and narrowly scoped startup cleanup.
- Added `AttachmentManagementFeature` with localized document list, import/refresh, preview, PDF page navigation, explicit delete confirmation and storage-recovery states.
- Integrated an SAF `OpenDocument` picker and the existing recovery-gated `TimelineApplication` APIs. No provider URI permission is retained.
- All attachment actions remain blocked until Part 2 recovery reports `Ready`; import is not allowed to reach the repository directly from UI code.
- Source filenames are not saved. The list shows type, size and import time; distinguishability is limited for similar entries. A persisted display name would need sanitization and a reviewed Room v8 migration.
- `USER_MANAGED` is currently the accepted retention identifier; no automatic expiry job was added.
- No document sharing/export, external viewer, Room migration, backend or network permission was added.

## New tests / CI

- `AttachmentImportPolicyTest`: five unit regression cases.
- `AttachmentPreviewCacheTest`: selective orphan cleanup, bounded text, malformed UTF-8 rejection, and a minimal one-page PDF render/temp-file cleanup instrumentation case.
- `AttachmentManagementFeatureTest`: recovery-gated import, explicit delete confirmation and import callback test.
- `scripts/verify-part3-attachment-ux.py`: 26 static source/UI/localization/security assertions, wired in `.github/workflows/android-ci.yml`.

## Verification actually performed

- Repository static suite: 29 pass, 2 expected environment blockers (Android SDK preflight and generated Room schema export), 0 unexpected failures.
- Part 3 static guard: 25/25 pass.
- Import-policy Kotlin harness: 5/5.
- Preview helper API-shaped stub harness: bounded text and selective crash-leftover cleanup passed.
- Compose feature API-shaped stub compilation passed before the latest explicit test-tag addition; it is not an actual AGP/Compose build.
- Android SDK/Gradle distribution are unavailable in this local environment. Actual app compilation, lint, JUnit/Compose tests, PDF rendering instrumentation and API 36 connected test have not run here.
- **Part 3 hosted CI remains pending.** The user's green CI report closes Parts 0–2 only.

## Current paths

- Project root: `agentic-islamic-timeline-0.7.0/`
- Implementation report: `docs/reports/PART3_ATTACHMENT_WORKFLOW_REPORT_2026-11-10.md`
- CI contract: `scripts/verify-part3-attachment-ux.py`
- Change delivery: patch-only ZIP, paths relative to the project root (to be extracted over the matching Part 2 tree).

## Next action

Apply the Part 3 patch over Parts 0–2 and run the complete hosted Android workflow. Do not start Part 4 until explicitly requested. Overall production release remains blocked on the independent gates listed in `docs/governance/RELEASE_GATES.md`.
