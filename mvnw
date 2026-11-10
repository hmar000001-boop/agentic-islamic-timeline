#!/usr/bin/env sh
# Reproducible Maven bootstrap launcher for this repository.
# The distribution is pinned by HTTPS URL and Apache-published SHA-512.
set -eu

fail() {
  printf '%s\n' "mvnw: $*" >&2
  exit 1
}

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$PROPERTIES" ] || fail "missing wrapper properties: $PROPERTIES"

read_property() {
  key=$1
  count=$(awk -F= -v key="$key" '$1 == key { count++ } END { print count + 0 }' "$PROPERTIES")
  [ "$count" -eq 1 ] || fail "expected exactly one '$key' entry in $PROPERTIES"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$PROPERTIES"
}

DIST_URL=$(read_property distributionUrl)
EXPECTED_SHA512=$(read_property distributionSha512Sum)
case "$DIST_URL" in
  https://*) ;;
  *) fail "distributionUrl must use HTTPS" ;;
esac
case "$DIST_URL" in
  */apache-maven-*-bin.tar.gz) ;;
  *) fail "unsupported Maven distribution URL: $DIST_URL" ;;
esac
case "$EXPECTED_SHA512" in
  ''|*[!0-9a-f]*) fail "distributionSha512Sum must be 128 lowercase hexadecimal characters" ;;
esac
[ "${#EXPECTED_SHA512}" -eq 128 ] || fail "distributionSha512Sum must be 128 lowercase hexadecimal characters"

DIST_NAME=${DIST_URL##*/}
MAVEN_VERSION=$(printf '%s' "$DIST_NAME" | sed -nE 's/^apache-maven-([0-9]+(\.[0-9]+)*)-bin\.tar\.gz$/\1/p')
[ -n "$MAVEN_VERSION" ] || fail "cannot derive Maven version from $DIST_NAME"

MAVEN_USER_HOME=${MAVEN_USER_HOME:-${HOME:?HOME or MAVEN_USER_HOME must be set}/.m2}
CACHE_ROOT="$MAVEN_USER_HOME/wrapper/dists"
INSTALL_DIR="$CACHE_ROOT/apache-maven-$MAVEN_VERSION/$EXPECTED_SHA512"
MAVEN="$INSTALL_DIR/bin/mvn"
STAMP="$INSTALL_DIR/.retail-agent-distribution-sha512"

cache_is_valid() {
  [ -x "$MAVEN" ] && [ -f "$STAMP" ] || return 1
  [ "$(cat "$STAMP")" = "$EXPECTED_SHA512" ] || return 1
  VERSION_OUTPUT=$("$MAVEN" --version 2>&1) || return 1
  case "$VERSION_OUTPUT" in
    "Apache Maven $MAVEN_VERSION"*) return 0 ;;
    *) return 1 ;;
  esac
}

if cache_is_valid; then
  exec "$MAVEN" -f "$BASE_DIR/pom.xml" "$@"
fi

mkdir -p "$CACHE_ROOT"
LOCK_DIR="$CACHE_ROOT/.apache-maven-$MAVEN_VERSION.lock"
LOCK_ACQUIRED=false
attempt=0
while [ "$attempt" -lt 120 ]; do
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    LOCK_ACQUIRED=true
    break
  fi
  if cache_is_valid; then
    exec "$MAVEN" -f "$BASE_DIR/pom.xml" "$@"
  fi
  attempt=$((attempt + 1))
  sleep 1
done
[ "$LOCK_ACQUIRED" = true ] || fail "timed out waiting for Maven installation lock: $LOCK_DIR"

TMP_DIR=$(mktemp -d "$CACHE_ROOT/.maven-wrapper.XXXXXX") || fail "cannot create temporary directory under $CACHE_ROOT"
cleanup() {
  rm -rf "$TMP_DIR"
  rmdir "$LOCK_DIR" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

# Another process may have completed installation while this process waited.
if cache_is_valid; then
  cleanup
  trap - EXIT
  exec "$MAVEN" -f "$BASE_DIR/pom.xml" "$@"
fi

ARCHIVE="$TMP_DIR/$DIST_NAME"
if command -v curl >/dev/null 2>&1; then
  curl --fail --location --silent --show-error --retry 3 --connect-timeout 30 --max-time 600 "$DIST_URL" --output "$ARCHIVE" \
    || fail "could not download $DIST_URL"
elif command -v wget >/dev/null 2>&1; then
  wget --quiet --tries=3 --timeout=30 "$DIST_URL" -O "$ARCHIVE" \
    || fail "could not download $DIST_URL"
else
  fail "Maven bootstrap requires curl or wget"
fi
[ -s "$ARCHIVE" ] || fail "downloaded Maven archive is empty"

if command -v sha512sum >/dev/null 2>&1; then
  ACTUAL_SHA512=$(sha512sum "$ARCHIVE" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL_SHA512=$(shasum -a 512 "$ARCHIVE" | awk '{print $1}')
else
  fail "Maven bootstrap requires sha512sum or shasum"
fi
[ "$ACTUAL_SHA512" = "$EXPECTED_SHA512" ] || fail "Maven distribution SHA-512 mismatch (expected $EXPECTED_SHA512, got $ACTUAL_SHA512)"

tar -xzf "$ARCHIVE" -C "$TMP_DIR" || fail "failed to extract Maven distribution"
EXTRACTED="$TMP_DIR/apache-maven-$MAVEN_VERSION"
[ -x "$EXTRACTED/bin/mvn" ] || fail "Maven archive has an unexpected layout"
VERSION_OUTPUT=$("$EXTRACTED/bin/mvn" --version 2>&1) || fail "downloaded Maven executable did not start"
case "$VERSION_OUTPUT" in
  "Apache Maven $MAVEN_VERSION"*) ;;
  *) fail "downloaded distribution reports an unexpected Maven version" ;;
esac
printf '%s\n' "$EXPECTED_SHA512" > "$EXTRACTED/.retail-agent-distribution-sha512"

rm -rf "$INSTALL_DIR"
mkdir -p "$(dirname "$INSTALL_DIR")"
mv "$EXTRACTED" "$INSTALL_DIR" || fail "could not install Maven distribution"

cleanup
trap - EXIT
exec "$MAVEN" -f "$BASE_DIR/pom.xml" "$@"
