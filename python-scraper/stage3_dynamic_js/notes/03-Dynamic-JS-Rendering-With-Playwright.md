# Stage 3: Dynamic JS Rendering with Playwright (Python)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use Playwright When:**
  - The target page is built with heavy **Client-Side Rendering (CSR)** frameworks (React, Next.js client-side hydration, Vue, Angular, Svelte) and data is completely absent from View Page Source (`Ctrl+U`).
  - The website provides **no accessible internal JSON API** (data is bundled into compiled JS chunks or rendered into SVG/Canvas).
  - The workflow requires human-like interactions (clicking pagination buttons, filling search inputs, scrolling infinite newsfeeds, solving basic UI challenges).

- **AVOID Playwright When:**
  - The data exists in raw HTML: Use Stage 1 HTTPX + BS4 (100x lower CPU & memory overhead).
  - You can find the data in **DevTools -> Network -> Fetch/XHR**: Reverse-engineering the JSON API (Stage 2) is 50x faster.
  - You need to scrape tens of thousands of URLs on modest hardware: Each Playwright browser context requires 150MB+ RAM.

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | Playwright Python Pattern | Engineering Best Practice |
|---|---|---|
| **Context Manager Lifecycle** | `with sync_playwright() as p:` | Guarantees browser processes terminate even on exceptions. |
| **Launch Headless Chromium** | `browser = p.chromium.launch(headless=True)` | Use `headless=False` during local development debugging. |
| **Emulate Desktop Profile** | `browser.new_context(user_agent="...", viewport={"width": 1280, "height": 800})` | Isolates cookies, cache, and viewport per crawling session. |
| **Navigate to URL** | `page.goto(url, wait_until="domcontentloaded", timeout=30000)` | Avoids waiting on endless third-party tracking scripts. |
| **Wait for Hydration Selector**| `page.wait_for_selector("div.product-card", timeout=10000)` | Never rely on static `time.sleep()` for dynamic page rendering. |
| **Query Multiple Elements** | `cards = page.query_selector_all("article.product_pod")` | Returns handles to matching DOM elements. |
| **Extract Text Safely** | `text = el.inner_text().strip() if el else "N/A"` | Avoids `AttributeError` on missing sub-elements. |
| **Execute JS in Page Context** | `page.evaluate("window.scrollTo(0, document.body.scrollHeight)")` | Interacts directly with browser DOM APIs and window globals. |
| **Block Heavy Media Assets** | `page.route("**/*.{png,jpg,jpeg,svg,woff2}", lambda r: r.abort())` | **Huge speedup:** Cuts page load time by 60% by skipping image downloads. |
| **Click Dynamic Buttons** | `page.click("button#next-page")` | Automatically scrolls target button into view and waits for actionability. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Detect Client-Side JS Rendering
1. Open target page in Chrome. Inspect an element (`F12`).
2. Press `Ctrl + U` (View Source). Search for the same text.
3. If search returns 0 results, the DOM is injected dynamically via client-side JavaScript.

### Step 2: Establish the Hydration Anchor
Identify the specific CSS selector that marks successful data rendering (e.g. `div.quote` or `div[data-testid='cell']`).
```python
# Block until JavaScript completes hydration
page.wait_for_selector("div.quote", timeout=10000)
```

### Step 3: Handle Infinite Scroll Feeds
For feeds that expand on scroll:
1. Measure current count: `len(page.query_selector_all("div.quote"))`.
2. Scroll to bottom: `page.evaluate("window.scrollTo(0, document.body.scrollHeight)")`.
3. Wait 1500–2000ms for network response and DOM insertion.
4. Exit loop when item count stops expanding or maximum passes reached.

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`playwright_dynamic_scraper.py`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/python-scraper/stage3_dynamic_js/codes/playwright_dynamic_scraper.py):

```python
"""
Author: raysi
Practical Stage 3 Demo: Scraping Client-Side Rendered (CSR) JavaScript pages
using Microsoft Playwright in Python with explicit hydration condition waits.
"""

from dataclasses import dataclass
from playwright.sync_api import sync_playwright, Page


@dataclass(frozen=True)
class QuoteRecord:
    text: str
    author: str
    tags: list[str]


def scrape_dynamic_quotes() -> list[QuoteRecord]:
    target_url = "https://quotes.toscrape.com/js/"
    quotes: list[QuoteRecord] = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
            viewport={"width": 1280, "height": 800},
        )
        page: Page = context.new_page()

        page.goto(target_url, timeout=30000)
        page.wait_for_selector("div.quote", timeout=10000)

        cards = page.query_selector_all("div.quote")
        for card in cards:
            text_el = card.query_selector("span.text")
            author_el = card.query_selector("small.author")
            tag_els = card.query_selector_all("div.tags a.tag")

            text = text_el.inner_text().strip() if text_el else "N/A"
            author = author_el.inner_text().strip() if author_el else "N/A"
            tags = [t.inner_text().strip() for t in tag_els]

            quotes.append(QuoteRecord(text=text, author=author, tags=tags))

        browser.close()

    return quotes


if __name__ == "__main__":
    scrape_dynamic_quotes()
```

---

# 5. Top 3 Real-World Pitfalls & How to Fix Them

### Pitfall 1: `Playwright.TimeoutError: Timeout 30000ms exceeded while waiting for event "load"`
- **Cause:** Third-party ads, font CDNs, or telemetry pixels keep network connection open indefinitely.
- **2-Line Fix:**
```python
# Wait for DOM content loaded instead of full network silence:
page.goto(url, wait_until="domcontentloaded", timeout=15000)
```

### Pitfall 2: Zombie Browser Processes Consuming High RAM
- **Cause:** Script crashes mid-scrape before reaching `browser.close()`.
- **2-Line Fix:**
```python
# Always use the context manager wrapper:
with sync_playwright() as p:
    with p.chromium.launch() as browser: ...
```

### Pitfall 3: Stale Element Handle After DOM Re-render
- **Cause:** React re-renders the DOM, invalidating previously fetched `ElementHandle` references.
- **2-Line Fix:**
```python
# Extract primitive strings immediately or use locators instead of holding raw ElementHandles:
page.locator("div.quote >> nth=0").inner_text()
```
