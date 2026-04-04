# ⚠️ Common Mistakes - OCE Project

> "A smart person learns from their mistakes. A wise person learns from others' mistakes."

Dokumentasi ini berisi daftar kesalahan umum yang pernah terjadi dan cara menghindarinya.

---

## 🔴 MISTAKE #1: Hapus Import Tanpa Verifikasi

**Tanggal:** 2026-04-04  
**File:** `master/MasterExtractors.kt`  
**Impact:** Build gagal di 8 providers

### ❌ Yang Dilakukan:
```kotlin
// Menghapus imports karena "tidak terlihat dipakai"
- import kotlin.io.encoding.Base64
- import kotlin.io.encoding.ExperimentalEncodingApi
- import android.annotation.SuppressLint
```

### ✅ Yang Seharusnya:
```kotlin
// Baca FULL file dulu, cari SEMUA usage:

// Line 968: @OptIn(ExperimentalEncodingApi::class)
// Line 979: String(Base64.decode(...))
// Line 1380: @SuppressLint("NewApi")
```

### 🛡️ Cara Menghindari:
1. **BACA file lengkap**, bukan cuma imports section
2. **Search** untuk setiap import di SELURUH file
3. **Check** annotations, companion objects, data classes
4. **Verifikasi 2x** dengan method berbeda

---

## 🔴 MISTAKE #2: PowerShell Sync Script

**Tanggal:** 2026-04-04  
**File:** All `generated_sync/SyncExtractors.kt` files  
**Impact:** File corruption, syntax errors

### ❌ Yang Dilakukan:
```powershell
(Get-Content master/MasterExtractors.kt -Raw) -replace 'pattern', 'replacement' | Set-Content file.kt
```

### ✅ Yang Seharusnya:
```bash
# BIARKAN CI/CD yang handle sync
git add master/*.kt && git commit -m "..." && git push
# CI otomatis run scripts/sync-all-masters.sh
```

### 🛡️ Cara Menghindari:
1. **JANGAN** pernah sync manual
2. **SELALU** gunakan CI/CD bash script
3. **PowerShell corrupts** special characters
4. **Bash awk** adalah satu-satunya cara yang tested

---

## 🔴 MISTAKE #3: Wrong Log.e() Signature

**Tanggal:** 2026-04-04  
**File:** `master/MasterExtractors.kt`  
**Impact:** Build gagal "Too many arguments"

### ❌ Yang Dilakukan:
```kotlin
Log.e("MasterExtractors", "[ExtractorName] Failed: ${e.message}", e)
//                                                      ↑ 3rd arg - WRONG!
```

### ✅ Yang Seharusnya:
```kotlin
Log.e("MasterExtractors", "[ExtractorName] Failed: ${e.message}")
// Hanya 2 arguments
```

### 🛡️ Cara Menghindari:
1. **Check API** sebelum pakai function
2. **CloudStream's Log.e** hanya 2 args (tag, message)
3. **Bukan** seperti Java/Android Log.e (tag, message, throwable)

---

## 🔴 MISTAKE #4: Broken Try-Catch Structure

**Tanggal:** 2026-04-04  
**File:** `master/MasterExtractors.kt`  
**Impact:** Syntax errors, build gagal

### ❌ Yang Dilakukan:
```kotlin
try {
    list.map { item ->
        riskyOperation(item)
    }
} catch (e: Exception) {  // ← TIDAK CAPTURE lambda errors!
    Log.e("Tag", "Failed")
}
```

### ✅ Yang Seharusnya:
```kotlin
list.map { item ->
    try {
        riskyOperation(item)
    } catch (e: Exception) {
        Log.e("Tag", "Failed for $item")
    }
}
```

### 🛡️ Cara Menghindari:
1. Try-catch harus di **scope yang benar**
2. Lambda punya **scope sendiri**
3. **Indentasi** harus match braces

---

## 🟡 MISTAKE #5: Empty Commit Doesn't Trigger Workflow

**Tanggal:** 2026-04-04  
**Impact:** Workflow tidak trigger, wasted time

### ❌ Yang Dilakukan:
```bash
git commit --allow-empty -m "ci: trigger rebuild"
```

### ✅ Yang Seharusnya:
```bash
# Buat perubahan minimal yang nyata
echo $(date) >> README.md
git add README.md
git commit -m "ci: trigger rebuild"
```

### 🛡️ Cara Menghindari:
1. **Empty commits** mungkin di-ignore oleh GitHub Actions
2. **Selalu** buat perubahan minimal (walaupun cuma README)
3. **Atau** gunakan `workflow_dispatch` manual trigger

---

## 🟡 MISTAKE #6: Force Push Without Backup

**Tanggal:** 2026-04-04  
**Impact:** Lost commits, confusion

### ❌ Yang Dilakukan:
```bash
git reset --hard <old-commit>
git push --force origin master
# ← Semua commit setelah old-commit hilang!
```

### ✅ Yang Seharusnya:
```bash
# 1. Backup dulu
git branch backup-branch

# 2. Baru force push
git reset --hard <old-commit>
git push --force origin master

# 3. Kalau perlu restore
git checkout backup-branch
```

### 🛡️ Cara Menghindari:
1. **Backup branch** sebelum force push
2. **Force push** hanya untuk emergency
3. **Revert commits** lebih aman daripada force push

---

## 🟡 MISTAKE #7: Not Reading Full Error Log

**Tanggal:** 2026-04-04  
**Impact:** Fixed wrong thing, wasted CI/CD minutes

### ❌ Yang Dilakukan:
```
Error: Unresolved reference 'Log'
→ Assume: Import Log hilang
→ Fix: Tambah import
→ Build lagi
→ Error: Too many arguments
→ Oops, bukan itu masalahnya!
```

### ✅ Yang Seharusnya:
```
1. Read FULL error log
2. Identify ALL errors
3. Fix them in order
4. Re-run after ALL fixes
```

### 🛡️ Cara Menghindari:
1. **Baca SEMUA** error messages
2. **Fix satu per satu**, re-run setelah setiap fix
3. **Jangan batch fixes** - hard to debug

---

## 📊 STATISTICS

| Mistake Type | Occurrences | Time Wasted | Build Failures |
|-------------|-------------|-------------|----------------|
| Wrong import deletion | 1 | ~30 min | 3 |
| PowerShell sync | 1 | ~20 min | 2 |
| Wrong Log.e signature | 1 | ~15 min | 2 |
| Broken try-catch | 1 | ~10 min | 1 |
| Empty commit | 1 | ~10 min | 0 |
| Force push | 1 | ~5 min | 0 |
| **Total** | **7** | **~90 min** | **8** |

---

## 🎯 KEY TAKEAWAYS

1. **Verifikasi 2x** sebelum hapus apapun
2. **BIARKAN CI/CD** handle sync operations
3. **Baca API docs** sebelum pakai functions
4. **Baca FULL error log** sebelum fix
5. **Backup** sebelum force push
6. **Fix satu per satu**, jangan batch

---

**Last Updated:** 2026-04-04  
**Status:** Active - Will be updated with each new mistake
