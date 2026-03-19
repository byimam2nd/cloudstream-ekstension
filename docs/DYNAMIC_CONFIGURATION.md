# ⚙️ Dynamic Configuration Guide

Panduan konfigurasi dinamis untuk cloudstream-ekstension repository.

---

## 🎯 Overview

Repository ini sekarang menggunakan **konfigurasi dinamis** untuk memudahkan maintenance dan customization.

### Keuntungan

✅ **Easy Updates** - Update versi dependency tanpa edit build.gradle.kts
✅ **Flexible** - Customize namespace, authors, SDK versions per environment
✅ **Safe** - Local config tidak ter-commit ke git
✅ **CI/CD Ready** - GitHub Actions override dengan env variables

---

## 📝 Configuration Hierarchy

Konfigurasi dibaca dengan prioritas:

```
1. Environment Variables (highest priority)
   ↓
2. local.properties
   ↓
3. Default values (lowest priority)
```

---

## 🔧 Setup

### 1. Create local.properties

```bash
# Copy template
cp local.properties.example local.properties

# Edit sesuai kebutuhan
nano local.properties
```

### 2. Available Configuration Options

#### **Repository Configuration**

| Property | Default | Description |
|----------|---------|-------------|
| `REPO_URL` | `https://github.com/phisher98/cloudstream-extensions-phisher` | Repository URL untuk cloudstream repo |
| `AUTHORS` | `Phisher98` | Comma-separated list of authors |

**Example:**
```properties
REPO_URL=https://github.com/myusername/my-repo
AUTHORS=MyName,Contributor1,Contributor2
```

---

#### **SDK Versions**

| Property | Default | Description |
|----------|---------|-------------|
| `MIN_SDK` | `21` | Minimum Android SDK version |
| `COMPILE_SDK` | `35` | Compile SDK version |
| `TARGET_SDK` | `35` | Target SDK version |

**Example:**
```properties
MIN_SDK=23
COMPILE_SDK=34
TARGET_SDK=34
```

---

#### **Java Version**

| Property | Default | Description |
|----------|---------|-------------|
| `JAVA_VERSION` | `1_8` | Java version (1_8, 11, 17, etc) |

**Example:**
```properties
JAVA_VERSION=11
```

---

#### **Dependency Versions**

| Property | Default | Description |
|----------|---------|-------------|
| `CLOUDSTREAM_VERSION` | `pre-release` | CloudStream app version |
| `KOTLIN_VERSION` | `stdlib` | Kotlin standard library |
| `NICEHTTP_VERSION` | `0.4.16` | NiceHttp library version |
| `JSOUP_VERSION` | `1.22.1` | JSoup HTML parser |
| `ANNOTATION_VERSION` | `1.9.1` | AndroidX annotation |
| `JACKSON_VERSION` | `2.20.1` | Jackson JSON parser |
| `COROUTINES_VERSION` | `1.10.2` | Kotlin coroutines |
| `RHINO_VERSION` | `1.9.0` | Rhino JavaScript engine |
| `FUZZYWUPPY_VERSION` | `1.4.0` | FuzzyWuzzy string matching |
| `GSON_VERSION` | `2.13.2` | Gson JSON parser |
| `SERIALIZATION_VERSION` | `1.9.0` | Kotlinx serialization |
| `JADB_VERSION` | `v1.2.1` | JADB Android debug bridge |
| `BC_VERSION` | `1.70` | BouncyCastle cryptography |

**Example:**
```properties
# Update all dependencies to latest
NICEHTTP_VERSION=0.4.17
JSOUP_VERSION=1.23.0
JACKSON_VERSION=2.21.0
```

---

#### **API Keys (Local Development Only)**

Untuk development lokal, tambahkan API keys:

```properties
TMDB_API=your_tmdb_api_key
DUMP_API=your_dump_api_key
ANICHI_API=your_anichi_api_key
# ... add other keys as needed
```

⚠️ **WARNING:** Jangan commit `local.properties` ke git! API keys untuk CI/CD gunakan GitHub Secrets.

---

## 🚀 Usage Examples

### Example 1: Update Dependency Versions

```bash
# Edit local.properties
nano local.properties

# Update versions
NICEHTTP_VERSION=0.4.17
JSOUP_VERSION=1.23.0

# Sync Gradle
./gradlew --refresh-dependencies

# Build
./gradlew build
```

### Example 2: Change Authors

```properties
# For personal fork
AUTHORS=YourName

# For team
AUTHORS=Person1,Person2,Person3
```

### Example 3: Custom SDK Versions

```properties
# Support older Android
MIN_SDK=19
COMPILE_SDK=33
TARGET_SDK=33
```

### Example 4: Different Repository

```properties
# For custom fork
REPO_URL=https://github.com/myusername/cloudstream-custom
```

---

## 🔄 GitHub Actions (CI/CD)

GitHub Actions **override** local.properties dengan environment variables:

```yaml
- name: Access Secrets
  env:
    TMDB_API: ${{ secrets.TMDB_API }}
    # ... other secrets
  run: |
    echo TMDB_API=$TMDB_API >> local.properties
    # ... add other keys
```

**Priority in CI/CD:**
1. GitHub Secrets (via env) → written to local.properties
2. local.properties defaults
3. build.gradle.kts defaults

---

## 📁 File Structure

```
cloudstream-ekstension/
├── build.gradle.kts              # Dynamic configuration logic
├── local.properties.example      # Template (commit-safe)
├── local.properties              # Your config (DO NOT COMMIT!)
└── .gitignore                    # Includes local.properties
```

---

## 🔒 Security

### What NOT to Commit

❌ **NEVER commit `local.properties`** - Contains API keys
❌ **NEVER commit hardcoded secrets** - Use GitHub Secrets

### What IS Safe to Commit

✅ `local.properties.example` - Template without real values
✅ `build.gradle.kts` - Configuration logic
✅ Documentation

---

## 🛠️ Troubleshooting

### Issue: Build fails after adding local.properties

**Solution:**
```bash
# Check syntax (no spaces around =)
# WRONG:
MIN_SDK = 21

# RIGHT:
MIN_SDK=21

# Refresh dependencies
./gradlew --refresh-dependencies
```

### Issue: Dependency version not updating

**Solution:**
```bash
# Force refresh
./gradlew clean build --refresh-dependencies --rerun-tasks

# Check version format (no quotes)
# WRONG:
NICEHTTP_VERSION="0.4.16"

# RIGHT:
NICEHTTP_VERSION=0.4.16
```

### Issue: Namespace conflict

**Solution:**
```properties
# Each module should have unique namespace
# Auto-generated from project name, but can override per module in module's build.gradle.kts
android {
    namespace = "com.custom.namespace"
}
```

---

## 📊 Comparison

| Configuration Method | Before | After |
|---------------------|--------|-------|
| Update dependency | Edit build.gradle.kts | Edit local.properties |
| Change authors | Hardcoded | Configurable |
| SDK versions | Fixed | Flexible per environment |
| API keys | Hardcoded or env only | local.properties + env |
| Fork customization | Manual edits | Simple config file |

---

## 🎓 Best Practices

1. **Use local.properties.example as template** - Document available options
2. **Keep local.properties in .gitignore** - Never commit secrets
3. **Use GitHub Secrets for CI/CD** - Secure API keys
4. **Document custom configs** - Help other contributors
5. **Test with default values** - Ensure build works without local.properties

---

## 📚 Related Documentation

- [EXTRACTOR_DOCUMENTATION.md](docs/EXTRACTOR_DOCUMENTATION.md) - Extractor system
- [EXTRACTOR_ARCHITECTURE.md](docs/EXTRACTOR_ARCHITECTURE.md) - Architecture
- [scripts/README.md](scripts/README.md) - Automation scripts

---

**Last Updated:** 2026-03-19
**Status:** ✅ Production Ready
