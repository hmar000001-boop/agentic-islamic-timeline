#!/usr/bin/env python3
"""Fail early when AGP's legacy Kotlin plugin compatibility flags drift apart."""
from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" not in line and ":" not in line:
            raise ValueError(f"Malformed Gradle property at {path}:{line_number}")
        separator = "=" if "=" in line else ":"
        key, value = line.split(separator, 1)
        key, value = key.strip(), value.strip()
        if key in values:
            raise ValueError(f"Duplicate Gradle property {key!r} at {path}:{line_number}")
        values[key] = value
    return values


def validate(root: Path) -> None:
    properties = read_properties(root / "gradle.properties")
    required = {
        "android.builtInKotlin": "false",
        "android.newDsl": "false",
    }
    for key, expected in required.items():
        actual = properties.get(key)
        if actual != expected:
            raise ValueError(
                f"{key} must be {expected!r} while org.jetbrains.kotlin.android is applied; "
                f"found {actual!r}. AGP 9 requires both compatibility opt-outs."
            )

    catalog = tomllib.loads((root / "gradle/libs.versions.toml").read_text(encoding="utf-8"))
    agp = str(catalog.get("versions", {}).get("agp", ""))
    match = re.match(r"^(\d+)\.", agp)
    if not match:
        raise ValueError(f"Could not determine pinned AGP major version from {agp!r}")
    if int(match.group(1)) >= 10:
        raise ValueError(
            f"AGP {agp} cannot use android.newDsl=false/android.builtInKotlin=false; "
            "migrate Android modules to built-in Kotlin and validate KSP before upgrading to AGP 10+."
        )
    if int(match.group(1)) != 9:
        raise ValueError(f"This compatibility profile is defined only for AGP 9.x, found {agp}")

    plugins = catalog.get("plugins", {})
    kotlin_android = plugins.get("kotlin-android", {})
    if kotlin_android.get("id") != "org.jetbrains.kotlin.android":
        raise ValueError("Version catalog must define kotlin-android as org.jetbrains.kotlin.android")

    android_modules = []
    for build_file in sorted(root.rglob("build.gradle.kts")):
        if build_file == root / "build.gradle.kts":
            continue
        content = build_file.read_text(encoding="utf-8")
        if re.search(r"alias\(libs\.plugins\.android\.(?:application|library)\)", content):
            android_modules.append(build_file)
            if "alias(libs.plugins.kotlin.android)" not in content:
                raise ValueError(
                    f"{build_file.relative_to(root)} applies AGP but not the external Kotlin Android plugin"
                )
    if not android_modules:
        raise ValueError("No Android application/library modules were found")

    root_build = (root / "build.gradle.kts").read_text(encoding="utf-8")
    if "alias(libs.plugins.kotlin.android) apply false" not in root_build:
        raise ValueError("Root build must declare the Kotlin Android plugin with apply false")

    print(
        f"AGP/Kotlin compatibility guard: PASS (AGP {agp}; both legacy compatibility opt-outs set; "
        f"{len(android_modules)} Android modules use the external Kotlin Android plugin)"
    )


if __name__ == "__main__":
    try:
        validate(Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1])
    except (OSError, ValueError, tomllib.TOMLDecodeError) as exc:
        print(f"AGP/Kotlin compatibility guard: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
