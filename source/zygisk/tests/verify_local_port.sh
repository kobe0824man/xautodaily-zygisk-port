#!/usr/bin/env bash
# Source-level verification for the XAutoDaily Zygisk port.
#
# This does NOT replace the Gradle build in CI; it checks everything that can be checked with a
# plain JDK and the public Android SDK jar:
#
#   1. the port's Java package compiles against android.jar and the published API 82 artifact,
#   2. the port's unit tests pass,
#   3. the vendored API 82 implementation compiles against the public SDK,
#   4. the JNI registration table matches the API 82 native declarations,
#   5. the vendored API 82 tree matches the pinned upstream sources,
#   6. the pinned OpenSSL static archives still match their upstream Git blob IDs,
#   7. the packaging scripts refuse a missing/invalid payload.
#
# Requirements: JAVA_HOME (JDK 17+), ANDROID_JAR (platform android.jar), and network access for the
# pinned API 82 source JAR unless API82_SOURCES_JAR points at a local copy.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${OUT_DIR:-$(mktemp -d)}"

: "${JAVA_HOME:?set JAVA_HOME to a JDK 17+ home}"
: "${ANDROID_JAR:?set ANDROID_JAR to an android.jar from an Android platform}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

LIB_DIR="$OUT/lib"
mkdir -p "$LIB_DIR"

fetch() { # fetch <url> <destination>
  if [[ ! -f "$2" ]]; then
    echo "Fetching $2"
    curl --retry 3 --fail --silent --show-error "$1" -o "$2"
  fi
}

API82_SOURCES_JAR="${API82_SOURCES_JAR:-$LIB_DIR/api-82-sources.jar}"
API82_JAR="${API82_JAR:-$LIB_DIR/api-82.jar}"
fetch https://api.xposed.info/de/robv/android/xposed/api/82/api-82-sources.jar "$API82_SOURCES_JAR"
fetch https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar "$API82_JAR"
fetch https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar "$LIB_DIR/junit-4.13.2.jar"
fetch https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar \
  "$LIB_DIR/hamcrest-core-1.3.jar"

CP="$LIB_DIR/junit-4.13.2.jar:$LIB_DIR/hamcrest-core-1.3.jar:$API82_JAR:$ANDROID_JAR"

echo "== 1. compile the port package =="
mkdir -p "$OUT/main" "$OUT/test"
"$JAVAC" -nowarn -d "$OUT/main" -cp "$CP" \
  "$ROOT"/app/src/main/java/me/teble/xposed/autodaily/zygisk/*.java

echo "== 2. compile and run the port unit tests =="
"$JAVAC" -nowarn -d "$OUT/test" -cp "$CP:$OUT/main" \
  "$ROOT"/app/src/test/java/me/teble/xposed/autodaily/zygisk/*.java
"$JAVA" -cp "$CP:$OUT/main:$OUT/test" org.junit.runner.JUnitCore \
  me.teble.xposed.autodaily.zygisk.HostClassLoaderBridgeTest \
  me.teble.xposed.autodaily.zygisk.HookRuntimeTest

echo "== 3. compile the vendored API 82 implementation =="
mkdir -p "$OUT/api82"
"$JAVAC" -nowarn -d "$OUT/api82" -cp "$ANDROID_JAR" \
  $(find "$ROOT/xposed-api82/src/main/java" "$ROOT/xposed-api82-stubs/src/main/java" -name "*.java")

echo "== 4/5. API 82 registration table and vendored tree =="
python3 "$ROOT/zygisk/tests/verify_xposed_api82.py" \
  "$API82_SOURCES_JAR" "$ROOT/app/src/main/cpp/zygisk_hook.cpp" \
  --vendored "$ROOT/xposed-api82/src/main/java"

echo "== 6. pinned OpenSSL archives =="
python3 "$ROOT/zygisk/tests/verify_openssl_archives.py"

echo "== 7. packaging script refusals =="
bash -n "$ROOT/zygisk/build-zygisk.sh"
bash -n "$ROOT/zygisk/build-native.sh"
DUMMY="$OUT/dummy.apk"
printf 'not-an-apk' > "$DUMMY"
if PAYLOAD_APK="" bash "$ROOT/zygisk/build-zygisk.sh" >/dev/null 2>&1; then
  echo "FAIL: a missing payload was accepted" >&2; exit 1
fi
if PAYLOAD_APK="$DUMMY" PAYLOAD_KIND=bogus bash "$ROOT/zygisk/build-zygisk.sh" >/dev/null 2>&1; then
  echo "FAIL: an unknown payload kind was accepted" >&2; exit 1
fi
if PAYLOAD_APK="$DUMMY" PAYLOAD_KIND=original bash "$ROOT/zygisk/build-zygisk.sh" >/dev/null 2>&1; then
  echo "FAIL: a mislabelled original payload was accepted" >&2; exit 1
fi
echo "packaging refusals OK"

echo
echo "All source-level checks passed."
