#!/usr/bin/env python3
"""Guard the complete AGP 9 built-in Kotlin/new DSL migration and AGP 10 readiness."""
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
errors = []

def fail(message: str) -> None:
    errors.append(message)

props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
for obsolete in ("android.builtInKotlin=false", "android.newDsl=false"):
    if obsolete in props:
        fail(f"Obsolete AGP opt-out remains: {obsolete}")
if not re.search(r"(?m)^org\.gradle\.configuration-cache\s*=\s*false\s*$", props):
    fail("Configuration cache must stay explicitly disabled until AGP 9.3.1 task serialization issues are resolved")

module_files = sorted(p for p in ROOT.glob("**/build.gradle.kts") if p != ROOT / "build.gradle.kts")
if not module_files:
    fail("No Gradle module build files found")
for file in module_files:
    content = file.read_text(encoding="utf-8")
    if "org.jetbrains.kotlin.android" in content or "libs.plugins.kotlin.android" in content:
        fail(f"External Kotlin Android plugin still applied in {file.relative_to(ROOT)}")
    if re.search(r"(?m)^\s*android\s*\{", content):
        fail(f"Legacy android extension accessor remains in {file.relative_to(ROOT)}")
    if "com.android.build.api.dsl.ApplicationExtension" not in content and "com.android.build.api.dsl.LibraryExtension" not in content:
        fail(f"No typed public Android DSL extension import in {file.relative_to(ROOT)}")

# Catch AGP APIs that produced the warnings in the user's CI log, including plugin/DSL
# spellings left in a module build script after a partial migration.
legacy_build_api_patterns = (
    r"\bapplicationVariants\b", r"\blibraryVariants\b", r"\btestVariants\b",
    r"\bunitTestVariants\b", r"\bkotlinOptions\b", r"\bbuildConfigField\s*\("
)
for file in ROOT.rglob("*.gradle.kts"):
    if any(part in {"build", ".gradle"} for part in file.parts):
        continue
    content = file.read_text(encoding="utf-8")
    for pattern in legacy_build_api_patterns:
        if re.search(pattern, content):
            fail(f"Deprecated AGP/Kotlin build API {pattern} remains in {file.relative_to(ROOT)}")

catalog_text = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
try:
    catalog = tomllib.loads(catalog_text)
except tomllib.TOMLDecodeError as exc:
    fail(f"Version catalog TOML invalid: {exc}")
    catalog = {}
if catalog.get("plugins", {}).get("kotlin-compose", {}).get("id") != "org.jetbrains.kotlin.plugin.compose":
    fail("Compose compiler Gradle plugin is missing")
if catalog.get("plugins", {}).get("ksp", {}).get("id") != "com.google.devtools.ksp":
    fail("KSP plugin is missing")
if catalog.get("versions", {}).get("kotlin") != "2.4.21":
    fail("Kotlin compiler/Compose version must remain 2.4.21")
if catalog.get("versions", {}).get("ksp") != "2.3.13":
    fail("KSP must be pinned to published 2.3.13")

root = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
for required in (
    'classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")',
    'classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.13")',
):
    if required not in root:
        fail(f"Root Gradle configuration missing explicit built-in Kotlin/KSP compiler classpath: {required}")
if "dependencyLocking { lockAllConfigurations() }" in root:
    fail("Global lockAllConfigurations conflicts with AGP runtime/CycloneDX dynamic-version enforcement; use the tested selective lock policy")

app = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if "ApplicationAndroidComponentsExtension" not in app or "variant.buildConfigFields.put(" not in app:
    fail("App BuildConfig must be configured through lazy Android Components Variant API")
if "buildConfigField(" in app:
    fail("Deprecated eager buildConfigField() DSL remains in app module")

if errors:
    print("Built-in Kotlin / modern AGP migration verification FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print(f"Built-in Kotlin and modern AGP DSL verified across {len(module_files)} Android modules; no transitional AGP opt-outs.")
