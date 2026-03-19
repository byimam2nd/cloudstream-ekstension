#!/bin/bash

# ========================================
# Sync Master Extractors to All Active Sites
# ========================================
# FULLY DYNAMIC - No hardcoded module names!
# Auto-detects modules, folders, and packages
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

SYNCED_COUNT=0
ERRORS=0

# Sync to each active module - FULLY DYNAMIC
for MODULE in "${MODULES[@]}"; do
    echo "📋 Processing $MODULE..."

    # Auto-detect: Find the actual folder structure
    SRC_DIR="$ROOT_DIR/$MODULE/src/main/kotlin/com"

    if [ ! -d "$SRC_DIR" ]; then
        echo "   ❌ Error: Source directory not found: $SRC_DIR"
        ERRORS=$((ERRORS + 1))
        continue
    fi

    # Get the actual folder name(s) in com/
    FOLDERS=$(ls -1 "$SRC_DIR" 2>/dev/null)
    FOLDER_COUNT=$(echo "$FOLDERS" | wc -l)

    if [ "$FOLDER_COUNT" -eq 0 ]; then
        echo "   ❌ Error: No folders found in $SRC_DIR"
        ERRORS=$((ERRORS + 1))
        continue
    fi

    # If multiple folders, find the one with Provider/Plugin file
    if [ "$FOLDER_COUNT" -gt 1 ]; then
        TARGET_FOLDER=""
        for folder in $FOLDERS; do
            if ls "$SRC_DIR/$folder"/*Plugin*.kt 1>/dev/null 2>&1 || \
               ls "$SRC_DIR/$folder"/*Provider*.kt 1>/dev/null 2>&1; then
                TARGET_FOLDER="$folder"
                break
            fi
        done

        if [ -z "$TARGET_FOLDER" ]; then
            echo "   ⚠️  Warning: No folder with Provider/Plugin found, using first folder"
            TARGET_FOLDER=$(echo "$FOLDERS" | head -1)
        fi

        FOLDER="$TARGET_FOLDER"
    else
        FOLDER="$FOLDERS"
    fi

    # Trim whitespace
    FOLDER=$(echo "$FOLDER" | xargs)

    echo "   Found folder: com.$FOLDER"

    # Auto-detect: Extract package name from Provider/Plugin file
    PACKAGE=""
    for pattern in "ProviderPlugin.kt" "Plugin.kt" "Provider.kt"; do
        provider_file=$(ls "$SRC_DIR/$FOLDER"/*$pattern 2>/dev/null | head -1)
        if [ -n "$provider_file" ] && [ -f "$provider_file" ]; then
            # Extract package declaration
            pkg_line=$(head -5 "$provider_file" | grep "^package ")
            if [ -n "$pkg_line" ]; then
                # Extract package name (last part after com.)
                PACKAGE=$(echo "$pkg_line" | sed 's/package com\.\?//')
                break
            fi
        fi
    done

    if [ -z "$PACKAGE" ]; then
        echo "   ⚠️  Warning: Could not detect package, using folder name (lowercase)"
        PACKAGE=$(echo "$FOLDER" | tr '[:upper:]' '[:lower:]')
    fi

    echo "   Detected package: $PACKAGE"

    # Destination directory and file
    DEST_DIR="$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER"
    DEST_FILE="$DEST_DIR/Extractors.kt"

    # Check if source folder exists
    if [ ! -d "$DEST_DIR" ]; then
        echo "   ❌ Error: Destination directory not found: $DEST_DIR"
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

    echo "   ✅ Synced: $MODULE/$FOLDER ($CLASS_COUNT extractor classes)"
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
