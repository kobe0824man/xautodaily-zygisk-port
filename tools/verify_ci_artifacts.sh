#!/usr/bin/env bash
# Download what a CI run produced and check it, rather than trusting a green tick.
#
# The build's output is a Magisk-installable Zygisk module: a payload APK plus the three
# native hook libraries per ABI. This script pulls the artifacts of a run, unpacks the
# candidate module ZIP and checks that it actually carries what the packager promised.
#
# Requirements:
#   * a token for the target account in GITHUB_PERSONAL_TOKEN or in .env next to this
#     repository. It is never echoed.
#
# Usage:
#   bash tools/verify_ci_artifacts.sh <run-id>
set -euo pipefail

OWNER="${OWNER:-kobe0824man}"
REPO_NAME="${REPO_NAME:-xautodaily-zygisk-port}"
WORKSPACE="${WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
API="https://api.github.com"

if [[ -z "${GITHUB_PERSONAL_TOKEN:-}" && -f "$WORKSPACE/.env" ]]; then
  GITHUB_PERSONAL_TOKEN="$(sed -n 's/^GITHUB_PERSONAL_TOKEN=//p' "$WORKSPACE/.env" | tail -1)"
fi
TOKEN="${GITHUB_PERSONAL_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
  echo "GITHUB_PERSONAL_TOKEN is not set. Export it or add it to $WORKSPACE/.env." >&2
  exit 2
fi
if [[ $# -lt 1 ]]; then
  echo "Usage: bash tools/verify_ci_artifacts.sh <run-id>" >&2
  exit 2
fi
RUN_ID="$1"

AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

echo "== artifacts of run $RUN_ID =="
LIST="$OUT/artifacts.json"
curl -sS -m 60 "${AUTH[@]}" "$API/repos/$OWNER/$REPO_NAME/actions/runs/$RUN_ID/artifacts" -o "$LIST"
python3 - "$LIST" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
arts = d.get("artifacts", [])
if not arts:
    print("  no artifacts")
for a in arts:
    print("  {} | {:44} | {} bytes | expired={}".format(
        a["id"], a["name"][:44], a["size_in_bytes"], a["expired"]))
PY

ART_ID="$(python3 - "$LIST" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
for a in d.get("artifacts", []):
    if a["name"] == "xautodaily-zygisk-source-rebuild" and not a["expired"]:
        print(a["id"]); break
PY
)"
if [[ -z "$ART_ID" ]]; then
  echo "No usable artifact named xautodaily-zygisk-source-rebuild." >&2
  exit 1
fi

echo
echo "== downloading artifact $ART_ID =="
ZIP="$OUT/artifact.zip"
curl -sSL -m 600 "${AUTH[@]}" "$API/repos/$OWNER/$REPO_NAME/actions/artifacts/$ART_ID/zip" -o "$ZIP"
echo "  downloaded $(wc -c < "$ZIP") bytes"
mkdir -p "$OUT/unpacked"
unzip -q -o "$ZIP" -d "$OUT/unpacked"

echo
echo "== contents =="
find "$OUT/unpacked" -type f | sed "s#$OUT/unpacked/##" | sort | sed 's/^/  /'

APK="$(find "$OUT/unpacked" -name 'app-debug.apk' -type f | head -1)"
MODULE_ZIP="$(find "$OUT/unpacked" -name 'XAutoDaily-Zygisk-*.zip' -type f | head -1)"
if [[ -z "$APK" ]]; then
  echo "The payload APK is missing from the artifact." >&2
  exit 1
fi
if [[ -z "$MODULE_ZIP" ]]; then
  echo "The candidate module ZIP is missing from the artifact." >&2
  exit 1
fi

echo
echo "== payload APK =="
echo "  sha256: $(sha256sum "$APK" | awk '{print $1}')"
echo "  size:   $(wc -c < "$APK") bytes"
for abi in arm64-v8a armeabi-v7a; do
  for lib in libxautodaily_hook.so liblsplant.so libdobby.so; do
    unzip -l "$APK" "lib/$abi/$lib" | grep -F "$lib" >/dev/null \
      && echo "  lib/$abi/$lib present" \
      || { echo "  lib/$abi/$lib MISSING" >&2; exit 1; }
  done
done

echo
echo "== candidate module ZIP =="
unzip -l "$MODULE_ZIP" | sed 's/^/  /'
mkdir -p "$OUT/module"
unzip -q -o "$MODULE_ZIP" -d "$OUT/module"
echo "  payload.info:"
sed 's/^/    /' "$OUT/module/payload.info"
PAYLOAD_SHA="$(sed -n 's/^sha256=//p' "$OUT/module/payload.info")"
ACTUAL_SHA="$(sha256sum "$OUT/module/payload.apk" | awk '{print $1}')"
if [[ "$PAYLOAD_SHA" != "$ACTUAL_SHA" ]]; then
  echo "  payload.info records $PAYLOAD_SHA but payload.apk is $ACTUAL_SHA" >&2
  exit 1
fi
echo "  payload.apk matches the recorded sha256"
for abi in arm64-v8a armeabi-v7a; do
  for lib in liblsplant.so libdobby.so libxautodaily_hook.so; do
    [[ -s "$OUT/module/native-libs/$abi/$lib" ]] \
      && echo "  native-libs/$abi/$lib present" \
      || { echo "  native-libs/$abi/$lib MISSING" >&2; exit 1; }
  done
done
echo
echo "All artifact checks passed."
