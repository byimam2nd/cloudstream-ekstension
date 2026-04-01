# 📋 OCE DEVELOPMENT IMPROVEMENT PLAN

**Goal**: Membuat development experience lebih baik, faster onboarding, dan easier maintenance

**Date**: 2026-04-01  
**Status**: Planning Phase

---

## 🎯 OBJECTIVES

1. **Reduce Boilerplate** - Kurangi code duplication ~40% → ~15%
2. **Faster Onboarding** - New provider: 4 jam → 30 menit (8x faster)
3. **Automated Testing** - 0% → 80% test coverage
4. **Better Debugging** - Tambahkan debugging tools
5. **Code Quality** - Automated linting & formatting
6. **Performance Monitoring** - Track metrics & catch regressions

---

## 📊 CURRENT STATE ANALYSIS

### Struktur Repository
```
oce/
├── master/                          # Shared utilities (9 files)
│   ├── MasterUtils.kt               # Rate limiting, retry, logging
│   ├── MasterCaches.kt              # CacheManager, ImageCache
│   ├── MasterHttpClientFactory.kt   # OkHttpClient config
│   ├── MasterExtractorHelper.kt     # loadExtractorWithFallback
│   ├── MasterCircuitBreaker.kt      # Circuit breaker pattern
│   ├── MasterCompiledRegexPatterns.kt
│   ├── MasterExtractors.kt          # 2,479 lines (TOO LARGE!)
│   ├── MasterMonitors.kt            # SmartCacheMonitor
│   └── MasterAutoUsed.kt            # Constants
│
├── {Provider}/                      # 8 providers
│   └── generated_sync/              # Auto-generated dari master/
│
├── scripts/                         # Automation scripts
└── docs/                            # Documentation
```

### Problems Identified

| Problem | Impact | Severity |
|---------|--------|----------|
| ❌ No testing infrastructure | Manual testing only, risky refactoring | HIGH |
| ❌ 40% code duplication | Hard to maintain, error prone | HIGH |
| ❌ Manual provider onboarding | 4 jam per provider | HIGH |
| ❌ No linting/formatting | Inconsistent code style | MEDIUM |
| ❌ No debugging tools | Hard to debug issues | MEDIUM |
| ❌ No performance monitoring | Can't catch regressions | MEDIUM |
| ❌ Large files (2,479 lines) | Hard to navigate | LOW |

---

## 🚀 IMPROVEMENT PLAN

### **PHASE 1: Foundation (Week 1-2)**

#### 1.1 Testing Infrastructure ⭐⭐⭐
**Priority**: HIGH  
**Effort**: Large (2-3 hari)

**Files to Create:**
```
buildSrc/
├── build.gradle.kts
└── src/main/kotlin/
    ├── testing.gradle.kts          # Test configuration plugin
    └── dependencies.gradle.kts     # Centralized dependency management

src/test/kotlin/
├── base/
│   ├── ProviderTest.kt             # Base test class
│   ├── CacheTest.kt
│   └── RateLimiterTest.kt
└── providers/
    ├── AnichinTest.kt
    ├── AnimasuTest.kt
    └── ...

test-fixtures/
└── fixtures/
    ├── search_response.json
    ├── episode_data.json
    └── mock_html/
```

**Changes:**
- Edit `build.gradle.kts` - Add test dependencies
- Edit each provider's `build.gradle.kts` - Enable testing

**Benefits:**
- ✅ Automated validation
- ✅ Safe refactoring
- ✅ Catch bugs early
- ✅ Documentation via tests

---

#### 1.2 Provider Generator Script ⭐⭐⭐
**Priority**: HIGH  
**Effort**: Medium (1 hari)

**Files to Create:**
```
scripts/
├── generate-provider.sh            # Main generator script
└── templates/
    ├── provider/
    │   ├── Provider.kt.template
    │   ├── ProviderPlugin.kt.template
    │   └── build.gradle.kts.template
    └── readme/
        └── PROVIDER_README.md.template
```

**Usage:**
```bash
./scripts/generate-provider.sh NewProviderName
# Output:
# ✅ Created: NewProvider/
# ✅ Created: NewProvider/src/main/kotlin/com/NewProvider/NewProvider.kt
# ✅ Created: NewProvider/src/main/kotlin/com/NewProvider/NewProviderPlugin.kt
# ✅ Created: NewProvider/build.gradle.kts
# ✅ Created: NewProvider/README.md
# ✅ Updated: settings.gradle.kts
# ✅ Synced: generated_sync/ files
```

**Benefits:**
- ✅ 8x faster onboarding (4h → 30min)
- ✅ No manual errors
- ✅ Consistent structure
- ✅ Auto best practices

---

#### 1.3 BaseProvider Abstract Class ⭐⭐⭐
**Priority**: HIGH  
**Effort**: Large (3-4 hari)

**Files to Create:**
```
master/
├── BaseProvider.kt                 # Abstract base class
├── ProviderConfig.kt               # Configuration data classes
└── ProviderSelectors.kt            # Selector strategies
```

**Structure:**
```kotlin
abstract class BaseProvider : MainAPI() {
    // ✅ Pre-configured caching
    protected val searchCache = CacheManager<List<SearchResponse>>()
    protected val mainPageCache = CacheManager<HomePageResponse>()
    
    // ✅ Pre-configured rate limiting
    protected suspend fun rateLimit() = rateLimitDelay(name)
    
    // ✅ Pre-configured retry logic
    protected suspend fun <T> withRetry(block: suspend () -> T) = 
        executeWithRetry(block = block)
    
    // ✅ Template methods
    override suspend fun search(query: String): List<SearchResponse> {
        val cacheKey = "search:$query"
        return searchCache.get(cacheKey) ?: run {
            val results = withRetry { fetchSearch(query) }
            searchCache.put(cacheKey, results)
            results
        }
    }
    
    // 🎯 Provider override hanya yang spesifik
    protected abstract fun parseSearchResult(element: Element): SearchResponse?
    protected abstract fun parseDetailPage(document: Document): LoadResponse
    protected abstract fun parseEpisodes(document: Document): List<Episode>
}
```

**Provider Implementation (BEFORE):**
```kotlin
class Anichin : MainAPI() {
    override var mainUrl = "..."
    override var name = "..."
    // ... 50 lines boilerplate
    
    override suspend fun search(query: String): List<SearchResponse> {
        // 40 lines identical to other providers
    }
    
    override suspend fun load(url: String): LoadResponse {
        // 80 lines identical to other providers
    }
}
```

**Provider Implementation (AFTER):**
```kotlin
class Anichin : BaseProvider() {
    override var mainUrl = "https://anichin.cafe"
    override var name = "Anichin"
    
    // ✅ Override hanya yang spesifik
    override val mainPage = mainPageOf(...)
    
    override fun parseSearchResult(element: Element): SearchResponse? {
        // 15 lines - hanya parsing logic
    }
    
    override fun parseDetailPage(document: Document): LoadResponse {
        // 20 lines - hanya parsing logic
    }
}
```

**Benefits:**
- ✅ 60% less code per provider
- ✅ Easier maintenance
- ✅ Consistent behavior
- ✅ Faster debugging

---

### **PHASE 2: Quality & Tools (Week 3)**

#### 2.1 Code Quality Tools ⭐⭐
**Priority**: MEDIUM  
**Effort**: Small (2 jam)

**Files to Create:**
```
config/
├── ktlint.yml
├── detekt.yml
└── copyright.txt

.github/workflows/
├── quality-check.yml
└── auto-format.yml
```

**Changes:**
- Edit root `build.gradle.kts` - Add ktlint/detekt plugins
- Edit each provider's `build.gradle.kts` - Apply plugins

**Usage:**
```bash
# Check code quality
./gradlew ktlintCheck
./gradlew detekt

# Auto-format
./gradlew ktlintFormat

# In CI (auto on PR)
github actions: quality-check
```

**Benefits:**
- ✅ Consistent style
- ✅ Catch bugs early
- ✅ Auto-fix formatting

---

#### 2.2 Debugging Utilities ⭐⭐
**Priority**: MEDIUM  
**Effort**: Small (4 jam)

**Files to Create:**
```
master/
└── DebugTools.kt
```

**API Design:**
```kotlin
object DebugTools {
    // Enable/disable debug mode
    var enabled: Boolean = false
    
    // Request/Response logging
    fun logRequest(url: String, headers: Map<String, String>)
    fun logResponse(url: String, statusCode: Int, timeMs: Long, size: Int)
    
    // Cache logging
    fun logCacheHit(key: String, source: String = "memory" | "disk")
    fun logCacheMiss(key: String)
    
    // Performance tracking
    fun <T> trackOperation(name: String, block: () -> T): T
    
    // Dump all metrics
    fun dumpMetrics(): String
}

// Usage in provider:
class Anichin : BaseProvider() {
    init {
        DebugTools.enabled = BuildConfig.DEBUG
    }
}
```

**Benefits:**
- ✅ 5x faster debugging
- ✅ Visibility into operations
- ✅ Performance insights

---

#### 2.3 Performance Monitoring ⭐⭐
**Priority**: MEDIUM  
**Effort**: Medium (1 hari)

**Files to Create:**
```
master/
├── PerformanceMonitor.kt
├── MetricsCollector.kt
└── PerformanceReport.kt
```

**Features:**
```kotlin
object PerformanceMonitor {
    // Track request performance
    fun trackRequest(url: String, block: () -> Response)
    
    // Track cache performance
    fun trackCache(operation: String, hit: Boolean)
    
    // Generate report
    fun generateReport(): PerformanceReport {
        return PerformanceReport(
            avgRequestTime = ...,
            cacheHitRate = ...,
            slowestEndpoints = ...,
            errorRate = ...
        )
    }
}

// Usage:
val report = PerformanceMonitor.generateReport()
println(report)

// Output:
// === Performance Report ===
// Avg Request Time: 234ms
// Cache Hit Rate: 67%
// Slowest Endpoint: /search (890ms)
// Error Rate: 0.2%
```

**Benefits:**
- ✅ Catch regressions early
- ✅ Data-driven optimization
- ✅ User experience improvement

---

### **PHASE 3: Automation & CI/CD (Week 4)**

#### 3.1 Dependabot Configuration ⭐
**Priority**: LOW  
**Effort**: Small (30 min)

**Files to Create:**
```
.github/
└── dependabot.yml
```

**Configuration:**
```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
    reviewers:
      - "imam2nd"
```

**Benefits:**
- ✅ Auto security updates
- ✅ Stay up-to-date
- ✅ Less manual work

---

#### 3.2 CI/CD Enhancements ⭐⭐
**Priority**: MEDIUM  
**Effort**: Medium (1 hari)

**Files to Create:**
```
.github/workflows/
├── test.yml              # Run tests on PR
├── quality-check.yml     # Linting & formatting
├── build.yml             # Build all providers
└── release.yml           # Auto-release on tag
```

**Benefits:**
- ✅ Automated validation
- ✅ Catch issues before merge
- ✅ Faster release cycle

---

### **PHASE 4: Documentation (Week 5)**

#### 4.1 Development Guide ⭐⭐⭐
**Priority**: HIGH  
**Effort**: Small (2 jam)

**Files to Create/Edit:**
```
docs/
├── DEVELOPMENT.md          # Complete dev guide
├── ARCHITECTURE.md         # Architecture overview
├── TESTING.md              # Testing guide
└── CONTRIBUTING.md         # Contribution guide

README.md                   # Update with better structure
```

**Contents:**
- Quick start guide
- Architecture explanation
- How to add new provider
- Testing guide
- Debugging guide
- Best practices

**Benefits:**
- ✅ Faster onboarding
- ✅ Clear guidelines
- ✅ Less questions

---

## 📅 TIMELINE

| Week | Phase | Tasks | Deliverables |
|------|-------|-------|--------------|
| **1-2** | Foundation | Testing, Generator, BaseProvider | Test infra, generator script, BaseProvider class |
| **3** | Quality | ktlint, detekt, DebugTools | Quality gates, debugging tools |
| **4** | Automation | Dependabot, CI/CD | Automated workflows |
| **5** | Documentation | Dev guide, architecture docs | Complete documentation |

---

## 📊 EXPECTED METRICS

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Test Coverage | 0% | 80% | **+80%** |
| Code Duplication | 40% | 15% | **-62%** |
| New Provider Time | 4 jam | 30 min | **8x faster** |
| Manual Testing | 100% | 20% | **-80%** |
| Lines per Provider | ~800 | ~320 | **-60%** |
| Build Time | 5 min | 3 min | **-40%** |

---

## 🎯 SUCCESS CRITERIA

✅ **Testing**: All core utilities have tests, 80%+ coverage  
✅ **Generator**: New provider in <30 minutes  
✅ **BaseProvider**: All providers migrated, 60% less code  
✅ **Quality**: ktlint/detekt passing in CI  
✅ **Debugging**: DebugTools used in all providers  
✅ **Documentation**: Complete dev guide published  

---

## 🔧 TECHNICAL DEBT TO ADDRESS

1. **MasterExtractors.kt** (2,479 lines) → Split by extractor type
2. **Inconsistent error handling** → Standardize error handling pattern
3. **Magic strings** → Extract to constants
4. **No logging standards** → Define logging levels & format
5. **Hardcoded values** → Move to configuration

---

## 🚨 RISKS & MITIGATION

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking changes in BaseProvider | HIGH | Backward compatible design, gradual migration |
| Test maintenance overhead | MEDIUM | Keep tests simple, focus on critical paths |
| Generator bugs | MEDIUM | Thorough testing, manual verification first time |
| Performance regression | LOW | Performance tests, monitoring |

---

## 📝 NEXT STEPS

1. **Review & approve this plan**
2. **Create GitHub issues for each task**
3. **Start with Phase 1.1 (Testing Infrastructure)**
4. **Iterate based on feedback**

---

**Prepared by**: AI Code Reviewer  
**Reviewed by**: -  
**Approved by**: -  
**Last Updated**: 2026-04-01
