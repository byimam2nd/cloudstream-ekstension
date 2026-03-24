#!/usr/bin/env python3
# =============================================================================
# Script Audit HTML dengan Selenium (Bypass Cloudflare)
# =============================================================================
# Requirement: Python + selenium + chromium
# Install (Termux):
#   pkg install python chromium
#   pip install selenium
# =============================================================================

import sys
import os
from datetime import datetime

try:
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.chrome.service import Service
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
except ImportError:
    print("Error: selenium not installed")
    print("Install with: pip install selenium")
    sys.exit(1)

# Colors
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
NC = '\033[0m'

def print_header(text):
    print(f"{BLUE}{'='*50}{NC}")
    print(f"{BLUE}  {text}{NC}")
    print(f"{BLUE}{'='*50}{NC}")

def main():
    if len(sys.argv) < 2:
        print_header("HTML Audit Script (Cloudflare Bypass)")
        print(f"\nUsage: {sys.argv[0]} <URL> [output_file]\n")
        print("Examples:")
        print(f"  {sys.argv[0]} https://samehadaku.com/")
        print(f"  {sys.argv[0]} https://animasu.stream/ audit.html")
        sys.exit(1)
    
    url = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else f"/tmp/selenium-audit-{datetime.now().strftime('%Y%m%d-%H%M%S')}.html"
    
    print_header("Fetching HTML with Selenium")
    print(f"\nURL: {url}")
    print(f"Output: {output_file}\n")
    
    # Chrome options
    chrome_options = Options()
    chrome_options.add_argument('--headless')
    chrome_options.add_argument('--no-sandbox')
    chrome_options.add_argument('--disable-dev-shm-usage')
    chrome_options.add_argument('--disable-gpu')
    chrome_options.add_argument('--window-size=1920,1080')
    chrome_options.add_argument('--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36')
    
    # Try different chromedriver paths
    driver_paths = [
        '/usr/bin/chromedriver',
        '/usr/local/bin/chromedriver',
        '/data/data/com.termux/files/usr/bin/chromedriver',
    ]
    
    driver = None
    for driver_path in driver_paths:
        if os.path.exists(driver_path):
            try:
                service = Service(driver_path)
                driver = webdriver.Chrome(service=service, options=chrome_options)
                break
            except Exception as e:
                continue
    
    if not driver:
        # Try without explicit driver path (should work with chromium installed)
        try:
            driver = webdriver.Chrome(options=chrome_options)
        except Exception as e:
            print(f"{RED}Error: Cannot start ChromeDriver{NC}")
            print(f"Details: {e}")
            print("\nInstall chromedriver:")
            print("  Termux: pkg install chromium")
            print("  Linux: apt install chromium-chromedriver")
            sys.exit(1)
    
    try:
        print(f"{YELLOW}Navigating to {url}...{NC}")
        driver.get(url)
        
        # Wait for page to load (including Cloudflare challenge)
        print("Waiting for page to load (max 60s)...")
        WebDriverWait(driver, 60).until(
            lambda d: d.execute_script("return document.readyState") == "complete"
        )
        
        # Additional wait for dynamic content
        import time
        time.sleep(5)
        
        # Get HTML
        html = driver.page_source
        
        # Save to file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(html)
        
        print(f"\n{GREEN}✓ HTML saved to {output_file}{NC}")
        print(f"✓ Page title: {driver.title}")
        
        # Extract useful info
        print("\n📊 Found Selectors:")
        print("==================")
        
        # Posters
        posters = driver.find_elements(By.CSS_SELECTOR, 'img[src], img[data-src], img[data-lazy-src]')
        print(f"\nPosters: {len(posters)}")
        for i, img in enumerate(posters[:10]):
            src = img.get_attribute('src') or img.get_attribute('data-src') or img.get_attribute('data-lazy-src')
            if src and src.startswith('http'):
                print(f"  {i+1}. {src[:80]}...")
        
        # Titles
        titles = driver.find_elements(By.CSS_SELECTOR, 'h1, h2, h3, .title')
        print(f"\nTitles: {len(titles)}")
        for i, title in enumerate(titles[:10]):
            text = title.text.strip()
            if text:
                print(f"  {i+1}. {text[:60]}...")
        
        # Div classes
        divs = driver.find_elements(By.CSS_SELECTOR, 'div[class*="poster"], div[class*="thumb"], div[class*="anime"]')
        print(f"\nDiv Classes: {len(divs)}")
        for i, div in enumerate(divs[:10]):
            print(f"  {i+1}. {div.get_attribute('class')}")
        
        print(f"\n{GREEN}✓ Done!{NC}")
        
    except Exception as e:
        print(f"\n{RED}Error: {e}{NC}")
        sys.exit(1)
    
    finally:
        driver.quit()
    
    print("\nQuick analysis commands:")
    print(f"  grep -i poster {output_file} | head -20")
    print(f"  grep -i thumb {output_file} | head -20")
    print(f"  grep -i 'img.*src' {output_file} | head -20")

if __name__ == '__main__':
    main()
