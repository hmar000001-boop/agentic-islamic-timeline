#!/usr/bin/env python3
"""Guard the persistence Android-test dependency graph and fixture context scope."""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "core/persistence/build.gradle.kts"
AUTH_TEST = ROOT / "core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/AgentAuthorizationRoomTest.kt"
AUDIT_TEST = ROOT / "core/persistence/src/androidTest/java/app/timeline/islamic/core/persistence/RoomAuditLogTest.kt"

build = BUILD.read_text(encoding="utf-8")
auth_test = AUTH_TEST.read_text(encoding="utf-8")
audit_test = AUDIT_TEST.read_text(encoding="utf-8")

checks = [
    (
        re.search(r'(?m)^\s*androidTestImplementation\(project\(":core:authorization"\)\)\s*$', build) is not None,
        "persistence Android-test source set declares the authorization module",
    ),
    (
        re.search(r'(?m)^\s*(?:api|implementation|testImplementation)\(project\(":core:authorization"\)\)\s*$', build) is None,
        "authorization engine is not added to persistence production or local-unit-test classpaths",
    ),
    (
        "import app.timeline.islamic.core.authorization.AuthorizationEngine" in auth_test
        and "private lateinit var engine: AuthorizationEngine" in auth_test,
        "authorization Room instrumentation test imports and uses AuthorizationEngine",
    ),
    (
        "private lateinit var context: Context" in audit_test
        and "context = ApplicationProvider.getApplicationContext<Context>()" in audit_test,
        "Room audit test initializes a fixture-scoped Android Context",
    ),
    (
        "val context = ApplicationProvider.getApplicationContext<Context>()" not in audit_test,
        "Room audit setup does not shadow the fixture Context with a local variable",
    ),
    (
        "KeystoreAuditMacProvider(context, alias)" in audit_test,
        "historical-key verification reuses the initialized fixture Context",
    ),
]

failed = 0
for ok, label in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
    failed += not ok
if failed:
    print(f"{failed} persistence Android-test contract check(s) failed", file=sys.stderr)
    raise SystemExit(1)
print(f"All {len(checks)} persistence Android-test contract checks passed.")
