#!/usr/bin/env python3
"""Guard the JUnit 4 method signature regression in ModelManagerTest.

Kotlin expression-bodied functions ending in nullable Unit expressions can
compile to kotlin.Unit rather than JVM void. JUnit 4 rejects those methods
before running any test in the class.
"""
from pathlib import Path

source = Path(
    "core/model-manager/src/test/java/app/timeline/islamic/core/models/ModelManagerTest.kt"
).read_text(encoding="utf-8")
marker = (
    "    @Test\n"
    "    fun cancellationDuringImportRollsBackStagingAndRegistry() {"
)
if marker not in source:
    raise SystemExit("FAIL: cancellation test must use a block body and emit JVM void")
start = source.index(marker)
next_test = source.find("\n    @Test", start + len(marker))
body = source[start : next_test if next_test >= 0 else len(source)]
if "runBlocking {" not in body or "scope.coroutineContext[Job]?.cancel()" not in body:
    raise SystemExit("FAIL: cancellation regression coverage was accidentally weakened")
for assertion in (
    "assertTrue(importJob.isCancelled)",
    "assertTrue(registry.rows.isEmpty())",
    "assertFalse(files.stagingFile(spec).exists())",
):
    if assertion not in body:
        raise SystemExit(f"FAIL: cancellation regression assertion missing: {assertion}")
print("PASS: cancellation test uses a JUnit 4-compatible block body and retains cleanup assertions")
