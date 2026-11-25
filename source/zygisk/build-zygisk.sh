#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="$ROOT/zygisk/module"
PAYLOAD="${PAYLOAD_APK:-}"
PAYLOAD_KIND="${PAYLOAD_KIND:-source-rebuild}"
OUT="${OUT_ZIP:-$ROOT/../build/XAutoDaily-Zygisk-3.1.0-rc6-${PAYLOAD_KIND}.zip}"

if [[ ! -f "$PAYLOAD" ]]; then
  echo "Set PAYLOAD_APK to the payload APK to package; no APK is synthesized by this script." >&2
  exit 1
fi
case "$PAYLOAD_KIND" in
  source-rebuild|original) ;;
  *) echo "PAYLOAD_KIND must be source-rebuild or original" >&2; exit 1 ;;
esac
PAYLOAD_SHA256="$(sha256sum "$PAYLOAD" | awk '{print $1}')"
if [[ "$PAYLOAD_KIND" == "original" && "$PAYLOAD_SHA256" != "2d337bb750d0187d6e6eb06fa0429bd3e37b0f2841b904f6d01d08c6886413df" ]]; then
  echo "PAYLOAD_KIND=original requires the exact uploaded APK SHA-256 (2d337bb750d0187d6e6eb06fa0429bd3e37b0f2841b904f6d01d08c6886413df); refusing to mislabel another APK." >&2
  exit 1
fi

"$ROOT/zygisk/build-native.sh"
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
  zip -qr "$OUT" META-INF module.prop customize.sh uninstall.sh zygisk payload.apk payload.info licenses
)
echo "Created candidate module archive: $OUT"
echo "Payload kind: $PAYLOAD_KIND"
echo "Payload SHA-256: $PAYLOAD_SHA256"
