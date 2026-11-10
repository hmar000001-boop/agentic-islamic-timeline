#!/usr/bin/env python3
"""Source contract for Phase 6 deterministic, offline Islamic domain engines and their UI wiring."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
read = lambda path: (ROOT / path).read_text(encoding="utf-8")
settings = read("settings.gradle.kts")
app_gradle = read("app/build.gradle.kts")
activity = read("app/src/main/java/app/timeline/islamic/MainActivity.kt")
app = read("app/src/main/java/app/timeline/islamic/TimelineApplication.kt")
hub = read("feature/domain-hub/src/main/java/app/timeline/islamic/feature/domainhub/DomainHubFeature.kt")
workflow = read(".github/workflows/android-ci.yml")
errors = []
checks = 0

def require(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        errors.append(message)

modules = {
    "prayer": ("PrayerCalculator.kt", "PrayerCalculatorTest.kt"),
    "hifz": ("HifzReviewPlanner.kt", "HifzReviewPlannerTest.kt"),
    "travel": ("TravelPlanning.kt", "TravelPlanningTest.kt"),
    "halal": ("HalalEvidenceAssessment.kt", "HalalEvidenceAssessmentTest.kt"),
    "zakat": ("ZakatCalculator.kt", "ZakatCalculatorTest.kt"),
    "inheritance": ("InheritanceCaseEngine.kt", "InheritanceCaseEngineTest.kt"),
    "community-local": ("CommunitySnapshot.kt", "CommunitySnapshotTest.kt"),
}

require('include(":core:domain-api")' in settings and 'include(":feature:domain-hub")' in settings,
        "domain API and hub modules are included")
require('implementation(project(":core:domain-api"))' in app_gradle and
        'implementation(project(":feature:domain-hub"))' in app_gradle and
        'domainWorkspaceRepository' in app and 'DomainHubFeature(' in activity,
        "application wires the domain hub to the encrypted Room workspace repository")
require('versionCode = 25' in app_gradle and 'versionName = "0.7.0-phase6-domain-engines"' in app_gradle,
        "Phase 6 application identity is 0.7.0 / versionCode 25")
require("fun persist(" in hub and "repository.create(" in hub and "repository.list(" in hub and "repository.delete(" in hub,
        "hub saves, lists and explicitly deletes local drafts")
require("DomainPayloadCodec.encode" in hub and "withContext(Dispatchers.IO)" in hub,
        "bounded payload encoding and repository I/O stay off the UI thread")
require("domainWorkspaceRepository" in app and "RoomDomainWorkspaceRepository(database)" in app,
        "domain workspace is backed by the existing encrypted Room database, not plaintext files")

for module, (main_file, test_file) in modules.items():
    folder = ROOT / "feature" / module
    build = (folder / "build.gradle.kts").read_text(encoding="utf-8")
    main_candidates = list((folder / "src/main").rglob(main_file))
    test_candidates = list((folder / "src/test").rglob(test_file))
    require(f'include(":feature:{module}")' in settings, f"{module}: registered Gradle module")
    require(len(main_candidates) == 1 and len(test_candidates) == 1, f"{module}: one engine source and one focused unit-test source")
    require("release(37)" in build and "minorApiLevel = 0" in build and "minSdk = 26" in build,
            f"{module}: Android API/bytecode configuration is pinned")
    require(f":feature:{module}:testDebugUnitTest" in workflow and f":feature:{module}:lint" in workflow,
            f"{module}: CI includes its unit tests and lint")

prayer = read("feature/prayer/src/main/java/app/timeline/islamic/feature/prayer/PrayerCalculator.kt")
require("data class PrayerCalculationProfile" in prayer and "HighLatitudeRule" in prayer and
        "fajrDepressionDegrees" in prayer and "asrShadowFactor" in prayer,
        "prayer engine requires explicit coordinates, timezone, angles, Asr and high-latitude policy")
require("SUNRISE_SUNSET_UNAVAILABLE_AT_THIS_LATITUDE" in prayer and "HIGH_LATITUDE_FALLBACK_APPLIED" in prayer,
        "prayer engine reports unavailable/extreme-latitude conditions rather than fabricating events")
require("previousSunset.atZone(zone).toInstant()" in prayer and "sunrise.atZone(zone).toInstant()" in prayer and
        "nextSunrise.atZone(zone).toInstant()" in prayer,
        "high-latitude night fractions use elapsed instants across daylight-saving transitions")
require("usesElapsedInstantDurationAcrossSpringDstForHighLatitudeFallback" in read("feature/prayer/src/test/java/app/timeline/islamic/feature/prayer/PrayerCalculatorTest.kt"),
        "prayer tests include a spring DST high-latitude fallback regression")

hifz = read("feature/hifz/src/main/java/app/timeline/islamic/feature/hifz/HifzReviewPlanner.kt")
require("stableEvidenceKey" in hifz and "mushafEditionId" in hifz and "quality in 0..5" in hifz,
        "Hifz review is tied to an explicit Mushaf reference and an explicit user rating")
require("tajwid" in hifz and "does not score recitation" in hifz,
        "Hifz engine explicitly does not score recitation or tajwid")

travel = read("feature/travel/src/main/java/app/timeline/islamic/feature/travel/TravelPlanning.kt")
require("getValidOffsets" in travel and "daylight-saving gap" in travel and "Ambiguous local time" in travel,
        "travel planner rejects DST gaps and requires a selected offset for overlaps")
resolver_signature = re.search(r"fun resolve\(([^)]*)\)", travel)
require(resolver_signature is not None and "hasApprovedRuleset" not in resolver_signature.group(1) and "NO_APPROVED_RULESET" in travel,
        "travel fiqh guidance cannot be elevated by a caller-provided approval Boolean")

halal = read("feature/halal/src/main/java/app/timeline/islamic/feature/halal/HalalEvidenceAssessment.kt")
require('conclusion: String = "NO_HALAL_OR_HARAM_VERDICT"' in halal,
        "Halal engine never emits an automatic halal/haram verdict")
require("IDENTIFIED_REPORTED" in halal and "CURRENT_AND_SOURCE_VERIFIED" not in halal,
        "user-supplied certification metadata remains reported evidence, not verified certification")

zakat = read("feature/zakat/src/main/java/app/timeline/islamic/feature/zakat/ZakatCalculator.kt")
require("BigInteger" in zakat and "nisabAmountMinor > 0" in zakat and "haulDays" in zakat,
        "Zakat calculation uses bounded integer arithmetic, positive manual nisab and haul policy")
require("valuationDate != input.valuationDate" in zakat and "PREVIEW_ONLY_RULES_UNVERIFIED" in zakat and
        "REVIEWED_RULESET_ARITHMETIC" not in zakat and "BELOW_NISAB" not in zakat and "HAUL_NOT_COMPLETE" not in zakat,
        "Zakat date checks and all arithmetic outcomes remain unverified previews, not fiqh status")
require("calculationNote" in zakat and "not a religious conclusion" in read("feature/zakat/src/test/java/app/timeline/islamic/feature/zakat/ZakatCalculatorTest.kt"),
        "Zakat results explain that threshold arithmetic does not establish a religious conclusion")

inheritance = read("feature/inheritance/src/main/java/app/timeline/islamic/feature/inheritance/InheritanceCaseEngine.kt")
require("allPotentialHeirsEnumerated" in inheritance and "NEEDS_CONFIRMED_FACTS" in inheritance,
        "inheritance arithmetic is blocked until all potential heir categories and financial facts are confirmed")
require("NEEDS_APPROVED_RULESET" in inheritance and "No heir shares calculated" in inheritance,
        "inheritance never distributes shares without approved rules")

community = read("feature/community-local/src/main/java/app/timeline/islamic/feature/community/local/CommunitySnapshot.kt")
require("TL-COMMUNITY-V1" in community and "NO_LIVE_UPDATES" in community and "STALE" in community,
        "community feature is a versioned offline import with explicit freshness and no live claims")
require("sourceCapturedAt = Instant.parse(header[2])" in community and "SOURCE_CAPTURE_TIME_IN_FUTURE" in community,
        "community source capture time is parsed separately from local import time")
require("Mark stale after this many days (your policy)" in hub and "snapshot.freshness(now, Duration.ofDays(maxAgeDays.toLong()))" in hub,
        "community UI uses a user-selected age threshold instead of assuming import time means freshness")
require("MAX_INPUT_BYTES" in community and "MAX_LINES" in community and "sourceVersion" in community,
        "community importer enforces bounded input and version consistency")

require("MIGRATION_6_7" in read("core/persistence/src/main/java/app/timeline/islamic/core/persistence/EncryptedDatabaseFactory.kt") and
        ":core:persistence:connectedDebugAndroidTest" in workflow,
        "CI runs SQLCipher Room workspace migration and CRUD instrumentation on API 36")
workspace_androidtest = read("core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomDomainWorkspaceTest.kt")
require("encryptedRoomWorkspaceSupportsHashVerifiedCasAndRejectsTampering" in workspace_androidtest and
        "additiveV6ToV7MigrationPreservesExistingEncryptedDatabaseRows" in workspace_androidtest and
        "SupportOpenHelperFactory" in workspace_androidtest,
        "SQLCipher Android tests cover workspace CRUD/CAS/hash tampering and additive migration preservation")
require("scripts/verify-phase6-domain-workspace.py" in workflow and "scripts/test-phase6-domain-engines.py" in workflow,
        "CI executes both Phase 6 independent source contracts")

# Keep offline manifests network-free.
for manifest in (ROOT / "app/src").rglob("AndroidManifest.xml"):
    root = ET.parse(manifest).getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    for node in root.findall("uses-permission"):
        require(node.attrib.get(android + "name") != "android.permission.INTERNET",
                f"offline app manifest has no INTERNET permission ({manifest.relative_to(ROOT)})")

if errors:
    print("Phase 6 domain-engine contract FAILED:", file=sys.stderr)
    for error in errors:
        print(" - " + error, file=sys.stderr)
    raise SystemExit(1)
print(f"Phase 6 domain-engine contract passed: {checks}/{checks} source, safety, module and CI checks")
