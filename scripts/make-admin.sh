#!/usr/bin/env bash
# Grants the platform ADMIN role (manage apps) to an existing account in the LOCAL Docker database.
# By design there is no API or UI for this (SplitMate decision D42): only someone with database access can do it.
# Usage: ./scripts/make-admin.sh you@example.com      (then sign out and in again: roles are read at sign-in)
set -euo pipefail
EMAIL="${1:?usage: make-admin.sh <email>}"
docker exec authreflector-postgres psql -U authreflector -d authreflector -v ON_ERROR_STOP=1 \
  -c "update users set role = 'ADMIN' where email = lower('${EMAIL//\'/}')" \
  -c "select email, role from users where email = lower('${EMAIL//\'/}')"
