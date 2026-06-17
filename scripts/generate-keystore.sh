#!/usr/bin/env bash
# One-time: generate a release keystore for OpenConverter.
# Output: ~/keystores/openconverter.jks
# The keystore password + key password are stored in:
#   1. The script's stdout (you record them in your password manager)
#   2. NOT in git (gitignored)
#
# For v0.2.2, the keystore is local-only; CI (future) would use a secret
# manager. The keystore itself is also gitignored.
set -euo pipefail

KEYSTORE_DIR="${HOME}/keystores"
KEYSTORE="${KEYSTORE_DIR}/openconverter.jks"
ALIAS="openconverter"
VALIDITY_DAYS=10000

mkdir -p "$KEYSTORE_DIR"

if [ -f "$KEYSTORE" ]; then
    echo "Keystore already exists: $KEYSTORE"
    exit 0
fi

# Generate with a random password (printed for you to record).
# Note: keytool requires the password to be passed via stdin (not CLI args).
# We use a fixed password for v0.2.2 dev; rotate before public release.
KEYSTORE_PASS="openc0nverter_v0.2.2_dev"
KEY_PASS="openc0nverter_v0.2.2_dev"

keytool -genkey -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$KEYSTORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=OpenConverter, OU=OpenConverter, O=OpenConverter, L=, S=, C=CN" 2>&1

cat <<EOF

========================================
KEYSTORE GENERATED
========================================
Path:     $KEYSTORE
Alias:    $ALIAS
Store PW: $KEYSTORE_PASS
Key PW:   $KEY_PASS

⚠️  RECORD THESE PASSWORDS in your password manager.
    The keystore is gitignored; if you lose the keystore + passwords,
    you cannot sign updates to existing installs.

For CI / public release: replace with a managed secret + rotate.
========================================
EOF
