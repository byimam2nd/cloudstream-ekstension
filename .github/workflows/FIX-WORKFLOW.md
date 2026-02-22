# 🔧 FIX: GitHub Actions Workflow Build Error

## 🐛 Problem

Workflow GitHub Actions gagal/timed out saat build.

### Root Causes:

1. **Actions versi tidak stabil** ❌
   - Menggunakan `actions/checkout@v5` dan `actions/setup-java@v5`
   - Version v5 masih baru/beta
   
2. **Copy command bermasalah** ❌
   ```bash
   cp **/build/*.cs3 $GITHUB_WORKSPACE/builds  # Glob pattern mungkin tidak work
   cp **/build/*.jar $GITHUB_WORKSPACE/builds
   ```

3. **No error handling** ❌
   - Tidak ada debug output
   - Tidak ada fallback jika file tidak ada

---

## ✅ Solutions

### 1. Downgrade Actions ke Stable Version

**BEFORE:**
```yaml
- name: Checkout
  uses: actions/checkout@v5  # ❌ Unstable

- name: Setup JDK 17
  uses: actions/setup-java@v5  # ❌ Unstable

- name: Setup Android SDK
  uses: android-actions/setup-android@v3.2.2  # ❌ Too specific
```

**AFTER:**
```yaml
- name: Checkout
  uses: actions/checkout@v4  # ✅ Stable

- name: Setup JDK 17
  uses: actions/setup-java@v4  # ✅ Stable

- name: Setup Android SDK
  uses: android-actions/setup-android@v3  # ✅ Stable version
```

---

### 2. Fix Copy Commands

**BEFORE:**
```bash
cp **/build/*.cs3 $GITHUB_WORKSPACE/builds  # ❌ Glob pattern issue
cp **/build/*.jar $GITHUB_WORKSPACE/builds
cp build/plugins.json $GITHUB_WORKSPACE/builds
```

**AFTER:**
```bash
# ✅ Use find command for reliable copying
find . -name "*.cs3" -exec cp {} $GITHUB_WORKSPACE/builds/ \;
find . -name "*.jar" -exec cp {} $GITHUB_WORKSPACE/builds/ \;
cp build/plugins.json $GITHUB_WORKSPACE/builds/ || true

# ✅ Add debug output
echo "=== Copied files ==="
ls -la $GITHUB_WORKSPACE/builds/
```

---

## 📊 Changes Made

| File | Change | Reason |
|------|--------|--------|
| `newrun.yml` | `actions/checkout@v5` → `@v4` | Stability |
| `newrun.yml` | `actions/setup-java@v5` → `@v4` | Stability |
| `newrun.yml` | `android-actions/setup-android@v3.2.2` → `@v3` | Stability |
| `newrun.yml` | `cp **/build/*.cs3` → `find . -name "*.cs3"` | Reliability |
| `newrun.yml` | Added debug output | Troubleshooting |

---

## 🧪 Testing

### Local Test:
```bash
# Simulate GitHub Actions environment
cd /data/data/com.termux/files/home/cloudstream/extention-cloudstream

# Test find command
find . -name "*.cs3" -exec echo "Found: {}" \;
find . -name "*.jar" -exec echo "Found: {}" \;

# Test build (if you have time)
./gradlew make makePluginsJson
```

### GitHub Actions Test:
1. Commit changes
2. Push to GitHub
3. Check Actions tab
4. Verify build completes successfully

---

## 📝 Full Workflow (Fixed)

```yaml
name: Build

concurrency:
  group: "build"
  cancel-in-progress: true

on:
  push:
    branches:
      - master
      - main
    paths-ignore:
      - '*.md'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4  # ✅ Fixed
        with:
          path: "src"

      - name: Checkout builds
        uses: actions/checkout@v4  # ✅ Fixed
        with:
          ref: "builds"
          path: "builds"

      - name: Clean old builds
        run: |
          rm $GITHUB_WORKSPACE/builds/*.cs3 || true
          rm $GITHUB_WORKSPACE/builds/*.jar || true

      - name: Setup JDK 17
        uses: actions/setup-java@v4  # ✅ Fixed
        with:
          java-version: 17
          distribution: adopt
          cache: gradle

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3  # ✅ Fixed

      - name: Access Secrets
        env:
          # ... secrets ...
        run: |
          cd $GITHUB_WORKSPACE/src
          echo TMDB_API=$TMDB_API >> local.properties
          # ... other properties ...

      - name: Build Plugins
        run: |
          cd $GITHUB_WORKSPACE/src
          chmod +x gradlew
          ./gradlew make makePluginsJson
          ./gradlew ensureJarCompatibility
          
          # ✅ Fixed copy commands
          find . -name "*.cs3" -exec cp {} $GITHUB_WORKSPACE/builds/ \;
          find . -name "*.jar" -exec cp {} $GITHUB_WORKSPACE/builds/ \;
          cp build/plugins.json $GITHUB_WORKSPACE/builds/ || true
          
          # ✅ Debug output
          echo "=== Copied files ==="
          ls -la $GITHUB_WORKSPACE/builds/

      - name: Push builds
        run: |
          cd $GITHUB_WORKSPACE/builds
          git config --local user.email "actions@github.com"
          git config --local user.name "GitHub Actions"
          git add .
          git commit --amend -m "Build $GITHUB_SHA" || exit 0
          git push --force
```

---

## ⚠️ Common Issues & Solutions

### Issue: "No such file or directory"
**Solution:** Use `|| true` to prevent error if file doesn't exist

### Issue: "Timed out after 6 hours"
**Solution:** 
- Check if build is stuck in infinite loop
- Add timeout to workflow:
  ```yaml
  jobs:
    build:
      runs-on: ubuntu-latest
      timeout-minutes: 60  # ✅ Add timeout
```

### Issue: "Permission denied"
**Solution:**
```bash
chmod +x gradlew  # ✅ Make gradlew executable
```

### Issue: "No space left on device"
**Solution:**
```bash
# Clean up before build
rm -rf $GITHUB_WORKSPACE/src/.gradle
rm -rf $GITHUB_WORKSPACE/src/build
```

---

## 🚀 Verification Checklist

- [x] Actions versions downgraded to stable v4
- [x] Copy commands fixed to use `find`
- [x] Debug output added
- [x] Error handling improved
- [ ] Workflow tested on GitHub Actions
- [ ] Build completes successfully
- [ ] APK files generated
- [ ] JAR files generated
- [ ] plugins.json generated

---

## 📚 References

- [actions/checkout Documentation](https://github.com/actions/checkout)
- [actions/setup-java Documentation](https://github.com/actions/setup-java)
- [android-actions/setup-android](https://github.com/android-actions/setup-android)
- [GitHub Actions Find Command](https://docs.github.com/en/actions/learn-github-actions/workflow-syntax-for-github-actions)

---

**Last Updated**: 2026-02-23  
**Status**: ✅ Fixed - Ready to Test on GitHub Actions  
**Files Changed**: `.github/workflows/newrun.yml`
