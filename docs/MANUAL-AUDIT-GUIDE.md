# 🔧 Manual HTML Audit Guide (Tanpa Script)

Untuk provider dengan Cloudflare protection, lakukan manual audit:

---

## 📋 Step-by-Step Guide

### Step 1: Buka di Browser

1. Buka URL provider di browser (Chrome/Firefox)
2. Tunggu halaman load sepenuhnya
3. Klik kanan → "View Page Source" atau tekan `Ctrl+U`

### Step 2: Cari Selector

**Di View Source, cari:**

```
Ctrl+F → "poster"
Ctrl+F → "thumb"  
Ctrl+F → "img src"
Ctrl+F → "og:image"
```

### Step 3: Catat Selector

**Contoh untuk Samehadaku:**

Dari ExtCloud reference:
```kotlin
// Poster selector
val poster = document.selectFirst("div.thumb img")?.attr("src")

// Title selector  
val title = document.selectFirst("h1.entry-title")?.text()

// Episode selector
val episodes = document.select("div.lstepsiode ul li a")
```

### Step 4: Implementasi Fallback

```kotlin
// FIXED: Fallback strategy untuk poster (Samehadaku)
val poster = document.selectFirst("div.thumb img")?.attr("src")
    ?: document.selectFirst("meta[property=og:image]")?.attr("content")
    ?: document.selectFirst("img[data-src]")?.attr("data-src")
    ?: ""
```

---

## 🔍 Provider-Specific Selectors (dari ExtCloud)

### Samehadaku

```kotlin
// Main URL: https://v1.samehadaku.how
// Poster: div.thumb img
// Title: h1.entry-title
// Description: div.desc
// Episodes: div.lstepsiode ul li a
```

### Animasu

```kotlin
// Main URL: https://animasu.stream
// Poster: div.poster img
// Title: h1.title
// Description: div.description
// Episodes: div.episode-list a
```

### LayarKaca21

```kotlin
// Main URL: https://lk21.de
// Poster: meta[property=og:image]
// Title: div.movie-info h1
// Description: div.meta-info
// API-based: https://gudangvape.com/search.php
```

### Pencurimovie

```kotlin
// Main URL: https://pencurimovie.cam
// Poster: div.poster img
// Title: h1.entry-title
// Description: div.entry-content
// Episodes: div.episode-list a
```

---

## 📝 Template Implementation

```kotlin
override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document
    
    // Title (3-layer fallback)
    val title = document.selectFirst("h1.entry-title")?.text()?.trim()
        ?: document.selectFirst("h1.title")?.text()?.trim()
        ?: document.selectFirst("meta[property=og:title]")?.attr("content")
        ?: "Unknown Title"
    
    // Poster (4-layer fallback)
    val poster = document.selectFirst("div.thumb img")?.attr("src")
        ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        ?: document.selectFirst("img[data-src]")?.attr("data-src")
        ?: ""
    
    // Description (3-layer fallback)
    val description = document.selectFirst("div.entry-content")?.text()?.trim()
        ?: document.selectFirst("div.description")?.text()?.trim()
        ?: ""
    
    // ... continue implementation
}
```

---

## ✅ Verification Checklist

Setelah implementasi:

- [ ] Build passing
- [ ] No compilation errors
- [ ] Test di CloudStream app
- [ ] Poster muncul
- [ ] Title correct
- [ ] Description loaded
- [ ] Episodes listed

---

## 🎯 Quick Reference

| Provider | Poster Selector | Title Selector | Episode Selector |
|----------|----------------|----------------|------------------|
| Samehadaku | `div.thumb img` | `h1.entry-title` | `div.lstepsiode ul li a` |
| Animasu | `div.poster img` | `h1.title` | `div.episode-list a` |
| LayarKaca21 | `meta[property=og:image]` | `div.movie-info h1` | API-based |
| Pencurimovie | `div.poster img` | `h1.entry-title` | `div.episode-list a` |

---

*Last updated: 2026-03-24*
