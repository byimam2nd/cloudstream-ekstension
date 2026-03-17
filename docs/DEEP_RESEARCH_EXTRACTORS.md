# Riset Mendalam: Custom Extractor untuk Pencurimovie

## Status: RESEARCH PHASE

---

## 1. ANALISIS SERVER

### 1.1 Do7go.com

**URL Pattern:** `https://do7go.com/e/{VIDEO_ID}`

**Karakteristik:**
- Domain relatif baru (2024)
- Menggunakan Cloudflare protection
- Video player berbasis VideoJS
- Didukung oleh infrastruktur yang sama dengan Dhcplay

**Proteksi:**
```
✓ Cloudflare Turnstile (Medium)
✓ JavaScript Obfuscation (Medium)  
✓ Dynamic Token (Unknown)
✓ HLS Encryption (Yes - m3u8)
```

**Kemungkinan Pattern:**
```javascript
// Berdasarkan video hosting sejenis:
player.src({ src: "https://cdn.do7go.com/hls/VIDEO_ID/index.m3u8" })

// Atau:
var _0xabc = atob("base64_encoded_url");
player.load(_0xabc);
```

**Strategy:**
1. Fetch embed page dengan proper headers
2. Extract semua `<script>` tags
3. Cari pattern: `src:`, `file:`, `player.src`
4. Decode Base64 jika ada
5. Extract .m3u8 URL

---

### 1.2 Dhcplay.com

**URL Pattern:** `https://dhcplay.com/e/{VIDEO_ID}`

**Karakteristik:**
- Mirip dengan Do7go (infrastruktur sama)
- Menggunakan player yang serupa
- Mungkin share decryption logic

**Proteksi:**
```
✓ Cloudflare Turnstile (Medium)
✓ JavaScript Obfuscation (High)
✓ Dynamic Token (Unknown)
✓ HLS Encryption (Yes - m3u8)
```

**Kemungkinan Pattern:**
```javascript
// Kemungkinan menggunakan packed JavaScript:
eval(function(p,a,c,k,e,d){
    // Packed code
});

// Video URL di-obfuscate:
var video_url = CryptoJS.AES.decrypt(encrypted_data, "secret_key");
```

**Strategy:**
1. Unpack JavaScript (jika packed)
2. Find decryption function
3. Extract encryption key
4. Decrypt video URL
5. Return .m3u8

---

### 1.3 Listeamed.net

**URL Pattern:** `https://listeamed.net/e/{VIDEO_ID}`

**Karakteristik:**
- Paling kompleks dari 4 server
- Menggunakan token-based authentication
- API-driven player

**Proteksi:**
```
✓ Cloudflare Turnstile (High)
✓ JavaScript Obfuscation (High)
✓ Dynamic Token (Yes)
✓ HLS Encryption (Yes - m3u8)
```

**Kemungkinan Pattern:**
```javascript
// Token generation:
var token = generateToken(videoId, timestamp, secret);

// API call:
fetch('/api/v1/source/' + videoId + '?token=' + token)
    .then(r => r.json())
    .then(data => {
        player.load(data.hlsUrl);
    });
```

**Strategy:**
1. Extract token generation logic
2. Replicate token generation di Kotlin
3. Call API dengan valid token
4. Extract hlsUrl dari response
5. Return .m3u8

---

### 1.4 Voe.sx

**URL Pattern:** `https://voe.sx/e/{VIDEO_ID}`

**Karakteristik:**
- Sudah ada extractor di CloudStream bawaan
- Player baru (2025) tidak didukung
- Menggunakan HLS encryption

**Proteksi:**
```
✓ Cloudflare Turnstile (Medium)
✓ JavaScript Obfuscation (High)
✓ Dynamic Token (No)
✓ HLS Encryption (Yes - AES-128)
```

**Known Issues:**
- CloudStream Issue #1665: "Voe player baru tidak bekerja"
- Old player masih bekerja
- New player menggunakan encryption berbeda

**Kemungkinan Pattern (New Player):**
```javascript
// Voe new player (2025):
var config = JSON.parse(atob(encrypted_config));
var streamUrl = config.streamUrl; // .m3u8
var decryptionKey = config.key; // AES key

// HLS dengan AES-128:
#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="https://voe.sx/key/VIDEO_ID.key"
#EXTINF:10.0,
segment1.ts
```

**Strategy:**
1. Extract encrypted config
2. Decode Base64
3. Parse JSON config
4. Extract streamUrl + decryption key
5. Pass key ke CloudStream (jika support)

---

## 2. ADVANCED EXTRACTION TECHNIQUES

### 2.1 JavaScript Unpacking

**Problem:** Server menggunakan packed JavaScript

```javascript
eval(function(p,a,c,k,e,d){
    e=function(c){
        return(c.toString(36) || (c<52?'0':'') + c.toString(36))
    };
    // ... packed code
});
```

**Solution:** Implement unpacker di Kotlin

```kotlin
fun unpackJavaScript(packed: String): String? {
    // Regex untuk extract packed data
    val evalRegex = Regex("""eval\(function\(p,a,c,k,e,d\)\{([^}]+)\}\)""")
    val match = evalRegex.find(packed) ?: return null
    
    // Extract parameters
    val packedData = match.groupValues[1]
    
    // Unpack logic (simplified)
    // ... implement unpacking algorithm
    
    return unpackedCode
}
```

---

### 2.2 Base64 Decoding dengan Obfuscation

**Problem:** Base64 string di-obfuscate

```javascript
// Obfuscated Base64:
var _0x5a8b = ['Y', 'W', 'F', 's', 'Z', '3', 'N', '...'];
var decoded = atob(_0x5a8b.join(''));
```

**Solution:** Reconstruct string sebelum decode

```kotlin
fun decodeObfuscatedBase64(obfuscated: String): String {
    // Extract array elements
    val arrayRegex = Regex("""\['([^']+)'\s*,\s*'([^']+)'\s*,\s*...\]""")
    val match = arrayRegex.find(obfuscated)
    
    if (match != null) {
        val elements = match.groupValues.drop(1)
        val reconstructed = elements.joinToString("")
        return base64Decode(reconstructed)
    }
    
    return obfuscated
}
```

---

### 2.3 AES Decryption

**Problem:** Video URL di-encrypt dengan AES

```javascript
var encrypted = "U2FsdGVkX1+abc123...";
var decrypted = CryptoJS.AES.decrypt(encrypted, "secret-key").toString(CryptoJS.enc.Utf8);
```

**Solution:** Implement AES decryption di Kotlin

```kotlin
fun decryptAES(encrypted: String, key: String): String? {
    return try {
        // CryptoJS compatible decryption
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(), "AES")
        
        // Extract IV dari encrypted data (biasanya 16 bytes pertama)
        val iv = encrypted.substring(0, 16).toByteArray()
        val actualData = encrypted.substring(16).toByteArray()
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
        val decrypted = cipher.doFinal(actualData)
        String(decrypted)
    } catch (e: Exception) {
        null
    }
}
```

---

### 2.4 Token Generation

**Problem:** Server require token untuk akses video

```javascript
function generateToken(videoId, timestamp) {
    var data = videoId + timestamp + "secret_salt";
    var hash = MD5(data);
    return hash;
}
```

**Solution:** Replicate token generation di Kotlin

```kotlin
fun generateToken(videoId: String, secret: String): String {
    val timestamp = (System.currentTimeMillis() / 1000).toString()
    val data = videoId + timestamp + secret
    return md5(data)
}

fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
```

---

### 2.5 HLS Key Extraction

**Problem:** Video .m3u8 encrypted dengan AES-128

```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example.com/key.bin",IV=0x1234567890abcdef
#EXTINF:10.0,
segment0.ts
```

**Solution:** Extract key dan pass ke CloudStream

```kotlin
data class HLSPlaylist(
    val playlistUrl: String,
    val keyUrl: String?,
    val iv: String?
)

fun parseHLSPlaylist(playlistContent: String): HLSPlaylist {
    val keyRegex = Regex("""#EXT-X-KEY:METHOD=AES-128,URI="([^"]+)""")
    val ivRegex = Regex("""IV=0x([a-fA-F0-9]+)""")
    
    val keyMatch = keyRegex.find(playlistContent)
    val ivMatch = ivRegex.find(playlistContent)
    
    return HLSPlaylist(
        playlistUrl = extractPlaylistUrl(playlistContent),
        keyUrl = keyMatch?.groupValues?.get(1),
        iv = ivMatch?.groupValues?.get(1)
    )
}
```

---

## 3. COMMON PATTERNS DATABASE

### Pattern 1: Direct Source
```regex
player\.src\(\s*\{\s*src:\s*['"]([^'"]+)['"]
```

### Pattern 2: Sources Array
```regex
sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+)['"]
```

### Pattern 3: Base64 Encoded
```regex
atob\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*\)
```

### Pattern 4: AES Encrypted
```regex
CryptoJS\.AES\.decrypt\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*,\s*['"]([^'"]+)['"]
```

### Pattern 5: API Response
```regex
fetch\(\s*['"]([^']+/api/[^'"]+)['"]
```

### Pattern 6: Packed JavaScript
```regex
eval\(function\(p,a,c,k,e,d\)\{([^}]+)\}\)
```

---

## 4. TESTING FRAMEWORK

### Test URLs (perlu di-refresh setiap saat)
```kotlin
val testUrls = mapOf(
    "Do7go" to "https://do7go.com/e/{VIDEO_ID}",
    "Dhcplay" to "https://dhcplay.com/e/{VIDEO_ID}",
    "Listeamed" to "https://listeamed.net/e/{VIDEO_ID}",
    "Voe" to "https://voe.sx/e/{VIDEO_ID}"
)
```

### Validation Checklist
```kotlin
fun validateExtractorLink(link: ExtractorLink): Boolean {
    // URL harus valid
    if (!link.url.startsWith("http")) return false
    
    // URL harus .m3u8 atau .mp4
    if (!link.url.contains(".m3u8") && !link.url.contains(".mp4")) return false
    
    // Quality harus terdeteksi
    if (link.quality <= 0) return false
    
    // Type harus valid
    if (link.type !in listOf(ExtractorLinkType.VIDEO, ExtractorLinkType.M3U8)) return false
    
    return true
}
```

---

## 5. NEXT STEPS

### Phase 1: Data Collection (1-2 hari)
- [ ] Fetch fresh video URLs dari Pencurimovie
- [ ] Save HTML + JavaScript dari setiap server
- [ ] Document semua pattern yang ditemukan

### Phase 2: Pattern Analysis (1 hari)
- [ ] Identify encryption method untuk setiap server
- [ ] Find decryption keys/algorithms
- [ ] Document token generation logic (jika ada)

### Phase 3: Implementation (2-3 hari)
- [ ] Buat base extractor class
- [ ] Implement decryption helpers
- [ ] Buat extractor untuk setiap server

### Phase 4: Testing (1 hari)
- [ ] Test dengan multiple videos
- [ ] Validate semua extractor links
- [ ] Fix bugs dan edge cases

### Phase 5: Deployment (30 menit)
- [ ] Register extractors di provider
- [ ] Update version
- [ ] Push ke GitHub

---

## 6. TOOLS YANG DIPERLUKAN

### Development Tools
```
✓ Android Studio / IntelliJ IDEA
✓ Kotlin compiler
✓ CloudStream library dependencies
✓ Jsoup (HTML parsing)
✓ OkHttp (HTTP requests)
```

### Testing Tools
```
✓ Browser DevTools (Network tab)
✓ curl / wget (manual testing)
✓ Base64 decoder online
✓ JavaScript unpacker online
✓ HLS player (untuk test .m3u8)
```

### Debugging Tools
```
✓ Logging (Android Logcat)
✓ Network inspection (Proxyman / Charles)
✓ JavaScript console (browser)
```

---

## 7. KENDALA YANG DIANTISIPASI

### 7.1 Video URLs Expired
**Mitigation:**
- Refresh video URLs setiap saat dari Pencurimovie
- Gunakan video yang baru upload
- Test dengan multiple videos

### 7.2 Server Update Protections
**Mitigation:**
- Buat extractor dengan fallback patterns
- Monitor error logs
- Quick response dengan update

### 7.3 Encryption Too Complex
**Mitigation:**
- Fallback ke WebView (hanya Android)
- Gunakan proxy service
- Skip server yang terlalu kompleks

### 7.4 Cloudflare Blocking
**Mitigation:**
- Rotate User-Agents
- Add delays between requests
- Use proper Referer headers
- Consider proxy service

---

## 8. SUCCESS CRITERIA

Extractor dianggap berhasil jika:

✅ Dapat extract video URL dari 80%+ videos
✅ URL valid dan bisa diputar
✅ Quality terdeteksi dengan benar (360p, 720p, 1080p)
✅ Subtitle terdeteksi (jika ada)
✅ Tidak ada error di log
✅ Works cross-platform (jika isCrossPlatform = true)
✅ Maintainable dan easy to update

---

**Status: READY FOR IMPLEMENTATION**

**Next Action:** Mulai Phase 1 - Data Collection dengan fresh video URLs
