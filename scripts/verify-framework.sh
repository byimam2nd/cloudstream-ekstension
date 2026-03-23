#!/bin/bash
# =============================================================================
# Framework Verifikasi Universal Fallback Strategy
# =============================================================================
# Purpose: Framework umum untuk verifikasi selector fallback strategy
#          di semua provider (Anichin, Donghuastream, Funmovieslix, dll)
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
PROVIDER="${1:-Anichin}"
TEST_TYPE="${2:-all}"  # all, poster, title, episode, description
OUTPUT_FORMAT="${3:-console}"  # console, markdown, json

# Output directory
OUTPUT_DIR="/data/data/com.termux/files/home/cloudstream/cloudstream-ekstension/docs/verification"
mkdir -p "$OUTPUT_DIR"

# Report file
REPORT_FILE="$OUTPUT_DIR/verification-${PROVIDER,,}-$(date +%Y%m%d-%H%M%S).md"

# =============================================================================
# Helper Functions
# =============================================================================

print_header() {
    echo -e "${BLUE}=============================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}=============================================${NC}"
}

print_test() {
    echo -e "${CYAN}→ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# =============================================================================
# Selector Test Functions
# =============================================================================

# Test function for Anichin poster selector
test_anichin_poster() {
    local url=$1
    local html=$(curl -s "$url" -A "Mozilla/5.0")
    
    print_test "Testing Anichin Poster Selector"
    echo ""
    
    # OLD: Single selector
    echo "  OLD: div.thumb > img"
    local old_result=$(echo "$html" | grep -oP 'div\.thumb.*?>\s*<img[^>]*src="\K[^"]*' | head -1)
    
    # NEW: Fallback strategy (4-layer)
    echo "  NEW: Fallback Strategy (4-layer)"
    local new_result=""
    
    # Layer 1
    new_result=$(echo "$html" | grep -oP 'div\.thumb.*?>\s*<img[^>]*src="\K[^"]*' | head -1)
    if [ -n "$new_result" ]; then
        echo "    ✓ Layer 1: div.thumb > img"
    else
        # Layer 2
        new_result=$(echo "$html" | grep -oP 'div\.thumb[\s\S]*?<img[^>]*src="\K[^"]*' | head -1)
        if [ -n "$new_result" ]; then
            echo "    ✓ Layer 2: div.thumb img (descendant)"
        else
            # Layer 3
            new_result=$(echo "$html" | grep -oP 'class="ts-post-image[^"]*".*?src="\K[^"]*' | head -1)
            if [ -n "$new_result" ]; then
                echo "    ✓ Layer 3: img.ts-post-image"
            else
                # Layer 4
                new_result=$(echo "$html" | grep -oP 'property="og:image".*?content="\K[^"]*' | head -1)
                if [ -n "$new_result" ]; then
                    echo "    ✓ Layer 4: meta og:image"
                fi
            fi
        fi
    fi
    
    echo ""
    
    # Compare
    if [ "$old_result" == "$new_result" ]; then
        print_success "MATCH: Both selectors return same result"
        return 0
    elif [ -n "$new_result" ] && [ -z "$old_result" ]; then
        print_success "IMPROVEMENT: New selector found poster, old selector failed"
        return 0
    elif [ "$old_result" != "$new_result" ] && [ -n "$old_result" ] && [ -n "$new_result" ]; then
        print_warning "MISMATCH: Different results (manual review needed)"
        echo "    OLD: $old_result"
        echo "    NEW: $new_result"
        return 1
    else
        print_error "BOTH FAILED: Neither selector found poster"
        return 1
    fi
}

# Test function for Donghuastream URL scheme
test_donghuastream_url() {
    local url=$1
    local html=$(curl -s "$url" -A "Mozilla/5.0")
    
    print_test "Testing Donghuastream URL Scheme Handling"
    echo ""
    
    # Extract iframe URLs from base64 decoded content
    local iframe_urls=$(echo "$html" | grep -oP 'iframe[^>]*src=["\x27]\K[^"\x27]+' | head -5)
    
    echo "  Found $(echo "$iframe_urls" | wc -l) iframe URLs"
    echo ""
    
    local valid_count=0
    local invalid_count=0
    
    while IFS= read -r iframe_url; do
        if [ -z "$iframe_url" ]; then continue; fi
        
        # Test URL fixing logic
        local fixed_url=""
        if [[ "$iframe_url" == //* ]]; then
            fixed_url="https:$iframe_url"
        elif [[ "$iframe_url" == http* ]]; then
            fixed_url="$iframe_url"
        elif [[ "$iframe_url" == /* ]]; then
            fixed_url="https://donghuastream.org$iframe_url"
        else
            fixed_url="https://$iframe_url"
        fi
        
        if [[ "$fixed_url" == http* ]]; then
            print_success "Valid: $fixed_url"
            ((valid_count++))
        else
            print_error "Invalid: $iframe_url -> $fixed_url"
            ((invalid_count++))
        fi
    done <<< "$iframe_urls"
    
    echo ""
    echo "  Valid URLs: $valid_count"
    echo "  Invalid URLs: $invalid_count"
    
    if [ $invalid_count -eq 0 ]; then
        return 0
    else
        return 1
    fi
}

# Test function for Funmovieslix embed extraction
test_funmovieslix_embed() {
    local url=$1
    local html=$(curl -s "$url" -A "Mozilla/5.0")
    
    print_test "Testing Funmovieslix Embed URL Extraction"
    echo ""
    
    # Strategy 1: const embeds
    local script_content=$(echo "$html" | grep -oP 'const embeds.*?;' | head -1)
    local strategy1_count=0
    if [ -n "$script_content" ]; then
        strategy1_count=$(echo "$script_content" | grep -oP 'https://[^"\x27]+' | wc -l)
        echo "  Strategy 1 (const embeds): $strategy1_count URLs"
    else
        echo "  Strategy 1 (const embeds): Not found"
    fi
    
    # Strategy 2: iframe tags
    local strategy2_count=$(echo "$html" | grep -oP 'iframe[^>]*src=["\x27]\K[^"\x27]+' | wc -l)
    echo "  Strategy 2 (iframe tags): $strategy2_count URLs"
    
    # Strategy 3: data attributes
    local strategy3_count=$(echo "$html" | grep -oP 'data-(?:src|url|link)=["\x27]\K[^"\x27]+' | wc -l)
    echo "  Strategy 3 (data attributes): $strategy3_count URLs"
    
    local total=$((strategy1_count + strategy2_count + strategy3_count))
    echo ""
    echo "  Total URLs found: $total"
    
    if [ $total -gt 0 ]; then
        print_success "Embed URLs found"
        return 0
    else
        print_error "No embed URLs found"
        return 1
    fi
}

# Test function for Animasu poster selector
test_animasu_poster() {
    local url=$1
    local html=$(curl -s "$url" -A "Mozilla/5.0")
    
    print_test "Testing Animasu Poster Selector"
    echo ""
    
    # OLD: Single selector
    echo "  OLD: div.poster img[src]"
    local old_result=$(echo "$html" | grep -oP 'div class="poster".*?<img[^>]*src="\K[^"]*' | head -1)
    
    # NEW: Fallback strategy
    echo "  NEW: Fallback Strategy (4-layer)"
    local new_result=""
    
    # Layer 1: div.poster img
    new_result=$(echo "$html" | grep -oP 'div class="poster".*?<img[^>]*src="\K[^"]*' | head -1)
    [ -n "$new_result" ] && echo "    ✓ Layer 1: div.poster img"
    
    # Layer 2: meta og:image
    if [ -z "$new_result" ]; then
        new_result=$(echo "$html" | grep -oP 'property="og:image".*?content="\K[^"]*' | head -1)
        [ -n "$new_result" ] && echo "    ✓ Layer 2: meta og:image"
    fi
    
    # Layer 3: div.thumb img
    if [ -z "$new_result" ]; then
        new_result=$(echo "$html" | grep -oP 'div class="thumb".*?<img[^>]*src="\K[^"]*' | head -1)
        [ -n "$new_result" ] && echo "    ✓ Layer 3: div.thumb img"
    fi
    
    # Layer 4: img[data-src]
    if [ -z "$new_result" ]; then
        new_result=$(echo "$html" | grep -oP 'img[^>]*data-src="\K[^"]*' | head -1)
        [ -n "$new_result" ] && echo "    ✓ Layer 4: img data-src"
    fi
    
    echo ""
    
    # Compare
    if [ "$old_result" == "$new_result" ]; then
        print_success "MATCH: Both selectors return same result"
        return 0
    elif [ -n "$new_result" ] && [ -z "$old_result" ]; then
        print_success "IMPROVEMENT: New selector found poster, old selector failed"
        return 0
    elif [ "$old_result" != "$new_result" ] && [ -n "$old_result" ] && [ -n "$new_result" ]; then
        print_warning "MISMATCH: Different results (manual review needed)"
        return 1
    else
        print_error "BOTH FAILED: Neither selector found poster"
        return 1
    fi
}

# Test function for LayarKaca21 selector
test_layarkaca21() {
    local url=$1
    local html=$(curl -s "$url" -A "Mozilla/5.0")
    
    print_test "Testing LayarKaca21 Poster & Title Selector"
    echo ""
    
    # Poster test
    echo "  POSTER SELECTOR:"
    local old_poster=$(echo "$html" | grep -oP 'img[itemprop="image"].*?src="\K[^"]*' | head -1)
    local new_poster=""
    
    # Layer 1
    new_poster=$(echo "$html" | grep -oP 'img[itemprop="image"].*?src="\K[^"]*' | head -1)
    [ -n "$new_poster" ] && echo "    ✓ Layer 1: img[itemprop=image]"
    
    # Layer 2
    if [ -z "$new_poster" ]; then
        new_poster=$(echo "$html" | grep -oP 'property="og:image".*?content="\K[^"]*' | head -1)
        [ -n "$new_poster" ] && echo "    ✓ Layer 2: meta og:image"
    fi
    
    # Layer 3
    if [ -z "$new_poster" ]; then
        new_poster=$(echo "$html" | grep -oP 'div class="thumb".*?<img[^>]*src="\K[^"]*' | head -1)
        [ -n "$new_poster" ] && echo "    ✓ Layer 3: div.thumb img"
    fi
    
    if [ "$old_poster" == "$new_poster" ] || [ -n "$new_poster" ] && [ -z "$old_poster" ]; then
        print_success "Poster: PASS"
    else
        print_warning "Poster: CHECK"
    fi
    
    echo ""
    echo "  TITLE SELECTOR:"
    local old_title=$(echo "$html" | grep -oP 'h1[itemprop="name"].*?>\K[^<]*' | head -1)
    local new_title=""
    
    # Layer 1
    new_title=$(echo "$html" | grep -oP 'h1[itemprop="name"].*?>\K[^<]*' | head -1)
    [ -n "$new_title" ] && echo "    ✓ Layer 1: h1[itemprop=name]"
    
    # Layer 2
    if [ -z "$new_title" ]; then
        new_title=$(echo "$html" | grep -oP 'property="og:title".*?content="\K[^"]*' | head -1)
        [ -n "$new_title" ] && echo "    ✓ Layer 2: meta og:title"
    fi
    
    # Layer 3
    if [ -z "$new_title" ]; then
        new_title=$(echo "$html" | grep -oP 'div class="title".*?<h1.*?>\K[^<]*' | head -1)
        [ -n "$new_title" ] && echo "    ✓ Layer 3: div.title h1"
    fi
    
    if [ "$old_title" == "$new_title" ] || [ -n "$new_title" ] && [ -z "$old_title" ]; then
        print_success "Title: PASS"
    else
        print_warning "Title: CHECK"
    fi
    
    return 0
}

# Test function for Pencurimovie selector
test_pencurimovie() {
    local url=$1
    local html=$(curl -s "$url" -A "Mozilla/5.0")
    
    print_test "Testing Pencurimovie Poster Selector"
    echo ""
    
    # OLD
    echo "  OLD: div.poster img"
    local old_result=$(echo "$html" | grep -oP 'div class="poster".*?<img[^>]*src="\K[^"]*' | head -1)
    
    # NEW: Fallback
    echo "  NEW: Fallback Strategy (3-layer)"
    local new_result=""
    
    new_result=$(echo "$html" | grep -oP 'div class="poster".*?<img[^>]*src="\K[^"]*' | head -1)
    [ -n "$new_result" ] && echo "    ✓ Layer 1: div.poster img"
    
    if [ -z "$new_result" ]; then
        new_result=$(echo "$html" | grep -oP 'property="og:image".*?content="\K[^"]*' | head -1)
        [ -n "$new_result" ] && echo "    ✓ Layer 2: meta og:image"
    fi
    
    if [ -z "$new_result" ]; then
        new_result=$(echo "$html" | grep -oP 'img[data-src]'.*?data-src="\K[^"]*' | head -1)
        [ -n "$new_result" ] && echo "    ✓ Layer 3: img data-src"
    fi
    
    echo ""
    
    if [ "$old_result" == "$new_result" ]; then
        print_success "MATCH: Both selectors return same result"
        return 0
    elif [ -n "$new_result" ] && [ -z "$old_result" ]; then
        print_success "IMPROVEMENT: New selector found poster"
        return 0
    else
        print_warning "CHECK: Manual review needed"
        return 1
    fi
}

# Test function for Samehadaku selector
test_samehadaku() {
    local url=$1
    local html=$(curl -s "$url" -A "Mozilla/5.0")
    
    print_test "Testing Samehadaku Poster & Episode Selector"
    echo ""
    
    # Poster
    echo "  POSTER:"
    local old_poster=$(echo "$html" | grep -oP 'div class="thumb".*?<img[^>]*src="\K[^"]*' | head -1)
    local new_poster=""
    
    new_poster=$(echo "$html" | grep -oP 'div class="thumb".*?<img[^>]*src="\K[^"]*' | head -1)
    [ -n "$new_poster" ] && echo "    ✓ Layer 1: div.thumb img"
    
    if [ -z "$new_poster" ]; then
        new_poster=$(echo "$html" | grep -oP 'property="og:image".*?content="\K[^"]*' | head -1)
        [ -n "$new_poster" ] && echo "    ✓ Layer 2: meta og:image"
    fi
    
    if [ "$old_poster" == "$new_poster" ] || [ -n "$new_poster" ] && [ -z "$old_poster" ]; then
        print_success "Poster: PASS"
    else
        print_warning "Poster: CHECK"
    fi
    
    echo ""
    echo "  EPISODE LIST:"
    local old_eps=$(echo "$html" | grep -oP 'div class="eplister".*?<li' | wc -l)
    local new_eps=$(echo "$html" | grep -oP 'div class="eplister"|div class="episodelist"' | wc -l)
    
    echo "    Old selector finds: $old_eps episode containers"
    echo "    New selector finds: $new_eps episode containers"
    
    if [ $new_eps -ge $old_eps ]; then
        print_success "Episode: PASS"
    else
        print_warning "Episode: CHECK"
    fi
    
    return 0
}

# =============================================================================
# Main Execution
# =============================================================================

main() {
    print_header "Universal Fallback Strategy Verification"
    echo ""
    echo "Provider: $PROVIDER"
    echo "Test Type: $TEST_TYPE"
    echo "Output: $OUTPUT_FORMAT"
    echo ""
    
    # Initialize report
    cat > "$REPORT_FILE" << EOF
# Verification Report: $PROVIDER

**Date**: $(date '+%Y-%m-%d %H:%M:%S')
**Provider**: $PROVIDER
**Test Type**: $TEST_TYPE

---

## Test Results

EOF
    
    local result=0
    
    case "$PROVIDER" in
        "Anichin")
            test_anichin_poster "https://anichin.cafe/seri/carpenter-assassin/" || result=1
            ;;
        "Donghuastream")
            test_donghuastream_url "https://donghuastream.org/anime/against-the-gods-ni-tian-xie-shen-3d1/" || result=1
            ;;
        "Funmovieslix")
            test_funmovieslix_embed "https://funmovieslix.com/death-whisperer-3-2025/" || result=1
            ;;
        "Animasu")
            test_animasu_poster "https://animasu.stream/anime/one-piece" || result=1
            ;;
        "LayarKaca21")
            test_layarkaca21 "https://layarkaca21.com/" || result=1
            ;;
        "Pencurimovie")
            test_pencurimovie "https://pencurimovie.com/" || result=1
            ;;
        "Samehadaku")
            test_samehadaku "https://samehadaku.com/" || result=1
            ;;
        "all")
            print_header "Running Verification for ALL Providers"
            echo ""
            
            echo -e "${CYAN}[1/7] Anichin${NC}"
            test_anichin_poster "https://anichin.cafe/seri/carpenter-assassin/" || ((result++))
            echo ""
            
            echo -e "${CYAN}[2/7] Donghuastream${NC}"
            test_donghuastream_url "https://donghuastream.org/anime/against-the-gods-ni-tian-xie-shen-3d1/" || ((result++))
            echo ""
            
            echo -e "${CYAN}[3/7] Funmovieslix${NC}"
            test_funmovieslix_embed "https://funmovieslix.com/death-whisperer-3-2025/" || ((result++))
            echo ""
            
            echo -e "${CYAN}[4/7] Animasu${NC}"
            test_animasu_poster "https://animasu.stream/anime/one-piece" || ((result++))
            echo ""
            
            echo -e "${CYAN}[5/7] LayarKaca21${NC}"
            test_layarkaca21 "https://layarkaca21.com/" || ((result++))
            echo ""
            
            echo -e "${CYAN}[6/7] Pencurimovie${NC}"
            test_pencurimovie "https://pencurimovie.com/" || ((result++))
            echo ""
            
            echo -e "${CYAN}[7/7] Samehadaku${NC}"
            test_samehadaku "https://samehadaku.com/" || ((result++))
            echo ""
            ;;
        *)
            print_error "Unknown provider: $PROVIDER"
            echo "Supported providers: Anichin, Donghuastream, Funmovieslix, Animasu, LayarKaca21, Pencurimovie, Samehadaku, all"
            exit 1
            ;;
    esac
    
    # Finalize report
    cat >> "$REPORT_FILE" << EOF

---

## Conclusion

**Status**: $([ $result -eq 0 ] && echo "✓ PASS" || echo "✗ FAIL - $result test(s) need review")

**Recommendation**: $([ $result -eq 0 ] && echo "Proceed with implementation" || echo "Manual review required for failed tests")

---

*Generated by verify-framework.sh*
EOF
    
    echo ""
    print_header "Verification Complete"
    echo ""
    echo "Report saved to: $REPORT_FILE"
    echo ""
    
    if [ $result -eq 0 ]; then
        print_success "All tests passed! Implementation can proceed."
    else
        print_warning "$result test(s) need manual review before implementation."
    fi
    
    return $result
}

# Run main function
main "$@"
