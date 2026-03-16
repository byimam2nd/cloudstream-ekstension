# LayarKacaProvider - Troubleshooting Guide

## Masalah: Plugin tidak muncul di Extensions List

### ✅ Yang Sudah Diverifikasi:
- [x] Build #77 SUCCESS
- [x] Version 8 di plugins.json
- [x] Plugin class ada (@CloudstreamPlugin annotation)
- [x] Package structure correct (com.layarkacaprovider)
- [x] JAR file generated (99,379 bytes)
- [x] Main API class (LayarKaca21) exists

### 🔧 SOLUSI - Step by Step:

#### Step 1: Verify Repository URL
Pastikan URL repository benar:
```
https://raw.githubusercontent.com/byimam2nd/cloudstream-ekstension/builds/plugins.json
```

Test di browser:
```
https://raw.githubusercontent.com/byimam2nd/cloudstream-ekstension/builds/plugins.json
```

Cari "LayarKacaProvider" - harus ada dengan version 8.

#### Step 2: Remove Old Repository
1. CloudStream → Settings → Extensions
2. Find repository di bagian atas
3. Tap icon **trash/delete**
4. Confirm delete

#### Step 3: Clear ALL CloudStream Data
1. Settings Android → Apps → CloudStream
2. Storage → **Clear Data** (bukan hanya Clear Cache)
3. Force Close CloudStream

#### Step 4: Reinstall CloudStream (Jika Step 3 Tidak Berhasil)
1. Backup data CloudStream (jika ada)
2. Uninstall CloudStream
3. Download latest CloudStream dari:
   ```
   https://github.com/recloudstream/cloudstream/releases
   ```
4. Install CloudStream
5. Open CloudStream

#### Step 5: Add Repository
1. Settings → Extensions
2. Tap **+** atau **Add Repository**
3. Paste URL:
   ```
   https://raw.githubusercontent.com/byimam2nd/cloudstream-ekstension/builds/plugins.json
   ```
4. Tap **Add**

#### Step 6: Search & Install
1. Di Extensions screen, tap **Search** icon
2. Ketik "LayarKacaProvider"
3. **HARUS MUNCUL** dengan info:
   ```
   Name: LayarKacaProvider
   Version: 8
   Status: ✓ OK (green)
   Description: LayarKaca21 - Nonton Film Streaming Sub Indo
   ```
4. Tap **Install**
5. Toggle **Enable** (ON)

#### Step 7: Verify di Home
1. Back ke Home screen
2. Scroll ke bawah
3. Section "Extensions" atau "Main Page"
4. **LayarKacaProvider HARUS MUNCUL** sebagai card/box

### ❌ Jika MASIH Tidak Muncul:

#### Test 1: Manual Download
Download JAR langsung:
```
https://raw.githubusercontent.com/byimam2nd/cloudstream-ekstension/builds/LayarKacaProvider.jar
```

Simpan ke Downloads folder, lalu di CloudStream:
- Settings → Extensions → Install from file
- Pilih file JAR yang didownload

#### Test 2: Check Logcat
Connect ke PC dan run:
```bash
adb logcat | grep -i "cloudstream\|layar"
```

Lalu di CloudStream:
- Add repository
- Search LayarKacaProvider

Cek error di logcat.

#### Test 3: Compare dengan Provider Lain
Install Anichin (yang working):
```
Search "Anichin" → Install → Enable
```

Jika Anichin muncul tapi LayarKacaProvider tidak, berarti ada masalah spesifik di plugin.

### 📋 Checklist Debugging:

- [ ] Repository URL benar
- [ ] Repository sudah di-remove dan add ulang
- [ ] CloudStream cache/data cleared
- [ ] CloudStream restarted
- [ ] Version 8 terlihat di plugins.json
- [ ] Status "OK" (hijau) di extensions list
- [ ] Provider bisa di-install
- [ ] Provider bisa di-enable
- [ ] Provider muncul di Home

### 🎯 Expected Behavior:

Setelah install dan enable, di Home akan muncul:
```
┌─────────────────────────────┐
│ Extensions                  │
├─────────────────────────────┤
│ ┌──────────────┐            │
│ │ LayarKaca    │            │
│ │ Provider     │            │
│ └──────────────┘            │
│ ┌──────────────┐            │
│ │ Anichin      │            │
│ └──────────────┘            │
└─────────────────────────────┘
```

### 📞 Contact Info:

Jika masih ada masalah setelah semua step di atas:
1. Screenshot Extensions list
2. Screenshot Error (jika ada)
3. CloudStream version
4. Android version

Submit issue dengan informasi lengkap.
