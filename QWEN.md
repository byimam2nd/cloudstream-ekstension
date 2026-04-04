## Qwen Added Memories
- OCE Project - CRITICAL RULES (learned from mistakes):

1. NEVER delete imports without verifying 2x by reading FULL file. Check: direct usage, annotations (@OptIn, @SuppressLint), companion objects, data classes.

2. NEVER sync master files manually with PowerShell. ALWAYS let CI/CD bash script (scripts/sync-all-masters.sh) handle generated_sync files. PowerShell corrupts special characters.

3. CloudStream's Log.e() only takes 2 arguments (tag, message), NOT 3 like Java/Android.

4. Empty commits may not trigger GitHub Actions workflow. Make minimal real change.

5. When build fails: read FULL error log, fix ONE error at a time, re-run after each fix.

6. Force push only acceptable for: corrupted generated files, broken sync state. Always backup branch first.

7. Try-catch scope matters: errors inside lambda won't be caught by outer try-catch.

8. ALWAYS verify imports by: grep search + read actual code + check annotations + check companion objects.

9. CI/CD workflow: git push → Sync Job (bash awk sync) → Build Job (./gradlew make) → Push artifacts.
