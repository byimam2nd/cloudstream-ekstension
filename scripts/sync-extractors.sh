#!/bin/bash

# ========================================
# Sync Master Extractors to All Active Sites
# ========================================
# This script syncs docs/MasterExtractors.kt to all active modules
# Active modules are folders that have build.gradle.kts
# ========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
MASTER_FILE="$ROOT_DIR/docs/MasterExtractors.kt"

echo "========================================"
echo "📦 Sync Master Extractors"
echo "========================================"
echo ""

# Check if master file exists
if [ ! -f "$MASTER_FILE" ]; then
    echo "❌ Master Extractors file not found at: $MASTER_FILE"
    exit 1
fi

echo "🔍 Detecting active modules (folders with build.gradle.kts)..."
echo ""

# Find all folders with build.gradle.kts (excluding root)
MODULES=()
while IFS= read -r build_file; do
    module_dir=$(dirname "$build_file" | sed "s|$ROOT_DIR/||")
    MODULES+=("$module_dir")
done < <(find "$ROOT_DIR" -maxdepth 2 -name "build.gradle.kts" | grep -v "^$ROOT_DIR/build.gradle.kts" || true)

MODULE_COUNT=${#MODULES[@]}

if [ "$MODULE_COUNT" -eq 0 ]; then
    echo "❌ No active modules found!"
    exit 1
fi

echo "✅ Found $MODULE_COUNT active module(s): ${MODULES[*]}"
echo ""

# Folder to package mapping
# Format: "folder"="subfolder:package"
declare -A FOLDER_CONFIGS=(
    ["Pencurimovie"]="Pencurimovie:pencurimovie"
    ["LayarKaca21"]="LayarKacaProvider:layarKacaProvider"
    ["Donghuastream"]="Donghuastream:donghuastream"
    ["Funmovieslix"]="Funmovieslix:funmovieslix"
    ["IdlixProvider"]="hexated:hexated"
    ["Anichin"]="Anichin:anichin"
)

SYNCED_COUNT=0
ERRORS=0

# Sync to each active module
for MODULE in "${MODULES[@]}"; do
    # Skip if module not in our config
    if [ -z "${FOLDER_CONFIGS[$MODULE]}" ]; then
        echo "⚠️  Warning: Module '$MODULE' not in config, skipping..."
        continue
    fi

    IFS=':' read -r FOLDER PACKAGE <<< "${FOLDER_CONFIGS[$MODULE]}"
    echo "📋 Syncing to $MODULE (folder: com/$FOLDER, package: com.$PACKAGE)..."

    # Destination directory and file
    DEST_DIR="$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER"
    DEST_FILE="$DEST_DIR/Extractors.kt"

    # Check if source folder exists
    if [ ! -d "$DEST_DIR" ]; then
        echo "❌ Error: Source directory not found: $DEST_DIR"
        echo "   Make sure the folder structure is correct for module: $MODULE"
        ERRORS=$((ERRORS + 1))
        continue
    fi

    # Copy master extractors with correct package name
    awk -v pkg="$PACKAGE" '
        NR==1 { print "// ========================================" }
        NR==2 { print "// AUTO-GENERATED - DO NOT EDIT MANUALLY" }
        NR==3 { print "// Synced from docs/MasterExtractors.kt" }
        NR==4 { print "// ========================================" }
        /^package / { print "package com." pkg; next }
        { print }
    ' "$MASTER_FILE" > "$DEST_FILE"

    # Count extractor classes
    CLASS_COUNT=$(grep -c "^class \|^open class " "$DEST_FILE" 2>/dev/null || echo 0)

    echo "✅ Synced: $MODULE/$FOLDER ($CLASS_COUNT extractor classes)"
    echo ""

    SYNCED_COUNT=$((SYNCED_COUNT + 1))
done

echo "========================================"
echo "📊 Sync Summary"
echo "========================================"
echo "   Total modules: $MODULE_COUNT"
echo "   Synced: $SYNCED_COUNT"
echo "   Errors: $ERRORS"
echo ""

if [ "$ERRORS" -gt 0 ]; then
    echo "❌ Sync FAILED with $ERRORS error(s)"
    exit 1
fi

if [ "$SYNCED_COUNT" -eq 0 ]; then
    echo "❌ Sync FAILED: No modules were synced"
    exit 1
fi

echo "✅ Sync completed successfully!"
echo ""
echo "Next steps:"
echo "  1. Review changes: git diff"
echo "  2. Commit: git add -A && git commit -m 'chore: sync extractors'"
echo "  3. Push: git push"
