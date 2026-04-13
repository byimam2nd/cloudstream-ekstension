# Fix: Episode Number Parsing Bug

**Tanggal:** 2026-04-08
**Provider:** Anichin
**File:** `Anichin/src/main/kotlin/com/Anichin/Anichin.kt`

## Masalah

Episode dengan format desimal seperti "214.5" di-scrape menjadi "2145" (dua ribuan).

**Root cause:** Regex `[^0-9]` menghapus SEMUA karakter non-digit termasuk titik desimal.

```kotlin
// Code saat ini (SALAH)
val episodeNumber = episodeText.replace(Regex("[^0-9]"), "").toIntOrNull()

// "214.5" → "2145" → 2145 ❌
// "237 END" → "237" → 237 ✅
```

## Format Episode yang Perlu di-Handle

| Format | Contoh | Expected |
|--------|--------|----------|
| Integer | "237" | 237 |
| Dengan END | "237 END" | 237 |
| Desimal | "214.5" | 214 (floor) |
| Leading zero | "01" | 1 |
| Spasi | " 237 " | 237 |
| Kombinasi | "214.5 END" | 214 |

## Solusi

Ganti regex untuk extract angka pertama (integer atau desimal) dari string:

```kotlin
// Extract angka pertama dari text (support desimal)
val numberMatch = Regex("""(\d+(?:\.\d+)?)""").find(episodeText)
val episodeNumber = numberMatch?.groupValues?.get(1)
    ?.split(".")?.firstOrNull()?.toIntOrNull()
```

**Cara kerja:**
1. Regex `(\d+(?:\.\d+)?)` match angka integer atau desimal
2. Ambil group pertama (full match)
3. Split by "." dan ambil bagian integer (floor)
4. Convert ke Int

**Contoh:**
- `"214.5"` → match "214.5" → split ["214", "5"] → "214" → `214` ✅
- `"237 END"` → match "237" → split ["237"] → "237" → `237` ✅
- `"01"` → match "01" → split ["01"] → "01" → `1` ✅
- `"214.5 END"` → match "214.5" → split ["214", "5"] → "214" → `214` ✅

## File yang Perlu Diubah

1. `Anichin/src/main/kotlin/com/Anichin/Anichin.kt` - line ~297

## Testing

Test URL: `https://anichin.cafe/seri/soul-land-season-2/`

Verify: Episode numbers tampil dengan benar (tidak ada yang jadi ribuan).
