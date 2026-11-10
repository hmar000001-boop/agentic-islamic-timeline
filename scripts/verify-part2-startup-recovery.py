#!/usr/bin/env python3
"""Source-level guard for Part 2 process startup and attachment recovery integration."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise SystemExit(f"FAIL: missing required file: {relative}")
    return path.read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")
    print(f"PASS: {message}")

coordinator = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AttachmentRecoveryCoordinator.kt")
require("sealed interface AttachmentRecoveryState" in coordinator, "typed attachment recovery state exists")
for state in ("NotStarted", "Running", "Ready", "Failed"):
    require(f"data object {state}" in coordinator or f"data class {state}" in coordinator,
            f"recovery state includes {state}")
require("private val mutex = Mutex()" in coordinator, "recovery attempts are serialized")
require("MutableStateFlow<AttachmentRecoveryState>" in coordinator and "asStateFlow()" in coordinator,
        "recovery state is observable as read-only StateFlow")
require("suspend fun retry()" in coordinator and "is AttachmentRecoveryState.Failed -> runRecovery()" in coordinator,
        "retry is explicit and only applies after failure")
require("mutableState.value = AttachmentRecoveryState.NotStarted" in coordinator and "throw cancelled" in coordinator,
        "cancellation resets startup state and propagates")
require("failure.message" not in coordinator and "failure.localizedMessage" not in coordinator,
        "exception messages are not exposed through recovery state")

application = read("app/src/main/java/app/timeline/islamic/TimelineApplication.kt")
require("CoroutineScope(SupervisorJob() + Dispatchers.IO)" in application,
        "process-owned recovery scope uses an I/O dispatcher")
require("override fun onCreate()" in application and "attachmentRecoveryCoordinator.initialize()" in application,
        "attachment reconciliation starts automatically at Application startup")
require("initializeAgentRuntime()" in application and "applicationScope.launch" in application,
        "agent audit and task recovery are no longer tied to an Activity lifecycle")
require("private val documentAttachmentRepository" in application,
        "raw attachment repository is not publicly exposed to bypass the readiness gate")
for api in ("importDocumentAttachment", "listDocumentAttachments", "withDocumentAttachmentContent", "deleteDocumentAttachment"):
    require(re.search(rf"suspend fun(?:\s+<[^>]+>)?\s+{api}\s*\(", application) is not None,
            f"application exposes gated {api} API")
require(application.count("requireDocumentAttachmentStorageReady()") >= 5,
        "all four attachment operations enforce successful recovery")

activity = read("app/src/main/java/app/timeline/islamic/MainActivity.kt")
require("attachmentRecoveryState.collectAsState()" in activity,
        "Compose observes recovery state")
require("app_attachment_recovery_failed" in activity and "app_attachment_recovery_retry" in activity,
        "recovery failures expose a retry action")
require("app_attachment_recovery_missing" in activity and "missingDatabaseReferencedFiles.size" in activity,
        "missing files are reported without displaying IDs or file paths")
require("timelineApplication.initializeAgentRuntime()" not in activity,
        "agent recovery no longer runs in the Activity scope")

resource_files = [
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "app/src/main/res/values-ar/strings.xml",
]
required_keys = {
    "app_attachment_recovery_failed",
    "app_attachment_recovery_missing",
    "app_attachment_recovery_retry",
}
resource_keys = []
for relative in resource_files:
    root = ET.fromstring(read(relative))
    keys = {element.attrib["name"] for element in root.findall("string")}
    require(required_keys <= keys, f"recovery messages exist in {relative}")
    resource_keys.append(keys)
require(resource_keys[0] == resource_keys[1] == resource_keys[2],
        "default, English and Arabic app string-key sets remain identical")

unit_tests = read("core/persistence/src/test/java/app/timeline/islamic/core/persistence/AttachmentRecoveryCoordinatorTest.kt")
for test_name in (
    "initializeRunsOnceAndRetainsReadyReport",
    "failureIsVisibleAndOnlyAnExplicitRetryRunsAgain",
    "cancellationResetsStateAndPropagates",
    "concurrentInitializationSharesOneSuccessfulRecovery",
):
    require(f"fun {test_name}()" in unit_tests, f"unit test covers {test_name}")

schema = read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt")
require(re.search(r"version\s*=\s*7", schema) is not None,
        "Room schema remains version 7; Part 2 introduces no database migration")

print("Part 2 startup recovery static contract: PASS")
