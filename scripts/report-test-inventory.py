#!/usr/bin/env python3
"""Summarize Surefire/Failsafe XML reports without changing test execution."""
from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    reports = sorted(args.root.glob("**/target/surefire-reports/TEST-*.xml"))
    reports += sorted(args.root.glob("**/target/failsafe-reports/TEST-*.xml"))
    rows: list[tuple[str, int, int, int, int]] = []
    for report in sorted(set(reports)):
        try:
            root = ET.parse(report).getroot()
        except (ET.ParseError, OSError) as exc:
            print(f"Invalid test report {report}: {exc}", file=sys.stderr)
            return 2
        rows.append((
            str(report.relative_to(args.root)),
            int(root.attrib.get("tests", 0)),
            int(root.attrib.get("failures", 0)),
            int(root.attrib.get("errors", 0)),
            int(root.attrib.get("skipped", 0)),
        ))

    if not rows:
        print("No Surefire/Failsafe XML reports were found.", file=sys.stderr)
        return 2

    totals = tuple(sum(row[index] for row in rows) for index in range(1, 5))
    lines = [
        "Retail Agent test inventory",
        "===========================",
        f"Report files: {len(rows)}",
        f"Tests: {totals[0]} | failures: {totals[1]} | errors: {totals[2]} | skipped: {totals[3]}",
        "",
        "Per test class:",
    ]
    lines.extend(
        f"- {name}: tests={tests}, failures={failures}, errors={errors}, skipped={skipped}"
        for name, tests, failures, errors, skipped in rows
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return 0 if totals[0] > 0 and totals[1] == 0 and totals[2] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
