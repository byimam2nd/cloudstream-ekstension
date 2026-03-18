#!/bin/bash

# ========================================
# Sync Master Extractors ke Semua Site
# ========================================
# Script untuk development lokal di Termux/PC
# ========================================

set -e

echo "========================================"
echo "📦 Sync Master Extractors ke Semua Site"
echo "========================================"
echo ""

# List of ALL sites
SITES=(
    "Pencurimovie"
    "LayarKaca21"
    "Donghuastream"
    "Funmovieslix"
    "HiAnime"
    "IdlixProvider"
    "Anichin"
)

# Master Extractors file location
MASTER_FILE="docs/MasterExtractors.kt"

# Check if master file exists
if [ ! -f "$MASTER_FILE" ]; then
    echo "❌ Master Extractors file not found at $MASTER_FILE"
    exit 1
fi

echo "✅ Master Extractors found: $MASTER_FILE"
echo ""

# Copy to each site
for SITE in "${SITES[@]}"; do
    echo "📋 Copying to $SITE..."

    # Destination directory
    DEST_DIR="$SITE/src/main/kotlin/com/$SITE"
    DEST_FILE="$DEST_DIR/Extractors.kt"

    # Create directory if not exists
    mkdir -p "$DEST_DIR"

    # Copy master extractors with correct package name
    # Using awk for better package replacement
    awk -v site="$SITE" '
        NR==1 { print "// ========================================" }
        NR==2 { print "// AUTO-GENERATED - DO NOT EDIT MANUALLY" }
        NR==3 { print "// Synced from docs/MasterExtractors.kt" }
        NR==4 { print "// ========================================" }
        /^package / { print "package com." site; next }
        { print }
    ' "$MASTER_FILE" > "$DEST_FILE"

    # Count extractor classes
    CLASS_COUNT=$(grep -c "^class \|^open class " "$DEST_FILE" 2>/dev/null || echo 0)
    echo "✅ Copied to $SITE ($CLASS_COUNT classes)"
done

echo ""
echo "========================================"
echo "✅ Sync Completed!"
echo "========================================"
echo ""
echo "📊 Summary:"
for SITE in "${SITES[@]}"; do
    DEST_FILE="$SITE/src/main/kotlin/com/$SITE/Extractors.kt"
    if [ -f "$DEST_FILE" ]; then
        COUNT=$(grep -c "^class \|^open class " "$DEST_FILE" 2>/dev/null || echo 0)
        echo "  ✅ $SITE: $COUNT extractor classes"
    else
        echo "  ❌ $SITE: No Extractors.kt found"
    fi
done

echo ""
echo "🔍 Next steps:"
echo "  1. Review changes: git diff"
echo "  2. Build all: ./gradlew clean build"
echo "  3. Commit: git add -A && git commit -m 'sync: update extractors'"
echo ""
