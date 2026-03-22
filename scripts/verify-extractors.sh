#!/bin/bash

# ========================================
# Verify Extractor Files
# ========================================
# This script verifies that all active modules
# have valid Extractors.kt files and Provider
# correctly uses them
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

VALID_COUNT=0
ERRORS=0
TOTAL_CLASSES=0

for MODULE in "${MODULES[@]}"; do
    echo "📋 Validating $MODULE..."

    # Auto-detect folder structure - FULLY DYNAMIC
    SRC_DIR="$ROOT_DIR/$MODULE/src/main/kotlin/com"

    if [ ! -d "$SRC_DIR" ]; then
        echo "   ❌ ERROR: Source directory not found: $SRC_DIR"
        ERRORS=$((ERRORS + 1))
        continue
    fi

    # Get folder name
    FOLDERS=$(ls -1 "$SRC_DIR" 2>/dev/null)
    FOLDER_COUNT=$(echo "$FOLDERS" | wc -l)

    if [ "$FOLDER_COUNT" -gt 1 ]; then
        # Find folder with Provider/Plugin
        TARGET_FOLDER=""
        for folder in $FOLDERS; do
            if ls "$SRC_DIR/$folder"/*Plugin*.kt 1>/dev/null 2>&1 || \
               ls "$SRC_DIR/$folder"/*Provider*.kt 1>/dev/null 2>&1; then
                TARGET_FOLDER="$folder"
                break
            fi
        done
        FOLDER="${TARGET_FOLDER:-$(echo "$FOLDERS" | head -1)}"
    else
        FOLDER="$FOLDERS"
    fi

    FOLDER=$(echo "$FOLDER" | xargs)
    echo "   Found folder: com.$FOLDER"

    EXTRACTORS_FILE="$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER/SyncExtractors.kt"

    # Find Provider file - prioritize Plugin files
    PROVIDER_FILE=$(find "$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER" -maxdepth 1 -name "*Plugin.kt" 2>/dev/null | head -1)
    if [ -z "$PROVIDER_FILE" ]; then
        PROVIDER_FILE=$(find "$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER" -maxdepth 1 -name "*Provider.kt" 2>/dev/null | grep -v "MainAPI" | head -1)
    fi

    echo "   Provider: $(basename "$PROVIDER_FILE" 2>/dev/null || echo 'NOT FOUND')"

    # Check 1: SyncExtractors.kt exists
    if [ ! -f "$EXTRACTORS_FILE" ]; then
        echo "   ❌ ERROR: SyncExtractors.kt not found: $EXTRACTORS_FILE"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ SyncExtractors.kt exists"

    # Check 2: Count extractor classes
    CLASS_COUNT=$(grep -c "^class \|^open class " "$EXTRACTORS_FILE" 2>/dev/null || echo 0)
    echo "   ✅ Extractor classes: $CLASS_COUNT"

    # Check 3: SyncExtractors object exists
    if ! grep -q "object SyncExtractors" "$EXTRACTORS_FILE" 2>/dev/null; then
        echo "   ❌ ERROR: SyncExtractors object not found in SyncExtractors.kt"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ SyncExtractors object exists"

    # Check 4: Count registered extractors
    REGISTERED_COUNT=$(grep -A 100 "object SyncExtractors" "$EXTRACTORS_FILE" 2>/dev/null | grep -c "()" || echo 0)
    echo "   ✅ Registered extractors: $REGISTERED_COUNT"

    # Check 5: Provider file exists
    if ! ls $PROVIDER_FILE 1>/dev/null 2>&1; then
        echo "   ❌ ERROR: Provider file not found: $PROVIDER_FILE"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ Provider file exists"

    # Check 6: Provider imports SyncExtractors OR uses fully qualified name
    if ! grep -q "import.*SyncExtractors" $PROVIDER_FILE 2>/dev/null && \
       ! grep -q "com\..*\.SyncExtractors" $PROVIDER_FILE 2>/dev/null; then
        echo "   ❌ ERROR: Provider does not import SyncExtractors"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ Provider imports SyncExtractors (or uses fully qualified name)"

    # Check 7: Provider uses SyncExtractors.list.forEach (direct or fully qualified)
    if ! grep -q "SyncExtractors\.list\.forEach" $PROVIDER_FILE 2>/dev/null && \
       ! grep -q "com\..*\.SyncExtractors\.list\.forEach" $PROVIDER_FILE 2>/dev/null; then
        echo "   ❌ ERROR: Provider does not use SyncExtractors.list.forEach"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ Provider uses SyncExtractors.list.forEach"

    # Check 8: Validate class count vs registered count
    if [ "$CLASS_COUNT" -lt "$REGISTERED_COUNT" ]; then
        echo "   ⚠️  WARNING: Registered count ($REGISTERED_COUNT) > Class count ($CLASS_COUNT)"
    fi

    # Consistency check - expect ~52 classes
    if [ "$CLASS_COUNT" -lt 45 ]; then
        echo "   ⚠️  WARNING: Low extractor count ($CLASS_COUNT), expected ~52"
    fi

    echo "   ✅ $MODULE validation PASSED"
    echo ""

    TOTAL_CLASSES=$((TOTAL_CLASSES + CLASS_COUNT))
    VALID_COUNT=$((VALID_COUNT + 1))
done

echo "========================================"
echo "📊 Validation Summary"
echo "========================================"
echo "   Total modules: $MODULE_COUNT"
echo "   Validated: $VALID_COUNT"
echo "   Errors: $ERRORS"
echo "   Total extractor classes: $TOTAL_CLASSES"
echo ""

if [ "$ERRORS" -gt 0 ]; then
    echo "❌ Validation FAILED: $ERRORS error(s) found"
    exit 1
fi

if [ "$VALID_COUNT" -eq 0 ]; then
    echo "❌ Validation FAILED: No modules validated"
    exit 1
fi

echo "✅ Validation PASSED: All $VALID_COUNT module(s) correctly use SyncExtractors.kt"
echo ""

# Consistency check
echo "📋 Consistency Check:"
echo "   Each module should have ~52 extractor classes"
echo "   Expected total: ~$((VALID_COUNT * 52)) classes"
echo ""

if [ "$TOTAL_CLASSES" -lt "$((VALID_COUNT * 45))" ]; then
    echo "⚠️  Warning: Total classes ($TOTAL_CLASSES) seems low!"
    echo "   Expected at least $((VALID_COUNT * 45)) classes for $VALID_COUNT modules"
    echo "   Check if MasterExtractors.kt is complete"
    exit 1
fi

echo "✅ Consistency check passed"
