#!/usr/bin/env python3
"""Guard Compose 1.12's API 37 compile requirement without raising targetSdk."""
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

android_modules = sorted(ROOT.rglob("build.gradle.kts"))
seen = 0
for path in android_modules:
    if path == ROOT / "build.gradle.kts":
        continue
    source = path.read_text(encoding="utf-8")
    match = re.search(r"\bcompileSdk\s*=\s*(\d+)", source)
    if not match:
        continue
    seen += 1
    value = int(match.group(1))
    if value < 37:
        errors.append(f"{path.relative_to(ROOT)} compiles against API {value}; Compose 1.12 needs API 37")
if seen != 15:
    errors.append(f"Expected 15 Android modules with explicit compileSdk, found {seen}")

app = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if not re.search(r"\btargetSdk\s*=\s*36\b", app):
    errors.append("App targetSdk must remain API 36; compileSdk upgrade must not opt into Android 17 behavior")
workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
if '"platforms;android-37"' not in workflow:
    errors.append("CI must install Android SDK Platform 37 for compile-time AAR metadata")
if '"system-images;android-36;google_apis;x86_64"' not in workflow or 'timeline-ci-api36' not in workflow:
    errors.append("CI should retain the API 36 emulator used for runtime compatibility tests")

if errors:
    print("Compose/API compile SDK verification FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print("Compose/API compile SDK guard passed: 15 Android modules compile with API 37, app targetSdk and CI emulator stay at API 36, and CI installs platform 37.")
