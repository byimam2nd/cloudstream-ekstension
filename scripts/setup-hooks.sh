#!/bin/bash
# ========================================
# Setup pre-commit hooks for OCE
# Works on Linux, macOS, and Windows (Git Bash)
# ========================================

echo "🔧 Setting up pre-commit hooks..."

# Create .husky directory
mkdir -p .husky

# Create pre-commit hook
cat > .husky/pre-commit << 'HOOK'
#!/bin/bash
# Pre-commit hook - runs ktlint and detekt before each commit

echo "🔍 Running pre-commit checks..."

# Run ktlintFormat to auto-fix formatting
./gradlew ktlintFormat --quiet 2>&1
if [ $? -ne 0 ]; then
    echo "❌ ktlintFormat failed. Please fix the issues and try again."
    exit 1
fi

# Run ktlintCheck to verify
./gradlew ktlintCheck --quiet 2>&1
if [ $? -ne 0 ]; then
    echo "⚠️  ktlintCheck found issues. Run './gradlew ktlintFormat' to fix."
fi

# Run detekt (non-blocking)
./gradlew detekt --quiet 2>&1 || {
    echo "⚠️  detekt found issues. Check: build/reports/detekt/"
}

echo "✅ Pre-commit checks completed!"
exit 0
HOOK

# Make hook executable
chmod +x .husky/pre-commit

echo "✅ Pre-commit hook installed at .husky/pre-commit"
echo ""
echo "📝 The hook will automatically:"
echo "   1. Run ktlintFormat to fix formatting issues"
echo "   2. Run ktlintCheck to verify code style"
echo "   3. Run detekt for static analysis (non-blocking)"
echo ""
echo "To skip the hook, use: git commit --no-verify"
