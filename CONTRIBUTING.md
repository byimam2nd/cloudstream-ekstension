# 🤝 Contributing to OCE

Terima kasih telah berkontribusi ke OCE (Open Cloudstream Extensions)! Panduan ini akan membantu Anda memulai.

## 📋 Table of Contents

- [Development Philosophy](#development-philosophy)
- [Getting Started](#getting-started)
- [Code Quality Standards](#code-quality-standards)
- [Development Workflow](#development-workflow)
- [Commit Message Guidelines](#commit-message-guidelines)
- [Pull Request Process](#pull-request-process)
- [Troubleshooting](#troubleshooting)

---

## 🧠 Development Philosophy

### Prinsip Utama:
1. **Shared utility → `master/`** ; **Provider-specific → provider folder**
2. **Core functionality → auto-used** (`MasterAutoUsed.kt`) ; **Optional → explicit import**
3. **Jangan pernah edit `generated_sync/`** langsung - edit master files saja
4. **Build setiap ada perubahan** - commit kecil, sering build
5. **Dokumentasi dulu, code kedua, test ketiga**

### Anti-Patterns (Hindari):
- ❌ **Over-engineering** - Keep it simple
- ❌ **Fighting sync workflow** - Kerja dalam constraint-nya
- ❌ **Premature optimization** - Measure first, optimize berdasarkan data
- ❌ **Documentation last** - Philosophy drives clarity

---

## 🚀 Getting Started

### Prerequisites:
- **JDK 17** atau lebih tinggi
- **Android SDK** (API 35)
- **Git** 2.30+
- **GitHub CLI** (`gh`) untuk deployment

### Setup:
```bash
# 1. Clone repository
git clone https://github.com/byimam2nd/oce.git
cd oce

# 2. Setup local.properties
cp local.properties.example local.properties
# Edit local.properties sesuai kebutuhan

# 3. Setup pre-commit hooks (recommended)
bash scripts/setup-hooks.sh

# 4. Build project
./gradlew make
```

### Project Structure:
```
oce/
├── master/                          # Single Source of Truth (9 files)
│   ├── MasterAutoUsed.kt           # Constants, helpers, utils
│   ├── MasterCaches.kt             # Caching system
│   ├── MasterCircuitBreaker.kt     # Failure isolation
│   ├── MasterCompiledRegexPatterns.kt  # Pre-compiled regex
│   ├── MasterExtractorHelper.kt    # Extractor utilities
│   ├── MasterExtractors.kt         # 75+ video extractors
│   ├── MasterHttpClientFactory.kt  # HTTP client factory
│   ├── MasterMonitors.kt           # Monitoring
│   └── MasterUtils.kt              # Rate limiting, retry, logging
│
├── {Provider}/                      # Provider modules (8 providers)
│   ├── src/main/kotlin/com/{Provider}/
│   │   ├── {Provider}.kt           # Main implementation
│   │   ├── {Provider}Plugin.kt     # Plugin registration
│   │   └── generated_sync/         # Auto-generated (JANGAN EDIT!)
│   └── build.gradle.kts
│
├── .github/workflows/ci-cd.yml     # CI/CD pipeline
├── config/detekt/detekt.yml        # Static analysis config
├── .editorconfig                    # Code style config
└── docs/                            # Documentation
```

---

## ✨ Code Quality Standards

### Automated Tools:
```bash
# Check code style
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat

# Static analysis
./gradlew detekt
```

### Key Standards:
- **Max line length:** 120 characters
- **Indentation:** 4 spaces (no tabs)
- **Naming:** camelCase untuk variables/functions, PascalCase untuk classes
- **Imports:** No wildcard imports (kecuali `java.util.*`)
- **Null safety:** Hindari `!!` operator, gunakan `?.` dan `?:`

### Code Review Checklist:
- [ ] Code mengikuti project conventions
- [ ] Tidak ada hardcoded secrets/credentials
- [ ] Error handling sudah adequate
- [ ] Logging informative (gunakan `logDebug`, `logError`)
- [ ] Performance impact minimal
- [ ] No breaking changes ke existing providers

---

## 🔄 Development Workflow

### Making Changes:

1. **Edit master files** (jika perubahan shared utility):
   ```bash
   # Edit master/MasterUtils.kt
   ```

2. **Edit provider files** (jika perubahan provider-specific):
   ```bash
   # Edit Anichin/src/main/kotlin/com/Anichin/Anichin.kt
   ```

3. **Sync master files** (jika edit master):
   ```bash
   bash scripts/sync-all-masters.sh
   ```

4. **Build specific provider**:
   ```bash
   ./gradlew :Anichin:make
   ```

5. **Test plugin** di CloudStream app

6. **Commit & push**:
   ```bash
   git add -A
   git commit -m "feat: description of change"
   git push origin master
   ```

### CI/CD Pipeline:
```
git push → Sync Job → Build Job → .cs3 artifacts pushed to builds branch
```

---

## 📝 Commit Message Guidelines

Gunakan [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <description>

[optional body]
```

### Types:
- `feat:` - Fitur baru
- `fix:` - Bug fix
- `refactor:` - Code refactoring (bukan fix atau feat)
- `docs:` - Dokumentasi
- `style:` - Formatting (no logic change)
- `test:` - Menambah/memperbaiki tests
- `chore:` - Maintenance tasks (CI/CD, dependencies)

### Examples:
```
feat: add Voe extractor with AES decryption
fix: resolve null pointer in loadExtractorWithFallback
refactor: split MasterExtractors into modular files
docs: add CONTRIBUTING.md with development guidelines
chore: update ktlint-gradle to version 14.2.0
```

---

## 🔀 Pull Request Process

### Before Submitting:
1. ✅ Run `./gradlew ktlintFormat` dan commit hasilnya
2. ✅ Run `./gradlew detekt` dan fix critical issues
3. ✅ Build semua providers: `./gradlew make`
4. ✅ Test di CloudStream app (jika possible)
5. ✅ Update dokumentasi jika ada breaking changes

### PR Description Template:
```markdown
## What does this PR do?
[Brief description]

## Why is this change needed?
[Reason/Context]

## Testing done:
- [ ] Built successfully with ./gradlew make
- [ ] Tested in CloudStream app
- [ ] No ktlint/detekt errors

## Breaking changes:
[Yes/No - jika ya, jelaskan]

## Related issues:
[Issue numbers jika ada]
```

---

## 🛠️ Troubleshooting

### Build fails dengan "Could not resolve..."
```bash
# Clean and rebuild
./gradlew clean make --refresh-dependencies
```

### Sync script error di Windows
```bash
# Gunakan Git Bash, bukan CMD/PowerShell
bash scripts/sync-all-masters.sh
```

### Java version mismatch
```bash
# Check Java version
java -version

# Set JAVA_HOME
export JAVA_HOME=/path/to/jdk-17
```

### Pre-commit hook terlalu lambat
```bash
# Skip hook (not recommended)
git commit --no-verify -m "your message"

# Atau disable hook sementara
rm .husky/pre-commit
```

---

## 📚 Additional Resources

- [Architecture Overview](docs/ARCHITECTURE.md)
- [Provider Analysis](docs/PROVIDER_ANALYSIS.md)
- [Code Examples](docs/CODE_EXAMPLES.md)
- [Improvement Plan](docs/IMPROVEMENT_PLAN.md)

---

## 💬 Need Help?

- **GitHub Issues:** Buat issue baru untuk bugs atau feature requests
- **Discussions:** Untuk pertanyaan atau diskusi umum
- **Email:** Contact maintainer langsung

---

**Thank you for contributing!** 🎉
