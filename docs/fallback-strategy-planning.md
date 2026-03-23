# 📋 Planning: Universal Fallback Strategy untuk Semua Selector

## 🎯 Objective
Menerapkan fallback strategy yang robust untuk SEMUA selector di SEMUA provider untuk mencegah error seperti:
- Poster tidak muncul
- URL tanpa scheme
- Selector tidak match
- Element tidak ditemukan

---

## 📊 Audit Provider (9 Sites Total)

### 1. **Anichin** ✅ (Sudah Fixed - Poster)
- [x] Poster selector (4-layer fallback)
- [ ] Title selector
- [ ] Episode selector
- [ ] Description selector
- [ ] Status selector

### 2. **Donghuastream** ✅ (Sudah Fixed - loadLinks URL)
- [x] URL scheme validation
- [ ] Poster selector
- [ ] Title selector
- [ ] Episode selector
- [ ] Description selector

### 3. **Funmovieslix** ✅ (Sudah Fixed - loadLinks)
- [x] Embed URL extraction (3 strategies)
- [x] URL scheme validation
- [ ] Poster selector
- [ ] Title selector
- [ ] Episode selector

### 4. **Idlix** ✅ (Sudah Fixed - Search)
- [x] Search API fallback to scraping
- [ ] Poster selector
- [ ] Title selector
- [ ] Episode selector
- [ ] Actor selector

### 5. **Animasu** (Perlu Audit)
- [ ] Poster selector
- [ ] Title selector
- [ ] Episode selector
- [ ] Description selector
- [ ] Status selector

### 6. **LayarKaca21** (Perlu Audit)
- [ ] Poster selector
- [ ] Title selector
- [ ] Episode selector
- [ ] Description selector
- [ ] Quality selector

### 7. **Pencurimovie** (Perlu Audit)
- [ ] Poster selector
- [ ] Title selector
- [ ] Episode selector
- [ ] Description selector
- [ ] Status selector

### 8. **Samehadaku** (Perlu Audit)
- [ ] Poster selector
- [ ] Title selector
- [ ] Episode selector
- [ ] Description selector
- [ ] Status selector

### 9. **ExtCloud Providers** (Reference - 30+ providers)
- Referensi untuk best practices

---

## 🔧 Universal Fallback Templates

### Template 1: Poster/Image Selector (5-Layer)
```kotlin
// 5-Layer Fallback Strategy untuk Poster
var poster = document.selectFirst("div.thumb img")?.attr("src")
    ?: document.selectFirst("div.thumb > img")?.attr("src")
    ?: document.selectFirst("img.ts-post-image")?.attr("src")
    ?: document.selectFirst("meta[property=og:image]")?.attr("content")
    ?: document.selectFirst("div.poster img")?.attr("src")
    ?: ""
```

### Template 2: Title Selector (4-Layer)
```kotlin
// 4-Layer Fallback Strategy untuk Title
val title = document.selectFirst("h1.entry-title")?.text()?.trim()
    ?: document.selectFirst("h1.title")?.text()?.trim()
    ?: document.selectFirst("meta[property=og:title]")?.attr("content")
    ?: document.selectFirst("div.title h1")?.text()?.trim()
    ?: "Unknown Title"
```

### Template 3: URL/Link Selector + Scheme Validation
```kotlin
// URL Scheme Validation + Fallback
var url = element.selectFirst("a")?.attr("href").orEmpty()
if (url.isNotEmpty()) {
    url = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http") -> url
        url.startsWith("/") -> mainUrl + url
        else -> "https://$url"
    }
}
```

### Template 4: Episode Selector (Multi-Selector)
```kotlin
// Multi-Selector Episode Strategy
val episodes = document.select(".eplister li")
    .ifEmpty { document.select(".episodelist li") }
    .ifEmpty { document.select("div.episode-list li") }
    .ifEmpty { document.select("ul.episodes li") }
    .mapNotNull { info ->
        val href = info.selectFirst("a")?.attr("href").orEmpty()
        val episodeNum = info.selectFirst(".epl-num")?.text()
            ?: info.selectFirst(".ep-num")?.text()
            ?: info.selectFirst(".episode-number")?.text()
            ?: "0"
        // ... process episode
    }
```

### Template 5: Description Selector (5-Layer)
```kotlin
// 5-Layer Fallback Strategy untuk Description
val description = document.selectFirst("div.entry-content")?.text()?.trim()
    ?: document.selectFirst("div.description")?.text()?.trim()
    ?: document.selectFirst("div.synopsis")?.text()?.trim()
    ?: document.selectFirst("div.plot")?.text()?.trim()
    ?: document.selectFirst("meta[name=description]")?.attr("content")
    ?: ""
```

### Template 6: Status Selector (Multi-Format)
```kotlin
// Multi-Format Status Detection
val statusText = document.select(".spe").text().lowercase()
    .ifEmpty { document.select(".status").text().lowercase() }
    .ifEmpty { document.select(".meta-status").text().lowercase() }

val showStatus = when {
    "ongoing" in statusText || "continuing" in statusText -> ShowStatus.Ongoing
    "completed" in statusText || "finished" in statusText -> ShowStatus.Completed
    "hiatus" in statusText -> ShowStatus.Hiatus
    else -> null
}
```

### Template 7: Quality Selector (Fallback)
```kotlin
// Quality Detection dengan Fallback
fun getSearchQuality(parent: Element): SearchQuality {
    val qualityText = parent.select("div.quality-badge").text().uppercase()
        .ifEmpty { parent.select(".quality").text().uppercase() }
        .ifEmpty { parent.select(".badge").text().uppercase() }
    
    return when {
        qualityText.contains("4K") -> SearchQuality.UHD
        qualityText.contains("BLURAY") -> SearchQuality.BlueRay
        qualityText.contains("WEB-DL") -> SearchQuality.WebRip
        qualityText.contains("HD") -> SearchQuality.HD
        else -> SearchQuality.HD
    }
}
```

---

## 📝 Implementation Checklist

### Phase 1: Critical Selectors (Priority: HIGH)
- [ ] **Anichin**: Complete all selectors (title, episode, description, status)
- [ ] **Donghuastream**: Complete all selectors (poster, title, episode, description)
- [ ] **Funmovieslix**: Complete all selectors (poster, title, episode)
- [ ] **Idlix**: Complete all selectors (poster, title, episode, actor)

### Phase 2: Remaining Providers (Priority: MEDIUM)
- [ ] **Animasu**: Audit + implement all fallback strategies
- [ ] **LayarKaca21**: Audit + implement all fallback strategies
- [ ] **Pencurimovie**: Audit + implement all fallback strategies
- [ ] **Samehadaku**: Audit + implement all fallback strategies

### Phase 3: Testing & Validation (Priority: HIGH)
- [ ] Test each provider with known working URLs
- [ ] Test each provider with edge cases (missing elements)
- [ ] Verify build passes for all providers
- [ ] Test on CloudStream app

### Phase 4: Documentation (Priority: LOW)
- [ ] Update README with fallback strategy documentation
- [ ] Create selector best practices guide
- [ ] Document common selector patterns per site type

---

## 🛠️ Implementation Steps

### Step 1: Audit Current Selectors
Untuk setiap provider file:
1. Identifikasi semua selector yang digunakan
2. Categorize by type (poster, title, episode, etc.)
3. Assess robustness (single selector vs fallback)
4. Mark for improvement

### Step 2: Implement Fallback
Untuk setiap selector yang perlu improvement:
1. Pilih template fallback yang sesuai
2. Customize untuk site-specific HTML structure
3. Add minimal 3-layer fallback
4. Add proper null handling (`?.` dan `?:`)

### Step 3: Testing
Untuk setiap perubahan:
1. Local test dengan curl/Jsoup
2. Commit perubahan
3. Push dan monitor GitHub Actions
4. Verify build success

### Step 4: Rollback Plan
Jika build gagal:
1. Cek error log
2. Fix compilation error
3. Commit fix dengan prefix `fix(Provider):`
4. Push dan re-test

---

## 📈 Success Metrics

- ✅ 100% providers menggunakan fallback strategy
- ✅ 0 build failures
- ✅ 0 runtime errors dari selector
- ✅ Poster muncul di semua content
- ✅ Episode list ter-load dengan benar
- ✅ Search results konsisten

---

## 🔗 References

- ExtCloud providers sebagai referensi best practices
- CloudStream documentation
- Jsoup selector documentation
- HTML structure variations per CMS (WordPress, etc.)

---

## 📅 Timeline Estimate

- Phase 1 (Critical): 1-2 hours
- Phase 2 (Remaining): 2-3 hours
- Phase 3 (Testing): 1 hour
- Phase 4 (Docs): 30 minutes

**Total: ~5-7 hours**

---

*Generated: 2026-03-23*
