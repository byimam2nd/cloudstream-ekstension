#!/usr/bin/env node
// ========================================
// Comprehensive Extractor Test Suite
// ========================================
// Test extractor dari MasterExtractors.kt (PUNYA ANDA).
// Jika extractor gagal → ambil referensi fix dari ExtCloud/Phisher.
// ========================================

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const BASE_DIR = path.resolve(__dirname, '../..');
const URL_DB = path.join(BASE_DIR, 'tests/extractors/test-data/extractor-urls.json');
const HISTORY_DIR = path.join(BASE_DIR, 'tests/extractors/reports/history');
const REPORT_FILE = path.join(BASE_DIR, 'tests/extractors/reports/comprehensive-report.md');

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

// ========================================
// Pattern Extractor (dari MasterExtractors.kt)
// ========================================
// Jika extractor Anda gagal, cari referensi fix di:
//   /cloudstream/ExtCloud/  — referensi open source
//   /cloudstream/phisher/   — referensi open source
// ========================================

const EXTRACTOR_PATTERNS = {

  // --- OK.ru ---
  'okru': {
    name: 'OK.ru / Odnoklassniki',
    urlRegex: /ok\.ru\/video(?:embed)?\/(\d+)/,
    expectedMinLinks: 1,
    expectedMaxLinks: 6,
    expectedQualities: [144, 240, 360, 480, 720, 1080],
    fetch: (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" \\
        -H "Accept: */*" \\
        -H "Connection: keep-alive" \\
        -H "Sec-Fetch-Dest: empty" \\
        -H "Sec-Fetch-Mode: cors" \\
        -H "Sec-Fetch-Site: cross-site" \\
        -H "Origin: https://odnoklassniki.ru" \\
        "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      let decoded = html
        .replace(/\\&quot;/g, '"')
        .replace(/\\\\/g, '\\')
        .replace(/\\u([0-9A-Fa-f]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
      const match = decoded.match(/"videos":(\[[^\]]*\])/);
      if (!match) return { success: false, links: [], reason: 'No "videos" array found' };
      try {
        const videos = JSON.parse(match[1]);
        const qualityMap = {
          'MOBILE': 144, 'LOWEST': 240, 'LOW': 360, 'SD': 480,
          'HD': 720, 'FULL': 1080, 'QUAD': 1440, 'ULTRA': 2160
        };
        const links = videos
          .map(v => {
            let url = v.url;
            if (url.startsWith('//')) url = 'https:' + url;
            url = url.replace(/\\u0026/g, '&');
            return { url, quality: qualityMap[v.name?.toUpperCase()] || 0, qualityName: v.name };
          })
          .filter(v => v.url && v.url.startsWith('http') && !v.url.includes('x0026'));
        if (links.length === 0) return { success: false, links: [], reason: 'No valid URLs after filtering' };
        return { success: true, links };
      } catch (e) {
        return { success: false, links: [], reason: `JSON parse: ${e.message}` };
      }
    }
  },

  // --- Dailymotion ---
  'dailymotion': {
    name: 'Dailymotion',
    urlRegex: /dailymotion\.com/,
    expectedMinLinks: 1,
    expectedMaxLinks: 10,
    fetch: (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      // Try cdndirector (geo player)
      const cdn = html.match(/https?:\/\/cdndirector\.dailymotion\.com\/cdn\/manifest\/video\/[^"\\']+/);
      if (cdn) return { success: true, links: [{ url: cdn[0], quality: 0 }] };
      // Try regular m3u8
      const m3u8 = html.match(/https?:\/\/[^"']+\.(?:m3u8|m3u)[^"']*/);
      if (m3u8) return { success: true, links: [{ url: m3u8[0], quality: 0 }] };
      return { success: false, links: [], reason: 'No m3u8/cdn found' };
    }
  },

  // --- DoodStream ---
  'doodstream': {
    name: 'DoodStream',
    urlRegex: /dood[s]?\.pro\/e\/([a-zA-Z0-9]+)/,
    expectedMinLinks: 1,
    fetch: (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      const pass = html.match(/\/pass([^"'&]*)/)?.[1] || '';
      const token = html.match(/token=([^&"'\\]*)/)?.[1];
      if (token) {
        const videoUrl = `https://doods.pro/download?token=${token}${pass ? '&pass=' + pass : ''}`;
        return { success: true, links: [{ url: videoUrl, quality: 0 }] };
      }
      const m3u8 = html.match(/https?:\/\/[^"']+\.m3u8[^"']*/);
      if (m3u8) return { success: true, links: [{ url: m3u8[0], quality: 0 }] };
      return { success: false, links: [], reason: 'No token/m3u8 found' };
    }
  },

  // --- StreamRuby ---
  'streamruby': {
    name: 'StreamRuby / StreamWish',
    urlRegex: /rubyvidhub\.com\/embed-([a-zA-Z0-9]+)\.html/,
    expectedMinLinks: 1,
    fetch: (url) => {
      const id = url.match(/embed-([a-zA-Z0-9]+)\.html/)?.[1];
      const cmd = `curl -s -L --max-time 15 -A "${UA}" \\
        -H "Origin: https://rubyvidhub.com" \\
        -H "Referer: https://rubyvidhub.com/" \\
        -X POST -d "op=embed&file_code=${id}&auto=1&referer=" \\
        "https://rubyvidhub.com/dl" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      // Try direct m3u8
      const m3u8 = html.match(/https?:\/\/[^"'\\ ]+\.m3u8[^"'\\ ]*/);
      if (m3u8) return { success: true, links: [{ url: m3u8[0], quality: 0 }] };
      // Try file: pattern
      const file = html.match(/file:\s*["']([^"']+\.m3u8[^"']*)["']/);
      if (file) return { success: true, links: [{ url: file[1], quality: 0 }] };
      return { success: false, links: [], reason: 'No m3u8 found' };
    }
  },

  // --- Rumble ---
  'rumble': {
    name: 'Rumble',
    urlRegex: /rumble\.com\/embed\/([a-zA-Z0-9]+)/,
    expectedMinLinks: 1,
    fetch: (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      const hls = html.match(/"hls":\{"url":"(https?:[^"]+playlist\.m3u8)"/);
      if (hls) return { success: true, links: [{ url: hls[1].replace(/\\\//g, '/'), quality: 0 }] };
      return { success: false, links: [], reason: 'No HLS found' };
    }
  }
};

// ========================================
// Validation Functions
// ========================================

function validateUrl(url) {
  const checks = {
    isHttp: url.startsWith('http://') || url.startsWith('https://'),
    hasDomain: url.includes('.') && !url.startsWith('http://localhost'),
    noTemplate: !url.includes('{') && !url.includes('}'),
    hasProtocol: url.match(/^https?:\/\//),
  };
  const isValid = Object.values(checks).every(v => v);
  return { isValid, checks };
}

function validateResult(result, group) {
  const pattern = EXTRACTOR_PATTERNS[group];
  if (!pattern) return { score: 0, issues: ['No pattern defined'] };

  const issues = [];
  let score = 100;

  if (!result.success) {
    score = 0;
    issues.push(`Extractor failed: ${result.reason}`);
    return { score, issues };
  }

  const links = result.links;

  // Check minimum links
  if (links.length < pattern.expectedMinLinks) {
    score -= 30;
    issues.push(`Too few links: ${links.length} (expected >= ${pattern.expectedMinLinks})`);
  }

  // Check maximum links
  if (pattern.expectedMaxLinks && links.length > pattern.expectedMaxLinks) {
    score -= 10;
    issues.push(`Too many links: ${links.length} (expected <= ${pattern.expectedMaxLinks})`);
  }

  // Validate each URL
  const invalidUrls = [];
  links.forEach((link, i) => {
    const validation = validateUrl(link.url);
    if (!validation.isValid) {
      score -= 15;
      invalidUrls.push(`Link ${i + 1} invalid: ${link.url.substring(0, 60)}...`);
    }
  });
  if (invalidUrls.length > 0) issues.push(...invalidUrls);

  // Check quality coverage
  if (pattern.expectedQualities) {
    const foundQualities = links.map(l => l.quality).filter(q => q > 0);
    const missing = pattern.expectedQualities.filter(q => !foundQualities.includes(q));
    if (missing.length > 0 && foundQualities.length > 0) {
      score -= Math.min(20, missing.length * 5);
      issues.push(`Missing qualities: ${missing.join(', ')}`);
    }
  }

  score = Math.max(0, score);
  return { score, issues };
}

// ========================================
// History Management
// ========================================

function loadHistory() {
  const files = fs.readdirSync(HISTORY_DIR).filter(f => f.endsWith('.json')).sort().reverse();
  return files.map(f => JSON.parse(fs.readFileSync(path.join(HISTORY_DIR, f), 'utf8')));
}

function saveResult(result) {
  fs.mkdirSync(HISTORY_DIR, { recursive: true });
  const date = new Date().toISOString().split('T')[0];
  const file = path.join(HISTORY_DIR, `${date}.json`);

  let history = [];
  if (fs.existsSync(file)) {
    history = JSON.parse(fs.readFileSync(file, 'utf8'));
  }
  history.push(result);
  fs.writeFileSync(file, JSON.stringify(history, null, 2));
}

// ========================================
// Report Generator
// ========================================

function generateReport(allResults, history) {
  const today = new Date().toISOString().split('T')[0];
  const total = allResults.length;
  const working = allResults.filter(r => r.validation.score >= 70).length;
  const partial = allResults.filter(r => r.validation.score > 0 && r.validation.score < 70).length;
  const failed = allResults.filter(r => r.validation.score === 0).length;

  // Calculate average score per group
  const groupScores = {};
  for (const r of allResults) {
    if (!groupScores[r.group]) groupScores[r.group] = [];
    groupScores[r.group].push(r.validation.score);
  }

  // Compare with history
  const prevResults = history.length > 0 ? history[0] : null;

  let report = `# 🧪 Comprehensive Extractor Test Report\n\n`;
  report += `**Generated:** ${today}\n`;
  report += `**Total URLs Tested:** ${total}\n`;
  report += `**Overall Score:** ${Math.round(allResults.reduce((a, r) => a + r.validation.score, 0) / total)}%\n\n`;

  report += `## Summary\n\n`;
  report += `| Status | Count | Percentage |\n|--------|-------|------------|\n`;
  report += `| ✅ Working (≥70%) | ${working} | ${Math.round(working/total*100)}% |\n`;
  report += `| ⚠️  Partial (1-69%) | ${partial} | ${Math.round(partial/total*100)}% |\n`;
  report += `| ❌ Failed (0%) | ${failed} | ${Math.round(failed/total*100)}% |\n\n`;

  report += `## Group Scores\n\n`;
  report += `| Extractor Group | Avg Score | URLs | Min | Max |\n|----------------|-----------|------|-----|-----|\n`;
  for (const [group, scores] of Object.entries(groupScores)) {
    const avg = Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
    const icon = avg >= 70 ? '✅' : avg > 0 ? '⚠️' : '❌';
    report += `| ${icon} ${group} | ${avg}% | ${scores.length} | ${Math.min(...scores)}% | ${Math.max(...scores)}% |\n`;
  }

  report += `\n## Detailed Results\n\n`;
  report += `| Extractor | URL | Score | Links | Issues |\n|-----------|-----|-------|-------|--------|\n`;

  for (const r of allResults) {
    const icon = r.validation.score >= 70 ? '✅' : r.validation.score > 0 ? '⚠️' : '❌';
    const linkCount = Array.isArray(r.result.links) ? r.result.links.length : 0;
    const issues = r.validation.issues.slice(0, 2).join('; ') || '-';
    report += `| ${icon} ${r.group} | ${r.url.substring(0, 45)} | ${r.validation.score}% | ${linkCount} | ${issues} |\n`;
  }

  // Trend comparison
  if (prevResults && prevResults.length > 0) {
    const prevTotal = prevResults.filter(r => r.result.success).length;
    const currTotal = working + partial;
    const trend = currTotal > prevTotal ? '📈 Improved' : currTotal < prevTotal ? '📉 Regressed' : '➡️ Stable';

    report += `\n## Trend Comparison\n\n`;
    report += `| Metric | Previous | Current | Trend |\n|--------|----------|---------|-------|\n`;
    report += `| Working URLs | ${prevTotal} | ${currTotal} | ${trend} |\n`;
  }

  // Recommendations
  report += `\n## Recommendations\n\n`;
  for (const [group, scores] of Object.entries(groupScores)) {
    const avg = scores.reduce((a, b) => a + b, 0) / scores.length;
    if (avg < 50) {
      report += `- **${group}**: Extractor pattern perlu diperbaiki. Referensi: \`/cloudstream/ExtCloud/\` atau \`/cloudstream/phisher/\`\n`;
    } else if (avg < 70) {
      report += `- **${group}**: Extractor bekerja sebagian. Cek URL yang gagal untuk pattern yang benar.\n`;
    }
  }

  fs.mkdirSync(path.dirname(REPORT_FILE), { recursive: true });
  fs.writeFileSync(REPORT_FILE, report);
  return report;
}

// ========================================
// Main Test Runner
// ========================================

async function main() {
  console.log('=== Comprehensive Extractor Test Suite ===\n');

  const db = JSON.parse(fs.readFileSync(URL_DB, 'utf8'));
  const history = loadHistory();
  const allResults = [];

  for (const group of db.extractors) {
    console.log(`\n── ${group.group} ──`);

    const pattern = EXTRACTOR_PATTERNS[group.group];
    if (!pattern) {
      console.log(`  ⚠️  No pattern defined`);
      continue;
    }

    for (const url of group.urls) {
      await new Promise(r => setTimeout(r, 1000));
      console.log(`  Testing: ${url}`);

      try {
        const html = pattern.fetch(url);
        const htmlSize = html.length;

        if (htmlSize < 100) {
          console.log(`    ❌ Blocked (${htmlSize} bytes)`);
          allResults.push({ group: group.group, url, result: { success: false, links: [], reason: 'Blocked' }, validation: { score: 0, issues: ['Blocked by server'] } });
          continue;
        }

        const result = pattern.extract(html);
        const validation = validateResult(result, group.group);

        if (result.success && result.links.length > 0) {
          console.log(`    ✅ ${result.links.length} links (score: ${validation.score}%)`);
        } else {
          console.log(`    ❌ ${result.reason} (score: ${validation.score}%)`);
        }

        const record = { group: group.group, url, result, validation };
        allResults.push(record);
        saveResult(record);

      } catch (e) {
        console.log(`    ❌ Error: ${e.message}`);
        allResults.push({ group: group.group, url, result: { success: false, links: [] }, validation: { score: 0, issues: [`Error: ${e.message}`] } });
      }
    }
  }

  // Generate report
  console.log('\n\n=== Generating Report ===');
  const report = generateReport(allResults, history);
  console.log(`Report saved to: ${REPORT_FILE}\n`);

  // Print summary
  const total = allResults.length;
  const working = allResults.filter(r => r.validation.score >= 70).length;
  const avg = Math.round(allResults.reduce((a, r) => a + r.validation.score, 0) / total);
  console.log(`Total: ${total} | Working: ${working}/${total} | Avg Score: ${avg}%`);
}

main().catch(e => {
  console.error('Fatal:', e.message);
  process.exit(1);
});
