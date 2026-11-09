#!/usr/bin/env python3
"""Static Phase 5A guards: offline model catalog, SAF, lifecycle, storage and UI wiring."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def require(condition: bool, label: str) -> None:
    if not condition:
        errors.append(label)

settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
app_gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
model_gradle = (ROOT / "core/model-manager/build.gradle.kts").read_text(encoding="utf-8")
feature_gradle = (ROOT / "feature/model-management/build.gradle.kts").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/app/timeline/islamic/TimelineApplication.kt").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/app/timeline/islamic/MainActivity.kt").read_text(encoding="utf-8")
catalog = (ROOT / "core/model-manager/src/main/java/app/timeline/islamic/core/models/ModelCatalog.kt").read_text(encoding="utf-8")
manager = (ROOT / "core/model-manager/src/main/java/app/timeline/islamic/core/models/ModelManager.kt").read_text(encoding="utf-8")
registry = (ROOT / "core/model-manager/src/main/java/app/timeline/islamic/core/models/ModelRegistry.kt").read_text(encoding="utf-8")
file_store = (ROOT / "core/model-manager/src/main/java/app/timeline/islamic/core/models/AndroidModelFileStore.kt").read_text(encoding="utf-8")
saf = (ROOT / "core/model-manager/src/main/java/app/timeline/islamic/core/models/SafModelArtifactImporter.kt").read_text(encoding="utf-8")
probe = (ROOT / "core/model-manager/src/main/java/app/timeline/islamic/core/models/AndroidDeviceResourceProbe.kt").read_text(encoding="utf-8")
feature = (ROOT / "feature/model-management/src/main/java/app/timeline/islamic/feature/models/ModelManagementFeature.kt").read_text(encoding="utf-8")
dao = (ROOT / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/Daos.kt").read_text(encoding="utf-8")
database = (ROOT / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")

require('include(":core:model-manager")' in settings and 'include(":feature:model-management")' in settings,
        "both Phase 5A modules are included in settings.gradle.kts")
require('implementation(project(":core:model-manager"))' in app_gradle and 'implementation(project(":feature:model-management"))' in app_gradle,
        "app declares production dependencies on model manager and Models feature")
for path, source in (("core/model-manager", model_gradle), ("feature/model-management", feature_gradle)):
    require("release(37)" in source and "minorApiLevel = 0" in source and "minSdk = 26" in source,
            f"{path} targets compileSdk 37.0 and minSdk 26")
require('versionCode = 23' in app_gradle and 'versionName = "0.6.2-phase5c-dense-retrieval"' in app_gradle,
        "app version is 0.6.1 versionCode 22 while preserving the Phase 5A module")

for model_id in ("gemma-4-e2b-it", "gemma-4-e4b-it", "embeddinggemma-2-text-270m"):
    require(f'id = "{model_id}"' in catalog, f"pinned catalog entry exists for {model_id}")
require(len(re.findall(r'revision = "[a-f0-9]{40}"', catalog)) == 3,
        "each catalog artifact is pinned to a full immutable 40-character revision")
require(len(re.findall(r'sha256 = "[a-f0-9]{64}"', catalog)) == 3,
        "each catalog artifact has a 64-character SHA-256 digest")
require(len(re.findall(r'expectedBytes = [0-9_]+L', catalog)) == 3,
        "each catalog artifact has an exact byte length")
require(catalog.count('licenseId = APACHE_2_0') == 3,
        "all pinned catalog artifacts declare Apache-2.0")
require(all(revision in catalog for revision in (
    "6e5c4f1e395deb959c494953478fa5cec4b8008f",
    "28299f30ee4d43294517a4ac93abd6163412f07f",
    "9be6e8b90982095dc05c2bd162e4b954ee4dbac7")) and catalog.count("/blob/") >= 3,
        "each catalog license/source README is pinned to the same immutable repository revision as its artifact")

require('uri.scheme == "content"' in saf and 'openInputStream(uri)' in saf,
        "SAF importer accepts user-selected content URIs and streams their contents")
require("takePersistableUriPermission" not in saf and "requestLegacyExternalStorage" not in saf,
        "model SAF importer does not retain URI grants or request broad storage access")
require("noBackupFilesDir" in file_store and 'ROOT_DIRECTORY_NAME = "on-device-models"' in file_store,
        "model weights are isolated under app-private noBackupFilesDir")
require("Os.rename" in file_store and "Os.fsync" in file_store and "createNewFile()" in file_store,
        "model staging uses exclusive create, atomic same-directory rename and fsync")
require('MessageDigest.getInstance("SHA-256")' in manager and "STREAM_BUFFER_BYTES = 64 * 1024" in manager,
        "import streams in 64 KiB chunks and verifies SHA-256")
require("expectedBytes" in manager and "copied == spec.expectedBytes" in manager,
        "import enforces both exact maximum and final byte length")
require("QUARANTINED" in manager and "REMOVAL_PENDING" in manager and "INSTALLING" in manager,
        "model lifecycle has fail-closed install, quarantine and removal recovery states")
require("deleteCommittedFile" in manager and "deleteStagingFile" in manager,
        "failed import rollback only deletes files created by that import attempt")
require("createInstallOwnershipMarker" in manager and "ownsInstallAttempt" in manager and
        "deleteInterruptedInstallFiles" in manager,
        "interrupted install recovery requires a matching durable per-attempt ownership marker")
require("artifact.exists()" in manager and "ModelLifecycleStatus.QUARANTINED" in manager,
        "invalid pre-existing final artifacts are quarantined rather than automatically deleted")
require("cleanupUnregisteredStagingFiles" not in manager and "cleanupUnregisteredStagingFiles" not in file_store,
        "unregistered partial artifacts are not deleted by filename pattern alone")
require("deleteOwnedFiles(spec, row.recordId)" in manager and "ownsInstallAttempt" in file_store,
        "explicit removal and pending recovery validate the matching registry ownership marker")
require("ModelCommitRecoveryRequired" in manager and "ModelCommitRecoveryRequired" in file_store,
        "post-rename durability failures preserve INSTALLING for exact recovery instead of guessing")
require("destination.createNewFile()" in file_store,
        "atomic rename reserves the final path exclusively and never replaces an unrelated pre-existing file")
require("createInstallOwnershipMarker" in file_store and "Os.fsync" in file_store and
        "ownsInstallAttempt" in file_store,
        "the file store durably binds staging/final bytes to the matching Room install record")
require("(staging.exists() || artifact.exists()) && !ownsInstallAttempt(spec, recordId)" in file_store,
        "file removal refuses to delete model bytes without the matching durable ownership marker")
require("currentCoroutineContext().ensureActive()" in manager,
        "large import and rehash loops respect coroutine cancellation between bounded chunks")
require("ModelPreflightDecision.RUNTIME_PROBE_REQUIRED" in manager and "runtimeReady: Boolean get() = false" in manager,
        "resource preflight never asserts runtime readiness")
require("totalMem" in probe and "lowMemory" in probe and "totalPss" in probe and "availableBytes" in probe and "currentThermalStatus" in probe,
        "device resource probe measures RAM, low-memory, PSS, private storage and thermal state")
require("AppDestination.MODELS" in activity and "ModelManagementFeature(" in activity and "OpenDocument()" in activity,
        "Models UI is reachable through navigation and uses the Android SAF picker")
require("LaunchedEffect(Unit) { refreshCorpusScreen() }" in activity and
        "destination = AppDestination.MODELS\n                                        refreshModelScreen()" in activity,
        "large model verification/recovery work is lazy and does not delay Timeline startup")
require("!state.inventoryReadable" in feature and "models_inventory_unavailable" in feature,
        "unreadable model inventory fails closed in the Models UI")
require("runtime not active" in (ROOT / "feature/model-management/src/main/res/values/strings.xml").read_text(encoding="utf-8"),
        "UI labels verified model files as inactive until a real runtime load test")
require("quarantineUnknownModelStatuses" in dao and "dao.quarantineUnknownModelStatuses()" in registry,
        "unknown persisted lifecycle states are normalized to quarantine")
require("version = 5" in database and "InstalledModelEntity::class" in database,
        "Phase 5A reuses the existing Room v5 installed_model entity without migration")
require(":core:model-manager:testDebugUnitTest" in workflow and "scripts/test-model-manager-contract.py" in workflow,
        "CI executes both the model manager unit tests and the independent Phase 5A contract script")
require(":core:model-manager:connectedDebugAndroidTest" in workflow and
        'testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"' in model_gradle,
        "CI runs Android instrumentation tests against the real Android model file store")
android_file_store_test = ROOT / "core/model-manager/src/androidTest/java/app/timeline/islamic/core/models/AndroidModelFileStoreTest.kt"
require(android_file_store_test.is_file() and len(re.findall(r"@Test", android_file_store_test.read_text(encoding="utf-8"))) >= 3,
        "real Android model file store has atomic-rename and destination-preservation instrumentation tests")
workflow_lines = workflow.splitlines()
phase5_step = next((index for index, line in enumerate(workflow_lines) if line.strip() == "- name: Verify Phase 5A offline model manager contract"), None)
require(phase5_step is not None and phase5_step + 1 < len(workflow_lines) and
        workflow_lines[phase5_step + 1].startswith("        run: python3 scripts/test-model-manager-contract.py"),
        "Phase 5A contract test is a standalone workflow step, not folded into a previous run block")
require("suspend fun importModelArtifact" in app and "BLOCKED_STORAGE" in app,
        "app checks private storage before delegating a selected model import")

manifest_files = list((ROOT / "app/src").rglob("AndroidManifest.xml"))
for manifest in manifest_files:
    try:
        root = ET.parse(manifest).getroot()
    except ET.ParseError as exc:
        errors.append(f"Invalid manifest {manifest.relative_to(ROOT)}: {exc}")
        continue
    for node in root.iter():
        if node.tag.endswith("uses-permission") and node.attrib.get("{http://schemas.android.com/apk/res/android}name") == "android.permission.INTERNET":
            errors.append(f"Offline app source declares INTERNET permission: {manifest.relative_to(ROOT)}")

# Ensure translations remain complete and XML resources parse correctly.
locale_keys: dict[str, set[str]] = {}
for locale in ("values", "values-en", "values-ar"):
    path = ROOT / f"feature/model-management/src/main/res/{locale}/strings.xml"
    require(path.is_file(), f"model management strings exist for {locale}")
    if path.is_file():
        try:
            parsed = ET.parse(path).getroot()
            locale_keys[locale] = {node.attrib["name"] for node in parsed.findall("string")}
        except ET.ParseError as exc:
            errors.append(f"Invalid model management string XML for {locale}: {exc}")
if len(locale_keys) == 3:
    require(locale_keys["values"] == locale_keys["values-en"] == locale_keys["values-ar"],
            "model management default, English and Arabic resources have identical key sets")

unit_test = ROOT / "core/model-manager/src/test/java/app/timeline/islamic/core/models/ModelManagerTest.kt"
require(unit_test.is_file() and len(re.findall(r"@Test", unit_test.read_text(encoding="utf-8"))) >= 20,
        "ModelManager has at least 20 focused unit tests")

if errors:
    print("Phase 5A Model Manager contract FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)
print("Phase 5A Model Manager contract passed: pinned catalog, closed SAF import, bounded hashing, atomic private install, Room v5 lifecycle, recovery, preflight, localized UI, offline manifest and CI coverage.")
