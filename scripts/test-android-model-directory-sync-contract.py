#!/usr/bin/env python3
"""Prevent use of hidden Android constants in durable model-directory syncing."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "core/model-manager/src/main/java/app/timeline/islamic/core/models/AndroidModelFileStore.kt"
text = SOURCE.read_text(encoding="utf-8")
errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


start = text.find("private fun syncDirectory(directory: File)")
end = text.find("\n    companion object", start)
require(start >= 0 and end > start, "syncDirectory implementation must exist")
body = text[start:end] if start >= 0 and end > start else ""

require("OsConstants.O_DIRECTORY" not in text, "must not reference hidden/non-SDK OsConstants.O_DIRECTORY")
require("Os.open(path, OsConstants.O_RDONLY, 0)" in body,
        "must open the directory using public Os.open + O_RDONLY")
require("Os.lstat(path)" in body, "must lstat before/after opening to reject path symlinks/replacement")
require("!Files.isSymbolicLink(directory.toPath())" in body, "must reject a symlink at the directory path before opening")
require("Os.fstat(descriptor)" in body, "must verify the opened descriptor itself")
require("OsConstants.S_ISDIR(" in body, "must verify path and descriptor are directories")
require("currentPath.st_dev == opened.st_dev" in body and "currentPath.st_ino == opened.st_ino" in body,
        "must verify the opened descriptor still identifies the directory at the path")
require("Os.fsync(descriptor)" in body, "must retain directory fsync durability")
require("finally" in body and "Os.close(descriptor)" in body,
        "must close the directory file descriptor in a finally block")

if errors:
    for error in errors:
        print(f"FAIL: {error}", file=sys.stderr)
    raise SystemExit(1)

print("Android model directory-sync contract passed (public SDK APIs, directory identity, fsync and close).")
