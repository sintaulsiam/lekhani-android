#!/usr/bin/env bash
# ==============================================================================
# scripts/setup_release_signing.sh
# Sets up production release signing for Lekhani Android.
#
# Generates a dedicated release keystore and keystore.properties so that APKs
# are signed with a proper release certificate (v1, v2, v3, v4 schemes),
# eliminating Google Play Protect "Unsafe app blocked" warnings.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$REPO_ROOT/android"
KEYSTORE_FILE="$ANDROID_DIR/lekhani-release.jks"
PROPERTIES_FILE="$ANDROID_DIR/keystore.properties"

echo "=== Lekhani Android Release Signing Setup ==="

# 1. Check if keystore already exists
if [ -f "$KEYSTORE_FILE" ] && [ -f "$PROPERTIES_FILE" ]; then
    echo "✓ Existing release keystore found at: $KEYSTORE_FILE"
    echo "✓ Existing keystore.properties found at: $PROPERTIES_FILE"
else
    echo "Generating new production release keystore..."
    
    KEY_PASS="${LEKHANI_KEY_PASSWORD:-$(openssl rand -base64 24 2>/dev/null || echo "lekhani_release_$(date +%s)")}"
    STORE_PASS="${LEKHANI_KEYSTORE_PASSWORD:-$KEY_PASS}"
    KEY_ALIAS="${LEKHANI_KEY_ALIAS:-lekhani}"

    keytool -genkeypair \
        -v \
        -keystore "$KEYSTORE_FILE" \
        -storepass "$STORE_PASS" \
        -alias "$KEY_ALIAS" \
        -keypass "$KEY_PASS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Lekhani Keyboard, OU=Mobile, O=Lekhani Open Source, L=Dhaka, ST=Dhaka, C=BD"

    cat > "$PROPERTIES_FILE" <<EOF
# Production Release Keystore Configuration
# Generated automatically by scripts/setup_release_signing.sh
# DO NOT COMMIT THIS FILE TO VERSION CONTROL
LEKHANI_KEYSTORE_PATH=lekhani-release.jks
LEKHANI_KEYSTORE_PASSWORD=$STORE_PASS
LEKHANI_KEY_ALIAS=$KEY_ALIAS
LEKHANI_KEY_PASSWORD=$KEY_PASS
EOF
    chmod 600 "$KEYSTORE_FILE" "$PROPERTIES_FILE"
    echo "✓ Created $KEYSTORE_FILE"
    echo "✓ Created $PROPERTIES_FILE"
fi

# 2. Extract and display certificate fingerprints
echo ""
echo "=== Certificate Fingerprints ==="
STORE_PASS="$(grep "LEKHANI_KEYSTORE_PASSWORD=" "$PROPERTIES_FILE" | cut -d'=' -f2)"
KEY_ALIAS="$(grep "LEKHANI_KEY_ALIAS=" "$PROPERTIES_FILE" | cut -d'=' -f2)"

keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASS" -alias "$KEY_ALIAS" | grep -E "Owner:|Valid from:|SHA1:|SHA256:" || true

echo ""
echo "=== Next Steps ==="
echo "1. Build a signed release APK:"
echo "   cd android && ./gradlew assembleRelease"
echo ""
echo "2. The APK at android/app/build/outputs/apk/release/app-release.apk will be signed with this release certificate."
echo ""
echo "3. Google Play Protect Whitelisting (Free & Instant):"
echo "   If distributing publicly outside the Google Play Store, submit the APK to:"
echo "   https://support.google.com/googleplay/android-developer/contact/protectappeals"
echo "   Play Protect will whitelist this certificate, eliminating all installer warnings."
