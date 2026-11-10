#!/usr/bin/env python3
"""Verify exact dependency selectors and lock rules that do not conflict with AGP/CycloneDX."""
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []
root = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if "configurations.configureEach" not in root:
    errors.append("Per-configuration dependency-lock policy is missing")
if "resolutionStrategy.activateDependencyLocking()" not in root:
    errors.append("Dependency locking is not enabled for ordinary resolvable configurations")
if "configurations.classpath" not in root:
    errors.append("Root KGP/KSP buildscript classpath is not dependency-locked")
if "resolutionStrategy.failOnDynamicVersions()" in root:
    errors.append("Do not set failOnDynamicVersions alongside dependency locking; AGP/CycloneDX already guard their own configurations")
if not re.search(
    r"val guardedByPluginDynamicPolicy\s*=\s*name\.endsWith\(\"runtimeClasspath\", ignoreCase\s*=\s*true\).*?name == \"cyclonedxBom\".*?name == \"cyclonedxDirectBom\".*?if \(guardedByPluginDynamicPolicy\)\s*\{.*?resolutionStrategy\.deactivateDependencyLocking\(\).*?else if \(isCanBeResolved\)\s*\{\s*resolutionStrategy\.activateDependencyLocking\(\)",
    root,
    re.S,
):
    errors.append("Runtime classpaths and CycloneDX configurations must explicitly deactivate locking; other resolvable configurations must be locked")

props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
if re.search(r"(?m)^org\.gradle\.configuration-cache\s*=\s*true\s*$", props):
    errors.append("Configuration cache remains disabled until AGP 9.3.1 Navigation/dependency-report serialization is verified fixed")

catalog_path = ROOT / "gradle/libs.versions.toml"
try:
    catalog = tomllib.loads(catalog_path.read_text(encoding="utf-8"))
except tomllib.TOMLDecodeError as exc:
    errors.append(f"Version catalog TOML is invalid: {exc}")
    catalog = {}
for alias, value in catalog.get("versions", {}).items():
    version = value if isinstance(value, str) else value.get("strictly", "") if isinstance(value, dict) else ""
    if not re.fullmatch(r"[0-9]+(?:\.[0-9A-Za-z_-]+)+(?:[-+][0-9A-Za-z._-]+)?", version):
        errors.append(f"Non-exact/dynamic version selector in version catalog: {alias}={version!r}")

for file in ROOT.rglob("*.gradle.kts"):
    if any(part in {"build", ".gradle"} for part in file.parts):
        continue
    for line_no, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
        if re.search(r"(?i)(implementation|api|runtimeOnly|testImplementation|classpath|ksp)\s*\(\s*[\"'][^\"']*(?:\+|SNAPSHOT|latest\.)", line):
            errors.append(f"Dynamic dependency declaration in {file.relative_to(ROOT)}:{line_no}")

if errors:
    print("Gradle reproducibility verification FAILED:", file=sys.stderr)
    for item in errors:
        print(f" - {item}", file=sys.stderr)
    sys.exit(1)
print(f"All {len(catalog.get('versions', {}))} catalog versions are exact; root buildscript plus ordinary resolvable project configurations are locked; AGP runtime and CycloneDX configurations are explicitly unlocked to avoid the locking/failOnDynamicVersions conflict.")
