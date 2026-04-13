#!/bin/bash

# ========================================
# Add Missing Extractors from Phisher + ExtCloud
# ========================================

MASTER_FILE="/data/data/com.termux/files/home/cloudstream/oce/master/MasterExtractors.kt"
PHERSHER_DIR="/data/data/com.termux/files/home/cloudstream/phisher"
EXTCLOUD_DIR="/data/data/com.termux/files/home/cloudstream/ExtCloud"
OUTPUT_FILE="/tmp/missing_extractors.kt"

# Get existing extractors from Master
existing=$(grep -h "^class\|^open class" "$MASTER_FILE" | grep -E "ExtractorApi|StreamWish|VidStack|DoodLa|Vidhide|Rabbitstream|Lixstream" | grep -oP 'class \K\w+' | sort -u)

# Get all extractors from Phisher and ExtCloud
all_refs=$(
    grep -rh "^class\|^open class" "$PHERSHER_DIR" "$EXTCLOUD_DIR" --include="*.kt" 2>/dev/null | \
    grep -E "ExtractorApi|StreamWish|VidStack|DoodLa|Vidhide|Rabbitstream|Lixstream" | \
    grep -oP 'class \K\w+' | sort -u
)

# Find missing ones
missing=""
missing_count=0
for ext in $all_refs; do
    if ! echo "$existing" | grep -q "^${ext}$"; then
        missing="$missing $ext"
        missing_count=$((missing_count + 1))
    fi
done

echo "Existing extractors: $(echo "$existing" | wc -w)"
echo "Total unique references: $(echo "$all_refs" | wc -w)"
echo "Missing from Master: $missing_count"
echo ""
echo "Missing extractors:"
for ext in $missing; do
    echo "  - $ext"
done

echo ""
echo "=== Finding source files ==="

# For each missing extractor, find source file
> "$OUTPUT_FILE"

for ext in $missing; do
    # Search in Phisher first
    src_file=$(grep -rl "class $ext " "$PHERSHER_DIR" --include="*.kt" 2>/dev/null | head -1)
    if [ -z "$src_file" ]; then
        # Search in ExtCloud
        src_file=$(grep -rl "class $ext " "$EXTCLOUD_DIR" --include="*.kt" 2>/dev/null | head -1)
    fi

    if [ -n "$src_file" ]; then
        echo "Found: $ext → $src_file"
        # Extract the class definition
        # Find the line number where class starts
        start_line=$(grep -n "class $ext " "$src_file" | head -1 | cut -d: -f1)
        if [ -n "$start_line" ]; then
            # Extract from class definition to next class or end of meaningful code
            echo "" >> "$OUTPUT_FILE"
            echo "// --- $ext (from $(basename $(dirname $src_file))/$(basename $src_file)) ---" >> "$OUTPUT_FILE"
            # Get the class and all its content until next class or end
            tail -n +$start_line "$src_file" | awk '
                /^class |^open class / && NR > 1 { exit }
                { print }
            ' >> "$OUTPUT_FILE"
            echo "" >> "$OUTPUT_FILE"
        fi
    else
        echo "NOT FOUND: $ext"
    fi
done

echo ""
echo "=== Output ==="
echo "Generated file: $OUTPUT_FILE"
echo "Total bytes: $(wc -c < "$OUTPUT_FILE")"
echo "Total lines: $(wc -l < "$OUTPUT_FILE")"
