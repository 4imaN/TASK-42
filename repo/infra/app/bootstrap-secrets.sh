#!/usr/bin/env bash
set -euo pipefail

SECRETS_DIR="${SECRETS_DIR:-/app/secrets}"
mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR"

# Derive a volume-local encryption passphrase from a machine-stable identifier.
# This is not HSM-grade; it prevents naive disk reads of raw signing material.
WRAP_PASS="${SECRETS_DIR}/.wrap"
if [ ! -f "$WRAP_PASS" ]; then
    openssl rand -base64 32 | tr -d '\n' > "$WRAP_PASS"
    chmod 600 "$WRAP_PASS"
fi
PASS=$(cat "$WRAP_PASS")

# Write a secret encrypted at rest with AES-256-CBC.
# The entrypoint decrypts on read.
write_encrypted_secret() {
    local file="$1"
    local value="$2"
    printf '%s' "$value" | openssl enc -aes-256-cbc -pbkdf2 -salt -pass "pass:$PASS" -out "$file" 2>/dev/null
    chmod 600 "$file"
}

# Read and decrypt a secret file.
read_encrypted_secret() {
    local file="$1"
    openssl enc -aes-256-cbc -pbkdf2 -d -pass "pass:$PASS" -in "$file" 2>/dev/null
}

generate_secret() {
    openssl rand -base64 64 | tr -d '\n'
}

if [ ! -f "$SECRETS_DIR/jwt-signing-key.enc" ]; then
    echo "Generating JWT signing key..."
    write_encrypted_secret "$SECRETS_DIR/jwt-signing-key.enc" "$(generate_secret)"
fi

if [ ! -f "$SECRETS_DIR/refresh-token-secret.enc" ]; then
    echo "Generating refresh token secret..."
    write_encrypted_secret "$SECRETS_DIR/refresh-token-secret.enc" "$(generate_secret)"
fi

if [ ! -f "$SECRETS_DIR/field-encryption-key.enc" ]; then
    echo "Generating field encryption key..."
    write_encrypted_secret "$SECRETS_DIR/field-encryption-key.enc" "$(openssl rand -base64 32 | tr -d '\n')"
fi

if [ ! -f "$SECRETS_DIR/bootstrap-passwords.enc" ]; then
    # Deterministic demo credentials documented in README.md for evaluation.
    # Override by setting RECLAIM_ADMIN_PASS / RECLAIM_REVIEWER_PASS / RECLAIM_USER_PASS
    # in the environment before first boot. Not for production — rotate on deployment.
    ADMIN_PASS="${RECLAIM_ADMIN_PASS:-AdminDemo1!pass}"
    REVIEWER_PASS="${RECLAIM_REVIEWER_PASS:-ReviewerDemo1!pass}"
    USER_PASS="${RECLAIM_USER_PASS:-UserDemo1!pass}"
    write_encrypted_secret "$SECRETS_DIR/bootstrap-passwords.enc" "admin_password=$ADMIN_PASS
reviewer_password=$REVIEWER_PASS
user_password=$USER_PASS"
    echo "========================================="
    echo "BOOTSTRAP CREDENTIALS (first boot):"
    echo "  admin    / $ADMIN_PASS"
    echo "  reviewer / $REVIEWER_PASS"
    echo "  user     / $USER_PASS"
    echo "========================================="
fi

echo "Secrets ready at $SECRETS_DIR (encrypted at rest, permissions: 600)"
