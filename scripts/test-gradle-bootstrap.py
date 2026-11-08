#!/usr/bin/env python3
"""Regression test for executable permissions in the custom Gradle bootstrap."""
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WRAPPER = ROOT / "gradlew"


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="timeline-gradle-bootstrap-") as tmp:
        gradle_home = Path(tmp) / "gradle-home"
        launcher = (
            gradle_home
            / "agentic-wrapper"
            / "gradle-9.5.0"
            / "gradle-9.5.0"
            / "bin"
            / "gradle"
        )
        launcher.parent.mkdir(parents=True)
        launcher.write_text(
            "#!/usr/bin/env bash\n"
            "printf 'bootstrap-test-ok\\n'\n"
            "printf 'args:%s\\n' \"$*\"\n",
            encoding="utf-8",
        )
        # Model Python zipfile extraction: file exists but has no executable bit.
        launcher.chmod(0o644)
        env = os.environ.copy()
        env["GRADLE_USER_HOME"] = str(gradle_home)
        result = subprocess.run(
            [str(WRAPPER), "--version"],
            cwd=ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        if result.returncode != 0:
            raise SystemExit(
                f"Gradle bootstrap regression failed (exit {result.returncode}):\n"
                f"{result.stdout}"
            )
        if "bootstrap-test-ok" not in result.stdout or "--version" not in result.stdout:
            raise SystemExit(f"Unexpected bootstrap output:\n{result.stdout}")
        if not (launcher.stat().st_mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)):
            raise SystemExit("Bootstrap returned success but launcher is still not executable")
    print("Gradle bootstrap executable-permission regression: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
