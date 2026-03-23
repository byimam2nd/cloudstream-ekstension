#!/bin/bash
# =============================================================================
# Script Verifikasi Fallback Strategy Selector - Anichin
# =============================================================================
# Purpose: Membandingkan output selector lama vs selector baru (fallback strategy)
#          untuk memastikan hasilnya sama atau lebih baik
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test URLs
TEST_URLS=(
    "https://anichin.cafe/seri/carpenter-assassin/"
    "https://anichin.cafe/seri/renegade-immortal/"
    "https://anichin.cafe/seri/against-the-gods/"
)

# Output file
REPORT_FILE="/data/data/com.termux/files/home/cloudstream/cloudstream-ekstension/docs/verification-report-anichin-$(date +%Y%m%d-%H%M%S).md"

echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}  Verifikasi Fallback Strategy - Anichin${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""

# Initialize report
cat > "$REPORT_FILE" << 'EOF'
# 🧪 Verification Report: Anichin Fallback Strategy

## Test Date
EOF
echo "**$(date '+%Y-%m-%d %H:%M:%S')**" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# =============================================================================
# Function: Test Poster Selector
# =============================================================================
test_poster_selector() {
    local url=$1
    local test_name=$2
    
    echo -e "${YELLOW}Testing Poster Selector: $test_name${NC}"
    echo ""
    
    # Fetch HTML
    local html=$(curl -s "$url")
    
    # --- OLD SELECTOR (Single) ---
    echo "  OLD: div.thumb > img"
    local old_poster=$(echo "$html" | grep -oP 'div class="thumb".*?>\s*<img[^>]*src="\K[^"]*' | head -1)
    
    # --- NEW SELECTOR (Fallback Strategy) ---
    # Layer 1: div.thumb > img
    local new_poster=$(echo "$html" | grep -oP 'div class="thumb".*?>\s*<img[^>]*src="\K[^"]*' | head -1)
    
    # Layer 2: div.thumb img (descendant)
    if [ -z "$new_poster" ]; then
        echo "    Layer 1 failed, trying Layer 2: div.thumb img"
        new_poster=$(echo "$html" | grep -oP 'class="thumb".*?<img[^>]*src="\K[^"]*' | head -1)
    fi
    
    # Layer 3: img.ts-post-image
    if [ -z "$new_poster" ]; then
        echo "    Layer 2 failed, trying Layer 3: img.ts-post-image"
        new_poster=$(echo "$html" | grep -oP 'class="ts-post-image[^"]*".*?src="\K[^"]*' | head -1)
    fi
    
    # Layer 4: meta og:image
    if [ -z "$new_poster" ]; then
        echo "    Layer 3 failed, trying Layer 4: meta og:image"
        new_poster=$(echo "$html" | grep -oP 'property="og:image".*?content="\K[^"]*' | head -1)
    fi
    
    # Compare results
    echo ""
    if [ "$old_poster" == "$new_poster" ]; then
        echo -e "  ${GREEN}✓ MATCH${NC}: Both selectors return same result"
        echo "    URL: ${old_poster:0:80}..."
        echo "  Result: PASS"
    elif [ -n "$new_poster" ] && [ -z "$old_poster" ]; then
        echo -e "  ${GREEN}✓ IMPROVEMENT${NC}: New selector found poster, old selector failed"
        echo "    URL: ${new_poster:0:80}..."
        echo "  Result: PASS (Better)"
    elif [ "$old_poster" != "$new_poster" ]; then
        echo -e "  ${RED}✗ MISMATCH${NC}: Selectors return different results"
        echo "    OLD: ${old_poster:0:80}..."
        echo "    NEW: ${new_poster:0:80}..."
        echo "  Result: FAIL"
    else
        echo -e "  ${YELLOW}⚠ BOTH FAILED${NC}: Neither selector found poster"
        echo "  Result: FAIL"
    fi
    
    echo ""
    echo "-------------------------------------------"
    echo ""
    
    # Write to report
    cat >> "$REPORT_FILE" << EOF
### $test_name - Poster Selector

| Metric | Result |
|--------|--------|
| Old Selector | \`${#old_poster > 0 :+✓ Found}\` |
| New Selector | \`${#new_poster > 0 :+✓ Found}\` |
| Match | \`${old_poster == new_poster :+Yes} |
| Status | \`$([ "$old_poster" == "$new_poster" ] || [ -n "$new_poster" ] && [ -z "$old_poster" ] && echo "PASS (Better)" || echo "CHECK")\` |

EOF
}

# =============================================================================
# Function: Test Title Selector
# =============================================================================
test_title_selector() {
    local url=$1
    local test_name=$2
    
    echo -e "${YELLOW}Testing Title Selector: $test_name${NC}"
    echo ""
    
    # Fetch HTML
    local html=$(curl -s "$url")
    
    # --- OLD SELECTOR (Single) ---
    local old_title=$(echo "$html" | grep -oP 'h1 class="entry-title".*?>\K[^<]*' | head -1 | xargs)
    
    # --- NEW SELECTOR (Fallback Strategy) ---
    # Layer 1: h1.entry-title
    local new_title=$(echo "$html" | grep -oP 'h1 class="entry-title".*?>\K[^<]*' | head -1 | xargs)
    
    # Layer 2: h1.title
    if [ -z "$new_title" ]; then
        echo "    Layer 1 failed, trying Layer 2: h1.title"
        new_title=$(echo "$html" | grep -oP 'h1 class="title".*?>\K[^<]*' | head -1 | xargs)
    fi
    
    # Layer 3: meta og:title
    if [ -z "$new_title" ]; then
        echo "    Layer 2 failed, trying Layer 3: meta og:title"
        new_title=$(echo "$html" | grep -oP 'property="og:title".*?content="\K[^"]*' | head -1)
    fi
    
    # Layer 4: div.title h1
    if [ -z "$new_title" ]; then
        echo "    Layer 3 failed, trying Layer 4: div.title h1"
        new_title=$(echo "$html" | grep -oP 'div class="title".*?<h1.*?>\K[^<]*' | head -1 | xargs)
    fi
    
    # Compare results
    echo ""
    if [ "$old_title" == "$new_title" ]; then
        echo -e "  ${GREEN}✓ MATCH${NC}: Both selectors return same result"
        echo "    Title: $old_title"
        echo "  Result: PASS"
    elif [ -n "$new_title" ] && [ -z "$old_title" ]; then
        echo -e "  ${GREEN}✓ IMPROVEMENT${NC}: New selector found title, old selector failed"
        echo "    Title: $new_title"
        echo "  Result: PASS (Better)"
    elif [ "$old_title" != "$new_title" ]; then
        echo -e "  ${YELLOW}⚠ MISMATCH${NC}: Selectors return different results"
        echo "    OLD: $old_title"
        echo "    NEW: $new_title"
        echo "  Result: CHECK (Manual review needed)"
    else
        echo -e "  ${YELLOW}⚠ BOTH FAILED${NC}: Neither selector found title"
        echo "  Result: FAIL"
    fi
    
    echo ""
    echo "-------------------------------------------"
    echo ""
    
    # Write to report
    cat >> "$REPORT_FILE" << EOF
### $test_name - Title Selector

| Metric | Result |
|--------|--------|
| Old Selector | \`${#old_title > 0 :+✓ Found}\` |
| New Selector | \`${#new_title > 0 :+✓ Found}\` |
| Match | \`${old_title == new_title :+Yes} |
| Status | \`$([ "$old_title" == "$new_title" ] || [ -n "$new_title" ] && [ -z "$old_title" ] && echo "PASS (Better)" || echo "CHECK")\` |

EOF
}

# =============================================================================
# Function: Test Description Selector
# =============================================================================
test_description_selector() {
    local url=$1
    local test_name=$2
    
    echo -e "${YELLOW}Testing Description Selector: $test_name${NC}"
    echo ""
    
    # Fetch HTML
    local html=$(curl -s "$url")
    
    # --- OLD SELECTOR (Single) ---
    local old_desc=$(echo "$html" | grep -oP 'div class="entry-content".*?>\K[^<]*' | head -1 | xargs)
    old_desc=${old_desc:0:100}  # Truncate for display
    
    # --- NEW SELECTOR (Fallback Strategy) ---
    # Layer 1: div.entry-content
    local new_desc=$(echo "$html" | grep -oP 'div class="entry-content".*?>\K[^<]*' | head -1 | xargs)
    
    # Layer 2: div.description
    if [ -z "$new_desc" ]; then
        echo "    Layer 1 failed, trying Layer 2: div.description"
        new_desc=$(echo "$html" | grep -oP 'div class="description".*?>\K[^<]*' | head -1 | xargs)
    fi
    
    # Layer 3: div.synopsis
    if [ -z "$new_desc" ]; then
        echo "    Layer 2 failed, trying Layer 3: div.synopsis"
        new_desc=$(echo "$html" | grep -oP 'div class="synopsis".*?>\K[^<]*' | head -1 | xargs)
    fi
    
    # Layer 4: meta description
    if [ -z "$new_desc" ]; then
        echo "    Layer 3 failed, trying Layer 4: meta description"
        new_desc=$(echo "$html" | grep -oP 'name="description".*?content="\K[^"]*' | head -1)
    fi
    
    new_desc=${new_desc:0:100}  # Truncate for display
    
    # Compare results
    echo ""
    if [ "$old_desc" == "$new_desc" ]; then
        echo -e "  ${GREEN}✓ MATCH${NC}: Both selectors return same result"
        echo "    Description: ${old_desc:0:50}..."
        echo "  Result: PASS"
    elif [ -n "$new_desc" ] && [ -z "$old_desc" ]; then
        echo -e "  ${GREEN}✓ IMPROVEMENT${NC}: New selector found description, old selector failed"
        echo "    Description: ${new_desc:0:50}..."
        echo "  Result: PASS (Better)"
    else
        echo -e "  ${YELLOW}⚠ CHECK${NC}: Manual review needed"
        echo "    OLD: ${old_desc:0:50}..."
        echo "    NEW: ${new_desc:0:50}..."
        echo "  Result: CHECK"
    fi
    
    echo ""
    echo "-------------------------------------------"
    echo ""
    
    # Write to report
    cat >> "$REPORT_FILE" << EOF
### $test_name - Description Selector

| Metric | Result |
|--------|--------|
| Old Selector | \`${#old_desc > 0 :+✓ Found}\` |
| New Selector | \`${#new_desc > 0 :+✓ Found}\` |
| Match | \`${old_desc == new_desc :+Yes} |
| Status | \`$([ "$old_desc" == "$new_desc" ] || [ -n "$new_desc" ] && [ -z "$old_desc" ] && echo "PASS (Better)" || echo "CHECK")\` |

EOF
}

# =============================================================================
# Main Test Loop
# =============================================================================
echo "Starting verification tests..."
echo ""

for i in "${!TEST_URLS[@]}"; do
    url="${TEST_URLS[$i]}"
    name="Test_$((i+1))_$(basename "$url")"
    
    echo -e "${BLUE}=============================================${NC}"
    echo -e "${BLUE}  Test $((i+1))/${#TEST_URLS[@]}${NC}"
    echo -e "${BLUE}  URL: $url${NC}"
    echo -e "${BLUE}=============================================${NC}"
    echo ""
    
    # Add URL to report
    cat >> "$REPORT_FILE" << EOF
## Test $((i+1)): $(basename "$url")

**URL**: \`$url\`

EOF
    
    # Run tests
    test_poster_selector "$url" "$name"
    test_title_selector "$url" "$name"
    test_description_selector "$url" "$name"
done

# =============================================================================
# Summary
# =============================================================================
echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}  Verification Complete!${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""
echo "Report saved to: $REPORT_FILE"
echo ""
echo -e "${GREEN}Next Steps:${NC}"
echo "1. Review the report: cat $REPORT_FILE"
echo "2. If all tests PASS, proceed with implementation"
echo "3. If any tests FAIL or CHECK, manual review needed"
echo ""

# Add summary to report
cat >> "$REPORT_FILE" << EOF

---

## Summary

| Test Type | Total | PASS | PASS (Better) | CHECK | FAIL |
|-----------|-------|------|---------------|-------|------|
| Poster | ${#TEST_URLS[@]} | - | - | - | - |
| Title | ${#TEST_URLS[@]} | - | - | - | - |
| Description | ${#TEST_URLS[@]} | - | - | - | - |

**Recommendation**: [Pending manual review]

---

*Report generated by verify-selector-anichin.sh*
EOF
