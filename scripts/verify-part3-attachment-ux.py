#!/usr/bin/env python3
"""Static safety and integration contracts for Part 3 attachment UX."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

checks = []
def check(condition: bool, label: str) -> None:
    checks.append((label, condition))

activity = read("app/src/main/java/app/timeline/islamic/MainActivity.kt")
application = read("app/src/main/java/app/timeline/islamic/TimelineApplication.kt")
policy = read("app/src/main/java/app/timeline/islamic/AttachmentImportPolicy.kt")
preview = read("app/src/main/java/app/timeline/islamic/AttachmentPreviewLoader.kt")
feature = read("app/src/main/java/app/timeline/islamic/AttachmentManagementFeature.kt")
unit_tests = read("app/src/test/java/app/timeline/islamic/AttachmentImportPolicyTest.kt")
ui_tests = read("app/src/androidTest/java/app/timeline/islamic/AttachmentManagementFeatureTest.kt")
cache_tests = read("app/src/androidTest/java/app/timeline/islamic/AttachmentPreviewCacheTest.kt")
gradle = read("app/build.gradle.kts")
workflow = read(".github/workflows/android-ci.yml")

check("ActivityResultContracts.OpenDocument()" in activity and "openAttachmentDocument" in activity,
      "attachment import uses Android document picker")
check("ATTACHMENT_MIME_TYPES" in activity and "application/pdf" in policy and "text/plain" in policy,
      "picker and import policy use the explicit PDF/text allowlist")
check("takePersistableUriPermission" not in activity and "persistable" not in application,
      "document-provider URI grants are not retained")
check("AttachmentImportPolicy.validate(content, mimeType)" in application and "USER_MANAGED" in policy,
      "application import facade enforces type validation and managed retention identifier")
check("%PDF-" in policy and "file signature" in policy,
      "PDF MIME value is checked against the file signature")
check("requireDocumentAttachmentStorageReady()" in application and
      all(name in application for name in ["importDocumentAttachment", "listDocumentAttachments", "withDocumentAttachmentContent", "deleteDocumentAttachment"]),
      "all attachment facade operations remain behind Part 2 recovery gate")
check("app.importDocumentAttachment" in activity and "app.listDocumentAttachments" in activity and
      "app.withDocumentAttachmentContent" in activity and "app.deleteDocumentAttachment" in activity,
      "UI delegates exclusively to application attachment facade")
check("AttachmentPreviewUiState.Loading" in activity and "AttachmentPreviewUiState.Failed" in activity and
      "previewRequestGeneration" in activity and "CancellationException" in activity,
      "preview loading, cancellation and stale-result handling are explicit")
check("MAXIMUM_TEXT_PREVIEW_CHARACTERS = 64 * 1024" in preview and "CodingErrorAction.REPORT" in preview,
      "text preview is bounded and rejects malformed UTF-8")
check("PdfRenderer" in preview and "MAX_PDF_BYTES = 25L * 1024L * 1024L" in preview and
      "MAX_SUPPORTED_PDF_PAGES = 1_000" in preview and "MAX_RENDER_PIXELS" in preview and
      "temporary.delete()" in preview,
      "PDF preview is bounded by file size, page count and rendered pixels, then deletes its private temporary file")
check("attachmentPreviewCacheCleanup.await()" in application and
      "cleanupOrphanedPreviews(cacheDir)" in application,
      "PDF previews wait for startup cleanup of crash-leftover plaintext")
check("AttachmentDeleteResult.Deleted" in activity and "deleteConfirmation" in feature and
      "attachment_delete_confirmation" in feature,
      "delete requires explicit confirmation and reports deferred encrypted-file cleanup")
check("AttachmentManagementFeature(" in activity and "AppDestination.ATTACHMENTS" in activity,
      "attachment screen is connected to app navigation")
check("Intent.ACTION_SEND" not in activity + feature and "FileProvider" not in activity + feature,
      "no external share/export path was added")
check("android.permission.INTERNET" not in read("app/src/main/AndroidManifest.xml"),
      "offline app manifest remains free of INTERNET permission")
check("version = 7" in read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt"),
      "Part 3 does not change Room schema version")
check("testImplementation(libs.junit)" in gradle and "libs.androidx.compose.ui.test.junit4" in gradle,
      "app unit and Compose instrumentation tests are wired")
check("assertThrows" in unit_tests and unit_tests.count("@Test") >= 5,
      "MIME/signature policy unit regression tests are present")
check("createComposeRule" in ui_tests and ui_tests.count("@Test") >= 2,
      "Compose screen readiness and import-action tests are present")
check("cleanupOrphanedPreviews" in cache_tests and "unrelated" in cache_tests and
      "pdfPreviewRendersOnePageAndDeletesPlaintextTemporaryFile" in cache_tests,
      "instrumentation tests cover selective cleanup, PDF rendering and preview-file deletion")
check("attachment-confirm-delete" in feature and "deleteCallbackRequiresExplicitConfirmation" in ui_tests,
      "destructive delete action has a testable explicit-confirmation boundary")
check("Verify Part 3 attachment UX" in workflow and "verify-part3-attachment-ux.py" in workflow,
      "Part 3 static security guard is part of hosted CI")

for path in ["values", "values-en", "values-ar"]:
    resource_path = ROOT / f"app/src/main/res/{path}/strings.xml"
    root = ET.parse(resource_path).getroot()
    names = {node.attrib["name"] for node in root.findall("string")}
    required = {
        "app_nav_attachments", "attachment_title", "attachment_description", "attachment_supported_types",
        "attachment_import", "attachment_refresh", "attachment_waiting_for_recovery", "attachment_imported",
        "attachment_import_failed", "attachment_import_unsupported", "attachment_import_too_large",
        "attachment_deleted", "attachment_deleted_cleanup_pending", "attachment_delete_failed",
        "attachment_delete_not_found", "attachment_preview_failed", "attachment_working", "attachment_empty",
        "attachment_pdf_label", "attachment_text_label", "attachment_item_metadata", "attachment_preview",
        "attachment_delete_title", "attachment_delete_confirmation", "attachment_cancel", "attachment_close",
        "attachment_preview_truncated", "attachment_pdf_page_description", "attachment_previous_page",
        "attachment_next_page", "attachment_page_count"
    }
    check(required.issubset(names), f"{path} attachment strings are complete")

failed = [label for label, passed in checks if not passed]
for label, passed in checks:
    print(("PASS" if passed else "FAIL") + " " + label)
if failed:
    raise SystemExit("Part 3 attachment UX guard failed: " + "; ".join(failed))
print(f"Part 3 attachment UX static contract: PASS ({len(checks)} assertions)")
