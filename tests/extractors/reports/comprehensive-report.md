# 🧪 Comprehensive Extractor Test Report

**Generated:** 2026-04-05
**Total URLs Tested:** 15
**Overall Score:** 67%

## Summary

| Status | Count | Percentage |
|--------|-------|------------|
| ✅ Working (≥70%) | 10 | 67% |
| ⚠️  Partial (1-69%) | 0 | 0% |
| ❌ Failed (0%) | 5 | 33% |

## Group Scores

| Extractor Group | Avg Score | URLs | Min | Max |
|----------------|-----------|------|-----|-----|
| ✅ okru | 100% | 4 | 100% | 100% |
| ⚠️ dailymotion | 50% | 4 | 0% | 100% |
| ✅ doodstream | 100% | 1 | 100% | 100% |
| ❌ streamruby | 0% | 3 | 0% | 0% |
| ✅ rumble | 100% | 3 | 100% | 100% |

## Detailed Results

| Extractor | URL | Score | Links | Issues |
|-----------|-----|-------|-------|--------|
| ✅ okru | https://ok.ru/videoembed/5989442652850 | 100% | 6 | - |
| ✅ okru | https://ok.ru/videoembed/5557876755122 | 100% | 6 | - |
| ✅ okru | https://ok.ru/videoembed/13158821399218 | 100% | 6 | - |
| ✅ okru | https://ok.ru/videoembed/13124981164722 | 100% | 6 | - |
| ❌ dailymotion | https://geo.dailymotion.com/player/xid0t.html | 0% | 0 | Error: Command failed: curl -s -L --max-time 15 -A "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" "https://geo.dailymotion.com/player/xid0t.html?video=k6W4CKqOutx6glFp5T2" 2>/dev/null |
| ✅ dailymotion | https://geo.dailymotion.com/player/xid0t.html | 100% | 1 | - |
| ✅ dailymotion | https://geo.dailymotion.com/player/xid0t.html | 100% | 1 | - |
| ❌ dailymotion | https://geo.dailymotion.com/player/xid0t.html | 0% | 0 | Extractor failed: No m3u8/cdn found |
| ✅ doodstream | https://doods.pro/e/b4ivd8wg8iwx | 100% | 1 | - |
| ❌ streamruby | https://rubyvidhub.com/embed-uu0enqwd51wl.htm | 0% | 0 | Extractor failed: No m3u8 found |
| ❌ streamruby | https://rubyvidhub.com/embed-fk3niu5m65ms.htm | 0% | 0 | Extractor failed: No m3u8 found |
| ❌ streamruby | https://rubyvidhub.com/embed-yk48r3fgjnuz.htm | 0% | 0 | Extractor failed: No m3u8 found |
| ✅ rumble | https://rumble.com/embed/v75uxgy/?pub=2li51c | 100% | 1 | - |
| ✅ rumble | https://rumble.com/embed/v75tbts/?pub=2li51c | 100% | 1 | - |
| ✅ rumble | https://rumble.com/embed/v75tc58/?pub=2li51c | 100% | 1 | - |

## Trend Comparison

| Metric | Previous | Current | Trend |
|--------|----------|---------|-------|
| Working URLs | 10 | 10 | ➡️ Stable |

## Recommendations

- **dailymotion**: Extractor bekerja sebagian. Cek URL yang gagal untuk pattern yang benar.
- **streamruby**: Extractor pattern perlu diperbaiki. Referensi: `/cloudstream/ExtCloud/` atau `/cloudstream/phisher/`
