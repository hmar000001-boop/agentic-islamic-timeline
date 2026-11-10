#!/usr/bin/env python3
"""Guard Android API 26+ exact BigInteger conversion semantics."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


helper = ROOT / "core/common/src/main/java/app/timeline/islamic/core/common/BigIntegerCompat.kt"
source = helper.read_text(encoding="utf-8")
require("fun BigInteger.toLongExactCompat(): Long" in source, "Shared exact conversion helper missing")
require("Long.MIN_VALUE" in source and "Long.MAX_VALUE" in source, "Signed Long boundaries are not checked")
require("throw ArithmeticException" in source, "Overflow must throw rather than truncate")
require("return toLong()" in source, "API-26-compatible conversion must use the Number.toLong() mapping after bounds validation")
require("longValueExact(" not in source, "API-31-only API reintroduced in compatibility helper")

for module in ("inheritance", "zakat"):
    module_root = ROOT / "feature" / module
    build = (module_root / "build.gradle.kts").read_text(encoding="utf-8")
    require('implementation(project(":core:common"))' in build, f"{module} does not depend on the shared compatibility helper")
    kotlin_sources = list((module_root / "src/main").rglob("*.kt"))
    combined = "\n".join(path.read_text(encoding="utf-8") for path in kotlin_sources)
    require("longValueExact(" not in combined, f"{module} still calls API-31 BigInteger.longValueExact()")
    require("toLongExactCompat()" in combined, f"{module} does not use exact API-26-safe conversion")

common_test = ROOT / "core/common/src/test/java/app/timeline/islamic/core/common/BigIntegerCompatTest.kt"
test_text = common_test.read_text(encoding="utf-8")
for marker in ("convertsSignedLongBoundariesExactly", "rejectsPositiveOverflowInsteadOfTruncating", "rejectsNegativeOverflowInsteadOfTruncating"):
    require(marker in test_text, f"Missing conversion regression: {marker}")

workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
require("test-api26-biginteger-conversion.py" in workflow, "CI does not run the API 26 conversion guard")
require(":core:common:testDebugUnitTest" in workflow, "CI does not execute shared conversion unit tests")

# Search all production Kotlin and Java source to ensure no API-31 exact BigInteger conversions remain.
for pattern in ("*.kt", "*.java"):
    for path in ROOT.rglob(pattern):
        if ".gradle" in path.parts or "build" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        for method in ("longValueExact", "intValueExact", "shortValueExact", "byteValueExact"):
            require(f"{method}(" not in text,
                    f"API-31 BigInteger.{method}() remains in {path.relative_to(ROOT)}")

print("API 26 BigInteger exact-conversion guard passed (shared helper, both consumers, boundary tests, CI wiring, whole-source scan).")
