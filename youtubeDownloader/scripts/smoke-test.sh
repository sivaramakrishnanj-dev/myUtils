#!/usr/bin/env bash
# smoke-test.sh — Cross-platform smoke test for youtube-downloader fat-jar.
# Exercises --version, --help, no-args, and invalid-URL to verify exit codes.
# Run from project root after: mvn clean package -DskipTests
set -euo pipefail

JAR="yt-cli/target/youtube-downloader-1.0.0.jar"
PASS=0
FAIL=0

assert_exit_code() {
    local description="$1"
    local expected="$2"
    shift 2
    local actual=0
    "$@" >/dev/null 2>&1 || actual=$?
    if [ "$actual" -eq "$expected" ]; then
        echo "  PASS: $description (exit=$actual)"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $description (expected=$expected, got=$actual)"
        FAIL=$((FAIL + 1))
    fi
}

echo "=== youtube-downloader smoke tests ==="
echo "OS: $(uname -s) $(uname -m)"
echo "Java: $(java -version 2>&1 | head -1)"
echo "Jar: $JAR"
echo ""

if [ ! -f "$JAR" ]; then
    echo "ERROR: Fat-jar not found at $JAR"
    echo "Run: mvn clean package -DskipTests"
    exit 1
fi

echo "--- Exit code checks ---"
assert_exit_code "--version exits 0" 0 java -jar "$JAR" --version
assert_exit_code "--help exits 0" 0 java -jar "$JAR" --help
assert_exit_code "no-args exits 2 (usage error)" 2 java -jar "$JAR"
assert_exit_code "invalid URL exits 2 (args error)" 2 java -jar "$JAR" "not-a-valid-url"

echo ""
echo "--- Output checks ---"
VERSION_OUT=$(java -jar "$JAR" --version 2>&1)
if echo "$VERSION_OUT" | grep -qE '[0-9]+\.[0-9]+\.[0-9]+'; then
    echo "  PASS: --version output contains semver"
    PASS=$((PASS + 1))
else
    echo "  FAIL: --version output missing semver: $VERSION_OUT"
    FAIL=$((FAIL + 1))
fi

HELP_OUT=$(java -jar "$JAR" --help 2>&1)
if echo "$HELP_OUT" | grep -qi 'usage\|youtube'; then
    echo "  PASS: --help output contains usage info"
    PASS=$((PASS + 1))
else
    echo "  FAIL: --help output missing usage info"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
