#!/usr/bin/env python3
"""
Extract missing extractors from Phisher + ExtCloud repos
and append to MasterExtractors.kt
"""

import os
import re
import subprocess

MASTER_FILE = "/data/data/com.termux/files/home/cloudstream/oce/master/MasterExtractors.kt"
PHERSHER_DIR = "/data/data/com.termux/files/home/cloudstream/phisher"
EXTCLOUD_DIR = "/data/data/com.termux/files/home/cloudstream/ExtCloud"
OUTPUT_FILE = "/data/data/com.termux/files/home/cloudstream/oce/_missing_extractors.kt"

def get_existing_extractors():
    """Get existing extractor names from MasterExtractors.kt"""
    extractors = set()
    try:
        with open(MASTER_FILE, 'r') as f:
            for line in f:
                match = re.match(r'^(?:open\s+)?class\s+(\w+)\s*:\s*(?:ExtractorApi|StreamWishExtractor|VidStack|DoodLaExtractor|VidhideExtractor|Rabbitstream|LixstreamExtractor)', line)
                if match:
                    extractors.add(match.group(1))
    except Exception as e:
        print(f"Error reading master: {e}")
    return extractors

def get_all_refs():
    """Get all extractor names from Phisher + ExtCloud"""
    extractors = set()
    pattern = re.compile(r'^(?:open\s+)?class\s+(\w+)\s*:\s*(?:ExtractorApi|StreamWishExtractor|VidStack|DoodLaExtractor|VidhideExtractor|Rabbitstream|LixstreamExtractor)')
    
    for search_dir in [PHERSHER_DIR, EXTCLOUD_DIR]:
        for root, dirs, files in os.walk(search_dir):
            for fname in files:
                if fname.endswith('.kt'):
                    fpath = os.path.join(root, fname)
                    try:
                        with open(fpath, 'r', errors='ignore') as f:
                            for line in f:
                                match = pattern.match(line)
                                if match:
                                    extractors.add(match.group(1))
                    except:
                        pass
    return extractors

def find_extractor_source(name):
    """Find source file for an extractor"""
    for search_dir in [PHERSHER_DIR, EXTCLOUD_DIR]:
        for root, dirs, files in os.walk(search_dir):
            for fname in files:
                if fname.endswith('.kt'):
                    fpath = os.path.join(root, fname)
                    try:
                        with open(fpath, 'r', errors='ignore') as f:
                            content = f.read()
                            # Check if this file contains the class definition
                            if re.search(rf'(?:open\s+)?class\s+{name}\s*:', content):
                                return fpath, content
                    except:
                        pass
    return None, None

def extract_class_code(name, content):
    """Extract a class definition from file content"""
    # Find class start
    pattern = rf'((?:open\s+)?class\s+{name}\s*(?::\s*[\w<>\s,]+)?\s*{{)'
    match = re.search(pattern, content)
    if not match:
        return None
    
    start = match.start()
    # Find matching closing brace
    brace_count = 0
    in_class = False
    end = start
    
    for i, char in enumerate(content[start:]):
        if char == '{':
            brace_count += 1
            in_class = True
        elif char == '}':
            brace_count -= 1
            if in_class and brace_count == 0:
                end = start + i + 1
                break
    
    if end > start:
        return content[start:end]
    return None

def main():
    print("=== Extracting missing extractors ===")
    print()
    
    existing = get_existing_extractors()
    all_refs = get_all_refs()
    missing = sorted(all_refs - existing)
    
    print(f"Existing in Master: {len(existing)}")
    print(f"Total unique references: {len(all_refs)}")
    print(f"Missing from Master: {len(missing)}")
    print()
    
    # Write to output file
    with open(OUTPUT_FILE, 'w') as out:
        out.write("// ========================================\n")
        out.write("// MISSING EXTRACTORS FROM PHERSHER + EXTCLOUD\n")
        out.write(f"// Total: {len(missing)} extractors\n")
        out.write("// ========================================\n\n")
        
        for i, name in enumerate(missing):
            src_file, content = find_extractor_source(name)
            if src_file:
                rel_path = src_file.replace(PHERSHER_DIR, 'phisher/').replace(EXTCLOUD_DIR, 'ExtCloud/')
                class_code = extract_class_code(name, content)
                if class_code:
                    out.write(f"// --- {name} (from {rel_path}) ---\n")
                    out.write(class_code.rstrip())
                    out.write("\n\n")
                    print(f"[{i+1}/{len(missing)}] ✅ {name}")
                else:
                    print(f"[{i+1}/{len(missing)}] ❌ {name} - Could not extract class code")
            else:
                print(f"[{i+1}/{len(missing)}] ❌ {name} - Source file not found")
    
    print()
    print(f"=== Output written to: {OUTPUT_FILE} ===")
    size = os.path.getsize(OUTPUT_FILE) if os.path.exists(OUTPUT_FILE) else 0
    print(f"File size: {size:,} bytes ({size/1024:.1f} KB)")

if __name__ == '__main__':
    main()
