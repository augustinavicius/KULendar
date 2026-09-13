#!/usr/bin/env bash
# Collects the assets of a release: the APK, the update manifest read by the in-app updater, and release notes.
#
# Inputs (environment): CHANNEL (stable|development), VERSION_NAME, VERSION_CODE, TAG, APK, OUT, ANDROID_HOME.
set -euo pipefail

: "${CHANNEL:?}" "${VERSION_NAME:?}" "${VERSION_CODE:?}" "${TAG:?}" "${APK:?}" "${OUT:?}" "${ANDROID_HOME:?}"

build_tools="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"

# Read identity and requirements from the APK itself rather than from the build script.
badging="$("$build_tools/aapt2" dump badging "$APK")"
application_id="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")"
apk_version_code="$(sed -n "s/^package: .* versionCode='\([^']*\)'.*/\1/p" <<< "$badging")"
min_sdk="$(sed -n "s/^minSdkVersion:'\([^']*\)'.*/\1/p" <<< "$badging")"
if [ "$apk_version_code" != "$VERSION_CODE" ]; then
  echo "APK has versionCode $apk_version_code, expected $VERSION_CODE" >&2
  exit 1
fi

# A release signed with a throwaway debug key could never update installed copies.
# Depending on its version, apksigner prints "Signer #1 certificate DN: ..." or "V2 Signer: certificate DN: ...".
certificates="$("$build_tools/apksigner" verify --print-certs "$APK")"
signer="$(awk -F'certificate DN: ' 'NF > 1 { print $2; exit }' <<< "$certificates")"
if [ -z "$signer" ] || [ "$signer" = "C=US, O=Android, CN=Android Debug" ]; then
  echo "APK is not signed with the release key (signer: ${signer:-none})" >&2
  exit 1
fi

mkdir -p "$OUT"
apk_name="KULendar-$VERSION_NAME.apk"
cp "$APK" "$OUT/$apk_name"
sha256="$(sha256sum "$OUT/$apk_name" | cut -d' ' -f1)"
size="$(stat -c %s "$OUT/$apk_name")"

APPLICATION_ID="$application_id" MIN_SDK="$min_sdk" APK_NAME="$apk_name" SHA256="$sha256" SIZE="$size" \
  COMMIT="${GITHUB_SHA:-$(git rev-parse HEAD)}" python3 - > "$OUT/kulendar-update.json" <<'PYTHON'
import json, os
print(json.dumps({
    "schema": 1,
    "applicationId": os.environ["APPLICATION_ID"],
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": os.environ["VERSION_NAME"],
    "channel": os.environ["CHANNEL"],
    "commit": os.environ["COMMIT"],
    "minSdk": int(os.environ["MIN_SDK"]),
    "apk": {"name": os.environ["APK_NAME"], "size": int(os.environ["SIZE"]), "sha256": os.environ["SHA256"]},
}, indent=2))
PYTHON

# Release notes: commits since the previous release of the same channel.
git fetch --force --tags --quiet origin 2> /dev/null || true
if [ "$CHANNEL" = stable ]; then
  pattern='^v[0-9.]+$'
else
  pattern='^v[0-9.]+-dev$'
fi
previous="$(git tag --list 'v*' | { grep -E "$pattern" || true; } | { grep -vxF "$TAG" || true; } \
  | awk -F. '{ run = $NF; sub(/-dev$/, "", run); print run, $0 }' | sort -k1,1n | tail -1 | cut -d' ' -f2)"

{
  if [ "$CHANNEL" = development ]; then
    echo "Development build of the \`development\` branch. It may contain unfinished changes."
    echo
  fi
  if [ -n "$previous" ]; then
    echo "## Changes since $previous"
    echo
    git log --no-merges --pretty='- %s (%h)' "$previous..HEAD"
  else
    echo "## Changes"
    echo
    git log --no-merges --pretty='- %s (%h)' -20 HEAD
  fi
  echo
  echo "SHA-256 of \`$apk_name\`: \`$sha256\`"
} > "$OUT/notes.md"

echo "Prepared $apk_name ($size bytes, versionCode $VERSION_CODE, $CHANNEL) for $TAG"
