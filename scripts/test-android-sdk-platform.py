#!/usr/bin/env python3
"""Regression tests for API 37.0 package identity and validation."""
from pathlib import Path
import tempfile
import unittest

from importlib.util import module_from_spec, spec_from_file_location

SCRIPT = Path(__file__).with_name("verify-android-sdk-platform.py")
SPEC = spec_from_file_location("verify_android_sdk_platform", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class AndroidSdkPlatformTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.sdk = Path(self.temp.name) / "sdk"
        self.platform = self.sdk / "platforms" / "android-37.0"
        self.platform.mkdir(parents=True)
        (self.platform / "android.jar").write_bytes(b"test android platform jar")
        (self.platform / "source.properties").write_text(
            "Pkg.Desc=Android SDK Platform 37.0\n"
            "AndroidVersion.ApiLevel=37.0\n"
            "AndroidVersion.CodeName=\n"
            "AndroidVersion.ExtensionLevel=22\n"
            "AndroidVersion.IsBaseSdk=true\n",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_accepts_exact_api_37_minor_zero_package(self) -> None:
        self.assertEqual(MODULE.validate_sdk_platform(self.sdk), self.platform.resolve())

    def test_rejects_old_android_37_package_name(self) -> None:
        old_sdk = Path(self.temp.name) / "sdk-old-package-name"
        old_path = old_sdk / "platforms" / "android-37"
        old_path.mkdir(parents=True)
        (old_path / "android.jar").write_bytes(b"old path only")
        with self.assertRaisesRegex(RuntimeError, "android-37.0"):
            MODULE.validate_sdk_platform(old_sdk)

    def test_rejects_wrong_api_level(self) -> None:
        (self.platform / "source.properties").write_text(
            "AndroidVersion.ApiLevel=36\nAndroidVersion.MinorApiLevel=0\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "API version|ApiLevel"):
            MODULE.validate_sdk_platform(self.sdk)

    def test_rejects_wrong_minor_api_level(self) -> None:
        (self.platform / "source.properties").write_text(
            "AndroidVersion.ApiLevel=37.1\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "37.1"):
            MODULE.validate_sdk_platform(self.sdk)

    def test_rejects_malformed_api_level(self) -> None:
        (self.platform / "source.properties").write_text(
            "AndroidVersion.ApiLevel=37.preview\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "invalid AndroidVersion.ApiLevel"):
            MODULE.validate_sdk_platform(self.sdk)

    def test_rejects_missing_source_properties(self) -> None:
        (self.platform / "source.properties").unlink()
        with self.assertRaisesRegex(RuntimeError, "source.properties"):
            MODULE.validate_sdk_platform(self.sdk)

    def test_accepts_split_api_level_representation(self) -> None:
        (self.platform / "source.properties").write_text(
            "AndroidVersion.ApiLevel=37\nAndroidVersion.MinorApiLevel=0\n", encoding="utf-8"
        )
        self.assertEqual(MODULE.validate_sdk_platform(self.sdk), self.platform.resolve())

    def test_rejects_conflicting_minor_representations(self) -> None:
        (self.platform / "source.properties").write_text(
            "AndroidVersion.ApiLevel=37.0\nAndroidVersion.MinorApiLevel=1\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(RuntimeError, "conflicting"):
            MODULE.validate_sdk_platform(self.sdk)

    def test_rejects_missing_android_jar(self) -> None:
        (self.platform / "android.jar").unlink()
        with self.assertRaisesRegex(RuntimeError, "android.jar"):
            MODULE.validate_sdk_platform(self.sdk)

    def test_rejects_sdk_platform_symlink_escape(self) -> None:
        outside = Path(self.temp.name) / "outside-platform"
        outside.mkdir()
        (outside / "android.jar").write_bytes(b"outside")
        (outside / "source.properties").write_text(
            "AndroidVersion.ApiLevel=37\nAndroidVersion.MinorApiLevel=0\n", encoding="utf-8"
        )
        import shutil
        shutil.rmtree(self.platform)
        self.platform.symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, "outside the SDK platforms"):
            MODULE.validate_sdk_platform(self.sdk)


if __name__ == "__main__":
    unittest.main(verbosity=2)
