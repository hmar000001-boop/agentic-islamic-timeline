#!/usr/bin/env python3
"""Guard app source imports and public module APIs required by the real app compile."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/app/timeline/islamic/MainActivity.kt"
APP = ROOT / "app/src/main/java/app/timeline/islamic/TimelineApplication.kt"
APP_GRADLE = ROOT / "app/build.gradle.kts"
PERSISTENCE_GRADLE = ROOT / "core/persistence/build.gradle.kts"
AGENT_RUNTIME = ROOT / "core/agent-api/src/main/java/app/timeline/islamic/core/agent/AgentRuntimeContracts.kt"
AUTHORIZATION = ROOT / "core/agent-api/src/main/java/app/timeline/islamic/core/agent/AuthorizationContracts.kt"
DATABASE = ROOT / "core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt"

main = MAIN.read_text(encoding="utf-8")
app = APP.read_text(encoding="utf-8")
app_gradle = APP_GRADLE.read_text(encoding="utf-8")
persistence_gradle = PERSISTENCE_GRADLE.read_text(encoding="utf-8")
agent_runtime = AGENT_RUNTIME.read_text(encoding="utf-8")
authorization = AUTHORIZATION.read_text(encoding="utf-8")
database = DATABASE.read_text(encoding="utf-8")

checks = [
    ("import androidx.compose.foundation.layout.weight" not in main,
     "MainActivity does not import the internal Compose weight property"),
    ("Column(Modifier.fillMaxSize()) {" in main and "Box(Modifier.weight(1f).fillMaxWidth())" in main,
     "Modifier.weight remains used inside the ColumnScope receiver"),
    ("import app.timeline.islamic.core.agent.TaskLease" in app and
     bool(re.search(r"data\s+class\s+TaskLease\b", agent_runtime)),
     "TimelineApplication imports TaskLease from its defining core.agent package"),
    ("import app.timeline.islamic.core.agent.ExecutionClaimResult" in app and
     bool(re.search(r"sealed\s+interface\s+ExecutionClaimResult\b", authorization)),
     "TimelineApplication imports ExecutionClaimResult from its defining core.agent package"),
    ("suspend fun claimExecution" in authorization and "lease: TaskLease): ExecutionClaimResult" in authorization,
     "authorization execution-claim contract remains aligned with TaskLease"),
    ("api(libs.androidx.room.runtime)" in persistence_gradle and
     "implementation(libs.androidx.room.runtime)" not in persistence_gradle,
     "persistence exports Room runtime because AppDatabase exposes RoomDatabase as a public supertype"),
    ("abstract class AppDatabase : RoomDatabase()" in database,
     "AppDatabase public RoomDatabase supertype is explicit"),
    ('implementation(project(":core:persistence"))' in app_gradle,
     "app consumes the persistence module through its declared project dependency"),
    ("versionCode = 23" in app_gradle and 'versionName = "0.6.2-phase5c-dense-retrieval"' in app_gradle,
     "application version is bumped for Phase 5B LiteRT-LM runtime integration"),
    ('implementation(project(":core:model-manager"))' in app_gradle and
     'implementation(project(":feature:model-management"))' in app_gradle and
     'implementation(project(":core:inference-runtime"))' in app_gradle,
     "app integrates model manager, Models feature and isolated inference runtime modules"),
]

failed = 0
for ok, label in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
    failed += not ok
if failed:
    print(f"{failed} app compile contract check(s) failed", file=sys.stderr)
    raise SystemExit(1)
print(f"All {len(checks)} app compile contract checks passed.")
