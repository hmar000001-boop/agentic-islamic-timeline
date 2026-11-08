#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION="9.5.0"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/agentic-wrapper/gradle-$VERSION"
DIST_HOME="$CACHE/gradle-$VERSION"
BIN="$DIST_HOME/bin/gradle"

# Python's zipfile.extractall() does not restore Unix executable bits. Repair
# an existing cache before testing -x, otherwise a valid cached Gradle install
# is mistaken for a missing distribution and may fail with exit code 126.
if [[ -f "$BIN" && ! -x "$BIN" ]]; then
  chmod 0755 "$BIN"
fi

if [[ ! -x "$BIN" ]]; then
  mkdir -p "$CACHE"
  ZIP="$CACHE/gradle-$VERSION-bin.zip"
  ZIP_URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 "$ZIP_URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$ZIP_URL" -O "$ZIP"
  else
    echo "curl or wget is required to bootstrap Gradle $VERSION" >&2; exit 2
  fi
  EXPECTED="553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
  if command -v sha256sum >/dev/null 2>&1; then ACTUAL="$(sha256sum "$ZIP" | awk '{print $1}')"; else ACTUAL="$(shasum -a 256 "$ZIP" | awk '{print $1}')"; fi
  if [[ "$EXPECTED" != "$ACTUAL" ]]; then echo "Gradle distribution SHA-256 mismatch" >&2; rm -f "$ZIP"; exit 3; fi
  python3 - "$ZIP" "$CACHE" <<'PYZIP'
import os, sys, zipfile
archive_path, destination = sys.argv[1], sys.argv[2]
root = os.path.realpath(destination)
with zipfile.ZipFile(archive_path) as archive:
    for entry in archive.infolist():
        target = os.path.realpath(os.path.join(root, entry.filename))
        if target != root and not target.startswith(root + os.sep):
            raise SystemExit(f"Unsafe path in Gradle distribution: {entry.filename}")
        archive.extract(entry, root)
        # Restore Unix permission bits recorded by the official distribution.
        mode = (entry.external_attr >> 16) & 0o777
        if mode:
            os.chmod(target, mode)
PYZIP
  rm -f "$ZIP"
  if [[ ! -f "$BIN" ]]; then
    echo "Gradle distribution extracted without expected executable: $BIN" >&2
    exit 4
  fi
  # Defensive fallback: always make the launcher executable even if the ZIP
  # omitted POSIX mode bits or an extractor did not preserve them.
  chmod 0755 "$BIN"
fi
exec "$BIN" -p "$ROOT" "$@"
