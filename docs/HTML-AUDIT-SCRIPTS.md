# 🕷️ HTML Audit Scripts (Cloudflare Bypass)

Scripts untuk audit HTML structure provider yang menggunakan Cloudflare protection.

---

## 🔧 Available Scripts

### 1. audit-html-playwright.sh (Node.js)

Menggunakan Playwright untuk execute JavaScript dan bypass Cloudflare.

**Requirements:**
- Node.js
- Playwright

**Install (Termux):**
```bash
pkg install nodejs
npm install -g playwright
npx playwright install chromium
```

**Usage:**
```bash
./scripts/audit-html-playwright.sh https://samehadaku.com/
./scripts/audit-html-playwright.sh https://animasu.stream/ audit.html
```

**Output:**
- HTML saved to file
- Console output dengan selector analysis

---

### 2. audit-html-selenium.py (Python)

Menggunakan Selenium untuk execute JavaScript dan bypass Cloudflare.

**Requirements:**
- Python 3
- selenium
- Chromium + Chromedriver

**Install (Termux):**
```bash
pkg install python chromium
pip install selenium
```

**Usage:**
```bash
python scripts/audit-html-selenium.py https://samehadaku.com/
python scripts/audit-html-selenium.py https://animasu.stream/ audit.html
```

**Output:**
- HTML saved to file
- Console output dengan:
  - Poster URLs (first 10)
  - Titles (first 10)
  - Div classes (first 10)

---

## 📊 Example Usage

### Audit Samehadaku

```bash
# Dengan Selenium (recommended untuk Termux)
python scripts/audit-html-selenium.py https://samehadaku.com/ samehadaku.html

# Analisis hasil
grep -i "poster" samehadaku.html | head -20
grep -i "thumb" samehadaku.html | head -20
```

### Audit Animasu

```bash
python scripts/audit-html-selenium.py https://animasu.stream/ animasu.html

# Cari selector untuk poster
grep -oP 'class="[^"]*poster[^"]*"' animasu.html | sort -u
```

### Audit Pencurimovie

```bash
python scripts/audit-html-selenium.py https://pencurimovie.cam/ pencuri.html

# Cari semua img tags
grep -oP 'img[^>]*src="[^"]*"' pencuri.html | head -20
```

---

## 🎯 What This Does

1. **Launches Headless Browser**
   - Chromium via Playwright/Selenium
   - Executes JavaScript
   - Waits for Cloudflare challenge

2. **Bypasses Cloudflare**
   - Real browser execution
   - Proper headers
   - Cookie handling

3. **Extracts HTML**
   - Full page source after JS execution
   - Dynamic content loaded
   - Ready for selector analysis

---

## 📝 Output Analysis

After running the script, analyze the HTML:

```bash
# Find poster-related selectors
grep -i "poster" output.html | head -20

# Find thumb-related selectors
grep -i "thumb" output.html | head -20

# Find all image sources
grep -oP 'img[^>]*src="[^"]*"' output.html | head -30

# Find div classes
grep -oP 'class="[^"]*"' output.html | sort -u | head -30
```

---

## ⚠️ Troubleshooting

### Error: "Cannot start ChromeDriver"

**Solution:**
```bash
# Termux
pkg install chromium

# Verify installation
which chromedriver
chromium --version
```

### Error: "Timeout waiting for page"

**Causes:**
- Slow internet
- Heavy Cloudflare protection
- Site is down

**Solution:**
- Increase timeout in script (default 60s)
- Check internet connection
- Try again later

### Error: "Module not found"

**For Playwright:**
```bash
npm install -g playwright
npx playwright install chromium
```

**For Selenium:**
```bash
pip install selenium
```

---

## 🔗 Integration with Verification

After getting HTML from these scripts:

1. **Analyze selectors manually**
   ```bash
   grep -i "poster" output.html | head -20
   ```

2. **Identify fallback patterns**
   - Look for multiple poster locations
   - Check for og:image meta tags
   - Find lazy-loaded images

3. **Implement fallback strategy**
   ```kotlin
   val poster = document.selectFirst("div.poster img")?.attr("src")
       ?: document.selectFirst("meta[property=og:image]")?.attr("content")
       ?: document.selectFirst("img[data-src]")?.attr("data-src")
       ?: ""
   ```

4. **Test in CloudStream app**

---

## 📚 References

- [Playwright Documentation](https://playwright.dev/)
- [Selenium Documentation](https://www.selenium.dev/documentation/)
- [Cloudflare Bypass Techniques](https://github.com/vvanglro/cf-clearance)

---

*Last updated: 2026-03-24*
