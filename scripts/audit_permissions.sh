#!/usr/bin/env bash
# scripts/audit_permissions.sh
# ══════════════════════════════════════════════════════════════════════════════
# Zero-Permission Audit — Phase 2, AGENTS.md §1.1 & ROADMAP.md Phase 2
#
# Verifies that android.permission.INTERNET is NEVER declared in any
# AndroidManifest.xml in the project (including library manifests merged
# by the Gradle manifest merger at build time).
#
# Usage:
#   ./scripts/audit_permissions.sh [path/to/manifest...]
#   ./scripts/audit_permissions.sh                        # scans entire project
#
# Exit codes:
#   0 — audit passed (no INTERNET permission found)
#   1 — audit FAILED (INTERNET permission found — build must be rejected)
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

FORBIDDEN_PERMISSION="android.permission.INTERNET"
PASS=0
FAIL=1

# ── Determine which files to scan ─────────────────────────────────────────────
if [ "$#" -gt 0 ]; then
    MANIFEST_FILES=("$@")
else
    # Default: find all AndroidManifest.xml files excluding build/ and target/
    mapfile -t MANIFEST_FILES < <(
        find . \
          -name "AndroidManifest.xml" \
          -not -path "*/build/*" \
          -not -path "*/target/*" \
          -not -path "*/.git/*"
    )
fi

if [ "${#MANIFEST_FILES[@]}" -eq 0 ]; then
    echo "⚠️  No AndroidManifest.xml files found — nothing to audit."
    exit $PASS
fi

echo "🔍 Scanning ${#MANIFEST_FILES[@]} manifest(s) for '$FORBIDDEN_PERMISSION'..."
echo ""

FOUND_ANY=false
for manifest in "${MANIFEST_FILES[@]}"; do
    if grep -E 'uses-permission[^>]+android\.permission\.INTERNET' "$manifest" 2>/dev/null; then
        echo "❌ VIOLATION: $manifest"
        grep -n "$FORBIDDEN_PERMISSION" "$manifest" | sed 's/^/    /'
        FOUND_ANY=true
    else
        echo "✅ CLEAN:     $manifest"
    fi
done

echo ""
if [ "$FOUND_ANY" = true ]; then
    echo "══════════════════════════════════════════════════════════════════════"
    echo "  AUDIT FAILED: android.permission.INTERNET is FORBIDDEN."
    echo "  Lekhani is a zero-network keyboard. Remove the permission and"
    echo "  ensure all data (models, dictionaries, prefs) is 100% local."
    echo "══════════════════════════════════════════════════════════════════════"
    exit $FAIL
else
    echo "══════════════════════════════════════════════════════════════════════"
    echo "  AUDIT PASSED: No internet permission found in any manifest."
    echo "══════════════════════════════════════════════════════════════════════"
    exit $PASS
fi
