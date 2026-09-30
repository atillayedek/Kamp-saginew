#!/usr/bin/env bash
# Read-only checks against the live Supabase project over HTTP, as the app sees it.
# Nothing is created or changed. Needs SUPABASE_URL and SUPABASE_ANON_KEY
# (the client-safe values the Android build uses); values are never printed.
set -euo pipefail

: "${SUPABASE_URL:?SUPABASE_URL is required}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY is required}"

# The key goes into the APK: it must be the client key, never the secret one.
case "$SUPABASE_ANON_KEY" in
  sb_publishable_*) ;;
  sb_secret_*)
    echo "FAIL SUPABASE_ANON_KEY is a secret key (sb_secret_...). Use the publishable or anon key."
    exit 1 ;;
  *)
    payload="$(printf '%s' "$SUPABASE_ANON_KEY" | cut -d. -f2 | tr '_-' '/+')"
    while [ $(( ${#payload} % 4 )) -ne 0 ]; do payload="${payload}="; done
    if ! printf '%s' "$payload" | base64 -d 2>/dev/null | grep -Eq '"role" *: *"anon"'; then
      echo "FAIL SUPABASE_ANON_KEY is not the anon key (its role is not anon). Use the publishable or anon key."
      exit 1
    fi ;;
esac
echo "PASS SUPABASE_ANON_KEY is a client key"

failures=0

# check <name> <expected HTTP status> <text the body must contain> <curl args...>
# Pushes to this branch deploy to the live project at the same time as CI runs
# (Supabase GitHub integration), so a mismatch is retried for up to a minute
# before it counts as a failure.
check() {
  local name="$1" expected="$2" needle="$3"
  shift 3
  local body status attempt
  body="$(mktemp)"
  for attempt in 1 2 3 4; do
    status="$(curl -sS -o "$body" -w '%{http_code}' -m 30 "$@")" || status="000"
    if [ "$status" = "$expected" ] && grep -q -- "$needle" "$body"; then
      echo "PASS $name"
      rm -f "$body"
      return
    fi
    [ "$attempt" -lt 4 ] && sleep 20
  done
  echo "FAIL $name: expected HTTP $expected containing '$needle', got HTTP $status: $(head -c 300 "$body")"
  failures=$((failures + 1))
  rm -f "$body"
}

anon=(-H "apikey: ${SUPABASE_ANON_KEY}" -H "Authorization: Bearer ${SUPABASE_ANON_KEY}" -H "Content-Type: application/json")

check "auth: email sign-up enabled, confirmation required" 200 '"mailer_autoconfirm":false' \
  "${SUPABASE_URL}/auth/v1/settings" -H "apikey: ${SUPABASE_ANON_KEY}"
check "rest: universities hidden from signed-out callers" 200 '\[\]' \
  "${SUPABASE_URL}/rest/v1/universities?select=id&limit=1" "${anon[@]}"
check "rest: feed RPC refused for signed-out callers" 401 'permission denied' \
  -X POST "${SUPABASE_URL}/rest/v1/rpc/list_posts" "${anon[@]}" -d '{"p_scope":"GENERAL"}'
check "rest: signup trigger not exposed" 404 'PGRST202' \
  -X POST "${SUPABASE_URL}/rest/v1/rpc/handle_new_user" "${anon[@]}" -d '{}'
check "rest: admin overview refused for signed-out callers" 401 'permission denied' \
  -X POST "${SUPABASE_URL}/rest/v1/rpc/admin_overview" "${anon[@]}" -d '{}'
check "rest: broadcast recipients not exposed to clients" 401 'permission denied' \
  -X POST "${SUPABASE_URL}/rest/v1/rpc/broadcast_recipients" "${anon[@]}" -d '{"p_audience":"ALL","p_marketing":false}'
# Forged unsubscribe links are refused. Before UNSUBSCRIBE_SECRET is set the function
# answers email_not_configured, reported as NOT CONFIGURED rather than a pass.
unsub_body="$(mktemp)"
unsub_status="$(curl -sS -o "$unsub_body" -w '%{http_code}' -m 30 -X POST \
  "${SUPABASE_URL}/functions/v1/email-unsubscribe?u=00000000-0000-0000-0000-000000000000&s=$(printf '0%.0s' {1..64})" \
  "${anon[@]}" -d '{}')" || unsub_status="000"
if [ "$unsub_status" = "400" ] && grep -q '"error":"invalid_link"' "$unsub_body"; then
  echo "PASS function email-unsubscribe: forged link refused"
elif [ "$unsub_status" = "503" ] && grep -q '"error":"email_not_configured"' "$unsub_body"; then
  echo "::warning::email-unsubscribe NOT CONFIGURED (UNSUBSCRIBE_SECRET missing); forged-link check NOT RUN"
else
  echo "FAIL function email-unsubscribe: got HTTP $unsub_status: $(head -c 300 "$unsub_body")"
  failures=$((failures + 1))
fi
rm -f "$unsub_body"
for fn in submit-student-document analyze-requirement publish-requirement verify-purchase delete-account admin-broadcast submit-course-note; do
  check "function $fn: requires a signed-in user" 401 '"error":"not_authenticated"' \
    -X POST "${SUPABASE_URL}/functions/v1/${fn}" "${anon[@]}" -d '{}'
done

if [ "$failures" -gt 0 ]; then
  echo "${failures} live check(s) failed"
  exit 1
fi
echo "All live checks passed"
