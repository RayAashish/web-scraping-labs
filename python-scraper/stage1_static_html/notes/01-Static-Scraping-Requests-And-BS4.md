# Stage 1: Static Scraping with HTTPX & BeautifulSoup4 (Python)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use This Approach When:**
  - The website serves static or **Server-Side Rendered (SSR)** HTML (WordPress, Wikipedia, classic directory portals, Shopify product pages).
  - You open **View Page Source (`Ctrl+U`)** and the required text/numbers are already present in the raw HTML returned by the server.
  - You want lightweight, fast extraction with minimal CPU/RAM footprint (running hundreds of requests per minute).

- **AVOID This Approach When:**
  - **A hidden JSON API exists:** If DevTools -> Network -> Fetch/XHR reveals the frontend is pulling data from a JSON endpoint, use `httpx.get()` directly on that endpoint (Stage 2).
  - **The page is a Client-Side Rendered (CSR) SPA:** If the page source contains only an empty `<div id="root"></div>` and requires a full JavaScript engine to execute React/Vue/Angular, you must use **Playwright** (Stage 3).
  - **Complex bot walls exist:** If the site triggers Cloudflare Turnstile or perimeter challenges on initial TLS handshake, basic Python HTTP clients will be blocked.

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | Python HTTPX / BeautifulSoup4 Pattern | Engineering Best Practice |
|---|---|---|
| **Initialize Client with Headers** | `client = httpx.Client(headers={"User-Agent": "..."}, timeout=10.0)` | Always reuse a client session for HTTP keep-alive connection pooling. |
| **Parse HTML with BS4** | `soup = BeautifulSoup(response.text, "html.parser")` | Use `"lxml"` if high-speed parsing of massive DOM trees is needed. |
| **Select Multiple Elements** | `cards = soup.select("article.product_pod")` | Returns a list of `Tag` objects matching CSS selector. |
| **Safely Select Single Element** | `el = card.select_one("h3 > a")` | Always verify `if el:` before accessing attributes or text. |
| **Extract Clean Text** | `text = el.get_text(strip=True)` | Strips leading/trailing whitespace and normalizes whitespace inside. |
| **Extract Attribute Safely** | `attr_val = el.get("title", "N/A")` | `.get()` avoids `KeyError` if attribute is absent on certain tags. |
| **Resolve Relative URL** | `from urllib.parse import urljoin; abs_url = urljoin(base, rel)` | Resolves `href="page-2.html"` into full absolute URL. |
| **Extract Embedded JSON Script** | `data = json.loads(soup.select_one("script#__NEXT_DATA__").string)` | Instantly grabs complete state payload from Next.js SSR apps. |
| **Regex Numerical Sanitization**| `cleaned = re.sub(r"[^0-9.]", "", raw_price)` | Strips symbols like `$`, `£`, `,` before float conversion. |
| **Polite Jitter Delay** | `time.sleep(random.uniform(1.5, 3.0))` | Randomizes inter-request intervals to avoid robotic bot fingerprints. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Inspect vs. View Source Verification
Open Chrome DevTools (`F12`), press `Ctrl + U` (View Source), and search for the target price or title:
- If present in `Ctrl + U`, confirm the selector container in Elements tab.
- Identify the parent repeating card container (e.g., `<article class="product_pod">`).

### Step 2: Formulate Break-Proof Selectors
Avoid machine-generated hashed classes produced by CSS Modules or styled-components (e.g., `.sc-1g4k9x-1` or `.css-1dbjc4n`).
- **Fragile Selector:** `div.css-1dbjc4n > div:nth-child(3) > span` (breaks on the next frontend build).
- **Resilient Selector:** `article.product_pod h3 > a[title]` or `[data-component='price-tag']`.

### Step 3: Handle Irregular / Missing Fields Gracefully
Never call `.get_text()` directly on a query result without a guard:
```python
# ❌ CRASH: Throws AttributeError: 'NoneType' object has no attribute 'get_text'
discount = card.select_one(".discount-badge").get_text()

# ✅ RESILIENT: Default fallback guarantees zero crash rate across 100k items
discount_tag = card.select_one(".discount-badge")
discount = discount_tag.get_text(strip=True) if discount_tag else "0%"
```

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`bs4_css_selector_scraper.py`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/python-scraper/stage1_static_html/codes/bs4_css_selector_scraper.py):

```python
"""
Author: raysi
Practical Stage 1 Demo: Extracting structured e-commerce data from static HTML
using BeautifulSoup4, HTTPX, resilient CSS selectors, and null-safe helpers.
"""

import random
import re
import time
from dataclasses import dataclass
import httpx
from bs4 import BeautifulSoup, Tag


@dataclass(frozen=True)
class BookProduct:
    title: str
    price: float
    availability: str


def extract_text_safely(parent: Tag, css_query: str, default: str = "N/A") -> str:
    el = parent.select_one(css_query)
    return el.get_text(strip=True) if el else default


def extract_attr_safely(parent: Tag, css_query: str, attr_key: str, default: str = "N/A") -> str:
    el = parent.select_one(css_query)
    if el and el.has_attr(attr_key):
        val = el[attr_key]
        return val.strip() if isinstance(val, str) else " ".join(val).strip()
    return default


def parse_price_safely(raw_price: str) -> float:
    cleaned = re.sub(r"[^0-9.]", "", raw_price)
    return float(cleaned) if cleaned else 0.0


def scrape_books_catalog() -> list[BookProduct]:
    target_url = "https://books.toscrape.com/catalogue/category/books/travel_2/index.html"
    headers = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Accept-Language": "en-US,en;q=0.9",
    }

    catalog: list[BookProduct] = []

    with httpx.Client(headers=headers, timeout=10.0, follow_redirects=True) as client:
        response = client.get(target_url)
        response.raise_for_status()

        soup = BeautifulSoup(response.text, "html.parser")
        product_cards = soup.select("article.product_pod")

        for card in product_cards:
            title = extract_attr_safely(card, "h3 > a", "title")
            raw_price = extract_text_safely(card, "p.price_color")
            price = parse_price_safely(raw_price)
            availability = extract_text_safely(card, "p.instock.availability")

            catalog.append(BookProduct(title=title, price=price, availability=availability))

        print(f"Extracted {len(catalog)} books successfully:")
        for item in catalog:
            print(f"• {item.title} | £{item.price:.2f} | {item.availability}")

    return catalog


if __name__ == "__main__":
    scrape_books_catalog()
```

---

# 5. Top 3 Real-World Pitfalls & How to Fix Them

### Pitfall 1: `AttributeError: 'NoneType' object has no attribute 'get_text'`
- **Cause:** Assuming every card has the same structure; 1 item missing a badge or discount crashes the crawler.
- **2-Line Fix:**
```python
tag = parent.select_one(css_selector)
value = tag.get_text(strip=True) if tag else "N/A"
```

### Pitfall 2: Broken Relative Pagination (`href="catalogue/page-2.html"`)
- **Cause:** Appending relative links via basic string concatenation (`base_url + href`) creates malformed paths (`site.com/catalogue/page-1.htmlcatalogue/page-2.html`).
- **2-Line Fix:**
```python
from urllib.parse import urljoin
next_url = urljoin(current_url, next_tag["href"])
```

### Pitfall 3: `httpx.HTTPStatusError: 403 Forbidden`
- **Cause:** Sending requests with default Python user agents (`python-httpx/...` or `python-requests/...`), which are blocked by default WAF rules.
- **2-Line Fix:**
```python
client = httpx.Client(headers={"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"})
```
