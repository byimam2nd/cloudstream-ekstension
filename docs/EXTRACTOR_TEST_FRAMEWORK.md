# 🧪 Extractor Testing Framework - Implementation Plan

**Goal:** Automated testing untuk 75+ extractors agar bisa detect broken extractor sebelum user complain.

**Date:** 2026-04-05
**Status:** Planning Phase

---

## 📋 Problem Statement

Saat ini 75+ extractors tidak punya automated test. Akibatnya:
- Extractor broken tidak terdeteksi sampai user report
- Tidak tahu URL mana yang masih valid atau expired
- Sulit maintain saat website target update struktur

---

## 🎯 Objectives

1. **Health Check** - Test semua extractor secara berkala
2. **Early Detection** - Detect broken extractor sebelum user complain
3. **URL Management** - Rotasi expired URLs otomatis
4. **Reporting** - Generate report yang mudah dibaca

---

## 📁 Struktur File

```
oce/
├── tests/
│   └── extractors/
│       ├── test-data/
│       │   └── extractor-urls.json          # URL database
│       ├── ExtractorHealthTest.kt            # Main test class
│       ├── ExtractorUrlRegistry.kt           # URL management
│       └── reports/
│           └── latest-report.md              # Generated report
│
├── .github/workflows/
│   └── extractor-health-check.yml            # Scheduled CI test
│
└── docs/
    └── EXTRACTOR_TEST_FRAMEWORK.md           # This file
```

---

## 🔧 Phase 1: URL Database

### File: `tests/extractors/test-data/extractor-urls.json`

```json
{
  "version": "1.0",
  "lastUpdated": "2026-04-05",
  "extractors": [
    {
      "group": "okru",
      "extractors": ["Odnoklassniki", "OkRuSSL", "OkRuHTTP"],
      "urls": [
        "https://ok.ru/videoembed/5989442652850",
        "https://ok.ru/videoembed/5557876755122"
      ],
      "expectedMinLinks": 1,
      "expectedType": "MP4",
      "notes": "OK.ru embed page"
    },
    {
      "group": "streamwish",
      "extractors": ["Do7go", "Dhcplay", "Hglink", "Ghbrisk", "Movearnpre", "Minochinos", "Mivalyo", "Ryderjet", "Bingezove", "Dingtezuni", "Dhtpre"],
      "urls": [
        "https://do7go.com/e/{ID}",
        "https://dhcplay.com/e/{ID}"
      ],
      "expectedMinLinks": 1,
      "expectedType": "M3U8",
      "notes": "Collect IDs from Anichin episodes"
    },
    {
      "group": "vidstack",
      "extractors": ["Listeamed", "Streamcasthub", "Dm21embed", "Dm21upns", "Pm21p2p", "Dm21", "Meplayer", "Fufaupns", "P2pplay", "Playerngefilm21", "Rpmvid"],
      "urls": [
        "https://listeamed.net/e/{ID}",
        "https://dm21.embed4me.vip/e/{ID}"
      ],
      "expectedMinLinks": 1,
      "expectedType": "M3U8",
      "notes": "Collect IDs from provider episodes"
    },
    {
      "group": "voe",
      "extractors": ["Voe"],
      "urls": [
        "https://voe.sx/e/{ID}",
        "https://voe.sx/e/{ID2}"
      ],
      "expectedMinLinks": 1,
      "expectedType": "M3U8",
      "notes": "Need to collect valid IDs"
    },
    {
      "group": "dailymotion",
      "extractors": ["Dailymotion"],
      "urls": [
        "https://www.dailymotion.com/embed/video/k1l9ujy1KqApITzcWnm",
        "https://www.dailymotion.com/embed/video/k4vm0zG3hIy4EYz4Vu1"
      ],
      "expectedMinLinks": 1,
      "expectedType": "M3U8",
      "notes": "Dailymotion embed pages"
    },
    {
      "group": "krakenfiles",
      "extractors": ["GDFlix"],
      "urls": [
        "https://krakenfiles.com/embed-video/{ID}"
      ],
      "expectedMinLinks": 0,
      "expectedType": "MP4",
      "notes": "STATUS: Domain may be down. Monitor for recovery.",
      "status": "degraded"
    },
    {
      "group": "vidguard",
      "extractors": ["Vidguard"],
      "urls": [
        "https://vidguard.to/e/{ID}",
        "https://bembed.net/e/{ID}"
      ],
      "expectedMinLinks": 1,
      "expectedType": "M3U8",
      "notes": "Need valid IDs from anime sites"
    },
    {
      "group": "doodstream",
      "extractors": ["DoodStream"],
      "urls": [
        "https://dsvplay.com/e/{ID}",
        "https://dood.ws/e/{ID}"
      ],
      "expectedMinLinks": 1,
      "expectedType": "M3U8",
      "notes": "Need valid IDs"
    },
    {
      "group": "megacloud",
      "extractors": ["Megacloud"],
      "urls": [
        "https://megacloud.blog/embed-2/e/{ID}"
      ],
      "expectedMinLinks": 1,
      "expectedType": "M3U8",
      "notes": "From anime streaming sites"
    },
    {
      "group": "rumble",
      "extractors": ["Rumble"],
      "urls": [
        "https://rumble.com/embed/{ID}"
      ],
      "expectedMinLinks": 1,
      "expectedType": "MP4",
      "notes": "Need valid IDs"
    }
  ]
}
```

---

## 🔧 Phase 2: Test Runner

### File: `tests/extractors/ExtractorHealthTest.kt`

```kotlin
/**
 * Extractor Health Check Test
 * 
 * Tests all 75+ extractors to ensure they can extract video links.
 * Run via: ./gradlew testExtractorHealth
 * 
 * Pass criteria: At least 1 of 2 URLs returns >= 1 ExtractorLink
 */
class ExtractorHealthTest {

    data class ExtractorResult(
        val extractorName: String,
        val url: String,
        val success: Boolean,
        val linksFound: Int,
        val duration: Long,
        val error: String?
    )

    data class ExtractorGroupReport(
        val groupName: String,
        val results: List<ExtractorResult>,
        val passed: Int,
        val failed: Int,
        val status: String // "working", "degraded", "broken"
    )

    /**
     * Test single extractor with given URL
     */
    private suspend fun testExtractor(
        extractor: ExtractorApi,
        url: String
    ): ExtractorResult {
        val startTime = System.currentTimeMillis()
        val links = mutableListOf<ExtractorLink>()
        val subtitles = mutableListOf<SubtitleFile>()
        var error: String? = null

        try {
            withTimeout(30_000L) {
                extractor.getUrl(
                    url = url,
                    referer = extractor.mainUrl,
                    subtitleCallback = { subtitles.add(it) },
                    callback = { links.add(it) }
                )
            }
        } catch (e: Exception) {
            error = "${e.javaClass.simpleName}: ${e.message}"
        }

        val duration = System.currentTimeMillis() - startTime

        return ExtractorResult(
            extractorName = extractor.name,
            url = url,
            success = links.isNotEmpty(),
            linksFound = links.size,
            duration = duration,
            error = error
        )
    }

    /**
     * Test all extractors in the database
     */
    suspend fun runAllTests(): List<ExtractorGroupReport> {
        val urlDb = loadExtractorUrls() // Load from JSON
        val reports = mutableListOf<ExtractorGroupReport>()

        for (group in urlDb.extractors) {
            val results = mutableListOf<ExtractorResult>()

            // Get extractor instance from SyncExtractors list
            val extractors = SyncExtractors.list.filter { 
                it.name in group.extractors 
            }

            for (extractor in extractors) {
                for (url in group.urls) {
                    if (url.contains("{ID}")) continue // Skip placeholder
                    val result = testExtractor(extractor, url)
                    results.add(result)
                }
            }

            val passed = results.count { it.success }
            val failed = results.count { !it.success }
            val status = when {
                failed == 0 -> "working"
                passed > 0 -> "degraded"
                else -> "broken"
            }

            reports.add(ExtractorGroupReport(
                groupName = group.group,
                results = results,
                passed = passed,
                failed = failed,
                status = status
            ))
        }

        return reports
    }

    /**
     * Generate markdown report
     */
    fun generateReport(reports: List<ExtractorGroupReport>): String {
        val totalExtractors = reports.sumOf { it.results.size }
        val totalPassed = reports.sumOf { it.passed }
        val totalFailed = reports.sumOf { it.failed }

        return buildString {
            appendLine("# Extractor Health Report")
            appendLine("Date: ${LocalDate.now()}")
            appendLine("Total: $totalExtractors tests, $totalPassed passed, $totalFailed failed")
            appendLine()

            // Working
            val working = reports.filter { it.status == "working" }
            if (working.isNotEmpty()) {
                appendLine("## ✅ WORKING (${working.size})")
                working.forEach { appendLine("- **${it.groupName}**: ${it.passed}/${it.results.size} OK") }
            }

            // Degraded
            val degraded = reports.filter { it.status == "degraded" }
            if (degraded.isNotEmpty()) {
                appendLine("## ⚠️  DEGRADED (${degraded.size})")
                degraded.forEach { report ->
                    appendLine("- **${report.groupName}**: ${report.passed}/${report.results.size} OK")
                    report.results.filter { !it.success }.forEach {
                        appendLine("  - ❌ ${it.extractorName}: ${it.error ?: "no links"}")
                    }
                }
            }

            // Broken
            val broken = reports.filter { it.status == "broken" }
            if (broken.isNotEmpty()) {
                appendLine("## ❌ BROKEN (${broken.size})")
                broken.forEach { report ->
                    appendLine("- **${report.groupName}**: ALL FAILED")
                    report.results.forEach {
                        appendLine("  - ❌ ${it.extractorName}: ${it.error ?: "no links"}")
                    }
                }
            }
        }
    }
}
```

---

## 🔧 Phase 3: GitHub Actions Workflow

### File: `.github/workflows/extractor-health-check.yml`

```yaml
name: Extractor Health Check

on:
  schedule:
    - cron: "0 6 * * 1"  # Every Monday 06:00 UTC
  workflow_dispatch:      # Manual trigger

jobs:
  test-extractors:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Run Extractor Tests
        run: ./gradlew testExtractorHealth

      - name: Generate Report
        run: |
          cat build/reports/extractor-health.md >> $GITHUB_STEP_SUMMARY

      - name: Create Issue if Broken
        if: env.BROKEN_COUNT > 0
        uses: actions/github-script@v7
        with:
          script: |
            const broken = process.env.BROKEN_EXTRACTORS
            await github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: `🚨 ${broken} extractors broken`,
              body: `See workflow summary for details`,
              labels: ['bug', 'extractor']
            })

      - name: Upload Report Artifact
        uses: actions/upload-artifact@v4
        with:
          name: extractor-health-report
          path: build/reports/extractor-health.md
```

---

## 📊 URL Collection Strategy

### Cara Mengumpulkan URL Valid

| Method | Extractors | Description |
|--------|-----------|-------------|
| **Scrape Anichin** | ~20 | Open 5 episodes, collect all embed URLs |
| **Scrape Samehadaku** | ~15 | Open 5 episodes, collect embed URLs |
| **Scrape Donghuastream** | ~10 | Open 3 episodes, collect embed URLs |
| **Manual** | ~30 | Visit extractor websites directly |
| **Auto-discover** | ~5 | Log all URLs during normal usage |

### Script Auto-Discovery

```bash
#!/bin/bash
# Extract all embed URLs from Anichin episodes
# Usage: ./scripts/collect-embed-urls.sh <episode-url>

EPISODE_URL=$1

curl -s -L -A "Mozilla/5.0" "$EPISODE_URL" | \
  grep -oP '<option[^>]*value="[^"]*"' | \
  sed 's/value="//;s/"$//' | \
  base64 -d 2>/dev/null | \
  grep -oP 'src="[^"]*"' | \
  sed 's/src="//;s/"$//' | \
  sort -u
```

---

## ⏱️ Timeline

| Phase | Task | Duration | Status |
|-------|------|----------|--------|
| **1** | Buat URL database + kumpulkan 50+ URLs | 2 jam | ⏳ Pending |
| **2** | Implement ExtractorHealthTest.kt | 1 jam | ⏳ Pending |
| **3** | Setup GitHub Actions workflow | 30 min | ⏳ Pending |
| **4** | Generate report + alert system | 1 jam | ⏳ Pending |
| **5** | Auto-rotate expired URLs | 1 jam | ⏳ Pending |

---

## 🚨 Limitations

1. **URL Expiration** - Video URLs dari beberapa host (OK.ru, KrakenFiles) expired dalam beberapa jam
2. **Rate Limiting** - Terlalu banyak request ke host yang sama bisa di-ban
3. **Geo-blocking** - Beberapa video hanya accessible dari region tertentu
4. **JS Rendering** - Beberapa extractor butuh JS execution yang tidak bisa di-curl
5. **Maintenance** - URL database perlu di-update berkala

---

## ✅ Success Criteria

- [ ] 50+ valid URLs collected untuk testing
- [ ] Test bisa detect broken extractor dalam < 5 menit
- [ ] Report otomatis digenerate setiap minggu
- [ ] GitHub issue otomatis dibuat jika ada extractor broken
- [ ] URL database auto-rotate expired URLs
