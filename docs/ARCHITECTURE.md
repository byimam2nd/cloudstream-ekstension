# 🏗️ Architecture Overview

Dokumentasi arsitektur OCE (Open Cloudstream Extensions).

## 📊 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     CLOUDSTREAM 3 APP                        │
│                   (Android Application)                      │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ Loads .cs3 plugins
                         │ from repository
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   PLUGIN REPOSITORY                          │
│  (builds branch - plugins.json + .cs3 files)                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ Served via repo.json
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    OCE REPOSITORY                            │
│  (github.com/byimam2nd/oce)                                 │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌──────────────┐    │
│  │  Anichin    │    │  Animasu    │    │  Samehadaku  │    │
│  │  (.cs3)     │    │  (.cs3)     │    │  (.cs3)      │    │
│  └─────────────┘    └─────────────┘    └──────────────┘    │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌──────────────┐    │
│  │Donghuastream│    │  Idlix      │    │  LayarKaca21 │    │
│  │  (.cs3)     │    │  (.cs3)     │    │  (.cs3)      │    │
│  └─────────────┘    └─────────────┘    └──────────────┘    │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐                        │
│  │Pencurimovie │    │Funmovieslix │                        │
│  │  (.cs3)     │    │  (.cs3)     │                        │
│  └─────────────┘    └─────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

## 🧩 Master-Sync Architecture

### Core Concept: "Centralized Intelligence, Distributed Execution"

```
┌─────────────────────────────────────────────────────────────┐
│                    MASTER FILES (Brain)                      │
│                                                              │
│  master/                                                     │
│  ├── MasterAutoUsed.kt        (367 lines - Constants, utils) │
│  ├── MasterCaches.kt          (500 lines - Caching system)   │
│  ├── MasterCircuitBreaker.kt  (200 lines - Failure isolation)│
│  ├── MasterCompiledRegex.kt   (350 lines - 319+ patterns)    │
│  ├── MasterExtractorHelper.kt (300 lines - Extractor utils)  │
│  ├── MasterExtractors.kt      (2400 lines - 75+ extractors)  │
│  ├── MasterHttpClient.kt      (387 lines - HTTP factory)     │
│  ├── MasterMonitors.kt        (500 lines - Monitoring)       │
│  └── MasterUtils.kt           (450 lines - Rate limit, etc)  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ sync-all-masters.sh
                         │ (CI/CD or manual)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│               GENERATED_SYNC (per Provider)                  │
│                                                              │
│  {Provider}/generated_sync/                                  │
│  ├── SyncAutoUsed.kt          ← MasterAutoUsed.kt           │
│  ├── SyncCaches.kt            ← MasterCaches.kt             │
│  ├── SyncCircuitBreaker.kt    ← MasterCircuitBreaker.kt     │
│  ├── SyncCompiledRegex.kt     ← MasterCompiledRegex.kt      │
│  ├── SyncExtractorHelper.kt   ← MasterExtractorHelper.kt    │
│  ├── SyncExtractors.kt        ← MasterExtractors.kt         │
│  ├── SyncHttpClient.kt        ← MasterHttpClient.kt         │
│  ├── SyncMonitors.kt          ← MasterMonitors.kt           │
│  └── SyncUtils.kt             ← MasterUtils.kt              │
└─────────────────────────────────────────────────────────────┘
```

### Sync Process:

```
1. Read master/MasterUtils.kt
2. Replace "package com.{MODULE}" → "package com.Anichin.generated_sync"
3. Replace "import com.{MODULE}" → "import com.Anichin.generated_sync"
4. Write to Anichin/generated_sync/SyncUtils.kt
5. Repeat for all 8 providers
```

## 🔄 CI/CD Pipeline

```
┌──────────────┐
│   git push   │
│   (master)   │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────┐
│  JOB 1: SYNC (7-10 seconds)          │
│                                      │
│  1. Checkout repository              │
│  2. Detect active modules            │
│  3. Run sync-all-masters.sh          │
│  4. Verify synced files              │
│  5. Commit & push generated_sync     │
└──────┬───────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│  JOB 2: BUILD (2-3 minutes)          │
│                                      │
│  1. Checkout src + builds branches   │
│  2. Setup JDK 17, Android SDK        │
│  3. ./gradlew make                   │
│  4. ./gradlew makePluginsJson        │
│  5. Copy .cs3 to builds/             │
│  6. Push to builds branch            │
└──────────────────────────────────────┘
```

## 🏗️ Provider Architecture

### Provider Structure:

```
{Provider}/
├── build.gradle.kts                 # Module configuration
└── src/main/kotlin/com/{Provider}/
    ├── {Provider}.kt               # Main API implementation
    ├── {Provider}Plugin.kt         # @CloudstreamPlugin registration
    └── generated_sync/             # Auto-generated (DO NOT EDIT!)
        ├── SyncAutoUsed.kt
        ├── SyncCaches.kt
        ├── SyncCircuitBreaker.kt
        ├── SyncCompiledRegexPatterns.kt
        ├── SyncExtractorHelper.kt
        ├── SyncExtractors.kt
        ├── SyncHttpClientFactory.kt
        ├── SyncMonitors.kt
        └── SyncUtils.kt
```

### Provider Types:

| Provider | Category | Complexity | Key Features |
|----------|----------|------------|--------------|
| **Anichin** | Anime | Medium | Standard anime site patterns |
| **Animasu** | Anime | Medium | Episode list parsing |
| **Samehadaku** | Anime | Medium | Download link extraction |
| **Donghuastream** | Donghua | Low | Standard donghua site |
| **Pencurimovie** | Movie/Anime | **High** | Deep resolver, AES decryption, domain learning |
| **LayarKaca21** | Movie/TV | Medium | External API integration |
| **Funmovieslix** | Movie/Anime | Medium | Category-based navigation |
| **Idlix** | Movie/TV/Anime | **High** | JSON API search, AJAX with nonce, AES decryption |

## 🔑 Key Design Patterns

### 1. **DRY Pattern**
```kotlin
// ❌ BAD: Duplicate code in each provider
private suspend fun rateLimit() {
    delay(500)
}

// ✅ GOOD: Use shared utility
rateLimitDelay(moduleName = "ProviderName")
```

### 2. **Auto-Used Pattern**
```kotlin
// MasterAutoUsed.kt provides utilities that require ZERO imports after sync
// Functions are directly available in the provider's package namespace

// Available after sync (no import needed):
rateLimitDelay()
executeWithRetry()
getRandomUserAgent()
logDebug()
```

### 3. **Circuit Breaker Pattern**
```
Extractor fails 3+ times
    → CircuitBreaker state: CLOSED → OPEN
    → Auto-skip for 1 minute
    → After timeout: OPEN → HALF_OPEN
    → Try once: success → CLOSED, fail → OPEN
```

### 4. **Factory Pattern (HTTP Client)**
```kotlin
// Creates optimized HTTP clients with:
// - HTTP/2 support
// - DNS caching with TTL
// - Connection pooling
// - Session-based User-Agent rotation
val client = MasterHttpClientFactory.create(providerName)
```

### 5. **Strategy Pattern (Extractors)**
```
loadExtractorWithFallback(url, referer, ...)
    ↓
1. Try CloudStream's loadExtractor()
    ↓ (if failed)
2. Find matching extractor from SyncExtractors
    ↓
3. Try with CircuitBreaker protection
    ↓
4. Return true if any extractor worked
```

## 📈 Performance Metrics

### Caching Strategy:
| Cache Type | TTL | Use Case |
|------------|-----|----------|
| **searchCache** | 5 minutes | Search results |
| **mainPageCache** | 3 minutes | Main page listings |
| **loadCache** | 10 minutes | Episode/page data |
| **ImageCache** | Disk only | Poster images |
| **PersistentCache** | 24 hours | Long-term data |

### Optimizations (v3.9):
- **Page Load (cached):** 500ms → **50ms** (90% faster)
- **Page Load (fresh):** 2000ms → **1000ms** (50% faster)
- **Image Loading:** 2000ms → **400ms** (80% faster)
- **Cold Start:** 500ms → **170ms** (66% faster)
- **Cache HIT Rate:** ~50% → **~95%**

## 🔐 Security

### Secrets Management:
- **NEVER** commit secrets to repository
- Use GitHub Secrets for CI/CD
- Use `local.properties` for local development
- All secrets are excluded via `.gitignore`

### Code Security:
- No hardcoded credentials
- User-Agent rotation for anonymity
- Rate limiting to prevent abuse
- Circuit breaker for failure isolation

## 📊 File Size Analysis

| Component | Lines | Purpose |
|-----------|-------|---------|
| MasterExtractors.kt | ~2,400 | 75+ video extractors |
| MasterCaches.kt | ~500 | Caching system |
| MasterMonitors.kt | ~500 | Monitoring & metrics |
| MasterUtils.kt | ~450 | Rate limiting, retry, logging |
| MasterHttpClient.kt | ~387 | HTTP client factory |
| MasterAutoUsed.kt | ~367 | Constants, utilities |
| MasterCompiledRegex.kt | ~350 | Pre-compiled patterns |
| MasterExtractorHelper.kt | ~300 | Extractor utilities |
| MasterCircuitBreaker.kt | ~200 | Failure isolation |
| **Total Master** | **~5,454** | |

## 🎯 Future Improvements

### Planned:
- [ ] **Testing Infrastructure** - JUnit 5 + MockK
- [ ] **Split MasterExtractors.kt** - Modular by type
- [ ] **Performance Monitoring** - Real-time metrics
- [ ] **Documentation** - KDoc for all public functions
- [ ] **Code Quality** - ktlint + detekt integration (✅ Done)

### Technical Debt:
- [ ] SeaTV uses old loadExtractor() pattern
- [ ] PreFetch domain matching bug
- [ ] No test coverage (0%)
- [ ] MasterExtractors.kt too large (2,400 lines)

---

**Last Updated:** 2026-04-04  
**Version:** v3.9
