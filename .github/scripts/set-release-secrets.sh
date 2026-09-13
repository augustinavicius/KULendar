#!/usr/bin/env bash
# Stores the release signing key as GitHub Actions secrets of this repository, for .github/workflows/release.yml.
#
# Usage: bash .github/scripts/set-release-secrets.sh [keystore.properties]
# Requires the GitHub CLI to be logged in (`gh auth login`). The values are piped to gh and never printed.
set -euo pipefail

properties="${1:-$HOME/.android/kulendar-release.properties}"
[ -f "$properties" ] || { echo "Signing properties not found: $properties" >&2; exit 1; }

setting() { sed -n "s/^$1=//p" "$properties" | head -1; }

store_file="$(setting storeFile)"
[ -f "$store_file" ] || { echo "Keystore not found: $store_file" >&2; exit 1; }
for name in storePassword keyAlias keyPassword; do
  [ -n "$(setting "$name")" ] || { echo "$name is missing in $properties" >&2; exit 1; }
done

base64 -w0 "$store_file" | gh secret set KULENDAR_KEYSTORE_BASE64
printf '%s' "$(setting storePassword)" | gh secret set KULENDAR_KEYSTORE_PASSWORD
printf '%s' "$(setting keyAlias)" | gh secret set KULENDAR_KEY_ALIAS
printf '%s' "$(setting keyPassword)" | gh secret set KULENDAR_KEY_PASSWORD

echo "Stored the release signing secrets for $(gh repo view --json nameWithOwner --jq .nameWithOwner)."
