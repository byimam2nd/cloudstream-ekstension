# 📜 Automation Scripts

Scripts untuk automasi development dan maintenance repository.

## 🚀 Quick Start

```bash
# Setup (buat scripts executable)
bash scripts/setup-scripts.sh

# Sync Master Extractors ke semua module
bash scripts/sync-extractors.sh

# Verify extractor files
bash scripts/verify-extractors.sh
```

---

## 📋 Available Scripts

### 1. `sync-extractors.sh`

**Fungsi:** Sync `docs/MasterExtractors.kt` ke semua active modules.

**Cara kerja:**
1. Deteksi module aktif (folder dengan `build.gradle.kts`)
2. Copy `MasterExtractors.kt` ke setiap module
3. Update package name sesuai module
4. Generate header comment

**Usage:**
```bash
bash scripts/sync-extractors.sh
```

**Output:**
```
========================================
📦 Sync Master Extractors
========================================

🔍 Detecting active modules (folders with build.gradle.kts)...

✅ Found 6 active module(s): Anichin Donghuastream Funmovieslix IdlixProvider LayarKaca21 Pencurimovie

📋 Syncing to Anichin (folder: com/Anichin, package: com.anichin)...
✅ Synced: Anichin/Anichin (39 extractor classes)

...

========================================
📊 Sync Summary
========================================
   Total modules: 6
   Synced: 6
   Errors: 0

✅ Sync completed successfully!
```

---

### 2. `verify-extractors.sh`

**Fungsi:** Verify bahwa semua module punya `Extractors.kt` yang valid.

**Cara kerja:**
1. Deteksi module aktif
2. Cek keberadaan file `Extractors.kt`
3. Hitung jumlah extractor classes
4. Hitung jumlah registered extractors di `AllExtractors.list`
5. Validasi konsistensi

**Usage:**
```bash
bash scripts/verify-extractors.sh
```

**Output:**
```
========================================
🔍 Verify Extractor Files
========================================

📋 Active modules: Anichin Donghuastream Funmovieslix IdlixProvider LayarKaca21 Pencurimovie

✅ Anichin: 39 classes, 39 registered
✅ Donghuastream: 39 classes, 39 registered
✅ Funmovieslix: 39 classes, 39 registered
✅ IdlixProvider: 39 classes, 39 registered
✅ LayarKaca21: 39 classes, 39 registered
✅ Pencurimovie: 39 classes, 39 registered

========================================
📊 Validation Summary
========================================
   Total modules: 6
   Valid extractors: 6
   Errors: 0
   Total extractor classes: 234

✅ Validation PASSED: All 6 module(s) have valid Extractors.kt

📋 Consistency Check:
   Each module should have the same number of extractor classes
   Expected: ~39 classes per module (from MasterExtractors.kt)

✅ Consistency check passed
```

---

### 3. `setup-scripts.sh`

**Fungsi:** Setup permissions untuk semua scripts.

**Usage:**
```bash
bash scripts/setup-scripts.sh
```

---

## 🔄 GitHub Actions Workflow

Workflow otomatis yang berjalan di GitHub Actions:

### Sync Extractors Workflow

**File:** `.github/workflows/sync-extractors.yml`

**Trigger:**
- Push ke `master` dengan perubahan `docs/MasterExtractors.kt`
- Pull request dengan perubahan `docs/MasterExtractors.kt`
- Manual trigger via `workflow_dispatch`

**Jobs:**
1. **Detect active modules** - Deteksi module aktif secara dinamis
2. **Sync Master Extractors** - Sync ke semua module aktif
3. **Verify extractor files** - Validasi hasil sync
4. **Commit and push** - Auto commit jika ada perubahan

**Keuntungan:**
- ✅ **Dinamis** - Hanya sync ke module yang ada (punya `build.gradle.kts`)
- ✅ **Auto-detect** - Tidak perlu hardcode module list
- ✅ **Validation** - Validasi otomatis setelah sync
- ✅ **Error handling** - Skip module yang tidak ada di config

**Module yang di-support:**
| Module | Folder | Package |
|--------|--------|---------|
| Pencurimovie | Pencurimovie | pencurimovie |
| LayarKaca21 | LayarKacaProvider | layarKacaProvider |
| Donghuastream | Donghuastream | donghuastream |
| Funmovieslix | Funmovieslix | funmovieslix |
| IdlixProvider | hexated | hexated |
| Anichin | Anichin | anichin |

---

## 🛠️ Development Workflow

### Menambah Module Baru

1. Buat folder module dengan struktur yang benar:
   ```
   NewModule/
   ├── build.gradle.kts
   └── src/main/kotlin/com/NewModule/
       ├── Extractors.kt
       ├── NewModule.kt
       └── NewModuleProvider.kt
   ```

2. Module akan otomatis terdeteksi oleh workflow sync (karena punya `build.gradle.kts`)

3. Tambahkan mapping ke workflow dan scripts:
   - `.github/workflows/sync-extractors.yml` - Tambah ke `FOLDER_CONFIGS` dan `FOLDER_MAP`
   - `scripts/sync-extractors.sh` - Tambah ke `FOLDER_CONFIGS`
   - `scripts/verify-extractors.sh` - Tambah ke `FOLDER_MAP`

4. Jalankan sync:
   ```bash
   bash scripts/sync-extractors.sh
   ```

5. Commit dan push:
   ```bash
   git add -A
   git commit -m "feat: add NewModule"
   git push
   ```

---

### Update Master Extractors

1. Edit `docs/MasterExtractors.kt`:
   - Tambah extractor class baru
   - Tambah ke `AllExtractors.list`
   - Update TOTAL comment

2. Sync ke semua module:
   ```bash
   bash scripts/sync-extractors.sh
   ```

3. Verify:
   ```bash
   bash scripts/verify-extractors.sh
   ```

4. Commit dan push:
   ```bash
   git add -A
   git commit -m "feat: add NewExtractor (40 total extractors)"
   git push
   ```

5. GitHub Actions akan otomatis sync dan build

---

## 🐛 Troubleshooting

### Module tidak terdeteksi

**Symptom:** Module tidak muncul di hasil detect

**Solution:**
```bash
# Cek apakah build.gradle.kts ada
ls -la ModuleName/build.gradle.kts

# Cek format build.gradle.kts
cat ModuleName/build.gradle.kts | head -5
```

### Extractors.kt tidak ter-generate

**Symptom:** Error "Extractors.kt not found"

**Solution:**
```bash
# Cek folder structure
ls -la ModuleName/src/main/kotlin/com/

# Buat folder jika tidak ada
mkdir -p ModuleName/src/main/kotlin/com/ModuleName

# Jalankan sync ulang
bash scripts/sync-extractors.sh
```

### Jumlah extractor tidak match

**Symptom:** Validation warning "Total classes seems low"

**Solution:**
```bash
# Cek MasterExtractors.kt
grep -c "^class \|^open class " docs/MasterExtractors.kt

# Cek AllExtractors.list
grep -A 100 "object AllExtractors" docs/MasterExtractors.kt | grep -c "()"

# Re-sync
bash scripts/sync-extractors.sh
```

---

## 📊 Metrics

| Metric | Value | Status |
|--------|-------|--------|
| Total Modules | 6 | ✅ |
| Extractors per Module | 39 | ✅ |
| Total Extractor Classes | 234 | ✅ |
| Sync Success Rate | 100% | ✅ |
| Auto-Sync Time | ~30s | ✅ |

---

## 📚 Related Documentation

- [EXTRACTOR_DOCUMENTATION.md](../docs/EXTRACTOR_DOCUMENTATION.md) - Extractor system docs
- [EXTRACTOR_ARCHITECTURE.md](../docs/EXTRACTOR_ARCHITECTURE.md) - Architecture reference
- [MasterExtractors.kt](../docs/MasterExtractors.kt) - Source code

---

**Last Updated:** 2026-03-19
**Maintainer:** Phisher98
**Status:** ✅ PRODUCTION READY
