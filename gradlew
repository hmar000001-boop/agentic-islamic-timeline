#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION="9.5.0"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/agentic-wrapper/gradle-$VERSION"
BIN="$CACHE/gradle-$VERSION/bin/gradle"
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
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z: z.extractall(sys.argv[2])
PYZIP
  rm -f "$ZIP"
fi
exec "$BIN" -p "$ROOT" "$@"
