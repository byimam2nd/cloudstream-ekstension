#!/bin/bash

# LayarKaca21 Provider - Manual Test Script
# This script tests the basic functionality of the LayarKaca21 provider
# by making HTTP requests to the domains and verifying responses

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "======================================"
echo "LayarKaca21 Provider - Test Script"
echo "======================================"
echo ""

# Test 1: Domain Connectivity
echo -e "${YELLOW}Test 1: Testing Domain Connectivity${NC}"
echo "-------------------------------------------"

test_domain() {
    local domain=$1
    local name=$2
    local timeout=5
    
    echo -n "Testing $name ($domain): "
    
    # Use curl with timeout
    http_code=$(curl -sI --connect-timeout $timeout "$domain" 2>/dev/null | head -1 | awk '{print $2}')
    
    if [ -z "$http_code" ]; then
        echo -e "${RED}FAILED (no response)${NC}"
        return 1
    elif [ "$http_code" -ge 200 ] && [ "$http_code" -lt 400 ]; then
        echo -e "${GREEN}OK (HTTP $http_code)${NC}"
        return 0
    else
        echo -e "${RED}FAILED (HTTP $http_code)${NC}"
        return 1
    fi
}

# Test main domains
test_domain "https://lk21.de" "Main Domain"
test_domain "https://series.lk21.de" "Series Domain"
test_domain "https://d21.team" "Landing Page"

echo ""

# Test 2: Search Endpoint
echo -e "${YELLOW}Test 2: Testing Search Functionality${NC}"
echo "-------------------------------------------"

# Test search on main domain
echo -n "Testing search for 'avatar': "
search_result=$(curl -sL --connect-timeout 10 "https://lk21.de/?s=avatar" 2>/dev/null)

if echo "$search_result" | grep -q "article"; then
    echo -e "${GREEN}OK (Found article elements)${NC}"
    # Count results
    result_count=$(echo "$search_result" | grep -o "<article" | wc -l)
    echo "  Found approximately $result_count results"
else
    echo -e "${YELLOW}WARNING (No articles found, may need authentication)${NC}"
fi

echo ""

# Test 3: HTML Structure
echo -e "${YELLOW}Test 3: Testing HTML Structure${NC}"
echo "-------------------------------------------"

# Check for required HTML elements
echo -n "Checking for movie-info element: "
if echo "$search_result" | grep -q "movie-info"; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${YELLOW}NOT FOUND${NC}"
fi

echo -n "Checking for poster images: "
if echo "$search_result" | grep -q "img"; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${RED}FAILED${NC}"
fi

echo ""

# Test 4: Redirect Chain
echo -e "${YELLOW}Test 4: Testing Redirect Chain${NC}"
echo "-------------------------------------------"

echo -n "Testing lk21.de redirect: "
redirect_url=$(curl -sI --connect-timeout 5 "https://lk21.de" 2>/dev/null | grep -i "location:" | awk '{print $2}' | tr -d '\r')

if [ -n "$redirect_url" ]; then
    echo -e "${GREEN}Redirects to: $redirect_url${NC}"
else
    echo -e "${YELLOW}No redirect (direct access)${NC}"
fi

echo ""

# Test 5: Response Time
echo -e "${YELLOW}Test 5: Testing Response Time${NC}"
echo "-------------------------------------------"

echo -n "Testing response time for main domain: "
start_time=$(date +%s%N)
curl -sL --connect-timeout 10 "https://lk21.de" > /dev/null 2>&1
end_time=$(date +%s%N)
elapsed=$(( (end_time - start_time) / 1000000 )) # Convert to milliseconds

if [ $elapsed -lt 3000 ]; then
    echo -e "${GREEN}${elapsed}ms (Good)${NC}"
elif [ $elapsed -lt 10000 ]; then
    echo -e "${YELLOW}${elapsed}ms (Acceptable)${NC}"
else
    echo -e "${RED}${elapsed}ms (Slow)${NC}"
fi

echo ""
echo "======================================"
echo "Test Summary"
echo "======================================"
echo ""
echo "Note: Some tests may fail due to:"
echo "  - Cloudflare protection"
echo "  - Regional blocking"
echo "  - Network issues"
echo "  - Domain changes"
echo ""
echo "If domains are blocked, check: https://d21.team"
echo "for the latest working mirrors."
echo ""
