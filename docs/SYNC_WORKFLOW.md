# 🔄 Sync Workflow Documentation

## Automatic Trigger Flow

### When Sync Workflow Triggers Automatically

The sync workflow will **automatically trigger** when:

```yaml
# Triggered by push to master branch
# ONLY when files in master/ folder change
on:
  push:
    branches: [ master ]
    paths:
      - 'master/MasterExtractors.kt'
      - 'master/MasterUtils.kt'
      - 'master/MasterCacheManager.kt'
      - 'master/MasterImageCache.kt'
      - 'master/MasterSmartCacheMonitor.kt'
      - 'master/MasterSuperSmartPrefetchManager.kt'
```

### Flow Diagram

```
┌────────────────────────────────────────────────────────┐
│ Developer pushes commit with changes in master/*.kt   │
└───────────────────┬────────────────────────────────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │  Sync Workflow       │ ← AUTO-TRIGGERED
         │  (GitHub Actions)    │
         └───────────┬──────────┘
                     │
                     ▼
         ┌──────────────────────┐
         │ For each module:     │
         │ - Create generated-  │
         │ - Sync Master*.kt →  │
         │   generated-sync/    │
         │ - Update package &   │
         │   imports            │
         └───────────┬──────────┘
                     │
                     ▼
         ┌──────────────────────┐
         │  Sync Workflow       │
         │  COMPLETED ✅        │
         └───────────┬──────────┘
                     │
                     ▼ (workflow_run trigger)
         ┌──────────────────────┐
         │  Build Workflow      │ ← AUTO-TRIGGERED
         │  (GitHub Actions)    │
         └───────────┬──────────┘
                     │
                     ▼
         ┌──────────────────────┐
         │ Build APK with       │
         │ generated-sync/*.kt  │
         └───────────┬──────────┘
                     │
                     ▼
         ┌──────────────────────┐
         │  BUILD SUCCESS ✅    │
         │  Artifacts ready     │
         └──────────────────────┘
```

---

## Manual Trigger (workflow_dispatch)

You can **manually trigger** the sync workflow when needed:

### Via GitHub UI:
1. Go to **Actions** tab
2. Select **Sync All Master Files** workflow
3. Click **Run workflow**
4. Select branch (usually `master`)
5. Click **Run workflow**

### Via GitHub CLI:
```bash
gh workflow run "Sync All Master Files" --repo byimam2nd/cloudstream-ekstension --ref master
```

### When to Manual Trigger:
- Testing new sync script changes
- Re-sync after fixing import issues
- Initial setup for new modules
- Debugging sync problems

---

## Build Workflow Trigger

The build workflow triggers automatically in **TWO scenarios**:

### 1. Direct Push (excluding master/ folder)
```yaml
on:
  push:
    branches: [ master, main ]
    paths-ignore:
      - '*.md'
      - 'master/**'  # Skip master/ changes (sync handles this)
```

**Example triggers:**
- ✅ Commit to `app/src/main/kotlin/...`
- ✅ Commit to `build.gradle.kts`
- ✅ Commit to `scripts/` (but not master/*.kt)
- ❌ Commit to `master/*.kt` (sync workflow handles this)

### 2. After Sync Workflow Completes
```yaml
on:
  workflow_run:
    workflows: ["Sync All Master Files"]
    types: [ completed ]
    branches: [ master ]
```

**This ensures:**
- Build runs **after** sync generates files
- Build uses **fresh** generated-sync/ files
- No manual intervention needed

---

## Common Scenarios

### Scenario 1: Update Master Extractor

```bash
# Developer edits master/MasterExtractors.kt
git add master/MasterExtractors.kt
git commit -m "feat: add new extractor"
git push
```

**What happens:**
1. ✅ Sync workflow **AUTO-TRIGGERS** (master/*.kt changed)
2. ✅ Sync generates `generated-sync/SyncExtractors.kt`
3. ✅ Build workflow **AUTO-TRIGGERS** (workflow_run)
4. ✅ Build APK with new extractor
5. ✅ Artifacts ready for download

---

### Scenario 2: Update Sync Script

```bash
# Developer edits scripts/sync-all-masters.sh
git add scripts/sync-all-masters.sh
git commit -m "fix: improve sync logic"
git push
```

**What happens:**
1. ❌ Sync workflow **DOES NOT trigger** (not master/*.kt)
2. ✅ Build workflow **TRIGGERS** (direct push)
3. ⚠️ Build uses **existing** generated-sync/ files
4. ✅ Build APK (no new sync needed)

**To test sync script changes:**
```bash
# After push, manually trigger sync
gh workflow run "Sync All Master Files"
```

---

### Scenario 3: Update Performance Optimizations

```bash
# Developer edits master/HttpClientFactory.kt
git add master/HttpClientFactory.kt
git commit -m "perf: optimize connection pooling"
git push
```

**What happens:**
1. ❌ Sync workflow **DOES NOT trigger** (not in paths list)
2. ✅ Build workflow **TRIGGERS** (direct push)
3. ⚠️ **PROBLEM:** HttpClientFactory.kt not synced!

**SOLUTION:** Add HttpClientFactory.kt to sync trigger paths:
```yaml
paths:
  - 'master/MasterExtractors.kt'
  - 'master/MasterUtils.kt'
  - 'master/HttpClientFactory.kt'  # ← Add this
  - 'master/CompiledRegexPatterns.kt'  # ← Add this
  # ... other files
```

---

## Current Trigger Paths

The sync workflow triggers on changes to:

```yaml
paths:
  - 'master/MasterExtractors.kt'
  - 'master/MasterUtils.kt'
  - 'master/MasterCacheManager.kt'
  - 'master/MasterImageCache.kt'
  - 'master/MasterSmartCacheMonitor.kt'
  - 'master/MasterSuperSmartPrefetchManager.kt'
```

**NOT included (yet):**
- `master/HttpClientFactory.kt` ⚠️
- `master/CompiledRegexPatterns.kt` ⚠️

**Recommendation:** Add these files to the trigger paths list.

---

## Troubleshooting

### Sync Workflow Not Triggering

**Check:**
1. Did you push to `master` branch?
2. Did you change files in `master/` folder?
3. Are the files in the `paths:` list?

**Debug:**
```bash
# Check workflow runs
gh run list --repo byimam2nd/cloudstream-ekstension --workflow "Sync All Master Files"

# View workflow config
gh workflow view "Sync All Master Files" --repo byimam2nd/cloudstream-ekstension
```

### Build Workflow Not Triggering After Sync

**Check:**
1. Did sync workflow complete successfully?
2. Is `workflow_run` trigger configured correctly?
3. Any errors in GitHub Actions logs?

**Debug:**
```bash
# Check recent workflow runs
gh run list --repo byimam2nd/cloudstream-ekstension --limit 10

# View build workflow
gh workflow view "Build" --repo byimam2nd/cloudstream-ekstension
```

### Manual Sync Needed

**When automatic trigger fails:**
```bash
# Trigger sync manually
gh workflow run "Sync All Master Files" --repo byimam2nd/cloudstream-ekstension

# Monitor progress
gh run watch <run-id> --repo byimam2nd/cloudstream-ekstension
```

---

## Best Practices

### For Developers

1. **Always commit to `master/` folder** for extractor changes
2. **Don't edit `generated-sync/` files** (auto-generated)
3. **Test locally** before pushing
4. **Check workflow status** after push

### For Maintainers

1. **Keep trigger paths updated** when adding new master files
2. **Monitor workflow runs** for errors
3. **Document manual sync steps** when needed
4. **Review generated code** in workflow logs

---

## Workflow Status

Check current workflow status:

```bash
# List recent runs
gh run list --repo byimam2nd/cloudstream-ekstension --limit 5

# Watch specific run
gh run watch <run-id> --repo byimam2nd/cloudstream-ekstension

# View workflow config
gh workflow view "<workflow-name>" --repo byimam2nd/cloudstream-ekstension
```

---

**Last Updated:** 2026-03-24  
**Status:** ✅ Automatic sync working correctly
