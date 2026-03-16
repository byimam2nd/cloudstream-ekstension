# LayarKaca21 Provider - Test Documentation

## Overview

This document describes the testing approach for the LayarKaca21 provider after the domain migration.

## Current Status (March 2026)

### Domain Changes

LK21 has migrated to new domains with Cloudflare protection:

| Old Domain | New Domain | Status |
|------------|------------|--------|
| `lk21.de` | `tv9.lk21official.cc` | ⚠️ Cloudflare Protected |
| `series.lk21.de` | `tv.lk21official.love` | ⚠️ Cloudflare Protected |
| - | `d21.team` | ✅ Landing Page (Working) |

### Known Issues

1. **Cloudflare Protection**: Main streaming domains now use Cloudflare challenge
2. **Search API**: Old `search.lk21.party` endpoint is deprecated (404)
3. **Fingerprint Protection**: Some domains require browser fingerprint

## Testing Approach

### 1. Manual Test Script

Run the test script to verify domain connectivity:

```bash
cd LayarKacaProvider
bash test-provider.sh
```

### 2. Unit Tests

Unit tests are located in:
```
src/test/kotlin/com/LayarKacaProvider/LayarKacaProviderTest.kt
```

To run tests (requires Android SDK):
```bash
./gradlew :LayarKacaProvider:test
```

### 3. In-App Testing

1. Build the extension:
   ```bash
   ./gradlew :LayarKacaProvider:assembleDebug
   ```

2. Install the APK on Android device

3. Test in CloudStream app:
   - Open LayarKaca21 provider
   - Try searching for "avatar"
   - Try loading a movie/series
   - Try playing a video

## Expected Behavior

### Working Features ✅

- Provider configuration loads correctly
- Main page URLs are valid
- Caching system works (5 minute TTL)
- Helper functions (getBaseUrl, encodeUrl) work

### May Not Work ⚠️

- **Search**: May return empty results due to Cloudflare
- **Main Page**: May fail if Cloudflare challenge not passed
- **Load Links**: Extractor may fail if iframe protected

## Troubleshooting

### Issue: Search returns empty results

**Cause**: Cloudflare blocking automated requests

**Workaround**: 
- Wait for domain mirror updates
- Check landing page: https://d21.team
- Use alternative search methods

### Issue: Load fails with timeout

**Cause**: Domain redirect chain too long or blocked

**Solution**:
```kotlin
// Increase timeout in LayarKacaProvider.kt
val document = app.get(url, timeout = 15000L) // Increase from 5000L
```

### Issue: Extractor fails

**Cause**: Iframe source requires authentication

**Workaround**: Check for alternative extractors

## Code Changes Summary

### Updated Files

1. **LayarKacaProvider.kt**
   - Updated `mainUrl` to use redirecting domain
   - Added `landingUrl` for mirror discovery
   - Modified `search()` to use direct HTML scraping
   - Added `encodeUrl()` helper function
   - Improved error handling with logging

2. **build.gradle.kts**
   - Updated version to 6
   - Updated iconUrl to use landing page domain

3. **Test Files**
   - Added `LayarKacaProviderTest.kt` for unit testing
   - Added `test-provider.sh` for manual testing

## Future Improvements

1. **Browser Automation**: Consider using WebView for Cloudflare bypass
2. **API Alternative**: Monitor `febriadj/lk21-api` for working endpoints
3. **Mirror Discovery**: Auto-discover working mirrors from landing page
4. **Fallback Chain**: Implement multiple domain fallback

## References

- LK21 API Project: https://github.com/febriadj/lk21-api
- Landing Page: https://d21.team
- CloudStream Extensions: https://github.com/recloudstream/extensions

## Contact

For issues or updates, check the landing page or Telegram channel linked from d21.team
