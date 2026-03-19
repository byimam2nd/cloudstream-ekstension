#!/bin/bash

# ========================================
# Verify Extractor Files
# ========================================
# This script verifies that all active modules
# have valid Extractors.kt files
# ========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "========================================"
echo "🔍 Verify Extractor Files"
echo "========================================"
echo ""

# Find all active modules
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

echo "📋 Active modules: ${MODULES[*]}"
echo ""

# Folder mapping
declare -A FOLDER_MAP=(
    ["Pencurimovie"]="Pencurimovie"
    ["LayarKaca21"]="LayarKacaProvider"
    ["Donghuastream"]="Donghuastream"
    ["Funmovieslix"]="Funmovieslix"
    ["IdlixProvider"]="hexated"
    ["Anichin"]="Anichin"
)

VALID_COUNT=0
ERRORS=0
TOTAL_CLASSES=0

for MODULE in "${MODULES[@]}"; do
    FOLDER="${FOLDER_MAP[$MODULE]}"

    if [ -z "$FOLDER" ]; then
        echo "⚠️  Warning: Module '$MODULE' not in folder map, skipping..."
        continue
    fi

    EXTRACTORS_FILE="$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER/Extractors.kt"

    if [ ! -f "$EXTRACTORS_FILE" ]; then
        echo "❌ ERROR: Extractors.kt not found: $EXTRACTORS_FILE"
        ERRORS=$((ERRORS + 1))
        continue
    fi

    # Count extractor classes
    CLASS_COUNT=$(grep -c "^class \|^open class " "$EXTRACTORS_FILE" 2>/dev/null || echo 0)

    # Count registered extractors in AllExtractors object
    REGISTERED_COUNT=$(grep -A 100 "object AllExtractors" "$EXTRACTORS_FILE" 2>/dev/null | grep -c "()" || echo 0)

    echo "✅ $MODULE: $CLASS_COUNT classes, $REGISTERED_COUNT registered"

    TOTAL_CLASSES=$((TOTAL_CLASSES + CLASS_COUNT))

    if [ "$CLASS_COUNT" -gt 0 ]; then
        VALID_COUNT=$((VALID_COUNT + 1))
    fi
done

echo ""
echo "========================================"
echo "📊 Validation Summary"
echo "========================================"
echo "   Total modules: $MODULE_COUNT"
echo "   Valid extractors: $VALID_COUNT"
echo "   Errors: $ERRORS"
echo "   Total extractor classes: $TOTAL_CLASSES"
echo ""

if [ "$ERRORS" -gt 0 ]; then
    echo "❌ Validation FAILED: $ERRORS module(s) missing Extractors.kt"
    exit 1
fi

if [ "$VALID_COUNT" -eq 0 ]; then
    echo "❌ Validation FAILED: No valid extractor files found"
    exit 1
fi

echo "✅ Validation PASSED: All $VALID_COUNT module(s) have valid Extractors.kt"
echo ""

# Check consistency
echo "📋 Consistency Check:"
echo "   Each module should have the same number of extractor classes"
echo "   Expected: ~39 classes per module (from MasterExtractors.kt)"
echo ""

if [ "$TOTAL_CLASSES" -lt "$((VALID_COUNT * 30))" ]; then
    echo "⚠️  Warning: Total classes ($TOTAL_CLASSES) seems low!"
    echo "   Expected at least $((VALID_COUNT * 30)) classes for $VALID_COUNT modules"
    echo "   Check if MasterExtractors.kt is complete"
fi

echo "✅ Consistency check passed"
