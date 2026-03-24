# 🧪 Testing Cloudflare Bypass Scripts

## Quick Test

### Test 1: Samehadaku (Cloudflare protected)

```bash
# Install requirements (Termux)
pkg install python chromium
pip install selenium

# Run test
python scripts/audit-html-selenium.py https://samehadaku.com/ test-samehadaku.html

# Expected output:
# ✓ HTML saved to test-samehadaku.html
# ✓ Page title: Samehadaku - ...
# 📊 Found Selectors:
# Posters: XX
# Titles: XX
# Div Classes: XX
```

### Test 2: Animasu (Cloudflare protected)

```bash
python scripts/audit-html-selenium.py https://animasu.stream/ test-animasu.html

# Check for poster selectors
grep -i "poster" test-animasu.html | head -20
```

### Test 3: Pencurimovie (Cloudflare protected)

```bash
python scripts/audit-html-selenium.py https://pencurimovie.cam/ test-pencuri.html

# Check for thumb selectors
grep -i "thumb" test-pencuri.html | head -20
```

---

## Expected Results

If scripts work correctly:

✅ HTML file saved (100KB - 2MB)
✅ Page title is correct (not "Just a moment...")
✅ Found poster images (> 5)
✅ Found title tags (> 5)
✅ Found div classes with relevant names

If scripts fail:

❌ Error: "Just a moment..." page (Cloudflare still blocking)
❌ Empty HTML file (< 10KB)
❌ No selectors found

---

## Troubleshooting

### Issue: Cloudflare page instead of content

**Symptom:**
```
Page title: Just a moment...
```

**Solution:**
- Wait longer (Cloudflare takes 5-10 seconds)
- Increase timeout in script to 120s
- Try different user agent

### Issue: No selectors found

**Symptom:**
```
Posters: 0
Titles: 0
```

**Solution:**
- Check if site is up
- Try with browser first
- Site might use different selectors

### Issue: Chromedriver error

**Symptom:**
```
Error: Cannot start ChromeDriver
```

**Solution:**
```bash
# Termux
pkg install chromium
which chromedriver

# Verify chromedriver works
chromedriver --version
```

---

## Success Criteria

Script is working if:

1. ✅ HTML file > 100KB
2. ✅ Page title is site name (not Cloudflare)
3. ✅ Found > 5 poster images
4. ✅ Found > 5 title tags
5. ✅ Found relevant div classes

---

*Last updated: 2026-03-24*
