#!/bin/bash

# ========================================
# Automated Deployment Script
# For CloudStream Extension
# ========================================
# Usage: ./scripts/auto-deploy.sh
# ========================================

set -e  # Exit on error

# Configuration
REPO="byimam2nd/cloudstream-ekstension"
BRANCH="master"
SYNC_WORKFLOW="Sync All Master Files"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Helper functions
log_step() {
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}➜${NC} ${GREEN}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites..."
    
    if ! command -v gh &> /dev/null; then
        log_error "GitHub CLI (gh) is not installed. Please install it first."
        exit 1
    fi
    
    if ! gh auth status &> /dev/null; then
        log_error "Not authenticated with GitHub. Please run: gh auth login"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Step 1: Commit and Push
commit_and_push() {
    log_step "Step 1: Committing and pushing changes..."
    
    # Check if there are changes
    if [ -z "$(git status --porcelain)" ]; then
        log_warn "No changes to commit"
    else
        git add .
        git commit -m "chore: automated deployment $(date +%Y-%m-%d)" || log_warn "No changes to commit"
    fi
    
    # Pull with rebase and push
    log_info "Pulling latest changes with rebase..."
    git pull --rebase > /dev/null 2>&1 || true
    
    log_info "Pushing to remote..."
    git push origin $BRANCH
    
    log_success "Changes pushed successfully"
}

# Step 2: Trigger Sync Workflow
trigger_sync() {
    log_step "Step 2: Triggering Sync workflow..."
    
    # Trigger workflow
    gh workflow run "$SYNC_WORKFLOW" --repo $REPO --ref $BRANCH
    
    log_success "Sync workflow triggered"
}

# Step 3: Monitor Sync Workflow
monitor_sync() {
    log_step "Step 3: Monitoring Sync workflow..."
    
    # Wait for GitHub to start the workflow
    sleep 10
    
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
    log_info "Watch URL: https://github.com/$REPO/actions/runs/$SYNC_RUN_ID"
    
    # Watch sync workflow
    log_info "Monitoring sync progress..."
    if gh run watch $SYNC_RUN_ID --repo $REPO --exit-status; then
        log_success "Sync workflow completed successfully"
        return 0
    else
        log_error "Sync workflow failed"
        return 1
    fi
}

# Step 4: Wait for Build to Start
wait_for_build() {
    log_step "Step 4: Waiting for Build workflow to start..."
    
    # Give GitHub time to trigger the build
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
    log_info "Watch URL: https://github.com/$REPO/actions/runs/$BUILD_RUN_ID"
}

# Step 5: Monitor Build Workflow
monitor_build() {
    log_step "Step 5: Monitoring Build workflow..."
    
    # Watch build workflow
    if gh run watch $BUILD_RUN_ID --repo $REPO --exit-status; then
        log_success "Build workflow completed successfully"
        return 0
    else
        log_error "Build workflow failed"
        return 1
    fi
}

# Step 6: Show Final Status
show_final_status() {
    log_step "Final Status"
    
    BUILD_STATUS=$(gh run view $BUILD_RUN_ID --repo $REPO --json conclusion --jq '.conclusion')
    
    if [ "$BUILD_STATUS" == "success" ]; then
        log_success "✅ BUILD SUCCESS!"
        echo ""
        log_info "Artifacts available at:"
        echo "    https://github.com/$REPO/actions/runs/$BUILD_RUN_ID"
        echo ""
        log_info "Direct download:"
        echo "    gh run download $BUILD_RUN_ID --repo $REPO"
        exit 0
    else
        log_error "❌ BUILD FAILED: $BUILD_STATUS"
        echo ""
        log_error "Check logs at:"
        echo "    https://github.com/$REPO/actions/runs/$BUILD_RUN_ID"
        echo ""
        log_info "View failed logs:"
        echo "    gh run view $BUILD_RUN_ID --repo $REPO --log-failed"
        exit 1
    fi
}

# Main execution
main() {
    echo ""
    log_step "🚀 CloudStream Extension Auto-Deploy"
    echo ""
    
    check_prerequisites
    commit_and_push
    trigger_sync
    monitor_sync
    wait_for_build
    monitor_build
    show_final_status
}

# Run main function
main "$@"
