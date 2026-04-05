#!/usr/bin/env node
// ========================================
// Extractor Tester
// ========================================
// Test extractor dari MasterExtractors.kt (PUNYA ANDA).
// Jika extractor gagal → ambil referensi fix dari ExtCloud/Phisher.
// ========================================

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const BASE_DIR = path.resolve(__dirname, '../..');
const URL_DB = path.join(BASE_DIR, 'tests/extractors/test-data/extractor-urls.json');
const REPORT_FILE = path.join(BASE_DIR, 'tests/extractors/reports/pattern-test-report.md');

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36';

// ========================================
// Pattern Extractor (dari MasterExtractors.kt)
// ========================================
// Jika extractor Anda gagal, cari referensi fix di:
//   /cloudstream/ExtCloud/  — referensi open source
//   /cloudstream/phisher/   — referensi open source
// ========================================

const EXTRACTOR_PATTERNS = {

  // --- OK.ru (dari Odnoklassniki extractor) ---
  'okru': {
    name: 'OK.ru / Odnoklassniki',
    urlRegex: /ok\.ru\/video(?:embed)?\/(\d+)/,
    fetch: async (url) => {
      const domain = new URL(url).hostname;
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
      // Decode: \&quot; → " , \\ → \ , \uXXXX → char
      let decoded = html
        .replace(/\\&quot;/g, '"')
        .replace(/\\\\/g, '\\')
        .replace(/\\u([0-9A-Fa-f]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));

      // Extract videos array
      const match = decoded.match(/"videos":(\[[^\]]*\])/);
      if (!match) return { success: false, reason: 'No "videos" array found' };

      try {
        const videos = JSON.parse(match[1]);
        const links = videos.map(v => {
          let url = v.url;
          if (url.startsWith('//')) url = 'https:' + url;
          // Decode \u0026 → &
          url = url.replace(/\\u0026/g, '&');
          const qualityMap = {
            'MOBILE': 144, 'LOWEST': 240, 'LOW': 360, 'SD': 480,
            'HD': 720, 'FULL': 1080, 'QUAD': 1440, 'ULTRA': 2160
          };
          return { url, quality: qualityMap[v.name.toUpperCase()] || 0 };
        }).filter(v => v.url && !v.url.includes('x0026') && v.url.startsWith('http'));

        return { success: true, links };
      } catch (e) {
        return { success: false, reason: `JSON parse error: ${e.message}` };
      }
    }
  },

  // --- Rumble ---
  'rumble': {
    name: 'Rumble',
    urlRegex: /rumble\.com\/embed\/([a-zA-Z0-9]+)/,
    fetch: async (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      // Pattern: "hls":{"url":"https:\/\/rumble.com\/hls-vod\/...playlist.m3u8"}
      const hls = html.match(/"hls":\{"url":"(https?:[^"]+playlist\.m3u8)"/);
      if (hls) {
        const url = hls[1].replace(/\\\//g, '/');
        return { success: true, links: [{ url, quality: 0 }] };
      }
      return { success: false, reason: 'No HLS found' };
    }
  },

  // --- StreamRuby ---
  'streamruby': {
    name: 'StreamRuby / StreamWish',
    urlRegex: /rubyvidhub\.com\/embed-([a-zA-Z0-9]+)\.html/,
    fetch: async (url) => {
      const id = url.match(/embed-([a-zA-Z0-9]+)\.html/)?.[1];
      const cmd = `curl -s -L --max-time 15 -A "${UA}" \\
        -H "Origin: https://rubyvidhub.com" \\
        -H "Referer: https://rubyvidhub.com/" \\
        -X POST -d "op=embed&file_code=${id}&auto=1&referer=" \\
        "https://rubyvidhub.com/dl" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      // Look for m3u8 in any form
      const m3u8 = html.match(/https?:\/\/[^"'\\ ]+\.m3u8[^"'\\ ]*/);
      if (m3u8) return { success: true, links: [{ url: m3u8[0], quality: 0 }] };
      // Try file: pattern (unpacked JS)
      const file = html.match(/file:\s*["']([^"']+\.m3u8[^"']*)["']/);
      if (file) return { success: true, links: [{ url: file[1], quality: 0 }] };
      return { success: false, reason: 'No m3u8 found (may need proper unpacker or rate-limited)' };
    }
  },

  // --- Dailymotion ---
  'dailymotion': {
    name: 'Dailymotion',
    urlRegex: /dailymotion\.com\/embed\/video\/([a-zA-Z0-9]+)/,
    fetch: async (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      // Dailymotion uses geo.dailymotion.com or direct embed
      const m3u8 = html.match(/https?:\/\/[^"']+\.m3u8[^"']*/);
      if (m3u8) return { success: true, links: [{ url: m3u8[0], quality: 0 }] };
      return { success: false, reason: 'No m3u8 found' };
    }
  },

  // --- DoodStream ---
  'doodstream': {
    name: 'DoodStream / doods.pro',
    urlRegex: /dood[s]?\.pro\/e\/([a-zA-Z0-9]+)/,
    fetch: async (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      // DoodStream has pass/token in URL
      const pass = html.match(/\/pass([^"]*)/)?.[1];
      const token = html.match(/token=([^&"&']*)/)?.[1];
      if (token) {
        const videoUrl = `https://doods.pro/download?token=${token}${pass ? '&pass=' + pass : ''}`;
        return { success: true, links: [{ url: videoUrl, quality: 0 }] };
      }
      const m3u8 = html.match(/https?:\/\/[^"']+\.m3u8[^"']*/);
      if (m3u8) return { success: true, links: [{ url: m3u8[0], quality: 0 }] };
      return { success: false, reason: 'No token/m3u8 found' };
    }
  },

  // --- Voe ---
  'voe': {
    name: 'Voe',
    urlRegex: /voe\.(?:sx|com)\/e\/([a-zA-Z0-9]+)/,
    fetch: async (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      // Try unpack
      const unpacked = unpackJsFromHtml(html) || html;
      const hls = unpacked.match(/['"]hls['"]\s*:\s*['"]([^'"]+)['"]/);
      if (hls) {
        const decodedUrl = hls[1].replace(/\\\//g, '/');
        return { success: true, links: [{ url: decodedUrl, quality: 0 }] };
      }
      return { success: false, reason: 'No HLS found' };
    }
  },

  // --- Rumble ---
  'rumble': {
    name: 'Rumble',
    urlRegex: /rumble\.com\/embed\/([a-zA-Z0-9]+)/,
    fetch: async (url) => {
      const cmd = `curl -s -L --max-time 15 -A "${UA}" "${url}" 2>/dev/null`;
      return execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    },
    extract: (html) => {
      const m3u8 = html.match(/https?:\/\/[^"']+\.m3u8[^"']*/);
      if (m3u8) return { success: true, links: [{ url: m3u8[0], quality: 0 }] };
      return { success: false, reason: 'No m3u8 found' };
    }
  }
};

// ========================================
// Utility: Simple JS unpacker (for packed eval)
// ========================================
function unpackJs(packed) {
  // Simplified: just extract the string
  try {
    // Remove eval( wrapper
    let code = packed.replace(/^eval\(/, '').replace(/\)$/, '');
    return code;
  } catch {
    return null;
  }
}

function unpackJsFromHtml(html) {
  const match = html.match(/eval\(function\(p,a,c,k,e,[rd]\)\{.*?\}\)/s);
  return match ? unpackJs(match[0]) : null;
}

// ========================================
// Main Test Runner
// ========================================
async function main() {
  console.log('=== Extractor Tester ===\n');

  const db = JSON.parse(fs.readFileSync(URL_DB, 'utf8'));
  const results = [];

  for (const group of db.extractors) {
    console.log(`\n── ${group.group} ──`);

    // Find matching pattern
    const pattern = EXTRACTOR_PATTERNS[group.group];
    if (!pattern) {
      console.log(`  ⚠️  No pattern defined — skipping`);
      continue;
    }

    for (const url of group.urls) {
      // Small delay to avoid rate limiting
      await new Promise(r => setTimeout(r, 1000));
      console.log(`  Testing: ${url}`);
      console.log(`    Pattern: ${pattern.name}`);

      try {
        const html = await pattern.fetch(url);
        const htmlSize = html.length;

        if (htmlSize < 100) {
          console.log(`    ❌ Blocked (HTML: ${htmlSize} bytes)`);
          results.push({ group: group.group, url, status: 'BLOCKED', links: 0 });
          continue;
        }

        const result = pattern.extract(html);

        if (result.success && result.links.length > 0) {
          console.log(`    ✅ ${result.links.length} links found`);
          result.links.slice(0, 2).forEach((l, i) => {
            console.log(`      [${i + 1}] ${l.quality}p → ${l.url.substring(0, 60)}...`);
          });
          results.push({ group: group.group, url, status: 'WORKING', links: result.links });
        } else {
          console.log(`    ❌ ${result.reason}`);
          results.push({ group: group.group, url, status: 'NO_MATCH', links: 0, reason: result.reason });
        }
      } catch (e) {
        console.log(`    ❌ Error: ${e.message}`);
        results.push({ group: group.group, url, status: 'ERROR', links: 0, error: e.message });
      }
    }
  }

  // Generate report
  const working = results.filter(r => r.status === 'WORKING').length;
  const failed = results.filter(r => r.status !== 'WORKING').length;

  let report = `# 🧪 Extractor Pattern Test Report\n\n`;
  report += `**Generated:** ${new Date().toISOString().split('T')[0]}\n`;
  report += `**Total URLs Tested:** ${results.length}\n`;
  report += `**Working:** ${working} | **Failed:** ${failed}\n\n`;
  report += `| Extractor | URL | Status | Links |\n`;
  report += `|-----------|-----|--------|-------|\n`;

  for (const r of results) {
    const icon = r.status === 'WORKING' ? '✅' : r.status === 'BLOCKED' ? '⚠️' : '❌';
    const linkCount = Array.isArray(r.links) ? r.links.length : r.links;
    const note = r.reason ? ` (${r.reason})` : r.error ? ` (${r.error})` : '';
    report += `| ${r.group} | ${r.url.substring(0, 50)} | ${icon} ${r.status} | ${linkCount}${note} |\n`;
  }

  fs.mkdirSync(path.dirname(REPORT_FILE), { recursive: true });
  fs.writeFileSync(REPORT_FILE, report);

  console.log(`\n\n=== Report: ${REPORT_FILE} ===`);
  console.log(`✅ Working: ${working}/${results.length}`);
  console.log(`❌ Failed: ${failed}/${results.length}`);
}

main().catch(e => {
  console.error('Fatal error:', e.message);
  process.exit(1);
});
