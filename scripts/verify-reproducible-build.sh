#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/retail-agent-reproducible.XXXXXX")
trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM

MAVEN_ARGS='-B -ntp -Dapp.llm.provider=simulated -Dapp.llm.record=false clean verify'

build_and_manifest() {
  label=$1
  # Intentional word splitting preserves the fixed Maven argument list.
  # shellcheck disable=SC2086
  ./mvnw $MAVEN_ARGS
  find retail-agent-core/target retail-agent-langgraph4j/target retail-agent-langchain4j/target \
    -maxdepth 1 -type f \( -name '*.jar' -o -name '*.war' \) \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print | LC_ALL=C sort > "$TMP_DIR/$label.files"
  [ -s "$TMP_DIR/$label.files" ] || {
    printf '%s\n' 'No packaged JAR/WAR artifacts were produced.' >&2
    exit 1
  }
  : > "$TMP_DIR/$label.sha256"
  while IFS= read -r artifact; do
    sha256sum "$artifact" >> "$TMP_DIR/$label.sha256"
  done < "$TMP_DIR/$label.files"
  sed "s#^#${label}: #" "$TMP_DIR/$label.sha256" > "$TMP_DIR/$label.report"
}

build_and_manifest first
build_and_manifest second
if ! diff -u "$TMP_DIR/first.sha256" "$TMP_DIR/second.sha256"; then
  printf '%s\n' 'The two clean builds produced different packaged artifacts.' >&2
  exit 1
fi
printf '%s\n' 'PASS: two clean Maven verify runs produced byte-identical packaged JAR/WAR artifacts.'
cat "$TMP_DIR/second.sha256"
