#!/bin/bash

# ========================================
# Verifikasi Ekstraktor
# ========================================

echo "========================================"
echo "📊 VERIFIKASI EKSTRAKTOR LENGKAP"
echo "========================================"
echo ""

echo "1️⃣ SATU SITE = SATU FILE EKSTRAKTOR?"
echo ""
for site in Pencurimovie LayarKaca21 Donghuastream Funmovieslix HiAnime IdlixProvider Anichin; do
    COUNT=$(find "$site/src/main/kotlin/com/$site" -name "Extractors.kt" -type f 2>/dev/null | wc -l)
    if [ "$COUNT" -eq 1 ]; then
        echo "  ✅ $site: 1 file Extractors.kt"
    else
        echo "  ❌ $site: $COUNT files (PROBLEM!)"
    fi
done

echo ""
echo "2️⃣ SETIAP FILE MENGGUNAKAN PACKAGE YANG BENAR?"
echo ""
for site in Pencurimovie LayarKaca21 Donghuastream Funmovieslix HiAnime IdlixProvider Anichin; do
    FILE="$site/src/main/kotlin/com/$site/Extractors.kt"
    PKG=$(grep "^package " "$FILE" 2>/dev/null | head -1)
    if [ "$PKG" = "package com.$site" ]; then
        echo "  ✅ $site: $PKG"
    else
        echo "  ❌ $site: $PKG (WRONG!)"
    fi
done

echo ""
echo "3️⃣ MENGGUNAKAN DYNAMIC REGISTRATION (NOT HARDCODE)?"
echo ""
for site in Pencurimovie LayarKaca21 Donghuastream Funmovieslix HiAnime IdlixProvider Anichin; do
    # Find provider file in the site folder
    PROVIDER_FILE=$(find "$site/src/main/kotlin/com/$site" -name "*Provider*.kt" -type f 2>/dev/null | head -1)
    if [ -n "$PROVIDER_FILE" ] && grep -q "AllExtractors.list.forEach" "$PROVIDER_FILE" 2>/dev/null; then
        echo "  ✅ $site: Dynamic registration (AllExtractors.list.forEach)"
    else
        echo "  ❌ $site: Hardcoded extractors or no provider found"
    fi
done

echo ""
echo "4️⃣ JUMLAH EXTRACTOR CLASSES DI SETIAP SITE"
echo ""
for site in Pencurimovie LayarKaca21 Donghuastream Funmovieslix HiAnime IdlixProvider Anichin; do
    FILE="$site/src/main/kotlin/com/$site/Extractors.kt"
    CLASS_COUNT=$(grep -c "^class \|^open class " "$FILE" 2>/dev/null || echo 0)
    # Count only lines inside AllExtractors.list that end with (),
    LIST_COUNT=$(grep -A 100 "object AllExtractors" "$FILE" | grep -c "()," 2>/dev/null || echo 0)
    if [ "$CLASS_COUNT" -eq "$LIST_COUNT" ]; then
        echo "  ✅ $site: $CLASS_COUNT classes, $LIST_COUNT registered ✅ MATCH"
    else
        echo "  ⚠️  $site: $CLASS_COUNT classes, $LIST_COUNT registered ⚠️  MISMATCH"
    fi
done

echo ""
echo "========================================"
echo "✅ VERIFIKASI SELESAI"
echo "========================================"
