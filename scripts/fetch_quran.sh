#!/usr/bin/env bash
# Fetches the Uthmani Quran text from tanzil.net (format: sura|aya|text).
# License: keep the text UNMODIFIED and credit tanzil.net (see https://tanzil.net/docs/text_license).
# The model never generates Quran text; the app only ever shows this verified corpus.
set -euo pipefail
OUT="app/src/main/assets/quran/quran-uthmani.txt"
URL="https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt-2&agree=true"
STRICT="${STRICT:-0}"

fail() {
  echo "::warning::Quran corpus not fetched: $1"
  if [ "$STRICT" = "1" ]; then exit 1; fi
  exit 0
}

mkdir -p "$(dirname "$OUT")"
if [ -s "$OUT" ] && [ "$(grep -c '^[0-9]\+|[0-9]\+|' "$OUT" || true)" = "6236" ]; then
  echo "Corpus already present."; exit 0
fi
tmp="$(mktemp)"
curl -fsSL --retry 3 "$URL" -o "$tmp" || fail "download failed"
n="$(grep -c '^[0-9]\+|[0-9]\+|' "$tmp" || true)"
[ "$n" = "6236" ] || fail "unexpected ayah count ($n, expected 6236)"
mv "$tmp" "$OUT"
echo "Corpus ready: $n ayat."
