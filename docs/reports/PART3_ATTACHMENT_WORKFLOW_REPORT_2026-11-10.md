# Part 3 implementation report — private attachment workflow

**Date:** 2026-11-10  
**Baseline:** Part 2 source tree, including the Android canonical-private-directory correction  
**Room schema:** v7 retained; no entity or migration change  
**Scope:** complete the user-facing local attachment path around Part 1 encrypted storage and Part 2 process recovery.

## Summary

Part 3 adds a bilingual Compose attachment manager, Android Storage Access Framework (SAF) import, gated list/read/delete operations, bounded plaintext previews, and regression/CI contracts. It uses the existing `TimelineApplication` facade and encrypted Room/Keystore repository; it does not create a second persistence path.

## Implementation

### Import boundary

- Uses `ActivityResultContracts.OpenDocument` and accepts only `application/pdf` and `text/plain`.
- The SAF read grant is consumed for the selected stream only. The app does not call `takePersistableUriPermission`, retain a provider URI, or retain a separate source file.
- `AttachmentImportPolicy` normalizes MIME parameters/case, rejects missing and unsupported MIME types, checks the `%PDF-` signature for PDF claims, and rewinds the buffered stream before encryption.
- The encrypted attachment store enforces its existing 25 MiB default maximum and consumes the source stream on `Dispatchers.IO` through the repository's cancellation-safe commit sequence.
- Retention is currently the explicit `USER_MANAGED` policy identifier. This increment does not add expiry scheduling or automatic retention deletion.

### Listing and management

- Adds a Private Documents destination with import, refresh, per-item type/size/import-time metadata, preview and delete actions.
- Items remain unavailable until the Part 2 recovery coordinator reaches `Ready`; the application-level import/list/read/delete APIs independently enforce the same readiness gate.
- Source display names are intentionally not persisted. The list therefore identifies an item by file type, byte size and import time. This reduces retention of provider-supplied metadata but makes same-type/similarly sized documents imported close together harder to distinguish. A future display-name feature should use sanitized metadata inside SQLCipher and a separately reviewed Room v8 migration.
- Delete requires a confirmation dialog. Repository deletion removes the Room row first and then ciphertext; a failed ciphertext cleanup is presented as pending reconciliation, not as complete physical deletion.

### Authenticated local preview

- Text preview decodes strict UTF-8 and exposes at most 64 KiB of characters; malformed UTF-8 fails instead of silently replacing bytes. The encrypted original is unchanged.
- PDF preview uses Android `PdfRenderer`, limits the plaintext PDF copy to 25 MiB, accepts 1–1,000 pages, and bounds each rendered bitmap to a maximum 1,440-pixel edge and 4,000,000 pixels.
- `RoomDocumentAttachmentRepository.withContent` invokes the preview callback only after the encrypted store has authenticated the complete AES-GCM payload and verified its recorded length and SHA-256. PDF rendering then uses a short-lived file under the app-private cache directory.
- The temporary PDF is deleted in `finally`. A failed deletion is surfaced and the bitmap is recycled; the app's next startup removes only feature-owned `preview-*.pdf` leftovers. PDF preview waits for that cleanup to complete and fails closed if cleanup did not complete.
- Preview requests use a generation token to discard/recycle stale results; activity destruction and dialog dismissal release rendered bitmaps. No share/export, external viewer or plaintext export path is added.

### Integration and localization

- Adds the attachment destination to existing app navigation.
- Adds matching default/English and Arabic UI strings.
- Adds `scripts/verify-part3-attachment-ux.py` to hosted CI. The guard checks picker/type restrictions, recovery gates, temporary-file bounds/cleanup, delete confirmation, localization parity, offline manifest policy and unchanged Room v7.
- Adds unit tests for import policy; Android instrumentation tests for selective cache cleanup, bounded UTF-8 preview, minimal PDF rendering/temp-file deletion; and Compose tests for recovery gating, import action and explicit delete confirmation.

## Verification performed in this environment

| Check | Result | Qualification |
|---|---|---|
| Part 3 static UX/security guard | PASS, 25 assertions | Source contract only |
| Repository static suite | 29 PASS; 2 environment blockers; 0 unexpected failures | SDK preflight and Room schema-export require the hosted Android toolchain |
| Import policy regression harness | 5/5 PASS | Kotlin 1.9.0 with local JUnit assertion/annotation shims; not Gradle/JUnit execution |
| Preview helper harness | PASS | Compiled against API-shaped Android stubs; exercised bounded text and selective cleanup, not Android `PdfRenderer` itself |
| Compose feature shape compilation | PASS | Compiled against API-shaped Compose stubs; not the real Compose compiler/AGP build |
| Actual Gradle app build, lint and Android instrumentation for Part 3 | NOT RUN HERE | Android SDK and cached Gradle distribution are not available in this environment |
| Hosted CI for Part 3 | PENDING | User-confirmed green CI applies to Parts 0–2; this Part 3 delta still needs a CI run |

A green Parts 0–2 run is not represented as Part 3 acceptance. In particular, the new PDF rendering instrumentation and Compose confirmation test have not yet executed on the hosted API 36 emulator.

## Acceptance required for Part 3

1. Run the complete hosted workflow against this patch. It includes app unit tests, app lint/assemble, the offline merged-manifest audit, API 36 connected Android tests and the Part 3 source guard.
2. Confirm the new `AttachmentImportPolicyTest`, `AttachmentPreviewCacheTest` and `AttachmentManagementFeatureTest` actually execute and pass in Gradle/Android test reports.
3. On a physical supported Android device, import a valid PDF and UTF-8 text file through at least two document providers; verify unsupported MIME/signature and oversized input are rejected; kill/restart during PDF preview; verify no `preview-*.pdf` plaintext remains after a successful preview or startup cleanup.
4. Verify Arabic RTL layout, TalkBack names/order, large font scaling, deletion confirmation and the cleanup-pending message on device.
5. Confirm that no SAF URI grant persists after import and no `INTERNET` permission appears in the offline merged manifest.

## Explicit limitations / not implemented

- PDF and UTF-8 plain text only. DOC/DOCX, images, OCR, audio and archive imports are not supported.
- Source filenames are not retained; see the listing trade-off above.
- `USER_MANAGED` is an accepted policy identifier, not an automated expiry/retention engine.
- No document annotation, search, cross-document retrieval indexing, external sharing, export, print, cloud backup or synchronization.
- No Room schema change, backend, account, analytics, telemetry, new Android permission or network path.

**Part 3 status:** source implementation and local static/harness checks complete; hosted CI and physical-device acceptance remain open. Parts 0–2 are closed as implementation increments based on the user's confirmation that the hosted CI rerun is green. Overall production release remains NO-GO until the independent governance, security, device, model and scholarly acceptance gates in `docs/governance/RELEASE_GATES.md` are signed off.
