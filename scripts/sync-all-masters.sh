#!/bin/bash

# ========================================
# Sync ALL Master Files to All Active Sites
# ========================================
# FULLY DYNAMIC - No hardcoded module names!
# Auto-detects modules, folders, and packages
# Syncs all Master*.kt files with Sync prefix
# ========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
COMMON_DIR="$ROOT_DIR/common"

echo "========================================"
echo "📦 Sync All Master Files"
echo "========================================"
echo ""

# List of Master files to sync
MASTER_FILES=(
    "MasterExtractors.kt:SyncExtractors.kt"
    "MasterUtils.kt:SyncUtils.kt"
    "MasterCacheManager.kt:SyncCacheManager.kt"
    "MasterImageCache.kt:SyncImageCache.kt"
    "MasterSmartCacheMonitor.kt:SyncSmartCacheMonitor.kt"
    "MasterSuperSmartPrefetchManager.kt:SyncSuperSmartPrefetchManager.kt"
)

echo "📋 Master files to sync:"
for master_entry in "${MASTER_FILES[@]}"; do
    master_file="${master_entry%%:*}"
    sync_file="${master_entry##*:}"
    echo "   - $master_file → $sync_file"
done
echo ""

# Check if common directory exists
if [ ! -d "$COMMON_DIR" ]; then
    echo "❌ Common directory not found at: $COMMON_DIR"
    exit 1
fi

echo "🔍 Detecting active modules (folders with build.gradle.kts)..."
echo ""

# Find all folders with build.gradle.kts (excluding root)
MODULES=()
while IFS= read -r build_file; do
    # Get directory relative to ROOT_DIR
    module_dir=$(dirname "$build_file")
    # Remove ROOT_DIR prefix and leading slash
    module_dir="${module_dir#$ROOT_DIR/}"
    # Skip root and empty
    if [ -n "$module_dir" ] && [ "$module_dir" != "." ]; then
        MODULES+=("$module_dir")
    fi
done < <(find "$ROOT_DIR" -mindepth 2 -maxdepth 2 -name "build.gradle.kts" 2>/dev/null || true)

MODULE_COUNT=${#MODULES[@]}

if [ "$MODULE_COUNT" -eq 0 ]; then
    echo "❌ No active modules found!"
    exit 1
fi

echo "✅ Found $MODULE_COUNT active module(s): ${MODULES[*]}"
echo ""

TOTAL_SYNCED=0
TOTAL_ERRORS=0

# Sync to each active module - FULLY DYNAMIC
for MODULE in "${MODULES[@]}"; do
    echo "📋 Processing $MODULE..."

    # Auto-detect: Find the actual folder structure
    SRC_DIR="$ROOT_DIR/$MODULE/src/main/kotlin/com"

    if [ ! -d "$SRC_DIR" ]; then
        echo "   ❌ Error: Source directory not found: $SRC_DIR"
        TOTAL_ERRORS=$((TOTAL_ERRORS + 1))
        continue
    fi

    # Get the actual folder name(s) in com/
    FOLDERS=$(ls -1 "$SRC_DIR" 2>/dev/null)
    FOLDER_COUNT=$(echo "$FOLDERS" | wc -l)

    if [ "$FOLDER_COUNT" -eq 0 ]; then
        echo "   ❌ Error: No folders found in $SRC_DIR"
        TOTAL_ERRORS=$((TOTAL_ERRORS + 1))
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
            # Extract package declaration (check first 15 lines to handle comment headers)
            pkg_line=$(head -15 "$provider_file" | grep "^package ")
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

    # Destination directory
    DEST_DIR="$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER"

    # Check if source folder exists
    if [ ! -d "$DEST_DIR" ]; then
        echo "   ❌ Error: Destination directory not found: $DEST_DIR"
        TOTAL_ERRORS=$((TOTAL_ERRORS + 1))
        continue
    fi

    # Sync each Master file
    MODULE_SYNCED=0
    for master_entry in "${MASTER_FILES[@]}"; do
        master_file="${master_entry%%:*}"
        sync_file="${master_entry##*:}"

        MASTER_SOURCE="$COMMON_DIR/$master_file"

        # Check if master file exists
        if [ ! -f "$MASTER_SOURCE" ]; then
            echo "   ⚠️  Warning: Master file not found: $master_file"
            continue
        fi

        DEST_FILE="$DEST_DIR/$sync_file"

        # Copy master file with correct package name
        awk -v pkg="$PACKAGE" '
            NR==1 { print "// ========================================" }
            NR==2 { print "// AUTO-GENERATED - DO NOT EDIT MANUALLY" }
            NR==3 { print "// Synced from common/'"$master_file"'" }
            NR==4 { print "// File: '"$sync_file"'" }
            NR==5 { print "// ========================================" }
            /^package / { print "package com." pkg; next }
            { print }
        ' "$MASTER_SOURCE" > "$DEST_FILE"

        # Count lines (excluding comments)
        LINE_COUNT=$(wc -l < "$DEST_FILE")

        echo "   ✅ Synced: $sync_file ($LINE_COUNT lines)"
        MODULE_SYNCED=$((MODULE_SYNCED + 1))
    done

    echo "   📊 Module $MODULE: $MODULE_SYNCED files synced"
    echo ""

    TOTAL_SYNCED=$((TOTAL_SYNCED + MODULE_SYNCED))
done

echo "========================================"
echo "📊 Sync Summary"
echo "========================================"
echo "   Total modules: $MODULE_COUNT"
echo "   Total files synced: $TOTAL_SYNCED"
echo "   Errors: $TOTAL_ERRORS"
echo ""

if [ "$TOTAL_ERRORS" -gt 0 ]; then
    echo "❌ Sync FAILED with $TOTAL_ERRORS error(s)"
    exit 1
fi

if [ "$TOTAL_SYNCED" -eq 0 ]; then
    echo "❌ Sync FAILED: No files were synced"
    exit 1
fi

echo "✅ Sync completed successfully!"
echo ""
echo "Next steps:"
echo "  1. Review changes: git diff"
echo "  2. Commit: git add -A && git commit -m 'chore: sync all master files'"
echo "  3. Push: git push"
