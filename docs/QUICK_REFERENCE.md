# ⚡ Quick Reference: GitHub CLI Workflow

## 🎯 One-Command Deploy

```bash
# Full automation (commit → push → sync → build → monitor)
./scripts/auto-deploy.sh
```

---

## 📝 Manual Step-by-Step

### 1. Commit & Push
```bash
git add . && git commit -m "feat: your changes" && git pull --rebase && git push
```

### 2. Trigger Sync Workflow
```bash
gh workflow run "Sync All Master Files" --repo byimam2nd/cloudstream-ekstension --ref master
```

### 3. Monitor Sync (Wait ~15s)
```bash
# Get latest run ID
SYNC_ID=$(gh run list --repo byimam2nd/cloudstream-ekstension --limit 1 --event workflow_dispatch --json databaseId --jq '.[0].databaseId')

# Watch it
gh run watch $SYNC_ID --repo byimam2nd/cloudstream-ekstension --exit-status
```

### 4. Monitor Build (Auto-triggered)
```bash
# Wait 5s for build to start
sleep 5

# Get build run ID
BUILD_ID=$(gh run list --repo byimam2nd/cloudstream-ekstension --limit 1 --event workflow_run --json databaseId --jq '.[0].databaseId')

# Watch it
gh run watch $BUILD_ID --repo byimam2nd/cloudstream-ekstension --exit-status
```

---

## 🔍 Common Commands

| Task | Command |
|------|---------|
| **List recent runs** | `gh run list --repo <repo> --limit 5` |
| **View run details** | `gh run view <run-id> --repo <repo>` |
| **Watch in real-time** | `gh run watch <run-id> --repo <repo> --exit-status` |
| **View failed logs** | `gh run view <run-id> --repo <repo> --log-failed` |
| **Trigger workflow** | `gh workflow run "<name>" --repo <repo>` |
| **Re-run failed** | `gh run rerun <run-id> --repo <repo>` |
| **Download artifacts** | `gh run download <run-id> --repo <repo>` |

---

## 🎯 Workflow Automation Pattern

```
┌─────────────────┐
│  git push       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Sync Workflow  │ ← Trigger: push to master/
│  (15-30s)       │
└────────┬────────┘
         │
         ▼ (auto-trigger via workflow_run)
┌─────────────────┐
│  Build Workflow │ ← Trigger: workflow_run completed
│  (2-5 min)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  SUCCESS ✓      │
└─────────────────┘
```

---

## 📊 Quick Status Check

```bash
# Latest 3 runs with status
gh run list --repo byimam2nd/cloudstream-ekstension --limit 3 --json \
  displayName,conclusion,headBranch --jq \
  '.[] | "\(.displayName) [\(.headBranch)]: \(.conclusion)"'
```

---

## 🐛 Troubleshooting

### Sync Failed?
```bash
# Check sync logs
gh run view <sync-run-id> --repo <repo> --log-failed
```

### Build Failed?
```bash
# Check build logs
gh run view <build-run-id> --repo <repo> --log-failed
```

### Workflow Not Triggering?
```bash
# List all workflows
gh workflow list --repo <repo>

# Manually trigger
gh workflow run "Sync All Master Files" --repo <repo>
```

---

## 📱 Mobile-Friendly (Termux)

```bash
# Quick deploy from phone
alias cs-deploy='gh workflow run "Sync All Master Files" --repo byimam2nd/cloudstream-ekstension'

# Check status
alias cs-status='gh run list --repo byimam2nd/cloudstream-ekstension --limit 3'

# Watch latest
alias cs-watch='gh run watch $(gh run list --repo byimam2nd/cloudstream-ekstension --limit 1 --json databaseId --jq ".[0].databaseId") --repo byimam2nd/cloudstream-ekstension --exit-status'
```

---

**Full documentation:** `docs/GITHUB_CLI_WORKFLOW_AUTOMATION.md`
