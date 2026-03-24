#!/bin/bash
# =============================================================================
# Script Audit HTML dengan Playwright (Bypass Cloudflare)
# =============================================================================
# Requirement: Node.js + Playwright
# Install: npm install -g playwright
#          npx playwright install chromium
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo -e "${RED}Error: Node.js not installed${NC}"
    echo "Install with: pkg install nodejs (Termux) or download from nodejs.org"
    exit 1
fi

# Check if Playwright is installed
if ! command -v npx &> /dev/null || ! npx playwright --version &> /dev/null; then
    echo -e "${YELLOW}Installing Playwright...${NC}"
    npm install -g playwright
    npx playwright install chromium --with-deps
fi

# Usage
if [ -z "$1" ]; then
    echo -e "${BLUE}=============================================${NC}"
    echo -e "${BLUE}  HTML Audit Script (Cloudflare Bypass)${NC}"
    echo -e "${BLUE}=============================================${NC}"
    echo ""
    echo "Usage: $0 <URL> [output_file]"
    echo ""
    echo "Examples:"
    echo "  $0 https://samehadaku.com/"
    echo "  $0 https://animasu.stream/ audit.html"
    echo "  $0 https://pencurimovie.cam/ | grep -i poster"
    echo ""
    exit 1
fi

URL="$1"
OUTPUT="${2:-/tmp/playwright-audit-$(date +%Y%m%d-%H%M%S).html}"

echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}  Fetching HTML with Playwright${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""
echo "URL: $URL"
echo "Output: $OUTPUT"
echo ""

# Create Node.js script for Playwright
cat > /tmp/playwright-fetch.js << 'NODESCRIPT'
const { chromium } = require('playwright');

(async () => {
    const url = process.argv[2];
    const outputFile = process.argv[3] || '/tmp/output.html';
    
    console.log('Launching browser...');
    const browser = await chromium.launch({
        headless: true,
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-accelerated-2d-canvas',
            '--disable-gpu'
        ]
    });
    
    const context = await browser.newContext({
        viewport: { width: 1920, height: 1080 },
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
    });
    
    const page = await context.newPage();
    
    console.log(`Navigating to ${url}...`);
    
    // Navigate and wait for network to be idle (Cloudflare protection bypass)
    await page.goto(url, { 
        waitUntil: 'networkidle',
        timeout: 60000 
    });
    
    // Wait for content to load
    await page.waitForTimeout(5000);
    
    // Get HTML content
    const html = await page.content();
    
    // Save to file
    const fs = require('fs');
    fs.writeFileSync(outputFile, html);
    
    console.log(`✓ HTML saved to ${outputFile}`);
    console.log(`✓ Page title: ${await page.title()}`);
    
    // Extract useful info
    const selectors = {
        posters: await page.$$eval('img[src], img[data-src], img[data-lazy-src]', imgs => 
            imgs.map(img => img.src || img.dataset.src || img.dataset.lazySrc).slice(0, 10)
        ),
        titles: await page.$$eval('h1, h2, h3, .title', els => 
            els.map(el => el.textContent.trim()).slice(0, 10)
        ),
        divs: await page.$$eval('div[class*="poster"], div[class*="thumb"], div[class*="anime"]', divs =>
            divs.map(div => div.className).slice(0, 10)
        )
    };
    
    console.log('\n📊 Found Selectors:');
    console.log('==================');
    console.log('Posters:', selectors.posters.length);
    selectors.posters.forEach((src, i) => {
        if (src && src.startsWith('http')) console.log(`  ${i+1}. ${src.substring(0, 80)}...`);
    });
    
    console.log('\nTitles:', selectors.titles.length);
    selectors.titles.forEach((title, i) => {
        if (title) console.log(`  ${i+1}. ${title.substring(0, 60)}...`);
    });
    
    console.log('\nDiv Classes:', selectors.divs.length);
    selectors.divs.forEach((cls, i) => {
        console.log(`  ${i+1}. ${cls}`);
    });
    
    await browser.close();
    console.log('\n✓ Done!');
})();
NODESCRIPT

echo -e "${YELLOW}Running Playwright...${NC}"
echo ""

# Run Playwright script
node /tmp/playwright-fetch.js "$URL" "$OUTPUT"

echo ""
echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}  Audit Complete!${NC}"
echo -e "${GREEN}=============================================${NC}"
echo ""
echo "HTML saved to: $OUTPUT"
echo ""
echo "Quick analysis:"
echo "  grep -i poster $OUTPUT | head -20"
echo "  grep -i thumb $OUTPUT | head -20"
echo "  grep -i 'img.*src' $OUTPUT | head -20"
echo ""
