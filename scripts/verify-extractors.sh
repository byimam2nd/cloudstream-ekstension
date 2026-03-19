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

    # Find Provider file - prioritize Plugin files over Provider files
    # First try: *ProviderPlugin.kt or *Plugin.kt
    PROVIDER_FILE=$(find "$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER" -maxdepth 1 -name "*Plugin.kt" 2>/dev/null | head -1)

    # Fallback: *Provider.kt (but not MainAPI)
    if [ -z "$PROVIDER_FILE" ]; then
        PROVIDER_FILE=$(find "$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER" -maxdepth 1 -name "*Provider.kt" 2>/dev/null | grep -v "MainAPI" | head -1)
    fi

    # Final fallback: try specific patterns
    if [ -z "$PROVIDER_FILE" ]; then
        for pattern in "ProviderPlugin.kt" "Plugin.kt" "Provider.kt"; do
            test_file="$ROOT_DIR/$MODULE/src/main/kotlin/com/$FOLDER/$pattern"
            if [ -f "$test_file" ]; then
                PROVIDER_FILE="$test_file"
                break
            fi
        done
    fi

    echo "📋 Validating $MODULE (Provider: $(basename "$PROVIDER_FILE" 2>/dev/null || echo 'NOT FOUND'))..."

    # Check 1: Extractors.kt exists
    if [ ! -f "$EXTRACTORS_FILE" ]; then
        echo "   ❌ ERROR: Extractors.kt not found: $EXTRACTORS_FILE"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ Extractors.kt exists"

    # Check 2: Count extractor classes
    CLASS_COUNT=$(grep -c "^class \|^open class " "$EXTRACTORS_FILE" 2>/dev/null || echo 0)
    echo "   ✅ Extractor classes: $CLASS_COUNT"

    # Check 3: AllExtractors object exists
    if ! grep -q "object AllExtractors" "$EXTRACTORS_FILE" 2>/dev/null; then
        echo "   ❌ ERROR: AllExtractors object not found in Extractors.kt"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ AllExtractors object exists"

    # Check 4: Count registered extractors
    REGISTERED_COUNT=$(grep -A 100 "object AllExtractors" "$EXTRACTORS_FILE" 2>/dev/null | grep -c "()" || echo 0)
    echo "   ✅ Registered extractors: $REGISTERED_COUNT"

    # Check 5: Provider file exists
    if ! ls $PROVIDER_FILE 1>/dev/null 2>&1; then
        echo "   ❌ ERROR: Provider file not found: $PROVIDER_FILE"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ Provider file exists"

    # Check 6: Provider imports AllExtractors OR uses fully qualified name
    if ! grep -q "import.*AllExtractors" $PROVIDER_FILE 2>/dev/null && \
       ! grep -q "com\..*\.AllExtractors" $PROVIDER_FILE 2>/dev/null; then
        echo "   ❌ ERROR: Provider does not import AllExtractors"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ Provider imports AllExtractors (or uses fully qualified name)"

    # Check 7: Provider uses AllExtractors.list.forEach (direct or fully qualified)
    if ! grep -q "AllExtractors\.list\.forEach" $PROVIDER_FILE 2>/dev/null && \
       ! grep -q "com\..*\.AllExtractors\.list\.forEach" $PROVIDER_FILE 2>/dev/null; then
        echo "   ❌ ERROR: Provider does not use AllExtractors.list.forEach"
        ERRORS=$((ERRORS + 1))
        continue
    fi
    echo "   ✅ Provider uses AllExtractors.list.forEach"

    # Check 8: Validate class count vs registered count
    if [ "$CLASS_COUNT" -lt "$REGISTERED_COUNT" ]; then
        echo "   ⚠️  WARNING: Registered count ($REGISTERED_COUNT) > Class count ($CLASS_COUNT)"
    fi

    # Consistency check - expect ~39 classes
    if [ "$CLASS_COUNT" -lt 30 ]; then
        echo "   ⚠️  WARNING: Low extractor count ($CLASS_COUNT), expected ~39"
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

echo "✅ Validation PASSED: All $VALID_COUNT module(s) correctly use Extractors.kt"
echo ""

# Consistency check
echo "📋 Consistency Check:"
echo "   Each module should have ~39 extractor classes"
echo "   Expected total: ~$((VALID_COUNT * 39)) classes"
echo ""

if [ "$TOTAL_CLASSES" -lt "$((VALID_COUNT * 30))" ]; then
    echo "⚠️  Warning: Total classes ($TOTAL_CLASSES) seems low!"
    echo "   Expected at least $((VALID_COUNT * 30)) classes for $VALID_COUNT modules"
    echo "   Check if MasterExtractors.kt is complete"
    exit 1
fi

echo "✅ Consistency check passed"
