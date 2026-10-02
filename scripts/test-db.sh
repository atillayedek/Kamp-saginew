#!/usr/bin/env bash
# Applies every migration to a throwaway PostgreSQL database and runs the SQL
# tests in supabase/tests. Any failed assertion stops the run with exit 1.
#
# Connection comes from the standard libpq variables (PGHOST, PGPORT, PGUSER,
# PGPASSWORD). The database named by TEST_DB (default kampusagi_test) is
# dropped and recreated on every run.
set -euo pipefail

cd "$(dirname "$0")/.."

DB="${TEST_DB:-kampusagi_test}"
PSQL=(psql -v ON_ERROR_STOP=1 --quiet --no-psqlrc)

"${PSQL[@]}" -d postgres -c "drop database if exists ${DB}" -c "create database ${DB}"
# Roles are cluster-wide; the harness creates them only if they are missing.
for role in anon authenticated service_role; do
  "${PSQL[@]}" -d postgres -c "drop role if exists ${role}" >/dev/null 2>&1 || true
done

"${PSQL[@]}" -d "$DB" -f supabase/tests/_supabase_harness.sql

for migration in supabase/migrations/*.sql; do
  echo "migration: ${migration}"
  "${PSQL[@]}" -d "$DB" -f "$migration"
done

"${PSQL[@]}" -d "$DB" -f supabase/tests/_helpers.sql

failed=0
for test in supabase/tests/[0-9]*.sql; do
  if "${PSQL[@]}" -d "$DB" -f "$test" >/dev/null; then
    echo "PASS ${test}"
  else
    echo "FAIL ${test}"
    failed=1
  fi
done

exit "$failed"
