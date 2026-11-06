#!/usr/bin/env python3
"""Small fail-closed scan for common accidental private-key/token material."""
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[1]
SKIP = {".git", ".gradle", "build", "schemas"}
PATTERNS = {
    "private key block": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "GitHub classic token": re.compile(r"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
    "AWS access key id": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
}
failures=[]
for path in ROOT.rglob("*"):
    if not path.is_file() or any(part in SKIP for part in path.parts): continue
    if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".zip", ".aar", ".apk", ".aab", ".jar"}: continue
    try: text=path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError): continue
    for label, pattern in PATTERNS.items():
        if pattern.search(text): failures.append(f"{path.relative_to(ROOT)}: {label}")
if failures:
    print("Potential secret material detected:", *failures, sep="\n", file=sys.stderr); sys.exit(1)
print("Secret pattern scan passed")
