#!/usr/bin/env python3
"""Regression tests for explicit Android SDK adb discovery and API 36 startup."""
from pathlib import Path
import os
import signal
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
STARTER = ROOT / "scripts/start-api36-emulator.sh"


def section_after(text: str, marker: str) -> str:
    start = text.find(marker)
    if start < 0:
        raise AssertionError(f"workflow marker is missing: {marker}")
    end = text.find("      - name:", start + len(marker))
    return text[start:] if end < 0 else text[start:end]


class Api36EmulatorSetupTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.starter = STARTER.read_text(encoding="utf-8")

    def test_sdk_install_installs_and_verifies_platform_tools(self) -> None:
        install = section_after(self.workflow, "      - name: Install Android SDK platform and emulator image")
        self.assertIn('"platform-tools"', install)
        self.assertIn('ADB="$ANDROID_HOME/platform-tools/adb"', install)
        self.assertIn('if [ ! -x "$ADB" ]', install)
        self.assertIn('echo "$ANDROID_HOME/platform-tools" >> "$GITHUB_PATH"', install)
        self.assertIn('echo "ADB=$ADB" >> "$GITHUB_ENV"', install)
        self.assertIn('"$ADB" version', install)

    def test_api36_step_uses_dedicated_startup_script(self) -> None:
        start = section_after(self.workflow, "      - name: Start API 36 emulator")
        self.assertIn("bash scripts/start-api36-emulator.sh", start)
        self.assertNotIn("adb wait-for-device", start)

    def test_startup_script_uses_sdk_absolute_adb_and_emulator_paths(self) -> None:
        self.assertIn('ADB="${ADB:-${SDK_ROOT}/platform-tools/adb}"', self.starter)
        self.assertIn('EMULATOR="${SDK_ROOT}/emulator/emulator"', self.starter)
        self.assertIn('if [[ ! -x "$ADB" ]]', self.starter)
        self.assertIn('"$ADB" start-server', self.starter)
        self.assertIn('"$ADB" get-state', self.starter)
        self.assertIn('"$ADB" shell getprop sys.boot_completed', self.starter)
        self.assertIn('"$ADB" shell settings put global', self.starter)
        self.assertNotRegex(self.starter, r"(?m)^\s*adb\s+")

    def test_startup_script_reports_emulator_logs_on_boot_failure(self) -> None:
        self.assertIn("within 180 seconds", self.starter)
        self.assertIn("within 600 seconds", self.starter)
        self.assertIn("tail -n 200", self.starter)
        self.assertIn('"$ADB" devices -l', self.starter)

    def test_missing_adb_fails_early_with_actionable_diagnostic(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            sdk = Path(temp) / "sdk"
            sdk.mkdir()
            env = os.environ.copy()
            env["ANDROID_HOME"] = str(sdk)
            env["AVDMANAGER"] = str(sdk / "cmdline-tools/latest/bin/avdmanager")
            result = subprocess.run(
                ["bash", str(STARTER)],
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("platform-tools/adb", result.stderr)
        self.assertIn("install the SDK package 'platform-tools'", result.stderr)

    def test_startup_script_completes_with_mocked_sdk_tools(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            sdk = root / "sdk"
            platform_tools = sdk / "platform-tools"
            emulator_dir = sdk / "emulator"
            fake_bin = root / "bin"
            for directory in (platform_tools, emulator_dir, fake_bin):
                directory.mkdir(parents=True, exist_ok=True)

            adb = platform_tools / "adb"
            adb.write_text(
                "#!/usr/bin/env python3\n"
                "import sys\n"
                "args = sys.argv[1:]\n"
                "if args == ['version']:\n print('Android Debug Bridge mock')\n"
                "elif args == ['get-state']:\n print('device')\n"
                "elif args[:2] == ['shell', 'getprop']:\n print('1')\n"
                "elif args[:2] == ['shell', 'settings']:\n pass\n"
                "elif args == ['devices', '-l']:\n print('List of devices attached\\nemulator-5554 device')\n"
                "else:\n pass\n",
                encoding="utf-8",
            )
            adb.chmod(0o755)

            pid_file = root / "mock-emulator.pid"
            emulator = emulator_dir / "emulator"
            emulator.write_text(
                "#!/usr/bin/env python3\n"
                "import os, sys, time\n"
                "if sys.argv[1:] == ['-version']:\n print('Android emulator mock'); raise SystemExit(0)\n"
                f"open({str(pid_file)!r}, 'w').write(str(os.getpid()))\n"
                "raise SystemExit(0)\n",
                encoding="utf-8",
            )
            emulator.chmod(0o755)

            avdmanager = root / "avdmanager"
            avdmanager.write_text("#!/bin/sh\ncat >/dev/null\nexit 0\n", encoding="utf-8")
            avdmanager.chmod(0o755)
            sudo = fake_bin / "sudo"
            sudo.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            sudo.chmod(0o755)

            env = os.environ.copy()
            env["ANDROID_HOME"] = str(sdk)
            env["AVDMANAGER"] = str(avdmanager)
            env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"
            env["TIMELINE_EMULATOR_LOG"] = str(root / "emulator.log")
            result = subprocess.run(
                ["bash", str(STARTER)],
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                timeout=20,
            )
            if pid_file.exists():
                try:
                    os.kill(int(pid_file.read_text(encoding="utf-8")), signal.SIGTERM)
                except (ProcessLookupError, ValueError):
                    pass
                time.sleep(0.05)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("API 36 emulator timeline-ci-api36 booted successfully", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
