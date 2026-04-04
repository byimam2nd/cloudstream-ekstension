#!/bin/bash
# ========================================
# Pre-commit hook for OCE
# Auto-formats code with ktlint and runs detekt
# ========================================

echo "🔍 Running pre-commit checks..."

# Run ktlintFormat to auto-fix formatting issues
echo "📝 Running ktlintFormat..."
./gradlew ktlintFormat --quiet 2>&1

if [ $? -ne 0 ]; then
    echo "❌ ktlintFormat failed. Please fix the issues above and try again."
    exit 1
fi

# Run ktlintCheck to ensure all issues are fixed
echo "📝 Running ktlintCheck..."
./gradlew ktlintCheck --quiet 2>&1

if [ $? -ne 0 ]; then
    echo "⚠️  ktlintCheck found issues. Staged files with formatting problems."
    echo "   Please run './gradlew ktlintFormat' manually and commit the fixes."
    # Don't fail - let the commit proceed but warn the user
fi

# Run detekt (non-blocking, just for info)
echo "🔬 Running detekt (non-blocking)..."
./gradlew detekt --quiet 2>&1 || {
    echo "⚠️  detekt found issues. Check the report for details."
    echo "   Reports: build/reports/detekt/"
    # Don't fail - let the commit proceed
}

echo "✅ Pre-commit checks completed!"
exit 0
