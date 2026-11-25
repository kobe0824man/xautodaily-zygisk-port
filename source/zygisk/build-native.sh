#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="$ROOT/zygisk/module"
JNI="$MODULE/jni"
NDK="${ANDROID_NDK_HOME:-${ANDROID_SDK_ROOT:-}/ndk/27.0.12077973}"
CMAKE="${CMAKE_BIN:-$(command -v cmake || true)}"
NINJA="${NINJA_BIN:-$(command -v ninja || true)}"

if [[ ! -d "$NDK" ]]; then
  echo "Android NDK not found: $NDK (set ANDROID_NDK_HOME or ANDROID_SDK_ROOT)" >&2
  exit 1
fi
if [[ -z "$CMAKE" || ! -x "$CMAKE" ]]; then
  echo "CMake executable not found; set CMAKE_BIN" >&2
  exit 1
fi
if [[ -z "$NINJA" || ! -x "$NINJA" ]]; then
  echo "Ninja executable not found; set NINJA_BIN" >&2
  exit 1
fi

mkdir -p "$MODULE/zygisk"
for ABI in arm64-v8a armeabi-v7a; do
  BUILD="$ROOT/../build/zygisk/$ABI"
  rm -rf "$BUILD"
  "$CMAKE" -G Ninja -S "$JNI" -B "$BUILD" \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=Release
  "$CMAKE" --build "$BUILD" --parallel
  test -f "$BUILD/libzygisk.so"
  cp "$BUILD/libzygisk.so" "$MODULE/zygisk/$ABI.so"
  echo "Built Zygisk loader: $ABI"
done
