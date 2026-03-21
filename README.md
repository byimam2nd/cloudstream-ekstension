# ☁️ CloudStream Extensions by imam2nd

**Shortcode:** `imam2ndrepo`

---

## 📥 Install

### **Method 1: CloudStream App (Recommended)**

1. Buka **CloudStream** app
2. Pergi ke **Settings** → **Extensions**
3. Klik **Add Repository**
4. Masukkan URL: `https://raw.githubusercontent.com/byimam2nd/cloudstream-ekstension/main/repo.json`
5. Install extensions yang diinginkan

### **Method 2: Manual Download**

1. Download `.cs3` file dari [GitHub Releases](https://github.com/byimam2nd/cloudstream-ekstension/releases)
2. Buka **CloudStream** → **Extensions**
3. Klik **Install .cs3 file**
4. Pilih file yang sudah di-download

---

## 📺 Available Extensions

### **Anime**

| Extension | Language | Status |
|-----------|----------|--------|
| **Anichin** | 🇮🇩 ID | ✅ Active |
| **Animasu** | 🇮🇩 ID | ✅ Active |
| **Samehadaku** | 🇮🇩 ID | ✅ Active |
| **Donghuastream** | 🇮🇩 ID | ✅ Active |

### **Movies & TV**

| Extension | Language | Status |
|-----------|----------|--------|
| **Pencurimovie** | 🇮🇩 ID | ✅ Active |
| **LayarKaca21** | 🇮🇩 ID | ✅ Active |
| **Funmovieslix** | 🇮🇩 ID | ✅ Active |
| **Idlix** | 🇮🇩 ID | ✅ Active |

---

## 🔧 Development

### **Quick Start**

```kotlin
// build.gradle.kts
version = 1

cloudstream {
    description = "My Extension"
    language = "id"
    authors = listOf("imam2nd")
    status = 1
    tvTypes = listOf("Anime")
    iconUrl = "https://example.com/icon.png"
}
```

```kotlin
// Main API
class MySite : MainAPI() {
    override var mainUrl = "https://mysite.com"
    override var name = "MySite"
    override val hasMainPage = true
    override var lang = "id"
    
    override val supportedTypes = setOf(TvType.Anime)
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}$page").document
        val items = document.select("div.anime").map { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }
    
    private fun Element.toSearchResult(): AnimeSearchResponse {
        val title = selectFirst("a[title]")?.attr("title").orEmpty()
        val href = fixUrl(selectFirst("a")?.attr("href").orEmpty())
        val poster = selectFirst("img")?.attr("src").orEmpty()
        
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }
}
```

### **Build**

```bash
# Build all extensions
./gradlew make

# Build specific module
./gradlew :Anichin:make

# Output: build/libs/Anichin-v1.cs3
```

---

## 📚 Documentation

- **Quick Start:** This file (README.md)
- **Extended Guide:** [docs/EXTENDED_GUIDE.md](docs/EXTENDED_GUIDE.md)
- **Stremio Addons:** [docs/README-StremioAddon.md](docs/README-StremioAddon.md)
- **Ultima Sync:** [docs/ULTIMA_SYNC_SETUP.md](docs/ULTIMA_SYNC_SETUP.md)

---

## 💖 Support

Jika project ini membantu, consider support:

- [Buy Me A Coffee](https://buymeacoffee.com/imam2nd)
- [Ko-fi](https://ko-fi.com/imam2nd)

---

## ⚖️ License

[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)

**Free software:** use, study, share, modify.

---

## ⚠️ Disclaimer

- **No content hosted** by this repository
- All content from **third-party websites**
- Users responsible for their usage
- Comply with local laws

---

**Maintained by:** imam2nd  
**Last Updated:** 2026-03-20  
**Status:** ✅ Production Ready
