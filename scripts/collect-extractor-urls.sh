#!/bin/bash
# ========================================
# Extractor URL Collector — Otomatis
# ========================================
# Crawls episode pages → collect embed URLs
# Fetches embed pages → saves raw HTML
# Applies known patterns → extracts video URLs
# Generates report: which pattern works for which domain
# ========================================

set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$BASE_DIR/tests/extractors/raw-data"
REPORT="$BASE_DIR/tests/extractors/reports/collector-report.md"
mkdir -p "$OUTPUT_DIR" "$OUTPUT_DIR/html" "$OUTPUT_DIR/urls"
mkdir -p "$(dirname "$REPORT")"

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

# ========================================
# STEP 1: Crawl episode pages → collect embed URLs
# ========================================
echo "=== STEP 1: Collecting embed URLs from episode pages ==="

PROVIDERS=(
    # Additional series for more diverse extractor URLs
    "https://anichin.cafe/seri/shrouding-the-heavens/"
    "https://anichin.cafe/seri/one-piece/"
    "https://anichin.cafe/seri/perfect-world/"
    "https://anichin.cafe/seri/my-senior-brother-is-too-steady/"
    "https://anichin.cafe/seri/peerless-battle-spirit/"
    "https://anichin.cafe/seri/purple-river-season-2/"
    "https://anichin.cafe/seri/release-that-witch/"
    "https://anichin.cafe/seri/against-the-gods/"
    "https://anichin.cafe/seri/jujutsu-kaisen-2nd-season/"
    "https://anichin.cafe/seri/bleach-sennen-kessen-hen/"
    "https://anichin.cafe/seri/naruto-shippuden/"
    "https://anichin.cafe/seri/spirit-sword-sovereign/"
    "https://anichin.cafe/seri/a-will-eternal/"
    "https://anichin.cafe/seri/martial-universe/"
    "https://anichin.cafe/seri/against-the-gods-season-2/"
    "https://anichin.cafe/seri/the-great-ruler/"
    "https://anichin.cafe/seri/tales-of-demons-and-gods/"
    "https://anichin.cafe/seri/stellar-transformations/"
    "https://anichin.cafe/seri/swallowed-star/"
    "https://anichin.cafe/seri/battle-through-the-heavens/"
    "https://anichin.cafe/seri/martial-peak/"
)

ALL_EMBED_URLS="$OUTPUT_DIR/urls/all-embed-urls.txt"
> "$ALL_EMBED_URLS"

for provider_url in "${PROVIDERS[@]}"; do
    domain=$(echo "$provider_url" | grep -oP 'https?://[^/]+')
    echo "  Crawling: $provider_url"

    # Fetch episode links from series page
    curl -s -L -A "$UA" "$provider_url" 2>/dev/null | \
      grep -oP 'href="https?://[^"]*episode-[^"]*"' | \
      sed 's/href="//;s/"$//' | \
      sort -u | head -20 | \
    while read episode_url; do
        echo "    Episode: $episode_url"

        # Extract base64 values from <option> elements
        curl -s -L -A "$UA" "$episode_url" 2>/dev/null | \
          grep -oP '<option[^>]*value="[^"]*"' | \
          sed 's/.*value="//;s/"$//' | \
        while read b64; do
            if [ ${#b64} -gt 30 ]; then
                # Decode base64 → extract iframe URL
                decoded=$(echo "$b64" | base64 -d 2>/dev/null || echo "")
                if [ -n "$decoded" ]; then
                    iframe_url=$(echo "$decoded" | grep -oP 'src="[^"]*"' | sed 's/src="//;s/"$//' | head -1)
                    if [ -n "$iframe_url" ]; then
                        echo "$iframe_url" >> "$ALL_EMBED_URLS"
                        echo "      Embed: $iframe_url"
                    fi
                fi
            fi
        done
    done
done

# Deduplicate
sort -u "$ALL_EMBED_URLS" -o "$ALL_EMBED_URLS"
total=$(wc -l < "$ALL_EMBED_URLS")
echo "  Total unique embed URLs: $total"

# ========================================
# STEP 2: Group by domain
# ========================================
echo ""
echo "=== STEP 2: Grouping by domain ==="

DOMAINS_FILE="$OUTPUT_DIR/urls/domains.txt"
> "$DOMAINS_FILE"

while read url; do
    echo "$url" | grep -oP 'https?://[^/]+' >> "$DOMAINS_FILE"
done < "$ALL_EMBED_URLS"

sort -u "$DOMAINS_FILE" -o "$DOMAINS_FILE"
echo "  Unique domains:"
while read domain; do
    count=$(grep -c "$domain" "$ALL_EMBED_URLS" || echo "0")
    echo "    $domain ($count URLs)"
done < "$DOMAINS_FILE"

# ========================================
# STEP 3: Fetch HTML + Apply patterns
# ========================================
echo ""
echo "=== STEP 3: Fetching HTML and applying patterns ==="

RESULTS="$OUTPUT_DIR/urls/pattern-results.txt"
> "$RESULTS"

while read embed_url; do
    domain=$(echo "$embed_url" | grep -oP 'https?://[^/]+')
    safe_name=$(echo "$embed_url" | sed 's|https\?://||;s|/|_|g;cut -c1-80')
    html_file="$OUTPUT_DIR/html/${safe_name}.html"

    echo "  Fetching: $embed_url"
    curl -s -L -A "$UA" \
      -H "Accept: */*" \
      -H "Origin: $domain" \
      "$embed_url" 2>/dev/null > "$html_file"

    html_size=$(wc -c < "$html_file")
    echo "    HTML size: ${html_size} bytes"

    # Pattern 1: OK.ru — videos array after unicode decode
    okru_match=$(cat "$html_file" | \
      sed 's/\\&quot;/"/g; s/\\\\/\\/g; s/\\u0026/\&/g' | \
      grep -oP '"videos":\[[^]]*\]' | head -1)
    if [ -n "$okru_match" ]; then
        url_count=$(echo "$okru_match" | grep -oP '"url":"[^"]*"' | wc -l)
        echo "    ✅ OK.ru pattern: $url_count URLs found"
        echo "OK.ru | $embed_url | $url_count URLs" >> "$RESULTS"
    fi

    # Pattern 2: M3U8 file regex
    m3u8_count=$(cat "$html_file" | grep -oP 'https?://[^"'"'"' ]+m3u8[^"'"'"' ]*' | wc -l)
    if [ "$m3u8_count" -gt 0 ]; then
        echo "    ✅ M3U8 pattern: $m3u8_count URLs found"
        echo "M3U8 | $embed_url | $m3u8_count URLs" >> "$RESULTS"
    fi

    # Pattern 3: MP4 file regex
    mp4_count=$(cat "$html_file" | grep -oP 'https?://[^"'"'"' ]+\.mp4[^"'"'"' ]*' | wc -l)
    if [ "$mp4_count" -gt 0 ]; then
        echo "    ✅ MP4 pattern: $mp4_count URLs found"
        echo "MP4 | $embed_url | $mp4_count URLs" >> "$RESULTS"
    fi

    # Pattern 4: data-options attribute (OK.ru modern)
    data_opts=$(cat "$html_file" | grep -oP 'data-options="[^"]*"' | wc -l)
    if [ "$data_opts" -gt 0 ]; then
        echo "    ✅ data-options attribute: found ($data_opts)"
        echo "data-options | $embed_url | present" >> "$RESULTS"
    fi

    # Pattern 5: sources: in script
    sources=$(cat "$html_file" | grep -oP 'sources\s*:' | wc -l)
    if [ "$sources" -gt 0 ]; then
        echo "    ✅ sources: pattern: found"
        echo "sources | $embed_url | present" >> "$RESULTS"
    fi

    # Pattern 6: packed JS
    packed=$(cat "$html_file" | grep -cP 'eval\(function\(p,a,c,k,e' || echo "0")
    if [ "$packed" -gt 0 ]; then
        echo "    ✅ Packed JS: found"
        echo "packed-js | $embed_url | present" >> "$RESULTS"
    fi

done < "$ALL_EMBED_URLS"

# ========================================
# STEP 4: Generate report
# ========================================
echo ""
echo "=== STEP 4: Generating report ==="

cat > "$REPORT" << EOF
# 🧪 Extractor Pattern Collector Report

**Generated:** $(date '+%Y-%m-%d %H:%M:%S')
**Total Embed URLs:** $(wc -l < "$ALL_EMBED_URLS")
**Unique Domains:** $(wc -l < "$DOMAINS_FILE")

## Domains Found

| Domain | Count |
|--------|-------|
$(while read domain; do
    count=$(grep -c "$domain" "$ALL_EMBED_URLS" || echo "0")
    echo "| $domain | $count |"
done < "$DOMAINS_FILE")

## Pattern Results

| Pattern | URL | Result |
|---------|-----|--------|
$(sort "$RESULTS" | awk -F'|' '{printf "| %s | %s | %s |\n", $1, $2, $3}')

## Summary

$(grep -oP '^[^|]+' "$RESULTS" | sort | uniq -c | sort -rn | while read count pattern; do
    echo "- **$pattern**: $count matches"
done)

## Raw Files

- Embed URLs: \`tests/extractors/raw-data/urls/all-embed-urls.txt\`
- HTML files: \`tests/extractors/raw-data/html/\`
- Domains: \`tests/extractors/raw-data/urls/domains.txt\`
- Pattern results: \`tests/extractors/raw-data/urls/pattern-results.txt\`
EOF

echo ""
echo "✅ Report saved to: $REPORT"
echo "✅ HTML files saved to: $OUTPUT_DIR/html/"
echo "✅ URL database saved to: $ALL_EMBED_URLS"
echo ""
echo "=== DONE ==="
