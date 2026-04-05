#!/usr/bin/env node
// ========================================
// Extractor Pattern Tester (JavaScript)
// ========================================
// Tests extractor patterns against real HTML
// No Kotlin, no Gradle — just curl + regex
// ========================================

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const BASE_DIR = path.resolve(__dirname, '../..');
const URL_DB = path.join(BASE_DIR, 'tests/extractors/test-data/extractor-urls.json');
const HTML_DIR = path.join(BASE_DIR, 'tests/extractors/raw-data/html');
const REPORT_FILE = path.join(BASE_DIR, 'tests/extractors/reports/pattern-test-report.md');

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36';

// ========================================
// Pattern Definitions (from ExtCloud references)
// ========================================

const PATTERNS = {
  'okru-videos': {
    name: 'OK.ru videos array',
    preprocess: (html) => html
      .replace(/\\&quot;/g, '"')
      .replace(/\\\\/g, '\\')
      .replace(/\\u0026/g, '&'),
    regex: /"videos":\[[^\]]*\]/,
    extractUrls: (match) => {
      const urls = match.match(/"url":"([^"]+)"/g) || [];
      return urls.map(u => u.replace('"url":"', '').replace('"', ''));
    }
  },
  'm3u8-direct': {
    name: 'Direct M3U8 URL',
    regex: /https?:\/\/[^"' ]+m3u8[^"' ]*/g,
    extractUrls: (matches) => Array.isArray(matches) ? matches : [matches]
  },
  'mp4-direct': {
    name: 'Direct MP4 URL',
    regex: /https?:\/\/[^"' ]+\.mp4[^"' ]*/g,
    extractUrls: (matches) => Array.isArray(matches) ? matches : [matches]
  },
  'file-source': {
    name: 'file: "..." (m3u8 in sources)',
    regex: /file:\s*["']([^'"']*?m3u8[^'"']*?)["']/,
    extractUrls: (match) => [match[1]]
  },
  'sources-array': {
    name: 'sources: [{file: "..."}]',
    regex: /sources\s*:\s*\[/,
    extractUrls: () => []
  },
  'packed-js': {
    name: 'Packed JavaScript',
    regex: /eval\(function\(p,a,c,k,e/,
    extractUrls: () => []
  },
  'data-options': {
    name: 'data-options attribute (OK.ru)',
    regex: /data-options="[^"]*"/,
    extractUrls: () => []
  },
  'iframe-src': {
    name: 'iframe src',
    regex: /<iframe[^>]*src=["']([^"']+)["']/,
    extractUrls: (match) => [match[1]]
  }
};

// ========================================
// Test Functions
// ========================================

function fetchHtml(url, domain) {
  try {
    const result = execSync(
      `curl -s -L --max-time 10 -A "${UA}" -H "Origin: ${domain}" "${url}" 2>/dev/null`,
      { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 }
    );
    return result;
  } catch (e) {
    return '';
  }
}

function testPatterns(html, patterns) {
  const results = {};
  
  for (const [key, pattern] of Object.entries(patterns)) {
    try {
      const processed = pattern.preprocess ? pattern.preprocess(html) : html;
      const match = pattern.regex.exec(processed);
      
      if (match) {
        const urls = pattern.extractUrls(match);
        results[key] = {
          found: true,
          urlCount: urls.length,
          sampleUrl: urls[0] ? urls[0].substring(0, 80) : ''
        };
      } else {
        // For global regex patterns
        if (pattern.regex.flags.includes('g')) {
          const matches = processed.match(pattern.regex) || [];
          results[key] = {
            found: matches.length > 0,
            urlCount: matches.length,
            sampleUrl: matches[0] ? matches[0].substring(0, 80) : ''
          };
        } else {
          results[key] = { found: false };
        }
      }
    } catch (e) {
      results[key] = { found: false, error: e.message };
    }
  }
  
  return results;
}

// ========================================
// Main
// ========================================

async function main() {
  console.log('=== Extractor Pattern Tester ===\n');
  
  // Load URL database
  const db = JSON.parse(fs.readFileSync(URL_DB, 'utf8'));
  console.log(`Loaded ${db.extractors.length} extractor groups\n`);
  
  const allResults = [];
  
  for (const group of db.extractors) {
    console.log(`\n--- ${group.group} (${group.extractors.join(', ')}) ---`);
    
    for (const url of group.urls) {
      const domain = new URL(url).hostname;
      console.log(`  Testing: ${url}`);
      console.log(`    Fetching HTML...`);
      
      const html = fetchHtml(url, domain);
      const htmlSize = html.length;
      console.log(`    HTML size: ${htmlSize} bytes`);
      
      if (htmlSize < 100) {
        console.log(`    ⚠️  HTML too small — likely blocked or 404`);
        allResults.push({
          group: group.group,
          url,
          status: 'BLOCKED',
          htmlSize,
          patterns: {}
        });
        continue;
      }
      
      // Apply all patterns
      const patternResults = testPatterns(html, PATTERNS);
      
      // Report findings
      const foundPatterns = Object.entries(patternResults)
        .filter(([_, r]) => r.found);
      
      if (foundPatterns.length > 0) {
        console.log(`    ✅ Found ${foundPatterns.length} patterns:`);
        for (const [name, result] of foundPatterns) {
          console.log(`      - ${PATTERNS[name].name}: ${result.urlCount} URLs`);
          if (result.sampleUrl) {
            console.log(`        Sample: ${result.sampleUrl}...`);
          }
        }
      } else {
        console.log(`    ❌ No patterns matched`);
      }
      
      allResults.push({
        group: group.group,
        url,
        status: foundPatterns.length > 0 ? 'WORKING' : 'NO_MATCH',
        htmlSize,
        patterns: Object.fromEntries(
          Object.entries(patternResults).map(([k, v]) => [k, v.found ? `${v.urlCount} URLs` : 'no'])
        )
      });
    }
  }
  
  // Generate markdown report
  let report = `# 🧪 Extractor Pattern Test Report\n\n`;
  report += `**Generated:** ${new Date().toISOString().split('T')[0]}\n`;
  report += `**Total URLs Tested:** ${allResults.length}\n\n`;
  
  // Summary table
  const working = allResults.filter(r => r.status === 'WORKING').length;
  const blocked = allResults.filter(r => r.status === 'BLOCKED').length;
  const noMatch = allResults.filter(r => r.status === 'NO_MATCH').length;
  
  report += `## Summary\n\n| Status | Count |\n|--------|-------|\n`;
  report += `| ✅ Working | ${working} |\n`;
  report += `| ⚠️  Blocked | ${blocked} |\n`;
  report += `| ❌ No Match | ${noMatch} |\n\n`;
  
  // Detailed results
  report += `## Detailed Results\n\n`;
  report += `| Group | URL | Status | Patterns |\n`;
  report += `|-------|-----|--------|----------|\n`;
  
  for (const r of allResults) {
    const status = r.status === 'WORKING' ? '✅' : r.status === 'BLOCKED' ? '⚠️' : '❌';
    const patterns = Object.entries(r.patterns)
      .filter(([_, v]) => v !== 'no')
      .map(([k, _]) => k)
      .join(', ') || '-';
    report += `| ${r.group} | ${r.url.substring(0, 50)}... | ${status} | ${patterns} |\n`;
  }
  
  // Save report
  fs.mkdirSync(path.dirname(REPORT_FILE), { recursive: true });
  fs.writeFileSync(REPORT_FILE, report);
  
  console.log(`\n\n=== Report saved to: ${REPORT_FILE} ===`);
  
  // Print summary
  console.log(`\n=== Summary ===`);
  console.log(`✅ Working: ${working}`);
  console.log(`⚠️  Blocked: ${blocked}`);
  console.log(`❌ No Match: ${noMatch}`);
}

main().catch(console.error);
