#!/usr/bin/env python3
"""Regression tests for Android English/Arabic resource parity and placeholders."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify-string-translations.py")
SPEC = importlib.util.spec_from_file_location("verify_string_translations", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class TranslationVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.base = self.root / "app/src/main/res/values/strings.xml"
        self.en = self.root / "app/src/main/res/values-en/strings.xml"
        self.ar = self.root / "app/src/main/res/values-ar/strings.xml"
        for path in (self.base, self.en, self.ar):
            path.parent.mkdir(parents=True, exist_ok=True)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self, path: Path, body: str) -> None:
        path.write_text(f"<resources>{body}</resources>", encoding="utf-8")

    def valid(self) -> None:
        self.write(self.base, '<string name="title">Title</string><string name="count">Count %1$d</string>')
        self.write(self.en, '<string name="title">Title</string><string name="count">Count %1$d</string>')
        self.write(self.ar, '<string name="title">العنوان</string><string name="count">العدد %1$d</string>')

    def test_complete_locales_pass(self) -> None:
        self.valid()
        self.assertEqual([], MODULE.verify(self.root))

    def test_missing_english_key_fails(self) -> None:
        self.valid()
        self.write(self.en, '<string name="title">Title</string>')
        self.assertTrue(any("en is missing" in e and 'name="count"' in e for e in MODULE.verify(self.root)))

    def test_missing_arabic_key_fails(self) -> None:
        self.valid()
        self.write(self.ar, '<string name="title">العنوان</string>')
        self.assertTrue(any("ar is missing" in e and 'name="count"' in e for e in MODULE.verify(self.root)))

    def test_placeholder_mismatch_fails(self) -> None:
        self.valid()
        self.write(self.en, '<string name="title">Title</string><string name="count">Count %1$s</string>')
        self.assertTrue(any("placeholder mismatch" in e for e in MODULE.verify(self.root)))

    def test_non_translatable_resource_is_ignored(self) -> None:
        self.write(self.base, '<string name="internal" translatable="false">Internal</string><string name="title">Title</string>')
        self.write(self.en, '<string name="title">Title</string>')
        self.write(self.ar, '<string name="title">العنوان</string>')
        self.assertEqual([], MODULE.verify(self.root))

    def test_missing_locale_file_fails(self) -> None:
        self.valid()
        self.ar.unlink()
        self.assertTrue(any("missing values-ar/strings.xml" in e for e in MODULE.verify(self.root)))


if __name__ == "__main__":
    unittest.main(verbosity=2)
