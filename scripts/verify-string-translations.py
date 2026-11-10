#!/usr/bin/env python3
"""Verify that every translatable Android string resource has English and Arabic entries."""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RESOURCE_TAGS = {"string", "plurals", "string-array", "integer-array"}
PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")


def read_resources(path: Path) -> dict[tuple[str, str], str]:
    root = ET.parse(path).getroot()
    resources: dict[tuple[str, str], str] = {}
    for element in root:
        name = element.attrib.get("name")
        if element.tag not in RESOURCE_TAGS or not name:
            continue
        if element.attrib.get("translatable", "true").lower() == "false":
            continue
        text = " ".join(part.strip() for part in element.itertext() if part and part.strip())
        resources[(element.tag, name)] = text
    return resources


def placeholder_signature(value: str) -> tuple[str, ...]:
    return tuple(sorted(PLACEHOLDER_RE.findall(value)))


def verify(root: Path) -> list[str]:
    errors: list[str] = []
    defaults = sorted(root.glob("**/src/main/res/values/strings.xml"))
    if not defaults:
        return [f"No default Android strings.xml resources found under {root}"]

    checked = 0
    for default_path in defaults:
        module = default_path.relative_to(root).as_posix().split("/src/main/res/values/strings.xml")[0]
        base = read_resources(default_path)
        if not base:
            continue
        checked += 1
        for locale in ("en", "ar"):
            locale_path = default_path.parent.parent / f"values-{locale}" / "strings.xml"
            if not locale_path.is_file():
                errors.append(f"{module}: missing values-{locale}/strings.xml")
                continue
            localized = read_resources(locale_path)
            missing = sorted(set(base) - set(localized))
            extra = sorted(set(localized) - set(base))
            for tag, name in missing:
                errors.append(f"{module}: {locale} is missing <{tag} name=\"{name}\">")
            for tag, name in extra:
                errors.append(f"{module}: {locale} has unknown <{tag} name=\"{name}\">")
            for key in sorted(set(base) & set(localized)):
                expected = placeholder_signature(base[key])
                actual = placeholder_signature(localized[key])
                if expected != actual:
                    tag, name = key
                    errors.append(
                        f"{module}: {locale} placeholder mismatch for <{tag} name=\"{name}\"> "
                        f"(default={expected}, localized={actual})"
                    )

    if not errors:
        print(f"Android string translations verified: {checked} modules/resources; English and Arabic keys and placeholders are complete.")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = verify(args.root.resolve())
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"Translation verification failed: {len(errors)} issue(s).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
