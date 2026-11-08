#!/usr/bin/env python3
"""Negative/positive tests for verify-agp-kotlin-mode.py."""
from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from importlib.util import module_from_spec, spec_from_file_location

ROOT = Path(__file__).resolve().parents[1]
SPEC = spec_from_file_location("verify_agp_kotlin_mode", ROOT / "scripts/verify-agp-kotlin-mode.py")
assert SPEC is not None and SPEC.loader is not None
MODULE = module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class AgpKotlinModeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory(prefix="timeline-agp-kotlin-")
        self.root = Path(self.tmp.name)
        (self.root / "gradle").mkdir()
        (self.root / "app").mkdir()
        (self.root / "core/lib").mkdir(parents=True)
        (self.root / "gradle.properties").write_text(
            "android.builtInKotlin=false\nandroid.newDsl=false\n", encoding="utf-8"
        )
        (self.root / "gradle/libs.versions.toml").write_text(
            '[versions]\nagp = "9.3.1"\n[plugins]\n'
            'kotlin-android = { id = "org.jetbrains.kotlin.android", version = "2.4.21" }\n',
            encoding="utf-8",
        )
        (self.root / "build.gradle.kts").write_text(
            "plugins { alias(libs.plugins.android.application) apply false; "
            "alias(libs.plugins.kotlin.android) apply false }\n",
            encoding="utf-8",
        )
        module = "plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }\n"
        (self.root / "app/build.gradle.kts").write_text(
            "plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android) }\n",
            encoding="utf-8",
        )
        (self.root / "core/lib/build.gradle.kts").write_text(module, encoding="utf-8")

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_valid_agp9_legacy_plugin_profile(self) -> None:
        with contextlib.redirect_stdout(io.StringIO()):
            MODULE.validate(self.root)

    def test_requires_new_dsl_opt_out(self) -> None:
        p = self.root / "gradle.properties"
        p.write_text("android.builtInKotlin=false\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "android.newDsl must be"):
            MODULE.validate(self.root)

    def test_requires_built_in_kotlin_opt_out(self) -> None:
        p = self.root / "gradle.properties"
        p.write_text("android.builtInKotlin=true\nandroid.newDsl=false\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "android.builtInKotlin must be"):
            MODULE.validate(self.root)

    def test_rejects_agp10_until_migrated(self) -> None:
        p = self.root / "gradle/libs.versions.toml"
        p.write_text(p.read_text(encoding="utf-8").replace("9.3.1", "10.0.0"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "AGP 10"):
            MODULE.validate(self.root)

    def test_every_android_module_must_apply_external_kotlin_plugin(self) -> None:
        p = self.root / "core/lib/build.gradle.kts"
        p.write_text("plugins { alias(libs.plugins.android.library) }\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "core/lib/build.gradle.kts"):
            MODULE.validate(self.root)

    def test_catalog_must_pin_kotlin_android_plugin_id(self) -> None:
        p = self.root / "gradle/libs.versions.toml"
        p.write_text(
            p.read_text(encoding="utf-8").replace("org.jetbrains.kotlin.android", "org.jetbrains.kotlin.jvm"),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "org.jetbrains.kotlin.android"):
            MODULE.validate(self.root)


if __name__ == "__main__":
    unittest.main(verbosity=2)
