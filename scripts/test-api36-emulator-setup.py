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

    def test_workflow_installs_qemu_pulse_runtime_dependency(self) -> None:
        install = section_after(
            self.workflow,
            "      - name: Install Linux runtime library required by Android Emulator QEMU",
        )
        self.assertIn("sudo apt-get update", install)
        self.assertIn("sudo apt-get install --yes --no-install-recommends libpulse0", install)
        self.assertIn("dpkg-query", install)

    def test_api36_step_uses_dedicated_startup_script(self) -> None:
        start = section_after(self.workflow, "      - name: Start API 36 emulator")
        self.assertIn("bash scripts/start-api36-emulator.sh", start)
        self.assertNotIn("adb wait-for-device", start)

    def test_startup_script_uses_sdk_absolute_adb_and_emulator_paths(self) -> None:
        self.assertIn('ADB="${ADB:-${SDK_ROOT}/platform-tools/adb}"', self.starter)
        self.assertIn('EMULATOR="${SDK_ROOT}/emulator/emulator"', self.starter)
        self.assertIn('QEMU="${SDK_ROOT}/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless"', self.starter)
        self.assertIn('QEMU_DEPENDENCIES="$(LD_LIBRARY_PATH="$QEMU_LIBRARY_PATH" ldd "$QEMU" 2>&1)"', self.starter)
        self.assertIn("MISSING_QEMU_LIBRARIES", self.starter)
        self.assertIn('if [[ ! -x "$ADB" ]]', self.starter)
        self.assertIn('"$ADB" start-server', self.starter)
        self.assertIn('"$ADB" get-state', self.starter)
        self.assertIn('"$ADB" shell getprop sys.boot_completed', self.starter)
        self.assertIn('"$ADB" shell settings put global', self.starter)
        self.assertNotRegex(self.starter, r"(?m)^\s*adb\s+")

    def test_headless_preflight_mirrors_android_launcher_library_paths(self) -> None:
        self.assertIn('qemu-system-x86_64-headless', self.starter)
        self.assertIn('EMULATOR_LIB_DIR="${SDK_ROOT}/emulator/lib64"', self.starter)
        self.assertIn('QEMU_GLES_LIB_DIR="${EMULATOR_LIB_DIR}/gles_swiftshader"', self.starter)
        self.assertIn('QEMU_VULKAN_LIB_DIR="${EMULATOR_LIB_DIR}/vulkan"', self.starter)
        self.assertIn('QEMU_STDLIB_LIB_DIR="${EMULATOR_LIB_DIR}/libstdc++"', self.starter)
        self.assertIn('${EMULATOR_LIB_DIR}:${QEMU_GLES_LIB_DIR}:${QEMU_VULKAN_LIB_DIR}:${QEMU_STDLIB_LIB_DIR}', self.starter)
        self.assertIn('LD_LIBRARY_PATH="$QEMU_LIBRARY_PATH" ldd "$QEMU"', self.starter)
        library_path_assignment = next(
            line for line in self.starter.splitlines() if line.startswith('QEMU_LIBRARY_PATH=')
        )
        self.assertNotIn('qt', library_path_assignment)
        self.assertIn('"$EMULATOR" -avd "$AVD_NAME"', self.starter)
        self.assertIn('-no-window -no-audio -no-boot-anim', self.starter)

    def test_startup_script_pins_compatible_android_user_and_avd_homes(self) -> None:
        self.assertIn('ANDROID_USER_HOME="${HOME}/.android"', self.starter)
        self.assertIn('ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"', self.starter)
        self.assertIn('ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"', self.starter)
        self.assertIn('ANDROID_SDK_HOME="$HOME"', self.starter)
        self.assertIn('unset ANDROID_PREFS_ROOT', self.starter)
        self.assertIn('export ANDROID_USER_HOME ANDROID_EMULATOR_HOME ANDROID_AVD_HOME ANDROID_SDK_HOME', self.starter)
        self.assertIn('AVD_INI_PATH="$ANDROID_AVD_HOME/${AVD_NAME}.ini"', self.starter)
        self.assertIn('AVD_DATA_DIR="$ANDROID_AVD_HOME/${AVD_NAME}.avd"', self.starter)
        self.assertIn('--path "$AVD_DATA_DIR"', self.starter)
        self.assertIn('[[ ! -s "$AVD_INI_PATH" || ! -s "$AVD_DATA_DIR/config.ini" ]]', self.starter)
        self.assertLess(self.starter.index('AVDMANAGER" create avd'), self.starter.index('[[ ! -s "$AVD_INI_PATH"'))
        self.assertLess(self.starter.index('[[ ! -s "$AVD_INI_PATH"'), self.starter.index('nohup "$EMULATOR"'))

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
            # CI exports a valid ADB path at job scope. Override it deliberately so
            # this test exercises the missing-SDK-binary path instead of the host ADB.
            env["ADB"] = str(sdk / "platform-tools" / "adb")
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

    def test_missing_qemu_shared_library_fails_before_emulator_launch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            sdk = root / "sdk"
            adb_dir = sdk / "platform-tools"
            emulator_dir = sdk / "emulator"
            qemu_dir = emulator_dir / "qemu/linux-x86_64"
            emulator_lib_dir = emulator_dir / "lib64"
            fake_bin = root / "bin"
            for directory in (
                adb_dir,
                qemu_dir,
                emulator_lib_dir / "gles_swiftshader",
                emulator_lib_dir / "vulkan",
                emulator_lib_dir / "libstdc++",
                fake_bin,
            ):
                directory.mkdir(parents=True, exist_ok=True)

            adb = adb_dir / "adb"
            adb.write_text("#!/bin/sh\necho adb-must-not-be-called >&2\nexit 99\n", encoding="utf-8")
            adb.chmod(0o755)
            qemu = qemu_dir / "qemu-system-x86_64-headless"
            qemu.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            qemu.chmod(0o755)
            emulator_marker = root / "emulator-was-launched"
            emulator = emulator_dir / "emulator"
            emulator.write_text(
                "#!/bin/sh\n"
                f"touch {str(emulator_marker)!r}\n"
                "exit 0\n",
                encoding="utf-8",
            )
            emulator.chmod(0o755)
            avdmanager = root / "avdmanager"
            avdmanager.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            avdmanager.chmod(0o755)
            ldd = fake_bin / "ldd"
            ldd.write_text(
                "#!/bin/sh\n"
                f"printf '%s\\n' \"$LD_LIBRARY_PATH\" > {str(root / 'ldd-library-path.txt')!r}\n"
                "echo 'libpulse.so.0 => not found'\nexit 0\n",
                encoding="utf-8",
            )
            ldd.chmod(0o755)

            env = os.environ.copy()
            env["ANDROID_HOME"] = str(sdk)
            env["AVDMANAGER"] = str(avdmanager)
            env["ADB"] = str(adb)
            env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"
            result = subprocess.run(
                ["bash", str(STARTER)],
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                timeout=10,
            )

        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("libpulse.so.0", result.stderr)
        self.assertIn("QEMU dependency search path:", result.stderr)
        self.assertFalse(emulator_marker.exists(), "emulator must not launch when QEMU dependencies are missing")

    def test_startup_script_completes_with_mocked_sdk_tools(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            sdk = root / "sdk"
            platform_tools = sdk / "platform-tools"
            emulator_dir = sdk / "emulator"
            qemu_dir = emulator_dir / "qemu/linux-x86_64"
            emulator_lib_dir = emulator_dir / "lib64"
            fake_bin = root / "bin"
            for directory in (
                platform_tools,
                qemu_dir,
                emulator_lib_dir / "gles_swiftshader",
                emulator_lib_dir / "vulkan",
                emulator_lib_dir / "libstdc++",
                fake_bin,
            ):
                directory.mkdir(parents=True, exist_ok=True)

            adb = platform_tools / "adb"
            adb_calls_file = root / "mock-adb-calls.log"
            adb.write_text(
                "#!/usr/bin/env python3\n"
                "import sys\n"
                f"with open({str(adb_calls_file)!r}, 'a', encoding='utf-8') as calls: "
                "calls.write(' '.join(sys.argv[1:]) + '\\n')\n"
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
            emulator_environment_file = root / "mock-emulator-environment.txt"
            emulator_launch_calls_file = root / "mock-emulator-launches.txt"
            emulator = emulator_dir / "emulator"
            emulator.write_text(
                "#!/usr/bin/env python3\n"
                "import os, sys, time\n"
                "if sys.argv[1:] == ['-version']:\n print('Android emulator mock'); raise SystemExit(0)\n"
                f"with open({str(emulator_environment_file)!r}, 'w', encoding='utf-8') as out:\n"
                " out.write(chr(10).join([os.environ.get('ANDROID_USER_HOME', ''), os.environ.get('ANDROID_EMULATOR_HOME', ''), os.environ.get('ANDROID_AVD_HOME', ''), os.environ.get('ANDROID_SDK_HOME', ''), ' '.join(sys.argv[1:])]))\n"
                f"with open({str(emulator_launch_calls_file)!r}, 'a', encoding='utf-8') as calls: calls.write('launch\\n')\n"
                f"open({str(pid_file)!r}, 'w').write(str(os.getpid()))\n"
                # A real emulator remains alive while adb registers the virtual
                # device and Android reports sys.boot_completed=1. Keep the mock
                # process alive for the same interval; early exit is a real fault.
                "while True: time.sleep(0.1)\n",
                encoding="utf-8",
            )
            emulator.chmod(0o755)
            qemu = qemu_dir / "qemu-system-x86_64-headless"
            qemu.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            qemu.chmod(0o755)
            ldd = fake_bin / "ldd"
            ldd_observations = root / "mock-ldd-observations.txt"
            ldd.write_text(
                "#!/bin/sh\n"
                f"printf '%s\\n' \"$1\" \"$LD_LIBRARY_PATH\" > {str(ldd_observations)!r}\n"
                "echo 'libc++.so => /tmp/mock-sdk/emulator/lib64/libc++.so (0x0)'\n"
                "echo 'libandroid-emu-shared.so => /tmp/mock-sdk/emulator/lib64/libandroid-emu-shared.so (0x0)'\n"
                "exit 0\n",
                encoding="utf-8",
            )
            ldd.chmod(0o755)

            avdmanager_environment_file = root / "mock-avdmanager-environment.txt"
            avdmanager = root / "avdmanager"
            avdmanager.write_text(
                "#!/usr/bin/env python3\n"
                "import os, pathlib, sys\n"
                "args = sys.argv[1:]\n"
                "if args[:2] == ['list', 'avd']:\n raise SystemExit(0)\n"
                "sys.stdin.read()\n"
                "if os.environ.get('MOCK_AVDMANAGER_SKIP_FILES') == '1':\n raise SystemExit(0)\n"
                "avd_home = pathlib.Path(os.environ['ANDROID_AVD_HOME'])\n"
                "path_arg = pathlib.Path(args[args.index('--path') + 1])\n"
                "path_arg.mkdir(parents=True, exist_ok=True)\n"
                "(avd_home / 'timeline-ci-api36.ini').write_text('path=' + str(path_arg) + '\\ntarget=android-36\\n', encoding='utf-8')\n"
                "(path_arg / 'config.ini').write_text('AvdId=timeline-ci-api36\\nabi.type=x86_64\\n', encoding='utf-8')\n"
                f"pathlib.Path({str(avdmanager_environment_file)!r}).write_text('\\n'.join([os.environ['ANDROID_USER_HOME'], os.environ['ANDROID_EMULATOR_HOME'], os.environ['ANDROID_AVD_HOME'], os.environ['ANDROID_SDK_HOME']]), encoding='utf-8')\n"
                "print('Created mock API 36 AVD')\n"
                "exit(0)\n",
                encoding="utf-8",
            )
            avdmanager.chmod(0o755)
            sudo = fake_bin / "sudo"
            sudo.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            sudo.chmod(0o755)

            env = os.environ.copy()
            isolated_home = root / "home"
            isolated_home.mkdir()
            env["HOME"] = str(isolated_home)
            env["ANDROID_HOME"] = str(sdk)
            env["AVDMANAGER"] = str(avdmanager)
            # Deliberately inject conflicting runner-level paths. The startup
            # script must normalize modern and legacy SDK tools to one AVD home.
            env["ANDROID_USER_HOME"] = str(root / "runner-user-home")
            env["ANDROID_EMULATOR_HOME"] = str(root / "runner-emulator-home")
            env["ANDROID_AVD_HOME"] = str(root / "runner-avd-home")
            env["ANDROID_SDK_HOME"] = str(root / "runner-sdk-home")
            env["ANDROID_PREFS_ROOT"] = str(root / "runner-prefs-root")
            # The workflow exports a job-wide ADB pointing at the real SDK.
            # Override it so the starter and this test use the same isolated SDK.
            env["ADB"] = str(adb)
            env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"
            env["TIMELINE_EMULATOR_LOG"] = str(root / "emulator.log")
            result = None
            try:
                result = subprocess.run(
                    ["bash", str(STARTER)],
                    env=env,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    check=False,
                    timeout=20,
                )
            finally:
                # The emulator is intentionally long-lived; always reap the
                # detached mock even when the startup assertion or timeout fails.
                if pid_file.exists():
                    try:
                        mock_pid = int(pid_file.read_text(encoding="utf-8"))
                        os.kill(mock_pid, signal.SIGTERM)
                    except (ProcessLookupError, ValueError):
                        mock_pid = None
                    if mock_pid is not None:
                        deadline = time.monotonic() + 2.0
                        while time.monotonic() < deadline:
                            try:
                                os.kill(mock_pid, 0)
                            except ProcessLookupError:
                                break
                            time.sleep(0.05)
            adb_calls = set(adb_calls_file.read_text(encoding="utf-8").splitlines())
            ldd_args = ldd_observations.read_text(encoding="utf-8").splitlines()
            avdmanager_environment = avdmanager_environment_file.read_text(encoding="utf-8").splitlines()
            emulator_environment = emulator_environment_file.read_text(encoding="utf-8").splitlines()
            expected_android_user_home = str(isolated_home / ".android")
            expected_android_avd_home = str(isolated_home / ".android" / "avd")
            avd_ini = Path(expected_android_avd_home) / "timeline-ci-api36.ini"
            avd_config = Path(expected_android_avd_home) / "timeline-ci-api36.avd" / "config.ini"
            avd_ini_exists = avd_ini.is_file() and avd_ini.stat().st_size > 0
            avd_config_exists = avd_config.is_file() and avd_config.stat().st_size > 0
            # Also prove that a zero exit status from avdmanager is insufficient:
            # if it creates neither the registration nor config file, the real
            # emulator must not be launched.
            avd_ini.unlink()
            avd_config.unlink()
            missing_avd_env = env.copy()
            missing_avd_env["MOCK_AVDMANAGER_SKIP_FILES"] = "1"
            missing_avd_result = subprocess.run(
                ["bash", str(STARTER)],
                env=missing_avd_env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                timeout=10,
            )
            emulator_launch_count = len(emulator_launch_calls_file.read_text(encoding="utf-8").splitlines())

        self.assertEqual(len(ldd_args), 2, ldd_args)
        self.assertEqual(ldd_args[0], str(qemu))
        expected_library_dirs = [
            str(emulator_lib_dir),
            str(emulator_lib_dir / "gles_swiftshader"),
            str(emulator_lib_dir / "vulkan"),
            str(emulator_lib_dir / "libstdc++"),
        ]
        for directory in expected_library_dirs:
            self.assertIn(directory, ldd_args[1].split(os.pathsep))
        self.assertNotIn(str(emulator_lib_dir / "qt"), ldd_args[1])

        expected_android_paths = [
            expected_android_user_home,
            expected_android_user_home,
            expected_android_avd_home,
            str(isolated_home),
        ]
        self.assertEqual(avdmanager_environment, expected_android_paths)
        self.assertEqual(emulator_environment[:4], expected_android_paths)
        self.assertIn("-avd timeline-ci-api36", emulator_environment[4])
        self.assertTrue(avd_ini_exists, "mock avdmanager must create the emulator-discoverable AVD registration")
        self.assertTrue(avd_config_exists, "mock avdmanager must create config.ini under the registered AVD directory")
        self.assertEqual(missing_avd_result.returncode, 1, missing_avd_result.stdout + missing_avd_result.stderr)
        self.assertIn("AVD creation did not produce a discoverable timeline-ci-api36 device", missing_avd_result.stderr)
        self.assertEqual(emulator_launch_count, 1, "missing AVD files must prevent a second emulator launch")

        self.assertIsNotNone(result)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("API 36 emulator timeline-ci-api36 booted successfully", result.stdout)
        self.assertTrue(
            {
                "version",
                "start-server",
                "get-state",
                "shell getprop sys.boot_completed",
                "shell settings put global window_animation_scale 0",
                "shell settings put global transition_animation_scale 0",
                "shell settings put global animator_duration_scale 0",
                "devices -l",
            }.issubset(adb_calls),
            f"the isolated mock adb did not receive all expected commands: {sorted(adb_calls)}",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
