# AnichinCafe - Cloudstream Extension

Extension untuk Cloudstream TV yang scrape dari **anichin.cafe** dan mirror domains.

## 📋 Informasi

- **Source**: [HiAnime](../HiAnime) (modified)
- **Website**: https://anichin.cafe
- **Language**: Indonesian (ID)
- **Content**: Anime & Donghua (SUB Indonesia)
- **Status**: ✅ Working

## 🌐 Mirror Domains

Extension ini support multiple domains untuk fallback:

1. `https://anichin.cafe` (Primary)
2. `https://anichin.team` (Backup)
3. `https://anichin.watch` (Streaming)
4. `https://anichin.top` (Backup)

## 🎯 Fitur

- ✅ Homepage dengan categories (Latest, Ongoing, Completed, Donghua, Popular)
- ✅ Search functionality
- ✅ Episode list dengan metadata
- ✅ Multi-extractor support
- ✅ MAL & AniList tracking
- ✅ TMDB metadata integration
- ✅ Chromecast support
- ✅ Download support

## 📺 Categories

- **Latest Episodes** - Update terbaru
- **Ongoing Anime** - Anime yang masih tayang
- **Completed Anime** - Anime tamat
- **Donghua** - Anime China (Bahasa Mandarin)
- **Popular Anime** - Anime populer

## 🔧 Technical Details

### Scraping Method
- **HTML Parsing** menggunakan Jsoup
- **AJAX calls** untuk episode list
- **Iframe extraction** untuk video sources

### Data Sources
- **Primary**: anichin.cafe HTML scraping
- **Metadata**: ani.zip API (MAL mapping)
- **Logos**: TMDB API

### Video Extractors
- Generic extractor (loadExtractor)
- Support untuk multiple video hosts
- Auto-detect m3u8/mp4 streams

## 📁 Structure

```
AnichinCafe/
├── build.gradle.kts              # Build configuration
├── src/main/
│   ├── AndroidManifest.xml
│   └── kotlin/com/AnichinCafe/
│       ├── AnichinCafe.kt         # Main scraping logic
│       ├── AnichinCafePlugin.kt   # Plugin registration & server list
│       └── AnichinCafeExtractors.kt  # Video extractors
```

## 🚀 Build Instructions

```bash
cd extention-cloudstream
./gradlew build
```

APK akan ada di: `AnichinCafe/build/outputs/apk/debug/`

## 📝 Changes from HiAnime

### Modified:
1. ✅ Base URL → anichin.cafe
2. ✅ HTML selectors → Anichin structure
3. ✅ Server list → Anichin domains
4. ✅ Removed DUB support (Anichin SUB only)
5. ✅ Simplified episode loading
6. ✅ Added Donghua category

### Removed:
- ❌ Complex actor scraping (not available on Anichin)
- ❌ Multi-server selection (Anichin uses single source)
- ❌ BottomSheet settings (not needed for basic usage)

### Added:
- ✅ Multiple domain fallback
- ✅ Donghua support
- ✅ Indonesian language tag

## ⚠️ Known Limitations

1. **No DUB** - Anichin hanya menyediakan SUB Indonesia
2. **HTML Scraping** - Rentan terhadap perubahan layout website
3. **No Official API** - Bergantung pada struktur HTML
4. **Rate Limiting** - Perlu delay untuk avoid blocking

## 🔐 Disclaimer

Extension ini hanya untuk tujuan edukasi. Konten yang diakses berasal dari website pihak ketiga. Developer tidak bertanggung jawab atas penggunaan extension ini.

## 📄 License

Same as parent HiAnime project and Cloudstream.

## 👥 Credits

- Original HiAnime: Stormunblessed, KillerDogeEmpire, RowdyRushya, Phisher98
- AnichinCafe Port: Based on HiAnime template
- Cloudstream Team: https://github.com/recloudstream

## 🐛 Troubleshooting

### Extension tidak muncul:
- Pastikan build berhasil
- Check `settings.gradle.kts` include project
- Restart Cloudstream app

### No streams found:
- Website mungkin down atau maintenance
- Coba ganti domain di settings
- Check internet connection

### Episodes tidak load:
- Struktur HTML mungkin berubah
- Update HTML selectors di `AnichinCafe.kt`
- Check CSS selectors di `toSearchResult()` dan `load()`

## 📞 Support

Untuk issues atau feature requests, buat issue di repository Cloudstream extensions.
