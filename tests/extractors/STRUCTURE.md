# Extractor Testing Framework

## Struktur

```
tests/extractors/
├── test-data/
│   └── extractor-urls.json          # URL database untuk testing
├── STRUCTURE.md                     # This file
└── reports/                         # Generated reports
    └── latest-report.md
```

## Cara Kerja

1. **Load URL** dari `test-data/extractor-urls.json`
2. **Jalankan extractor** dengan setiap URL
3. **Cek hasil**: apakah extractor return >= `expectedMinLinks` ExtractorLink
4. **Generate report**: ✅ Working / ⚠️ Degraded / ❌ Broken

## Status Extractor

| Status | Kriteria |
|--------|----------|
| ✅ Working | Semua URLs berhasil, >= expectedMinLinks |
| ⚠️ Degraded | Sebagian URLs berhasil |
| ❌ Broken | Semua URLs gagal |
| ⏳ Pending | Belum ada URL valid |

## Phase 1: URL Collection (DONE ✅)

- [x] Buat extractor-urls.json
- [x] Kumpulkan URL valid untuk OK.ru (2 URLs)
- [x] Kumpulkan URL valid untuk Dailymotion (2 URLs)
- [x] Dokumentasi pattern extractor di Knowledge Base
- [ ] Lengkapi URL untuk extractor lain (perlu scraping dari episode pages)

## Phase 2: Test Runner (TODO)

- [ ] Buat `ExtractorHealthTest.kt`
- [ ] Setup Gradle task `testExtractorHealth`
- [ ] Generate markdown report

## Phase 3: CI/CD (TODO)

- [ ] Buat GitHub Actions workflow `extractor-health-check.yml`
- [ ] Schedule: weekly (Monday 06:00 UTC)
- [ ] Auto-create issue jika ada extractor broken
