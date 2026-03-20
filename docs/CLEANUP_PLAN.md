# 🧹 REPO CLEANUP PLAN

## 📊 CURRENT STATE ANALYSIS

### Repo Statistics
- **Total Kotlin Files:** 40 files
- **Git History:** 3,503 commits (112 since March 2026)
- **Total Size:** ~800KB (excluding .git)
- **Branches:** 3 (master, origin/master, origin/builds)

### Issues Found 🔍

#### 🔴 HIGH PRIORITY

1. **Git History Too Large** - 3,503 commits
   - Many fixup commits, test commits, CI triggers
   - Makes repo slow to clone
   - Hard to navigate history

2. **Duplicate Documentation** - Multiple overlapping docs
   - `EXTRACTOR_PLANNING.md` (planning phase, obsolete)
   - `DEEP_RESEARCH_EXTRACTORS.md` (research phase, obsolete)
   - `MASTER_EXTRACTOR_COLLECTION.md` (superseded by EXTRACTOR_DOCUMENTATION.md)
   - `EXTRACTOR_ARCHITECTURE.md` (partially redundant)

3. **Root Directory Clutter**
   - `Screenshot_20260317_122656_CloudStream.jpg` (529KB - should be in assets/)
   - `local.properties` (should be in .gitignore)
   - `gradlew.bat` (Windows-only, can be optional)

4. **Build Artifacts**
   - `.gradle/` folder (should be in .gitignore)
   - `build/` folders in each module (should be in .gitignore)

#### 🟡 MEDIUM PRIORITY

5. **Docs Organization**
   - Mixed active docs + planning docs + research docs
   - No clear separation of current vs historical

6. **Script Organization**
   - Scripts in root `scripts/` folder
   - Could be better organized

7. **README.md Outdated**
   - Still references old structure
   - Missing new extractor info

#### 🟢 LOW PRIORITY

8. **Commit Message Consistency**
   - Mixed formats: `feat:`, `fix:`, `ci:`, etc.
   - Some commits have no clear purpose

9. **Unused Branches**
   - `origin/builds` branch (build artifacts - should be separate repo)

---

## 🎯 CLEANUP GOALS

### Primary Goals
1. ✅ **Reduce Git History** - Keep only meaningful commits
2. ✅ **Clean Documentation** - Remove obsolete docs
3. ✅ **Organize Files** - Proper folder structure
4. ✅ **Update .gitignore** - Exclude build artifacts
5. ✅ **Update README** - Reflect current state

### Secondary Goals
6. ✅ **Optimize Images** - Compress or move screenshots
7. ✅ **Standardize Commits** - Consistent message format
8. ✅ **Clean Branches** - Remove unused branches

---

## 📋 CLEANUP PHASES

### PHASE 1: GIT HISTORY CLEANUP ⚠️

**WARNING:** This will rewrite history. Force push required.

#### Step 1.1: Interactive Rebase

```bash
# Backup current state
git branch backup-before-cleanup

# Start interactive rebase from first commit
git rebase -i --root

# Actions:
# - pick: Keep important commits (feat, major fixes)
# - squash: Combine fixup commits
# - drop: Remove CI triggers, test commits
```

#### Step 1.2: Target Commits to Keep

**KEEP (Important):**
```
✅ feat: add Megacloud extractor to MasterExtractors (39 total)
✅ feat: DYNAMIC EXTRACTOR REGISTER (no hardcode!)
✅ feat: sync master extractors v2.0 (37 classes)
✅ fix: restore optimization files + remove duplicate provider
✅ fix(LayarKaca21): import LayarKaca21 from correct package
✅ docs: add complete extractor documentation
```

**SQUASH (Combine):**
```
🔀 fix: replace parseJson → combine with parent
🔀 fix: remove build step → combine with parent
🔀 fix: separate workflow → combine with parent
🔀 ci: trigger build → DROP (not needed in history)
```

**DROP (Remove):**
```
❌ ci: trigger build (multiple instances)
❌ fix: add local.properties (temporary file)
❌ fix: add Extractors.kt to Pencurimovie (testing)
```

#### Step 1.3: Expected Result

**Before:** 3,503 commits  
**After:** ~50-100 meaningful commits

**Size Reduction:** ~97% smaller history

---

### PHASE 2: DOCUMENTATION CLEANUP 📚

#### Step 2.1: Archive Obsolete Docs

```bash
# Create archive folder
mkdir -p docs/archive/2026-03-research

# Move obsolete docs to archive
mv docs/EXTRACTOR_PLANNING.md docs/archive/
mv docs/DEEP_RESEARCH_EXTRACTORS.md docs/archive/
mv docs/MASTER_EXTRACTOR_COLLECTION.md docs/archive/
mv docs/PARALLEL_LINK_EXTRACTION_TEMPLATE.md docs/archive/
mv docs/CACHING_TEMPLATE.md docs/archive/
```

#### Step 2.2: Keep Active Docs

**KEEP in `docs/`:**
```
✅ EXTRACTOR_DOCUMENTATION.md (main docs)
✅ EXTRACTOR_ARCHITECTURE.md (architecture reference)
✅ BUGFIX_EXTRACTOR_2026-03-18.md (bug fix history)
✅ MasterExtractors.kt (extractor source)
✅ README-StremioAddon.md (user guide)
✅ README-StremioX.md (user guide)
✅ ULTIMA_SYNC_SETUP.md (user guide)
```

#### Step 2.3: Update Cross-References

Update any links in active docs that point to archived docs.

---

### PHASE 3: FILE ORGANIZATION 📁

#### Step 3.1: Move Large Files

```bash
# Create assets folder
mkdir -p assets/images

# Move screenshot
mv Screenshot_*.jpg assets/images/
mv Screenshot_*.jpg assets/images/cloudstream-screenshot.jpg
```

#### Step 3.2: Update .gitignore

```bash
# Add to .gitignore
cat >> .gitignore << 'EOF'

# Local configuration
local.properties
local.properties.example

# Build artifacts
build/
.gradle/
.idea/
*.iml

# OS files
.DS_Store
Thumbs.db

# Logs
*.log

# Temporary files
*.tmp
*.bak
EOF
```

#### Step 3.3: Remove Tracked Build Artifacts

```bash
# Remove from git (but keep on disk)
git rm -r --cached .gradle/
git rm -r --cached **/build/
git rm --cached local.properties

# Commit cleanup
git commit -m "chore: add build artifacts to .gitignore"
```

---

### PHASE 4: README UPDATE 📖

#### Step 4.1: New README Structure

```markdown
# 🎬 CloudStream Extensions - Phisher Repo

## ⚡ Quick Start

### Install Repo in CloudStream
```
Repository URL: https://raw.githubusercontent.com/byimam2nd/cloudstream-ekstension/master/repo.json
Shortcode: phisherrepo
```

## 📦 Available Extensions

| Extension | Type | Language | Status |
|-----------|------|----------|--------|
| Pencurimovie | Movie/TV | ID | ✅ Active |
| LayarKaca21 | Movie/TV | ID | ✅ Active |
| Donghuastream | Anime | ZH | ✅ Active |
| Funmovieslix | Movie/TV | EN | ✅ Active |
| HiAnime | Anime | EN | ✅ Active |
| Anichin | Anime | ID | ✅ Active |
| IdlixProvider | Movie/TV | ID | ✅ Active |

## 🔌 Extractor System

This repo uses **Distributed Extractors with Centralized Master**:
- **39 extractor classes** in `docs/MasterExtractors.kt`
- Auto-synced to all 7 extensions via GitHub Actions
- Dynamic registration (no hardcode)

### Supported Extractors (39 Total)

**StreamWish Based (11):** Do7go, Dhcplay, Hglink, Ghbrisk, Movearnpre, Minochinos, Mivalyo, Ryderjet, Bingezove, Dingtezuni

**VidStack Based (7):** Listeamed, Streamcasthub, Dm21embed, Dm21upns, Pm21p2p, Dm21, Meplayer

**Custom (3):** Voe, Veev, Dintezuvio

**OK.RU Based (3):** Odnoklassniki, OkRuSSL, OkRuHTTP

**Other (15):** Dailymotion, Rumble, StreamRuby, Svanila, Svilla, Vidguardto (1-3), Archivd, Newuservideo, Vidhidepro, Dsvplay, ArchiveOrg, Megacloud

## 📚 Documentation

- [Complete Extractor Documentation](docs/EXTRACTOR_DOCUMENTATION.md)
- [Architecture Overview](docs/EXTRACTOR_ARCHITECTURE.md)
- [Bug Fix History](docs/BUGFIX_EXTRACTOR_2026-03-18.md)

## 🛠️ Development

### Build Locally
```bash
./gradlew clean build
```

### Sync Extractors
```bash
bash scripts/sync-extractors.sh
bash scripts/verify-extractors.sh
```

## 📊 Repo Stats

- **Extensions:** 7
- **Extractors:** 39
- **Build Status:** ✅ Passing
- **Auto-Sync:** ✅ Enabled

## 💖 Support

If you find this repo helpful, consider supporting:

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/phisher98)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-F16061?logo=ko-fi&logoColor=white)](https://ko-fi.com/phisher98)

## 📝 License

[![GNU GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

This project is licensed under GPLv3.
```

---

### PHASE 5: SCRIPT ORGANIZATION 🔧

#### Step 5.1: Create Script Categories

```bash
# Create script subfolders
mkdir -p scripts/{sync,build,utils}

# Move scripts
mv scripts/sync-extractors.sh scripts/sync/
mv scripts/verify-extractors.sh scripts/sync/

# Create build script
cat > scripts/build/build-all.sh << 'EOF'
#!/bin/bash
# Build all extensions
./gradlew clean build
echo "✅ Build complete!"
EOF

# Create utility script
cat > scripts/utils/cleanup.sh << 'EOF'
#!/bin/bash
# Cleanup build artifacts
find . -name "build" -type d -exec rm -rf {} +
find . -name ".gradle" -type d -exec rm -rf {} +
echo "✅ Cleanup complete!"
EOF
```

#### Step 5.2: Create Master Script

```bash
cat > scripts/dev.sh << 'EOF'
#!/bin/bash
# Master development script

case "$1" in
    sync)
        bash scripts/sync/sync-extractors.sh
        bash scripts/sync/verify-extractors.sh
        ;;
    build)
        bash scripts/build/build-all.sh
        ;;
    clean)
        bash scripts/utils/cleanup.sh
        ;;
    *)
        echo "Usage: $0 {sync|build|clean}"
        exit 1
        ;;
esac
EOF
```

---

### PHASE 6: BRANCH CLEANUP 🌿

#### Step 6.1: Review Branches

```bash
# List all branches
git branch -a

# Check if builds branch is needed
git log origin/builds --oneline | head -5
```

#### Step 6.2: Remove Unused Branches

```bash
# Delete remote builds branch (if only contains artifacts)
git push origin --delete builds

# Verify
git branch -a
```

---

## 📅 IMPLEMENTATION TIMELINE

| Phase | Tasks | Estimated Time | Risk Level |
|-------|-------|----------------|------------|
| **Phase 1** | Git history cleanup | 2-3 hours | 🔴 HIGH (rewrite history) |
| **Phase 2** | Documentation cleanup | 30 min | 🟢 LOW |
| **Phase 3** | File organization | 30 min | 🟢 LOW |
| **Phase 4** | README update | 1 hour | 🟢 LOW |
| **Phase 5** | Script organization | 30 min | 🟢 LOW |
| **Phase 6** | Branch cleanup | 15 min | 🟡 MEDIUM |

**Total Time:** ~5-6 hours  
**Risk:** Mostly LOW, except Phase 1 (HIGH - requires force push)

---

## ⚠️ RISK MITIGATION

### Phase 1 Risks (Git History Rewrite)

**Risk:** Lost commits, broken history

**Mitigation:**
```bash
# 1. Create backup branch
git branch backup-before-cleanup

# 2. Push backup to remote
git push origin backup-before-cleanup

# 3. Test locally first
git rebase -i --root # Test locally

# 4. Only force push after testing
git push origin master --force
```

### Other Phases Risks

**Risk:** Broken links, missing files

**Mitigation:**
- Test all links after moving files
- Keep archive folder (don't delete, just move)
- Update cross-references

---

## ✅ SUCCESS CRITERIA

### Quantitative Metrics

- [ ] Git history reduced from 3,503 to <100 commits
- [ ] Repo size reduced by >50%
- [ ] Documentation files reduced from 12 to <7 active files
- [ ] Build time reduced by >20%
- [ ] README updated with current info

### Qualitative Metrics

- [ ] Clear separation of active vs archived docs
- [ ] All scripts organized in categories
- [ ] .gitignore properly configured
- [ ] No build artifacts in git history
- [ ] Clean, professional appearance

---

## 🚀 POST-CLEANUP BENEFITS

### For Users
- ✅ Faster clone time
- ✅ Clearer documentation
- ✅ Professional appearance

### For Developers
- ✅ Easier to navigate history
- ✅ Cleaner codebase
- ✅ Better organized scripts
- ✅ Clear contribution guidelines

### For Maintainers
- ✅ Smaller repo size
- ✅ Easier to manage
- ✅ Better CI/CD performance
- ✅ Clear separation of concerns

---

## 📝 NEXT STEPS AFTER CLEANUP

1. **Add CONTRIBUTING.md** - Contribution guidelines
2. **Add CHANGELOG.md** - Version history
3. **Add CODE_OF_CONDUCT.md** - Community guidelines
4. **Setup GitHub Wiki** - Extended documentation
5. **Add Issue Templates** - Standardized bug reports
6. **Add Pull Request Template** - Standardized PRs

---

**Plan Created:** 2026-03-18
**Last Updated:** 2026-03-20
**Status:** ✅ COMPLETED - Animasu module added (8 modules total)
**Next Module:** Ready for new site additions
**Priority:** Phase 1 (HIGH), Phase 2-6 (MEDIUM)
