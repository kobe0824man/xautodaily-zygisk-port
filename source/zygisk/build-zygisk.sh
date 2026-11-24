#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="$ROOT/zygisk/module"
JNI="$MODULE/jni"
NDK="\${ANDROID_NDK_HOME:-\${ANDROID_SDK_ROOT:-}/ndk/27.0.12077973}"
CMAKE="\${CMAKE_BIN:-\${ANDROID_SDK_ROOT:-}/cmake/3.22.1/bin/cmake}"
NINJA="\${NINJA_BIN:-\${ANDROID_SDK_ROOT:-}/cmake/3.22.1/bin/ninja}"
PAYLOAD="\${PAYLOAD_APK:-}"
: "\${NDK:?Set ANDROID_NDK_HOME or ANDROID_SDK_ROOT}"
[ -d "$NDK" ] || { echo "NDK not found: $NDK" >&2; exit 1; }
[ -x "$CMAKE" ] || { echo "CMake not found: $CMAKE" >&2; exit 1; }
[ -x "$NINJA" ] || { echo "Ninja not found: $NINJA" >&2; exit 1; }
[ -f "$PAYLOAD" ] || { echo "Set PAYLOAD_APK to the exact XAutoDaily 3.1.0-rc6 APK" >&2; exit 1; }
OUT="$ROOT/../build/XAutoDaily-Zygisk-3.1.0-rc6.zip"
mkdir -p "$MODULE/zygisk" "$(dirname "$OUT")"
cp "$PAYLOAD" "$MODULE/payload.apk"
for ABI in arm64-v8a armeabi-v7a; do
  BUILD="$ROOT/../build/zygisk/$ABI"
  rm -rf "$BUILD"
  "$CMAKE" -G Ninja -S "$JNI" -B "$BUILD" \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=Release
  "$CMAKE" --build "$BUILD" --parallel
  cp "$BUILD/libzygisk.so" "$MODULE/zygisk/$ABI.so"
done
(cd "$MODULE" && zip -qr "$OUT" module.prop customize.sh uninstall.sh zygisk payload.apk)
echo "Created $OUT"
