#!/usr/bin/env bash
set -euo pipefail

echo "Waiting for MySQL..."
until mysqladmin ping -h mysql --silent 2>/dev/null; do
    sleep 1
done
echo "MySQL is ready."

echo "Bootstrapping secrets..."
./bootstrap-secrets.sh

# Decrypt secrets at runtime — never written to disk in plaintext.
SECRETS_DIR="${SECRETS_DIR:-/app/secrets}"
PASS=$(cat "$SECRETS_DIR/.wrap")

decrypt() {
    openssl enc -aes-256-cbc -pbkdf2 -d -pass "pass:$PASS" -in "$1" 2>/dev/null
}

export RECLAIM_JWT_SECRET=$(decrypt "$SECRETS_DIR/jwt-signing-key.enc")
export RECLAIM_REFRESH_SECRET=$(decrypt "$SECRETS_DIR/refresh-token-secret.enc")
export RECLAIM_ENCRYPTION_KEY=$(decrypt "$SECRETS_DIR/field-encryption-key.enc")

# Decrypt bootstrap passwords to a temporary file, pass to app, then delete.
BOOTSTRAP_TMP=$(mktemp)
decrypt "$SECRETS_DIR/bootstrap-passwords.enc" > "$BOOTSTRAP_TMP"
chmod 600 "$BOOTSTRAP_TMP"

echo "Starting ReClaim Portal..."
exec java -jar app.jar \
    --reclaim.bootstrap.passwords-file="$BOOTSTRAP_TMP"
