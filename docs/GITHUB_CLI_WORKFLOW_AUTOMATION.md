# 📚 Dokumentasi GitHub CLI Workflow Automation

## 🎯 Overview

Dokumentasi ini menjelaskan cara melakukan **commit, push, dan monitoring GitHub Actions** secara otomatis menggunakan GitHub CLI (`gh`), termasuk **auto-trigger workflow** setelah workflow lain selesai.

---

## 📋 Prerequisites

### 1. Install GitHub CLI

```bash
# Termux/Android
pkg install gh

# Linux (Debian/Ubuntu)
sudo apt update && sudo apt install gh

# macOS
brew install gh

# Verify installation
gh --version
```

### 2. Authentication

```bash
# Login dengan browser
gh auth login

# Atau dengan token
gh auth login --with-token < YOUR_GITHUB_TOKEN
```

**Required Scopes untuk Token:**
- `repo` (Full control of private repositories)
- `workflow` (Update GitHub Action workflows)
- `read:org` (Read org and team membership)

---

## 🚀 Basic Commands

### 1. Commit & Push

```bash
# Add files
git add <file1> <file2>

# Commit dengan message
git commit -m "feat: your commit message"

# Push ke remote
git push origin <branch>

# One-liner
git add . && git commit -m "feat: changes" && git push
```

### 2. Check Workflow Status

```bash
# List recent workflow runs
gh run list --repo <username>/<repo> --limit 10

# View specific run details
gh run view <run-id> --repo <username>/<repo>

# Watch run in real-time
gh run watch <run-id> --repo <username>/<repo> --exit-status

# View failed run logs
gh run view <run-id> --repo <username>/<repo> --log-failed
```

---

## 🔄 Automated Workflow Pattern

### Scenario: Sync → Build Automation

**Use Case:** Setelah sync workflow selesai, otomatis trigger build workflow.

### Step-by-Step

#### 1. Commit dan Push Changes

```bash
#!/bin/bash

# File yang diubah
FILES="master/HttpClientFactory.kt master/CompiledRegexPatterns.kt"

# Commit dengan descriptive message
git add $FILES
git commit -m "perf: optimize video streaming performance

- Add HttpClientFactory for singleton HTTP client
- Add CompiledRegexPatterns for pre-compiled regex
- Expected: 40-60% latency reduction"

# Push dengan rebase (handle concurrent changes)
git pull --rebase && git push
```

#### 2. Trigger Sync Workflow Manual

```bash
# Trigger workflow by name
gh workflow run "Sync All Master Files" \
  --repo byimam2nd/cloudstream-ekstension \
  --ref master
```

#### 3. Monitor Sync Workflow

```bash
# Get latest workflow run ID
SYNC_RUN_ID=$(gh run list --repo byimam2nd/cloudstream-ekstension --limit 1 --json databaseId --jq '.[0].databaseId')

# Watch until complete
gh run watch $SYNC_RUN_ID --repo byimam2nd/cloudstream-ekstension --exit-status
```

#### 4. Auto-Trigger Build After Sync Success

```bash
#!/bin/bash

# Check sync workflow status
SYNC_STATUS=$(gh run view $SYNC_RUN_ID --repo byimam2nd/cloudstream-ekstension --json status --jq '.status')

if [ "$SYNC_STATUS" == "completed" ]; then
    echo "✅ Sync completed successfully!"
    
    # Build workflow akan auto-trigger via workflow_run trigger
    # Tunggu sebentar lalu check
    sleep 5
    
    # Get build run ID (triggered by workflow_run)
    BUILD_RUN_ID=$(gh run list --repo byimam2nd/cloudstream-ekstension \
      --limit 1 \
      --event workflow_run \
      --json databaseId \
      --jq '.[0].databaseId')
    
    echo "🔨 Build workflow started: $BUILD_RUN_ID"
    
    # Monitor build workflow
    gh run watch $BUILD_RUN_ID --repo byimam2nd/cloudstream-ekstension --exit-status
    
    # Check final status
    BUILD_STATUS=$(gh run view $BUILD_RUN_ID --repo byimam2nd/cloudstream-ekstension --json conclusion --jq '.conclusion')
    
    if [ "$BUILD_STATUS" == "success" ]; then
        echo "✅ BUILD SUCCESS!"
        exit 0
    else
        echo "❌ BUILD FAILED: $BUILD_STATUS"
        exit 1
    fi
else
    echo "❌ Sync failed or not completed"
    exit 1
fi
```

---

## 📜 Complete Automation Script

Simpan sebagai `scripts/deploy.sh`:

```bash
#!/bin/bash

set -e  # Exit on error

# Configuration
REPO="byimam2nd/cloudstream-ekstension"
BRANCH="master"
SYNC_WORKFLOW="Sync All Master Files"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Step 1: Commit and Push
log_info "Step 1: Committing and pushing changes..."
git add .
git commit -m "chore: automated deployment $(date +%Y-%m-%d)" || log_warn "No changes to commit"
git pull --rebase && git push

# Step 2: Trigger Sync Workflow
log_info "Step 2: Triggering sync workflow..."
gh workflow run "$SYNC_WORKFLOW" --repo $REPO --ref $BRANCH
log_info "Sync workflow triggered!"

# Step 3: Wait for sync to complete
log_info "Step 3: Monitoring sync workflow..."
sleep 10  # Give GitHub time to start the workflow

# Get sync run ID (most recent workflow_dispatch)
SYNC_RUN_ID=$(gh run list --repo $REPO \
  --limit 1 \
  --event workflow_dispatch \
  --json databaseId \
  --jq '.[0].databaseId')

if [ -z "$SYNC_RUN_ID" ]; then
    log_error "Failed to get sync workflow run ID"
    exit 1
fi

log_info "Sync Run ID: $SYNC_RUN_ID"

# Watch sync workflow
gh run watch $SYNC_RUN_ID --repo $REPO --exit-status

SYNC_STATUS=$(gh run view $SYNC_RUN_ID --repo $REPO --json conclusion --jq '.conclusion')

if [ "$SYNC_STATUS" != "success" ]; then
    log_error "Sync workflow failed with status: $SYNC_STATUS"
    exit 1
fi

log_info "✅ Sync workflow completed successfully!"

# Step 4: Wait for build workflow (auto-triggered via workflow_run)
log_info "Step 4: Waiting for build workflow to start..."
sleep 5

# Get build run ID (most recent workflow_run event)
BUILD_RUN_ID=$(gh run list --repo $REPO \
  --limit 1 \
  --event workflow_run \
  --json databaseId \
  --jq '.[0].databaseId')

if [ -z "$BUILD_RUN_ID" ]; then
    log_error "Failed to get build workflow run ID"
    exit 1
fi

log_info "Build Run ID: $BUILD_RUN_ID"

# Step 5: Monitor build workflow
log_info "Step 5: Monitoring build workflow..."
gh run watch $BUILD_RUN_ID --repo $REPO --exit-status

BUILD_STATUS=$(gh run view $BUILD_RUN_ID --repo $REPO --json conclusion --jq '.conclusion')

if [ "$BUILD_STATUS" == "success" ]; then
    log_info "✅ BUILD SUCCESS!"
    log_info "Artifacts will be available at:"
    log_info "https://github.com/$REPO/actions/runs/$BUILD_RUN_ID"
    exit 0
else
    log_error "❌ BUILD FAILED: $BUILD_STATUS"
    log_error "Check logs at: https://github.com/$REPO/actions/runs/$BUILD_RUN_ID"
    exit 1
fi
```

**Usage:**
```bash
chmod +x scripts/deploy.sh
./scripts/deploy.sh
```

---

## 🔍 Advanced Monitoring Commands

### 1. Real-time Status with Compact Output

```bash
gh run watch <run-id> --repo <repo> --compact --exit-status
```

### 2. Get Specific Job Logs

```bash
# List jobs in a run
gh run view <run-id> --repo <repo> --json jobs --jq '.jobs[].name'

# Get logs for specific job
gh run view <run-id> --repo <repo> --log | grep -A 50 "job-name"
```

### 3. Check Workflow Run History

```bash
# Last 10 runs with status
gh run list --repo <repo> --limit 10 --json status,conclusion,displayName,headBranch --jq '.[] | "\(.displayName) [\(.headBranch)]: \(.status) - \(.conclusion)"'

# Filter by status (e.g., failed runs)
gh run list --repo <repo> --event push --json conclusion --jq '.[] | select(.conclusion == "failure")'
```

### 4. Download Build Artifacts

```bash
# List available artifacts
gh run download <run-id> --repo <repo>

# Download specific artifact
gh run download <run-id> --repo <repo> --name <artifact-name> --dir ./output
```

---

## ⚡ One-Liner Commands

### Quick Status Check

```bash
# Latest run status
gh run list --repo <repo> --limit 1 --json conclusion --jq '.[0].conclusion'
```

### Auto-retry Failed Build

```bash
# Re-run failed workflow
gh run rerun $(gh run list --repo <repo> --limit 1 --json databaseId --jq '.[0].databaseId') --repo <repo>
```

### Wait and Check

```bash
# Wait for latest run to complete and show status
gh run watch $(gh run list --repo <repo> --limit 1 --json databaseId --jq '.[0].databaseId') --repo <repo> --exit-status && echo "SUCCESS" || echo "FAILED"
```

---

## 🎯 Workflow Trigger Configuration

### build.yml - Auto-trigger After Sync

```yaml
name: Build

on:
  # Trigger after Sync workflow completes
  workflow_run:
    workflows: ["Sync All Master Files"]
    types:
      - completed
    branches:
      - master

jobs:
  build:
    runs-on: ubuntu-latest
    # Only run if sync was successful
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      # ... build steps
```

### sync-all-masters.yml - Trigger on Master Folder Changes

```yaml
name: Sync All Master Files

on:
  push:
    branches: [ master ]
    paths:
      - 'master/*.kt'  # Only trigger on master folder changes
  workflow_dispatch:  # Allow manual trigger

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      # ... sync steps
```

---

## 📊 Monitoring Dashboard

### Create Status Summary

```bash
#!/bin/bash

REPO="byimam2nd/cloudstream-ekstension"

echo "======================================"
echo "📊 GitHub Actions Status Dashboard"
echo "======================================"
echo ""

# Latest 5 runs
echo "📋 Recent Workflow Runs:"
gh run list --repo $REPO --limit 5 --json \
  displayName,headBranch,status,conclusion,updatedAt \
  --jq '
    .[] | 
    "  \(.displayName) [\(.headBranch)]: \(.status) - \(.conclusion) (\(.updatedAt))"
  '

echo ""
echo "📈 Success Rate (Last 20 runs):"
TOTAL=$(gh run list --repo $REPO --limit 20 --json conclusion --jq 'length')
SUCCESS=$(gh run list --repo $REPO --limit 20 --json conclusion --jq '[.[] | select(.conclusion == "success")] | length')
echo "  $SUCCESS / $TOTAL successful ($(echo "scale=2; $SUCCESS * 100 / $TOTAL" | bc)%)"

echo ""
echo "🔗 Dashboard: https://github.com/$REPO/actions"
```

---

## 🐛 Troubleshooting

### Issue: Workflow Not Triggering

```bash
# Check workflow file syntax
gh workflow view <workflow-name> --repo <repo>

# List all workflows
gh workflow list --repo <repo>

# Manually trigger if needed
gh workflow run <workflow-name> --repo <repo> --ref <branch>
```

### Issue: Permission Denied

```bash
# Re-authenticate
gh auth logout
gh auth login

# Check token scopes
gh auth status
```

### Issue: Run ID Not Found

```bash
# Refresh run list
sleep 5
gh run list --repo <repo> --limit 5

# Use JSON query to get correct ID
gh run list --repo <repo> --json databaseId,displayName --jq '.[0]'
```

---

## 📝 Best Practices

### 1. Descriptive Commit Messages

```bash
# Good
git commit -m "perf: optimize HTTP client with connection pooling

- Add singleton HttpClientFactory
- Configure 30s read timeout for video streaming
- Add session-based User-Agent rotation
Expected: 40-60% latency reduction"

# Bad
git commit -m "fix stuff"
```

### 2. Handle Concurrent Changes

```bash
# Always rebase before push
git pull --rebase && git push

# Or use interactive rebase for cleanup
git pull --rebase=interactive
```

### 3. Monitor with Timeouts

```bash
# Set timeout for watch command
timeout 300 gh run watch <run-id> --repo <repo> --exit-status || echo "Timeout after 5 minutes"
```

### 4. Log Important Run IDs

```bash
# Save run ID for later reference
RUN_ID=$(gh run list --repo <repo> --limit 1 --json databaseId --jq '.[0].databaseId')
echo "Run ID: $RUN_ID" >> deployment-log.txt
```

---

## 🎓 Quick Reference Card

| Command | Description |
|---------|-------------|
| `gh run list --repo <repo> --limit 5` | List recent runs |
| `gh run view <run-id> --repo <repo>` | View run details |
| `gh run watch <run-id> --repo <repo>` | Watch in real-time |
| `gh run view <run-id> --log-failed` | View failed logs |
| `gh workflow run <name> --repo <repo>` | Trigger workflow |
| `gh run rerun <run-id> --repo <repo>` | Re-run workflow |
| `gh run download <run-id> --repo <repo>` | Download artifacts |
| `gh workflow list --repo <repo>` | List all workflows |

---

## 🔗 Useful Links

- GitHub CLI Documentation: https://cli.github.com/
- GitHub Actions Documentation: https://docs.github.com/en/actions
- gh run command reference: https://cli.github.com/manual/gh_run
- Workflow syntax: https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions

---

**Last Updated:** 2026-03-24  
**Author:** CloudStream Extension Team  
**Version:** 1.0
