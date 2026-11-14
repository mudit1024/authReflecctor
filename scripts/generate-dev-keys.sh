#!/usr/bin/env bash
# Creates a local development RSA key pair for signing tokens.
# Output: backend/secrets/dev-private.pem (PKCS#8) and dev-public.pem (X.509). The folder is git-ignored.
# Usage (Git Bash or any Unix shell, from the repository root): ./scripts/generate-dev-keys.sh
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)/backend/secrets"
mkdir -p "$DIR"

if [[ -f "$DIR/dev-private.pem" ]]; then
  echo "Keys already exist in $DIR (delete them first to regenerate; existing tokens will stop verifying)."
  exit 0
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$DIR/dev-private.pem"
openssl pkey -in "$DIR/dev-private.pem" -pubout -out "$DIR/dev-public.pem"
echo "Created $DIR/dev-private.pem and $DIR/dev-public.pem"
