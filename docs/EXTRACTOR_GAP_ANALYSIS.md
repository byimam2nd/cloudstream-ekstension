# Extractor Gap Analysis

**Tanggal:** 2026-04-13
**Status:** Analysis complete, ready for implementation

## Metodologi

1. **List 8 providers user:** Anichin, Animasu, Donghuastream, Funmovieslix, Idlix, LayarKaca21, Pencurimovie, Samehadaku
2. **Cari matching providers** di Phisher dan ExtCloud
3. **Extract semua extractors** dari matching providers tersebut
4. **Bandingkan** dengan MasterExtractors.kt user
5. **Identifikasi missing & outdated**

## Matching Providers

| Provider User | ExtCloud Match | Phisher Match |
|---------------|---------------|---------------|
| Anichin | AnichinMoe | - |
| Animasu | Animasu | - |
| Donghuastream | - | Donghuastream |
| Funmovieslix | Funmovieslix | Funmovieslix |
| Idlix | IdlixProvider | IdlixProvider |
| LayarKaca21 | LayarKacaProvider | LayarKacaProvider |
| Pencurimovie | Pencurimovie | Pencurimovie |
| Samehadaku | Samehadaku | - |

## Extractors Status

### Sudah Ada (13) ✅
- Dailymotion, Odnoklassniki, Rumble, StreamRuby, Vidguardto
- Archivd, Newuservideo, Vidhidepro
- Dhtpre, Jeniusplay, Hownetwork
- Dingtezuni, Dsvplay, Hglink

### Missing dari MasterExtractors.kt (16) ❌

| Extractor | Source | Provider | Priority |
|-----------|--------|----------|----------|
| **VideyV2** | Phisher/Funmovieslix | Funmovieslix | 🔴 HIGH |
| **ByseSX** | Phisher/Funmovieslix | Funmovieslix | 🔴 HIGH |
| **Ryderjet** | ExtCloud/Funmovieslix + Phisher/Funmovieslix | Funmovieslix | 🟡 MEDIUM |
| **Vidhideplus** | ExtCloud/Funmovieslix + Phisher/Funmovieslix | Funmovieslix | 🟡 MEDIUM |
| **EmturbovidExtractor** | ExtCloud/Layarasia | LayarKaca21 | 🟡 MEDIUM |
| **BuzzServer** | ExtCloud/Layarasia | LayarKaca21 | 🟢 LOW |
| **Nunap2p/Nunastrp/Nunaupns/Nunaxyz** | ExtCloud/Layarasia | LayarKaca21 | 🟢 LOW |
| **Dhtpre** | ExtCloud/Funmovieslix | Funmovieslix | 🟡 MEDIUM (check if updated) |
| **F75s** | ExtCloud/Funmovieslix | Funmovieslix | 🟢 LOW |
| **PlayStreamplay** | Phisher/Donghuastream | Donghuastream | 🟢 LOW |
| **Ultrahd** | Phisher/Donghuastream | Donghuastream | 🟢 LOW |
| **Vtbe** | Phisher/Donghuastream | Donghuastream | 🟢 LOW |
| **wishfast** | Phisher/Donghuastream | Donghuastream | 🟢 LOW |

## Implementation Plan

### Phase 1: Urgent (VideyV2 + ByseSX)
- Fix Funmovieslix untuk film "Tron: Ares"
- Source: Phisher/Funmovieslix/Extractors.kt

### Phase 2: Funmovieslix extractors
- Ryderjet, Vidhideplus, Dhtpre, F75s
- Source: ExtCloud/Funmovieslix + Phisher/Funmovieslix

### Phase 3: LayarKaca21 extractors
- EmturbovidExtractor, BuzzServer, Nuna* family
- Source: ExtCloud/Layarasia

### Phase 4: Donghuastream extractors
- PlayStreamplay, Ultrahd, Vtbe, wishfast
- Source: Phisher/Donghuastream
