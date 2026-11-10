#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TEMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/retail-agent-wrapper-test.XXXXXX")
trap 'rm -rf "$TEMP_ROOT"' EXIT HUP INT TERM

fail() {
  printf '%s\n' "wrapper test: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required test command is unavailable: $1"
}
for command_name in tar sha512sum awk sed grep cp; do
  require_command "$command_name"
done

create_fixture() {
  fixture=$1
  archive_root="$fixture/archive-root/apache-maven-3.10.0"
  mkdir -p "$archive_root/bin" "$fixture/project/.mvn/wrapper" "$fixture/bin" "$fixture/home"
  cat > "$archive_root/bin/mvn" <<'MVN'
#!/usr/bin/env sh
case " $* " in
  *' --version '*|*' -version '*) printf '%s\n' 'Apache Maven 3.10.0 (wrapper-test-distribution)' ;;
  *)
  printf 'FAKE_MAVEN_ARGS:'
  printf ' <%s>' "$@"
  printf '\n' ;;
esac
MVN
  chmod +x "$archive_root/bin/mvn"
  tar -czf "$fixture/apache-maven-3.10.0-bin.tar.gz" -C "$fixture/archive-root" apache-maven-3.10.0
  checksum=$(sha512sum "$fixture/apache-maven-3.10.0-bin.tar.gz" | awk '{print $1}')
  cat > "$fixture/project/.mvn/wrapper/maven-wrapper.properties" <<PROPERTIES
distributionUrl=https://example.invalid/maven/apache-maven-3.10.0-bin.tar.gz
distributionSha512Sum=$checksum
PROPERTIES
  cp "$ROOT/mvnw" "$fixture/project/mvnw"
  chmod +x "$fixture/project/mvnw"
  cat > "$fixture/bin/curl" <<'CURL'
#!/usr/bin/env sh
set -eu
out=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --output|-o) out=$2; shift 2 ;;
    *) shift ;;
  esac
done
[ -n "$out" ] || { echo 'fake curl: missing output path' >&2; exit 2; }
printf 'download\n' >> "$TEST_CURL_LOG"
cp "$TEST_ARCHIVE" "$out"
CURL
  chmod +x "$fixture/bin/curl"
  printf '%s\n' "$checksum"
}

GOOD="$TEMP_ROOT/good"
mkdir -p "$GOOD"
GOOD_SUM=$(create_fixture "$GOOD")
TEST_ARCHIVE="$GOOD/apache-maven-3.10.0-bin.tar.gz" \
TEST_CURL_LOG="$GOOD/download.log" \
MAVEN_USER_HOME="$GOOD/home/.m2" \
HOME="$GOOD/home" \
PATH="$GOOD/bin:$PATH" \
  "$GOOD/project/mvnw" --version > "$GOOD/first-run.txt"
grep -q '^Apache Maven 3\.10\.0 ' "$GOOD/first-run.txt" || fail 'fresh bootstrap did not run the pinned Maven version'
[ "$(wc -l < "$GOOD/download.log" | tr -d ' ')" = 1 ] || fail 'fresh bootstrap did not perform exactly one download'

TEST_ARCHIVE="$GOOD/apache-maven-3.10.0-bin.tar.gz" \
TEST_CURL_LOG="$GOOD/download.log" \
MAVEN_USER_HOME="$GOOD/home/.m2" \
HOME="$GOOD/home" \
PATH="$GOOD/bin:$PATH" \
  "$GOOD/project/mvnw" --version > "$GOOD/cache-run.txt"
[ "$(wc -l < "$GOOD/download.log" | tr -d ' ')" = 1 ] || fail 'verified cache hit unexpectedly downloaded Maven again'

BAD="$TEMP_ROOT/bad"
mkdir -p "$BAD"
create_fixture "$BAD" >/dev/null
sed 's/^distributionSha512Sum=.*/distributionSha512Sum=00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000/' \
  "$BAD/project/.mvn/wrapper/maven-wrapper.properties" > "$BAD/props.tmp"
mv "$BAD/props.tmp" "$BAD/project/.mvn/wrapper/maven-wrapper.properties"
if TEST_ARCHIVE="$BAD/apache-maven-3.10.0-bin.tar.gz" \
   TEST_CURL_LOG="$BAD/download.log" \
   MAVEN_USER_HOME="$BAD/home/.m2" \
   HOME="$BAD/home" \
   PATH="$BAD/bin:$PATH" \
   "$BAD/project/mvnw" --version > "$BAD/stdout.txt" 2> "$BAD/stderr.txt"; then
  fail 'tampered distribution checksum was not rejected'
fi
grep -q 'SHA-512 mismatch' "$BAD/stderr.txt" || fail 'checksum failure did not provide a clear diagnostic'

HTTP="$TEMP_ROOT/http"
mkdir -p "$HTTP"
create_fixture "$HTTP" >/dev/null
sed 's#^distributionUrl=https://#distributionUrl=http://#' \
  "$HTTP/project/.mvn/wrapper/maven-wrapper.properties" > "$HTTP/props.tmp"
mv "$HTTP/props.tmp" "$HTTP/project/.mvn/wrapper/maven-wrapper.properties"
if TEST_ARCHIVE="$HTTP/apache-maven-3.10.0-bin.tar.gz" \
   TEST_CURL_LOG="$HTTP/download.log" \
   MAVEN_USER_HOME="$HTTP/home/.m2" \
   HOME="$HTTP/home" \
   PATH="$HTTP/bin:$PATH" \
   "$HTTP/project/mvnw" --version > "$HTTP/stdout.txt" 2> "$HTTP/stderr.txt"; then
  fail 'non-HTTPS distribution URL was not rejected'
fi
grep -q 'must use HTTPS' "$HTTP/stderr.txt" || fail 'non-HTTPS URL failure did not provide a clear diagnostic'
[ ! -f "$HTTP/download.log" ] || fail 'non-HTTPS URL was downloaded before validation'

DUPLICATE="$TEMP_ROOT/duplicate"
mkdir -p "$DUPLICATE"
create_fixture "$DUPLICATE" >/dev/null
cp "$DUPLICATE/project/.mvn/wrapper/maven-wrapper.properties" "$DUPLICATE/duplicate.properties"
cat "$DUPLICATE/duplicate.properties" >> "$DUPLICATE/project/.mvn/wrapper/maven-wrapper.properties"
if TEST_ARCHIVE="$DUPLICATE/apache-maven-3.10.0-bin.tar.gz" \
   TEST_CURL_LOG="$DUPLICATE/download.log" \
   MAVEN_USER_HOME="$DUPLICATE/home/.m2" \
   HOME="$DUPLICATE/home" \
   PATH="$DUPLICATE/bin:$PATH" \
   "$DUPLICATE/project/mvnw" --version > "$DUPLICATE/stdout.txt" 2> "$DUPLICATE/stderr.txt"; then
  fail 'duplicate distribution properties were not rejected'
fi
grep -q "expected exactly one 'distributionUrl'" "$DUPLICATE/stderr.txt" || fail 'duplicate property failure did not provide a clear diagnostic'

WRONG_VERSION="$TEMP_ROOT/wrong-version"
mkdir -p "$WRONG_VERSION"
create_fixture "$WRONG_VERSION" >/dev/null
sed 's/Apache Maven 3\.10\.0/Apache Maven 3.9.9/g' \
  "$WRONG_VERSION/archive-root/apache-maven-3.10.0/bin/mvn" > "$WRONG_VERSION/mvn.tmp"
mv "$WRONG_VERSION/mvn.tmp" "$WRONG_VERSION/archive-root/apache-maven-3.10.0/bin/mvn"
chmod +x "$WRONG_VERSION/archive-root/apache-maven-3.10.0/bin/mvn"
tar -czf "$WRONG_VERSION/apache-maven-3.10.0-bin.tar.gz" -C "$WRONG_VERSION/archive-root" apache-maven-3.10.0
WRONG_SUM=$(sha512sum "$WRONG_VERSION/apache-maven-3.10.0-bin.tar.gz" | awk '{print $1}')
sed "s/^distributionSha512Sum=.*/distributionSha512Sum=$WRONG_SUM/" \
  "$WRONG_VERSION/project/.mvn/wrapper/maven-wrapper.properties" > "$WRONG_VERSION/props.tmp"
mv "$WRONG_VERSION/props.tmp" "$WRONG_VERSION/project/.mvn/wrapper/maven-wrapper.properties"
if TEST_ARCHIVE="$WRONG_VERSION/apache-maven-3.10.0-bin.tar.gz" \
   TEST_CURL_LOG="$WRONG_VERSION/download.log" \
   MAVEN_USER_HOME="$WRONG_VERSION/home/.m2" \
   HOME="$WRONG_VERSION/home" \
   PATH="$WRONG_VERSION/bin:$PATH" \
   "$WRONG_VERSION/project/mvnw" --version > "$WRONG_VERSION/stdout.txt" 2> "$WRONG_VERSION/stderr.txt"; then
  fail 'distribution reporting the wrong Maven version was accepted'
fi
grep -q 'unexpected Maven version' "$WRONG_VERSION/stderr.txt" || fail 'wrong Maven version failure did not provide a clear diagnostic'

printf '%s\n' 'PASS: cold-cache install, exact-version validation, cache reuse, checksum rejection, HTTPS enforcement, duplicate-property and wrong-version rejection.'
