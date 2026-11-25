#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="$ROOT/zygisk/module"
PAYLOAD="${PAYLOAD_APK:-}"
PAYLOAD_KIND="${PAYLOAD_KIND:-source-rebuild}"
SUPPORT_APK="${SUPPORT_APK:-$PAYLOAD}"
OUT="${OUT_ZIP:-$ROOT/../build/XAutoDaily-Zygisk-3.1.0-rc6-$PAYLOAD_KIND.zip}"

if [[ ! -f "$PAYLOAD" ]]; then
  echo "Set PAYLOAD_APK to the payload APK to package; no APK is synthesized by this script." >&2
  exit 1
fi
if [[ ! -f "$SUPPORT_APK" ]]; then
  echo "Set SUPPORT_APK to a source-built APK containing LSPlant/Dobby hook libraries." >&2
  exit 1
fi
case "$PAYLOAD_KIND" in
  source-rebuild|original) ;;
  *) echo "PAYLOAD_KIND must be source-rebuild or original" >&2; exit 1 ;;
esac

PAYLOAD_SHA256="$(sha256sum "$PAYLOAD" | awk '{print $1}')"
if [[ "$PAYLOAD_KIND" == "original" && "$PAYLOAD_SHA256" != "2d337bb750d0187d6e6eb06fa0429bd3e37b0f2841b904f6d01d08c6886413df" ]]; then
  echo "PAYLOAD_KIND=original requires the exact uploaded APK SHA-256; refusing to mislabel another APK." >&2
  exit 1
fi
if [[ "$PAYLOAD_KIND" == "original" ]]; then
  echo "The exact original APK is verified, but it lacks this port's Bootstrap/HookRuntime/XposedHooker Java classes. It cannot be the runnable Zygisk payload; use PAYLOAD_KIND=source-rebuild. Refusing to create a non-working installer." >&2
  exit 1
fi

# Refuse to package a source APK that omitted any runtime bridge classes.
DEX_FILES="$(unzip -Z1 "$PAYLOAD" | grep -E '^classes([0-9]+)?[.]dex$' || true)"
for CLASS in Bootstrap BindApplicationHook HostClassLoaderBridge HookRuntime XposedHooker NativeLoader; do
  FOUND=0
  while IFS= read -r DEX; do
    [[ -n "$DEX" ]] || continue
    if unzip -p "$PAYLOAD" "$DEX" | grep -aFq "me/teble/xposed/autodaily/zygisk/$CLASS"; then
      FOUND=1
      break
    fi
  done <<< "$DEX_FILES"
  if [[ "$FOUND" != 1 ]]; then
    echo "PAYLOAD_APK is missing required Zygisk Java bridge class: $CLASS" >&2
    exit 1
  fi
done

"$ROOT/zygisk/build-native.sh"
# Keep the selected payload byte-for-byte intact. New hook backend libraries are staged
# separately so the payload APK need not be modified to add the native hook backend.
rm -rf "$MODULE/native-libs"
for ABI in arm64-v8a armeabi-v7a; do
  mkdir -p "$MODULE/native-libs/$ABI"
  for LIB in liblsplant.so libdobby.so libxautodaily_hook.so; do
    unzip -p "$SUPPORT_APK" "lib/$ABI/$LIB" > "$MODULE/native-libs/$ABI/$LIB"
    if [[ ! -s "$MODULE/native-libs/$ABI/$LIB" ]]; then
      echo "SUPPORT_APK is missing lib/$ABI/$LIB" >&2
      exit 1
    fi
  done
done

mkdir -p "$(dirname "$OUT")"
cp "$PAYLOAD" "$MODULE/payload.apk"
cat > "$MODULE/payload.info" <<INFO
kind=$PAYLOAD_KIND
sha256=$PAYLOAD_SHA256
baseline_revision=09618e67d7441c24b0aad149be3485fcb2eab91d
payload_version=3.1.0-rc6
INFO
(
  cd "$MODULE"
  zip -qr "$OUT" META-INF module.prop customize.sh uninstall.sh zygisk native-libs payload.apk payload.info licenses
)
echo "Created candidate module archive: $OUT"
echo "Payload kind: $PAYLOAD_KIND"
echo "Payload SHA-256: $PAYLOAD_SHA256"
