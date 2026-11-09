#!/usr/bin/env python3
"""Guard all JUnit 4 Kotlin test signatures across unit and Android tests.

JUnit 4 requires @Test methods to be public, zero-argument JVM void methods.
Kotlin expression-bodied tests that delegate to generic runBlocking can infer a
non-Unit result from the final assertion/cleanup expression. Requiring an
explicit Unit return type for every expression-bodied @Test function makes the
JVM descriptor unambiguous; block-bodied functions are already void.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TEST_ROOT_MARKERS = ("/src/test/", "/src/androidTest/")
EXPRESSION_TEST = re.compile(
    r"@Test(?:\s*\([^)]*\))?\s+"
    r"(?:(?:public|internal|private)\s+)?fun\s+[A-Za-z_][A-Za-z0-9_]*\s*"
    r"\([^)]*\)\s*"
    r"(?P<return_type>:\s*[^=\n{]+)?\s*=",
    re.MULTILINE,
)

sources = sorted(
    path for path in ROOT.rglob("*.kt")
    if any(marker in path.as_posix() for marker in TEST_ROOT_MARKERS)
)
expression_tests = 0
invalid = []
for path in sources:
    content = path.read_text(encoding="utf-8")
    for match in EXPRESSION_TEST.finditer(content):
        expression_tests += 1
        return_type = (match.group("return_type") or "").strip()
        if return_type not in (": Unit", ": kotlin.Unit"):
            line = content.count("\n", 0, match.start()) + 1
            invalid.append(f"{path.relative_to(ROOT)}:{line}: expression-bodied @Test must declare : Unit")

if invalid:
    raise SystemExit("FAIL: JUnit 4 expression-bodied test methods may return a non-void JVM value:\n" + "\n".join(invalid))

# Preserve the original regression's behavior checks: cancellation must still
# roll back staging and leave no installed registry record.
model_manager = ROOT / "core/model-manager/src/test/java/app/timeline/islamic/core/models/ModelManagerTest.kt"
source = model_manager.read_text(encoding="utf-8")
marker = "    @Test\n    fun cancellationDuringImportRollsBackStagingAndRegistry() {"
if marker not in source:
    raise SystemExit("FAIL: cancellation test must retain a block body and emit JVM void")
start = source.index(marker)
next_test = source.find("\n    @Test", start + len(marker))
body = source[start : next_test if next_test >= 0 else len(source)]
if "runBlocking {" not in body or "scope.coroutineContext[Job]?.cancel()" not in body:
    raise SystemExit("FAIL: cancellation regression coverage was weakened")
for assertion in (
    "assertTrue(importJob.isCancelled)",
    "assertTrue(registry.rows.isEmpty())",
    "assertFalse(files.stagingFile(spec).exists())",
):
    if assertion not in body:
        raise SystemExit(f"FAIL: cancellation regression assertion missing: {assertion}")

if expression_tests == 0:
    raise SystemExit("FAIL: no expression-bodied @Test methods were discovered; review the guard's coverage")

print(f"PASS: {expression_tests} expression-bodied @Test methods explicitly declare Unit across {len(sources)} test source files")
print("PASS: cancellation rollback test retains all assertions and uses a JUnit 4-compatible block body")
