#!/bin/bash

# ========================================
# Make Scripts Executable
# ========================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

chmod +x "$SCRIPT_DIR/sync-extractors.sh"
chmod +x "$SCRIPT_DIR/verify-extractors.sh"

echo "✅ Scripts are now executable"
echo ""
echo "Usage:"
echo "  ./sync-extractors.sh    - Sync Master Extractors to all modules"
echo "  ./verify-extractors.sh  - Verify extractor files"
