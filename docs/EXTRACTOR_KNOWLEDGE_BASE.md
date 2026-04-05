# 🧪 Extractor Knowledge Base

**Purpose:** Dokumentasi referensi untuk memperbaiki extractor di MasterExtractors.kt.

**Sumber Referensi:**
- `/cloudstream/ExtCloud/` — 134 files (open source)
- `/cloudstream/phisher/` — 386 files (open source)
- `/oce/master/MasterExtractors.kt` — Extractor milik Anda (75+)

**Cara Pakai:**
1. Test extractor Anda → `node tests/extractors/test-extractors.js`
2. Lihat hasil → mana yang gagal
3. Buka dokumentasi ini → cari pattern yang sesuai
4. Fix extractor Anda dengan referensi dari ExtCloud/Phisher

---

## 📋 Table of Contents

1. [Extractor Anatomy](#extractor-anatomy)
2. [HTTP Patterns](#http-patterns)
3. [Video URL Extraction](#video-url-extraction)
4. [Quality Mapping](#quality-mapping)
5. [Common Pitfalls](#common-pitfalls)
6. [Best Practices](#best-practices)
7. [Reference Extractors](#reference-extractors)
8. [Verified Test URLs](#verified-test-urls)

---

## 🔬 Extractor Anatomy

### Struktur Minimal Extractor

```kotlin
class MyExtractor : ExtractorApi() {
    override val name = "MyExtractor"
    override val mainUrl = "https://example.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. Fetch embed page
        // 2. Extract video URL dari HTML/script
        // 3. callback.invoke(ExtractorLink(...))
    }
}
```

### Pola Inheritance (DRY)

```kotlin
// Base extractor
open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://rubyvidhub.com"
    override val requiresReferer = true

    override suspend fun getUrl(...) { /* implementation */ }
}

// Child extractors — hanya override name + mainUrl
class svanila : StreamRuby() {
    override var name = "svanila"
    override var mainUrl = "https://streamruby.net"
}

class svilla : StreamRuby() {
    override var name = "svilla"
    override var mainUrl = "https://streamruby.com"
}
```

**Referensi:** `ExtCloud/AnichinMoe/ExtractorsStreamRuby.kt`

---

## 🌐 HTTP Patterns

### Pattern 1: Simple GET + Regex

```kotlin
val response = app.get(url, referer = referer).text
val videoUrl = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(response)?.groupValues?.get(1)
```

**Digunakan di:** StreamRuby, VidGuard, Dailymotion

### Pattern 2: POST + Packed JS Unpack

```kotlin
val response = app.post("$mainUrl/dl", data = mapOf(
    "op" to "embed",
    "file_code" to id,
    "auto" to "1",
    "referer" to "",
), referer = referer)

val script = if (!getPacked(response.text).isNullOrEmpty()) {
    getAndUnpack(response.text)
} else {
    response.document.selectFirst("script:containsData(sources:)")?.data()
}
```

**Digunakan di:** StreamRuby, Filemoon, DoodStream

### Pattern 3: Headers + Unicode Decode (OK.ru)

```kotlin
val headers = mapOf(
    "Accept" to "*/*",
    "Connection" to "keep-alive",
    "Sec-Fetch-Dest" to "empty",
    "Sec-Fetch-Mode" to "cors",
    "Sec-Fetch-Site" to "cross-site",
    "Origin" to mainUrl,
    "User-Agent" to USER_AGENT,
)

val videoReq = app.get(embedUrl, headers = headers).text
    .replace("\\&quot;", "\"")
    .replace("\\\\", "\\")
    .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
        Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
    }
```

**Penting:** Tanpa decode unicode `\u0026` → `&`, URL video jadi invalid!

**Referensi:** `ExtCloud/AnichinMoe/ExtractorsOkru.kt`

### Pattern 4: Document Parsing + iframe

```kotlin
val document = app.get(url, referer = referer).document
val iframeUrl = document.selectFirst("iframe")?.attr("src")
    ?.let(::httpsify)
```

### Pattern 5: JSON API

```kotlin
val json = app.post("$apiUrl/api", json = mapOf(
    "id" to videoId
)).parsedSafe<Response>()
```

---

## 🎬 Video URL Extraction

### M3U8 Playlist

```kotlin
val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1)
callback.invoke(newExtractorLink(
    source = this.name,
    name = this.name,
    url = m3u8.toString(),
    type = ExtractorLinkType.M3U8
) {
    this.referer = mainUrl
})
```

### Direct MP4

```kotlin
val mp4 = Regex("src:\\s*\"(.*?\\.mp4.*?)\"").find(script)?.groupValues?.getOrNull(1)
callback.invoke(newExtractorLink(
    source = this.name,
    name = this.name,
    url = mp4.toString(),
    type = INFER_TYPE
) {
    this.quality = getQualityFromName("720p")
})
```

### Multi-Quality (OK.ru Pattern)

```kotlin
val videosStr = Regex(""""videos":(\[[^]]*])""").find(videoReq)?.groupValues?.get(1)
val videos = tryParseJson<List<OkRuVideo>>(videosStr)

for (video in videos) {
    val quality = video.name.uppercase()
        .replace("MOBILE", "144p")
        .replace("LOWEST", "240p")
        .replace("LOW", "360p")
        .replace("SD", "480p")
        .replace("HD", "720p")
        .replace("FULL", "1080p")

    callback.invoke(newExtractorLink(
        source = this.name, name = this.name, url = video.url, type = INFER_TYPE
    ) {
        this.quality = getQualityFromName(quality)
        this.referer = "$mainUrl/"
    })
}
```

---

## 📊 Quality Mapping

### Standard Quality Values

| Quality Name | Value (px) | Common Aliases |
|-------------|-----------|----------------|
| 144p | 144 | MOBILE, mobile |
| 240p | 240 | LOWEST, lowest |
| 360p | 360 | LOW, low |
| 480p | 480 | SD, sd |
| 720p | 720 | HD, hd |
| 1080p | 1080 | FULL, full, FHD |
| 1440p | 1440 | QUAD, 2K |
| 4K | 2160 | ULTRA, 4k, 2160p |

---

## ⚠️ Common Pitfalls

### 1. Missing Unicode Decode

**Problem:** OK.ru encode `&` sebagai `\u0026` dalam URL. Tanpa decode, URL jadi invalid.

**Fix:**
```kotlin
.replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
    Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
}
```

### 2. Wrong Headers

**Problem:** Tanpa `Origin` header, OK.ru return HTML error page.

**Fix:**
```kotlin
val headers = mapOf("Origin" to mainUrl, "User-Agent" to USER_AGENT)
```

### 3. Double Escape Issues

**Problem:** `\\&quot;` vs `\"` vs `"`. Harus decode bertahap.

**Fix:**
```kotlin
.replace("\\&quot;", "\"")   // Step 1
.replace("\\\\", "\\")       // Step 2
.replace(unicodeRegex)       // Step 3
```

### 4. Packed JavaScript

**Problem:** Video URL ada dalam packed JS (`eval(function(p,a,c,k,e,d){...})`).

**Fix:**
```kotlin
val script = if (!getPacked(response.text).isNullOrEmpty()) {
    getAndUnpack(response.text)
} else {
    response.document.selectFirst("script:containsData(sources:)")?.data()
}
```

### 5. Video URL Validation

**Problem:** Extractor return tanpa link tapi tidak ada error.

**Fix:**
```kotlin
val videosStr = Regex("""...""").find(videoReq)?.groupValues?.get(1)
    ?: throw ErrorLoadingException("Video not found")
```

---

## ✅ Best Practices

### DO:

```kotlin
// ✅ Use USER_AGENT bawaan CloudStream
"User-Agent" to USER_AGENT

// ✅ Use tryParseJson untuk JSON parsing
val videos = tryParseJson<List<Video>>(jsonStr) ?: throw ErrorLoadingException("...")

// ✅ Validate sebelum return
if (videoUrl.isEmpty()) return

// ✅ Set referer
this.referer = "$mainUrl/"

// ✅ Use INFER_TYPE untuk auto-detect type
type = INFER_TYPE
```

### DON'T:

```kotlin
// ❌ Hardcode User-Agent
"User-Agent" to "Mozilla/5.0 ..."

// ❌ Throw generic exception
throw Exception("Error")

// ❌ Return tanpa link (silent failure)
return

// ❌ Missing referer
this.referer = ""
```

---

## 📋 Extractor Checklist

Sebelum commit extractor baru:

- [ ] `name` deskriptif dan unik
- [ ] `mainUrl` dengan protocol (`https://`)
- [ ] Headers minimal: `User-Agent`, `Origin`, `Referer`
- [ ] Error handling: throw `ErrorLoadingException` dengan pesan jelas
- [ ] URL validation: cek `videoUrl.isNotEmpty()`
- [ ] Quality mapping: gunakan `getQualityFromName()`
- [ ] Referer set: `this.referer = "$mainUrl/"`
- [ ] Test minimal 2 URLs valid
- [ ] Unicode decode jika perlu (`\\uXXXX`)
- [ ] Packed JS unpack jika perlu (`getAndUnpack()`)

---

**Last Updated:** 2026-04-05
**Status:** Active
