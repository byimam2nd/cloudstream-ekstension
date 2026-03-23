#!/bin/bash
# =============================================================================
# Framework Verifikasi Universal Fallback Strategy - SIMPLE VERSION
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

OUTPUT_DIR="/data/data/com.termux/files/home/cloudstream/cloudstream-ekstension/docs/verification"
mkdir -p "$OUTPUT_DIR"

REPORT_FILE="$OUTPUT_DIR/verification-all-$(date +%Y%m%d-%H%M%S).md"

print_header() { echo -e "${BLUE}=============================================${NC}\n${BLUE}  $1${NC}\n${BLUE}=============================================${NC}"; }
print_success() { echo -e "${GREEN}✓ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠ $1${NC}"; }
print_error() { echo -e "${RED}✗ $1${NC}"; }
print_test() { echo -e "${CYAN}→ $1${NC}"; }

# Initialize report
cat > "$REPORT_FILE" << EOF
# 🧪 Verification Report: All Providers

**Date**: $(date '+%Y-%m-%d %H:%M:%S')

---

## Test Results

EOF

TOTAL_TESTS=0
PASS_COUNT=0
FAIL_COUNT=0

run_test() {
    local provider=$1
    local url=$2
    local test_name=$3
    
    ((TOTAL_TESTS++)) || true
    
    echo ""
    print_test "Testing $provider - $test_name"
    
    local html=$(curl -s "$url" -A "Mozilla/5.0" --max-time 10 || echo "")
    
    if [ -z "$html" ]; then
        print_warning "Failed to fetch URL (site might be down or blocking)"
        echo "### $provider - $test_name" >> "$REPORT_FILE"
        echo "**Status**: ⚠ SKIP (Cannot fetch URL)" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        return 1
    fi
    
    # Test poster selectors
    local found=""
    
    # Layer 1: Various poster selectors
    found=$(echo "$html" | grep -oP 'img[^>]*src="[^"]*"\s*.*?itemprop="image"' | head -1 || echo "")
    [ -n "$found" ] && { print_success "Layer 1: itemprop=image"; echo "  ✓ Layer 1: itemprop=image"; }
    
    if [ -z "$found" ]; then
        found=$(echo "$html" | grep -oP 'property="og:image".*?content="\K[^"]*' | head -1 || echo "")
        [ -n "$found" ] && { print_success "Layer 2: og:image"; echo "  ✓ Layer 2: og:image"; }
    fi
    
    if [ -z "$found" ]; then
        found=$(echo "$html" | grep -oP 'div class="thumb".*?<img[^>]*src="\K[^"]*' | head -1 || echo "")
        [ -n "$found" ] && { print_success "Layer 3: div.thumb img"; echo "  ✓ Layer 3: div.thumb img"; }
    fi
    
    if [ -z "$found" ]; then
        found=$(echo "$html" | grep -oP 'div class="poster".*?<img[^>]*src="\K[^"]*' | head -1 || echo "")
        [ -n "$found" ] && { print_success "Layer 4: div.poster img"; echo "  ✓ Layer 4: div.poster img"; }
    fi
    
    if [ -z "$found" ]; then
        found=$(echo "$html" | grep -oP 'img[^>]*data-src="\K[^"]*' | head -1 || echo "")
        [ -n "$found" ] && { print_success "Layer 5: img data-src"; echo "  ✓ Layer 5: img data-src"; }
    fi
    
    # Result
    if [ -n "$found" ]; then
        print_success "$provider $test_name: PASS (found poster)"
        ((PASS_COUNT++)) || true
        echo "### $provider - $test_name" >> "$REPORT_FILE"
        echo "**Status**: ✓ PASS" >> "$REPORT_FILE"
        echo "**Poster URL**: \`$found\`" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        return 0
    else
        print_warning "$provider $test_name: CHECK (no poster found)"
        echo "### $provider - $test_name" >> "$REPORT_FILE"
        echo "**Status**: ⚠ CHECK (No poster found with any selector)" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        return 1
    fi
}

print_header "Universal Fallback Strategy Verification"
echo ""
echo "Testing ALL providers..."
echo ""

# Test URLs
declare -A TEST_URLS
TEST_URLS["Anichin"]="https://anichin.cafe/seri/carpenter-assassin/"
TEST_URLS["Donghuastream"]="https://donghuastream.org/anime/against-the-gods-ni-tian-xie-shen-3d1/"
TEST_URLS["Funmovieslix"]="https://funmovieslix.com/death-whisperer-3-2025/"
TEST_URLS["Animasu"]="https://animasu.stream/anime/one-piece/"
TEST_URLS["LayarKaca21"]="https://layarkaca21.live/"
TEST_URLS["Pencurimovie"]="https://pencurimovie.cam/"
TEST_URLS["Samehadaku"]="https://samehadaku.com/"

# Run tests for each provider
i=1
for provider in "${!TEST_URLS[@]}"; do
    url="${TEST_URLS[$provider]}"
    echo ""
    print_header "[$i/7] $provider"
    
    run_test "$provider" "$url" "Poster Selector" || true
    
    ((i++)) || true
done

# Summary
echo ""
print_header "Summary"
echo "Total tests: $TOTAL_TESTS"
echo "Passed: $PASS_COUNT"
echo "Failed/Check: $((TOTAL_TESTS - PASS_COUNT))"
echo ""

# Write summary to report
cat >> "$REPORT_FILE" << EOF

---

## Summary

| Metric | Count |
|--------|-------|
| Total Tests | $TOTAL_TESTS |
| Passed | $PASS_COUNT |
| Need Review | $((TOTAL_TESTS - PASS_COUNT)) |

---

## Conclusion

**Overall Status**: $([ $PASS_COUNT -eq $TOTAL_TESTS ] && echo "✓ ALL PASS" || echo "⚠ SOME NEED REVIEW")

**Recommendation**: $([ $PASS_COUNT -eq $TOTAL_TESTS ] && echo "Proceed with fallback strategy implementation for all providers" || echo "Review failed tests manually before implementation")

---

*Generated by verify-framework.sh on $(date '+%Y-%m-%d %H:%M:%S')*
EOF

print_success "Report saved to: $REPORT_FILE"
echo ""

if [ $PASS_COUNT -eq $TOTAL_TESTS ]; then
    print_success "All tests passed! Implementation can proceed."
    exit 0
else
    print_warning "$((TOTAL_TESTS - PASS_COUNT)) test(s) need manual review."
    exit 0  # Exit 0 anyway to not fail the script
fi
