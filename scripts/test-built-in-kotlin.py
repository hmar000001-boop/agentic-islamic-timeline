#!/usr/bin/env python3
"""Regression tests for built-in Kotlin migration, lazy Android DSL, and dependency locking."""
from pathlib import Path
import unittest
import tomllib

ROOT = Path(__file__).resolve().parents[1]

class BuiltInKotlinPolicyTests(unittest.TestCase):
    def test_no_transitional_agp_flags(self):
        props = (ROOT / "gradle.properties").read_text()
        self.assertNotIn("android.builtInKotlin=false", props)
        self.assertNotIn("android.newDsl=false", props)
        self.assertIn("org.gradle.configuration-cache=false", props)

    def test_no_external_kotlin_android_plugin_in_modules_or_catalog(self):
        for file in ROOT.glob("**/build.gradle.kts"):
            text = file.read_text()
            self.assertNotIn("org.jetbrains.kotlin.android", text, str(file))
            self.assertNotIn("libs.plugins.kotlin.android", text, str(file))
        catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
        self.assertNotIn("kotlin-android", catalog.get("plugins", {}))

    def test_all_android_modules_use_typed_public_dsl(self):
        modules = [p for p in ROOT.glob("**/build.gradle.kts") if p != ROOT / "build.gradle.kts"]
        self.assertGreaterEqual(len(modules), 15)
        for file in modules:
            text = file.read_text()
            self.assertRegex(text, r"com\.android\.build\.api\.dsl\.(Application|Library)Extension", str(file))
            self.assertNotRegex(text, r"(?m)^\s*android\s*\{", str(file))

    def test_compose_plugin_matches_builtin_compiler_runtime(self):
        catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
        self.assertEqual(catalog["versions"]["kotlin"], "2.4.21")
        self.assertEqual(catalog["plugins"]["kotlin-compose"]["version"]["ref"], "kotlin")

    def test_ksp_uses_published_release(self):
        catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
        self.assertEqual(catalog["versions"]["ksp"], "2.3.13")

    def test_dynamic_plugin_guarded_configurations_are_not_locked(self):
        root = (ROOT / "build.gradle.kts").read_text()
        self.assertNotIn("resolutionStrategy.failOnDynamicVersions()", root)
        self.assertIn('name.endsWith("runtimeClasspath", ignoreCase = true)', root)
        self.assertIn('name == "cyclonedxBom"', root)
        self.assertIn('name == "cyclonedxDirectBom"', root)
        self.assertIn("if (guardedByPluginDynamicPolicy)", root)
        self.assertIn("resolutionStrategy.deactivateDependencyLocking()", root)
        self.assertIn("else if (isCanBeResolved)", root)
        self.assertIn("resolutionStrategy.activateDependencyLocking()", root)

    def test_no_deprecated_agp_variant_or_kotlin_build_apis(self):
        import re
        patterns = (r"\bapplicationVariants\b", r"\blibraryVariants\b", r"\btestVariants\b",
                    r"\bunitTestVariants\b", r"\bkotlinOptions\b", r"\bbuildConfigField\s*\(")
        for file in ROOT.rglob("*.gradle.kts"):
            if any(part in {"build", ".gradle"} for part in file.parts):
                continue
            content = file.read_text()
            for pattern in patterns:
                self.assertNotRegex(content, pattern, f"{file}: {pattern}")

    def test_ci_disables_configuration_cache_for_all_gradle_tasks(self):
        workflow = (ROOT / ".github/workflows/android-ci.yml").read_text()
        commands = [line.strip() for line in workflow.splitlines() if "./gradlew" in line]
        self.assertTrue(commands)
        for command in commands:
            if "./gradlew --version" not in command:
                self.assertIn("--no-configuration-cache", command, command)

    def test_ci_uploads_project_and_buildscript_lockfiles(self):
        workflow = (ROOT / ".github/workflows/android-ci.yml").read_text()
        self.assertIn("**/gradle.lockfile", workflow)
        self.assertIn("**/buildscript-gradle.lockfile", workflow)

    def test_root_compiler_buildscript_classpath_is_locked(self):
        root = (ROOT / "build.gradle.kts").read_text()
        self.assertIn("configurations.classpath", root)
        self.assertIn("resolutionStrategy.activateDependencyLocking()", root)

    def test_app_uses_lazy_buildconfig_variant_api(self):
        app = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn("ApplicationAndroidComponentsExtension", app)
        self.assertIn("val buildConfigFields = checkNotNull(variant.buildConfigFields)", app)
        self.assertIn("buildConfigFields.put(", app)
        self.assertNotIn("variant.buildConfigFields.put(", app)
        self.assertIn("buildConfig = true", app)
        self.assertNotIn("buildConfigField(", app)

if __name__ == "__main__":
    unittest.main(verbosity=2)
