#!/usr/bin/env python3
"""Prevent nullable public API properties from being smart-cast across modules."""
from pathlib import Path

app = Path("app/src/main/java/app/timeline/islamic/TimelineApplication.kt").read_text(encoding="utf-8")
manager = Path("core/model-manager/src/main/java/app/timeline/islamic/core/models/ModelManager.kt").read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")

app_start = app.index("private fun runtimeLoadBlockReason(")
app_end = app.index("\n    private companion object", app_start)
app_function = app[app_start:app_end]
require("val thermalStatus = snapshot.thermalStatus" in app_function,
        "app must snapshot nullable cross-module thermalStatus once")
require("thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE" in app_function,
        "app thermal guard must narrow the stable local value")
require("snapshot.thermalStatus != null && snapshot.thermalStatus" not in app_function,
        "app must not smart-cast the external public property directly")

manager_start = manager.index("fun preflight(modelId: String, snapshot: ModelResourceSnapshot)")
manager_end = manager.index("\n    private suspend fun verifyFile", manager_start)
manager_function = manager[manager_start:manager_end]
require("val thermalStatus = snapshot.thermalStatus" in manager_function,
        "model manager must snapshot nullable thermalStatus once")
require("thermalStatus != null && thermalStatus >= THERMAL_STATUS_SEVERE" in manager_function,
        "model manager must use the stable local value for threshold decision")
require("thermalStatus = thermalStatus" in manager_function,
        "preflight result must report the same observation used for its decision")

# Keep the exact non-fatal compiler-warning sites from the preceding CI run clean.
publisher = Path("core/corpus/src/main/java/app/timeline/islamic/core/corpus/SignedPublisherKeyringArchiveImporter.kt").read_text(encoding="utf-8")
require("return payload!! to signature!!" not in publisher and "requireNotNull(payload)" in publisher and "requireNotNull(signature)" in publisher,
        "keyring archive payload/signature must use explicit null validation")
corpus_ui = Path("feature/corpus-management/src/main/java/app/timeline/islamic/feature/corpus/CorpusManagementFeature.kt").read_text(encoding="utf-8")
require("import androidx.compose.material3.HorizontalDivider" in corpus_ui and not any(line.strip().startswith("Divider(") for line in corpus_ui.splitlines()),
        "corpus UI must use the current Material3 HorizontalDivider API")
prayer_test = Path("feature/prayer/src/test/java/app/timeline/islamic/feature/prayer/PrayerCalculatorTest.kt").read_text(encoding="utf-8")
require("transitionDay.sunrise!!.minusMinutes" not in prayer_test,
        "prayer DST test must not assert non-null on a non-null sunrise result")
migrations = Path("core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabaseMigrations.kt").read_text(encoding="utf-8")
require("override fun migrate(db: SupportSQLiteDatabase)" in migrations and "migrate(database:" not in migrations,
        "Room migration override parameter must match the supertype's db name")
authorization_store = Path("core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomAuthorizationStore.kt").read_text(encoding="utf-8")
require("lease!!" not in authorization_store and "val ownedLease = requireNotNull(lease)" in authorization_store,
        "authorization lease must be explicitly validated after fence ownership")
print("PASS: cross-module thermal nullability contract and all warning cleanups from the supplied CI log")
