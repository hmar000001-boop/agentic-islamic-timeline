#!/usr/bin/env python3
"""Guard Compose 1.12's API 37.0 compile requirement without raising targetSdk."""
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []
catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8"))
compose_bom = catalog.get("versions", {}).get("composeBom", "")
if compose_bom != "2026.09.00":
    errors.append(f"Unexpected Compose BOM {compose_bom!r}; review its compileSdk requirement before changing the baseline")

# API 37 is published as the minor-versioned SDK package android-37.0. Use
# AGP's typed release/minor API DSL so AGP resolves that exact SDK identity.
compile_api_pattern = re.compile(
    r"compileSdk\s*\{\s*version\s*=\s*release\(37\)\s*\{\s*minorApiLevel\s*=\s*0\s*}\s*}",
    re.MULTILINE,
)
android_modules = sorted(ROOT.rglob("build.gradle.kts"))
seen = 0
for path in android_modules:
    if path == ROOT / "build.gradle.kts":
        continue
    source = path.read_text(encoding="utf-8")
    if "compileSdk" not in source:
        continue
    seen += 1
    if not compile_api_pattern.search(source):
        errors.append(
            f"{path.relative_to(ROOT)} must configure compileSdk with release(37) {{ minorApiLevel = 0 }}"
        )
if seen != 17:
    errors.append(f"Expected 17 Android modules with explicit compileSdk, found {seen}")

app = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if not re.search(r"\btargetSdk\s*=\s*36\b", app):
    errors.append("App targetSdk must remain API 36; compile SDK upgrade must not opt into Android 17 behavior")
workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
if '"platforms;android-37.0"' not in workflow:
    errors.append("CI must install the exact Android SDK Platform 37.0 package: platforms;android-37.0")
if '"platforms;android-37"' in workflow:
    errors.append("CI must not request the non-existent platforms;android-37 package path")
if "verify-android-sdk-platform.py" not in workflow:
    errors.append("CI must verify the installed API 37.0 platform before starting Gradle")
emulator_setup = (ROOT / "scripts/start-api36-emulator.sh").read_text(encoding="utf-8")
if '"system-images;android-36;google_apis;x86_64"' not in workflow or "timeline-ci-api36" not in emulator_setup:
    errors.append("CI should retain the API 36 emulator used for runtime compatibility tests")

if errors:
    print("Compose/API compile SDK verification FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print(
    "Compose/API compile SDK guard passed: all 17 Android modules select API 37.0, "
    "CI installs and verifies platforms;android-37.0, targetSdk and the emulator stay at API 36."
)
