#!/usr/bin/env python3
"""Fail early unless the exact Android SDK Platform 37.0 package is installed."""
from pathlib import Path
import os
import re
import sys


def _properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def validate_sdk_platform(sdk_root: Path) -> Path:
    sdk_root = sdk_root.expanduser().resolve(strict=True)
    platforms_root = (sdk_root / "platforms").resolve(strict=True)
    package_dir = sdk_root / "platforms" / "android-37.0"
    if not package_dir.exists():
        raise RuntimeError(
            f"Android SDK Platform 37.0 is missing: {package_dir}. "
            "Install the package platforms;android-37.0 (not platforms;android-37)."
        )
    resolved = package_dir.resolve(strict=True)
    try:
        resolved.relative_to(platforms_root)
    except ValueError as exc:
        raise RuntimeError("Android SDK Platform 37.0 resolves outside the SDK platforms directory") from exc
    if not resolved.is_dir():
        raise RuntimeError(f"Android SDK Platform 37.0 is not a directory: {resolved}")

    android_jar = resolved / "android.jar"
    source_properties = resolved / "source.properties"
    if not android_jar.is_file() or android_jar.stat().st_size == 0:
        raise RuntimeError(f"Android SDK Platform 37.0 has no valid android.jar: {android_jar}")
    if not source_properties.is_file():
        raise RuntimeError(f"Android SDK Platform 37.0 has no source.properties: {source_properties}")
    properties = _properties(source_properties)
    # The actual SDK Platform 37.0 package reports AndroidVersion.ApiLevel=37.0
    # (one dotted value), not ApiLevel=37 plus a separate MinorApiLevel=0.
    # Accept the split representation as a compatibility form, but normalize
    # both representations to the same (major, minor) tuple before comparison.
    raw_api = properties.get("AndroidVersion.ApiLevel", "")
    match = re.fullmatch(r"(?P<major>\d+)(?:\.(?P<minor>\d+))?", raw_api)
    if match is None:
        raise RuntimeError(
            "SDK package android-37.0 has an invalid AndroidVersion.ApiLevel: "
            f"{raw_api!r}; expected '37.0'"
        )
    major = int(match.group("major"))
    dotted_minor = int(match.group("minor") or "0")
    separate_minor_raw = properties.get("AndroidVersion.MinorApiLevel")
    if separate_minor_raw is not None:
        if not re.fullmatch(r"\d+", separate_minor_raw):
            raise RuntimeError(
                "SDK package android-37.0 has an invalid AndroidVersion.MinorApiLevel: "
                f"{separate_minor_raw!r}"
            )
        separate_minor = int(separate_minor_raw)
        if match.group("minor") is not None and separate_minor != dotted_minor:
            raise RuntimeError(
                "SDK package android-37.0 has conflicting dotted and separate minor API levels"
            )
        dotted_minor = separate_minor
    if (major, dotted_minor) != (37, 0):
        raise RuntimeError(
            "SDK package android-37.0 reports an unexpected Android API version: "
            f"{major}.{dotted_minor}; expected 37.0"
        )
    return resolved


def main() -> int:
    sdk_value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_value:
        print("ANDROID_HOME or ANDROID_SDK_ROOT must be set", file=sys.stderr)
        return 2
    try:
        platform_dir = validate_sdk_platform(Path(sdk_value))
    except (OSError, RuntimeError) as exc:
        print(f"Android SDK Platform verification FAILED: {exc}", file=sys.stderr)
        return 1
    print(f"Verified Android SDK Platform API 37.0: {platform_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
