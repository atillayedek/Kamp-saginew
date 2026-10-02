#!/usr/bin/env bash
# Writes ios/Config/Secrets.xcconfig from environment variables (CI secrets or a local shell).
# Reports which values are present, never the values. xcconfig treats "//" as a comment, so
# URLs are written with "/$()/" (an empty variable between the slashes).
set -euo pipefail
out="$(dirname "$0")/../Config/Secrets.xcconfig"
escape() { printf '%s' "$1" | sed 's#//#/$()/#g'; }
{
  echo "SUPABASE_URL = $(escape "${SUPABASE_URL:-}")"
  echo "SUPABASE_ANON_KEY = ${SUPABASE_ANON_KEY:-}"
  echo "WEBSITE_URL = $(escape "${WEBSITE_URL:-}")"
  echo "KAMPUSAGI_BUILD_NUMBER = ${KAMPUSAGI_BUILD_NUMBER:-1}"
} > "$out"
for name in SUPABASE_URL SUPABASE_ANON_KEY WEBSITE_URL; do
  if [ -n "${!name:-}" ]; then echo "$name: present"; else echo "$name: MISSING"; fi
done
