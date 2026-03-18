# Planning: Custom Extractor untuk Pencurimovie

## Masalah Saat Ini
- Error "tautan tidak ditemukan" pada beberapa film
- Server video (do7go, dhcplay, listeamed, voe) menggunakan proteksi Cloudflare + JavaScript encryption
- Extractor bawaan CloudStream (Hglink, Dsvplay) tidak support semua server

## Server yang Perlu Dicontek

| Server | Domain | Status | Proteksi |
|--------|--------|--------|----------|
| Do7go | do7go.com | ❌ Tidak dikenali | Cloudflare Turnstile + JS Encryption |
| Dhcplay | dhcplay.com | ❌ Tidak dikenali | Cloudflare + JS Obfuscation |
| Listeamed | listeamed.net | ❌ Tidak dikenali | Cloudflare + Token Auth |
| Voe | voe.sx | ⚠️ Partial support | Cloudflare + HLS Encryption |

---

## Phase 1: Research & Reverse Engineering (1-2 hari)

### 1.1 Analisis Setiap Server

**Untuk setiap server, lakukan:**

```bash
# 1. Fetch halaman embed
curl -sL -A "Mozilla/5.0" "https://do7go.com/e/VIDEO_ID"

# 2. Cari video source
- player.src() calls
- sources: [] arrays  
- .m3u8 URLs
- .mp4 direct links

# 3. Identifikasi proteksi
- Cloudflare Turnstile token
- Base64 encoded URLs
- JavaScript obfuscation patterns
```

### 1.2 Pattern yang Dicari

**Common patterns di video hosting:**

```javascript
// Pattern 1: player.src()
player.src({ src: "https://cdn.example.com/video.m3u8" })

// Pattern 2: sources array
sources: [{ file: "https://cdn.example.com/video.m3u8", label: "720p" }]

// Pattern 3: Base64 encoded
atob("aHR0cHM6Ly9jZG4uZXhhbXBsZS5jb20vdmlkZW8ubTN1OA==")

// Pattern 4: Packed JavaScript
eval(function(p,a,c,k,e,d){...})

// Pattern 5: API endpoint
fetch('/api/source/VIDEO_ID').then(r => r.json())
```

---

## Phase 2: Development Extractor (2-3 hari)

### 2.1 Struktur Extractor

**File: `PencurimovieExtractors.kt`**

```kotlin
package com.Pencurimovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import org.jsoup.nodes.Element

// ========================================
// EXTRACTOR 1: Do7go
// ========================================
class Do7go : ExtractorApi() {
    override val name = "Do7go"
    override val mainUrl = "https://do7go.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Step 1: Fetch embed page
        val document = app.get(url, referer = referer ?: mainUrl).document
        
        // Step 2: Extract encrypted data
        val script = document.selectFirst("script:containsData(player.src)")?.data()
            ?: document.selectFirst("script:containsData(sources))")?.data()
            ?: return
        
        // Step 3: Decrypt/Parse video URL
        val videoUrl = extractVideoUrl(script)
        
        // Step 4: Return ExtractorLink
        if (videoUrl != null) {
            callback.invoke(
                newExtractorLink(
                    name, name, videoUrl,
                    if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 
                    else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }
    
    private fun extractVideoUrl(script: String): String? {
        // TODO: Implement decryption logic
        // Regex patterns to try:
        // - src:\s*['"]([^'"]+)['"]
        // - file:\s*['"]([^'"]+)['"]
        // - sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+)['"]
        return null
    }
}

// ========================================
// EXTRACTOR 2: Dhcplay
// ========================================
class Dhcplay : ExtractorApi() {
    override val name = "Dhcplay"
    override val mainUrl = "https://dhcplay.com"
    override val requiresReferer = false

    override suspend fun getUrl(...) {
        // Similar structure, different decryption
    }
}

// ========================================
// EXTRACTOR 3: Listeamed
// ========================================
class Listeamed : ExtractorApi() {
    override val name = "Listeamed"
    override val mainUrl = "https://listeamed.net"
    override val requiresReferer = false

    override suspend fun getUrl(...) {
        // May require API call with token
    }
}

// ========================================
// EXTRACTOR 4: Voe
// ========================================
class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = false

    override suspend fun getUrl(...) {
        // Voe uses HLS encryption
        // Need to handle .m3u8 playlist
    }
}
```

### 2.2 Decryption Helper Functions

**File: `PencurimovieDecoders.kt`**

```kotlin
package com.Pencurimovie

import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object PencurimovieDecoders {
    
    // Decode Base64
    fun decodeBase64(encoded: String): String {
        return try {
            base64Decode(encoded)
        } catch (e: Exception) {
            encoded
        }
    }
    
    // Decode URL encoding
    fun decodeUrl(encoded: String): String {
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            encoded
        }
    }
    
    // AES decryption (common in video hosts)
    fun decryptAES(encrypted: String, key: String): String? {
        return try {
            val keySpec = SecretKeySpec(key.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = base64Decode(encrypted)
            val decrypted = cipher.doFinal(decoded.toByteArray())
            String(decrypted)
        } catch (e: Exception) {
            null
        }
    }
    
    // Extract video URL from JavaScript
    fun extractFromJS(script: String, pattern: String): String? {
        val regex = Regex(pattern)
        return regex.find(script)?.groupValues?.get(1)
    }
    
    // Common patterns
    object Patterns {
        const val SRC = """src:\s*['"]([^'"]+)['"]"""
        const val FILE = """file:\s*['"]([^'"]+)['"]"""
        const val SOURCES = """sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+)['"]"""
        const val PLAYER_SRC = """player\.src\(\s*\{\s*src:\s*['"]([^'"]+)['"]"""
        const val VIDEO_URL = """['"](https?://[^'"]+\.(?:m3u8|mp4)[^'"]*)['"]"""
    }
}
```

---

## Phase 3: Testing & Validation (1 hari)

### 3.1 Test Cases

**Test untuk setiap extractor:**

```kotlin
// Test URL (ganti dengan video ID yang valid)
val testUrls = mapOf(
    "Do7go" to "https://do7go.com/e/8p4crc4r7qis",
    "Dhcplay" to "https://dhcplay.com/e/migruefo3rea",
    "Listeamed" to "https://listeamed.net/e/PKN4OwzALeXEDLj",
    "Voe" to "https://voe.sx/e/mj4ynurrcj7i"
)

// Expected: ExtractorLink dengan URL valid
// - URL harus dimulai dengan https://
// - URL harus berisi .m3u8 atau .mp4
// - Quality harus terdeteksi (360p, 720p, 1080p)
```

### 3.2 Validation Checklist

- [ ] Extractor berhasil parse video URL
- [ ] URL valid (bisa diakses)
- [ ] Quality terdeteksi dengan benar
- [ ] Subtitle terdeteksi (jika ada)
- [ ] Tidak ada error di log
- [ ] Video bisa diputar di CloudStream app

---

## Phase 4: Integration (30 menit)

### 4.1 Update PencurimovieProvider

```kotlin
@CloudstreamPlugin
class PencurimovieProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())
        
        // Register ALL extractors
        registerExtractorAPI(Do7go())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(Voe())
        
        // Keep existing extractors
        registerExtractorAPI(Dsvplay())
        registerExtractorAPI(Hglink())
    }
}
```

### 4.2 Update build.gradle.kts

```kotlin
version = 9  // Increment version

cloudstream {
    // ... existing config
}
```

---

## Timeline Estimasi

| Phase | Durasi | Output |
|-------|--------|--------|
| 1. Research | 1-2 hari | Pattern decryption untuk setiap server |
| 2. Development | 2-3 hari | Extractor working code |
| 3. Testing | 1 hari | Validated extractors |
| 4. Integration | 30 menit | Ready to deploy |
| **Total** | **4-6 hari** | **Full working extractors** |

---

## Kendala yang Mungkin Dihadapi

### 1. Cloudflare Turnstile
**Solusi:** 
- Gunakan header yang benar (User-Agent, Referer)
- Mungkin perlu delay sebelum request
- Alternatif: Gunakan proxy/rotating IPs

### 2. JavaScript Obfuscation
**Solusi:**
- Reverse engineer packed JS
- Cari decryption key di script
- Gunakan regex patterns yang lebih kompleks

### 3. Dynamic Token Auth
**Solusi:**
- Extract token dari halaman embed
- Include token di API request
- Refresh token jika expired

### 4. HLS Encryption
**Solusi:**
- Extract .m3u8 playlist
- Parse key URI
- Decrypt segments (complex, mungkin perlu WebView)

---

## Alternatif Jika Gagal

Jika custom extractor terlalu kompleks:

1. **Gunakan AutoEmbedProvider** - Embedder pihak ketiga
2. **Gunakan WebView** - Execute JavaScript di Android WebView (hanya Android)
3. **Cari source alternatif** - Extension lain dengan film sama

---

## Next Steps

1. **Mulai Phase 1** - Research setiap server
2. **Document pattern** yang ditemukan
3. **Implement extractor** satu per satu
4. **Test** dengan video yang diketahui bekerja
5. **Deploy** ke GitHub

---

## Catatan Penting

- **Jangan hardcode video URLs** - Gunakan regex patterns
- **Handle errors gracefully** - Fallback ke loadExtractor
- **Log untuk debugging** - Tapi jangan di production
- **Test di multiple videos** - Pastikan konsisten
