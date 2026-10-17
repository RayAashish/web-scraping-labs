# Stage 2: Intercepting Hidden JSON APIs (Python)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use This Approach When:**
  - Chrome DevTools (`F12`) -> **Network** tab -> **Fetch/XHR** filter reveals clean background JSON requests feeding the frontend.
  - The application is a modern Single Page App (React, Next.js, Vue, Angular) where the frontend consumes internal microservices.
  - You want **50x faster scraping** with fractional memory usage, bypassing CSS selector extraction and HTML parsing entirely.

- **AVOID This Approach When:**
  - The website is pure server-side rendered HTML and makes zero XHR API requests (use Stage 1 BS4).
  - The API requests require complex dynamic cryptographic signatures (HMACs generated in obfuscated WebAssembly or dynamic canvas nonces) that take hours to reverse-engineer. In those cases, use Stage 3 Playwright.
  - The endpoint is blocked by an aggressive TLS fingerprinting WAF (Cloudflare Turnstile) that drops non-browser TLS handshakes.

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | Python HTTPX / Pydantic Pattern | Engineering Best Practice |
|---|---|---|
| **Reusable Session Client** | `client = httpx.Client(headers=headers, timeout=10.0)` | Reuses TCP connections; auto-manages cookies across requests. |
| **Fetch & Parse JSON Directly** | `data = client.get(url).json()` | Automatically deserializes JSON string into native Python dictionaries/lists. |
| **Validate with Pydantic** | `ItemModel.model_validate(response.json())` | Ensures strict typing and catches schema regressions instantly. |
| **Ignore Unexpected API Fields** | `model_config = ConfigDict(extra="ignore")` | Prevents crashes when backend teams introduce new fields. |
| **Pass Query Parameters Safely**| `client.get(url, params={"page": 2, "limit": 50})` | Automatically encodes URL query parameters properly. |
| **Pass JSON Body in POST** | `client.post(url, json={"query": "laptops", "sortBy": "price"})` | Automatically sets `Content-Type: application/json`. |
| **Spoof Fetch Metadata Headers**| `"Sec-Fetch-Mode": "cors", "Sec-Fetch-Site": "same-origin"` | Essential for mimicking authentic browser API calls. |
| **Check HTTP Status Gracefully**| `if resp.status_code == 429: time.sleep(backoff)` | Avoids terminating entire crawl on transient rate limits. |
| **Copy as cURL Replay** | DevTools -> Copy -> Copy as cURL | Quickest way to isolate minimal required headers in CLI. |
| **Extract Embedded Next.js Data**| `re.search(r'<script id="__NEXT_DATA__".*?>(.*?)</script>', html)` | Grabs server-injected state JSON when no direct API exists. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Filter Network Traffic in DevTools
1. Open DevTools (`F12`), switch to the **Network** tab, and select **Fetch/XHR**.
2. Click through the page (e.g. click "Next Page" or "Filter by Category").
3. Inspect responses in the **Preview** subtab. Look for arrays of data dictionaries.

### Step 2: Discover the Minimal Headers
1. Right-click the request -> **Copy** -> **Copy as cURL**.
2. Run the command in your terminal.
3. Test removing headers one by one. You will find that most APIs only require:
   - `User-Agent`
   - `Referer` or `Origin`
   - Session Cookie or `Authorization` header (if authenticated)

### Step 3: Implement Client with Data Validation
Wrap the endpoint with `httpx.Client()` and parse records into Pydantic models with `extra="ignore"`.

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`xhr_json_api_extractor.py`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/python-scraper/stage2_hidden_apis/codes/xhr_json_api_extractor.py):

```python
"""
Author: raysi
Practical Stage 2 Demo: Reverse-engineering the DevTools Network Tab (Fetch/XHR)
to query backend JSON endpoints directly in Python using HTTPX and structured models.
"""

import random
import time
from typing import Any
import httpx
from pydantic import BaseModel, ConfigDict


class ProductItem(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: int
    title: str
    price: float
    rating: float
    category: str
    stock: int


class ProductApiResponse(BaseModel):
    model_config = ConfigDict(extra="ignore")
    products: list[ProductItem]
    total: int
    skip: int
    limit: int


def fetch_api_catalog() -> list[ProductItem]:
    endpoint = "https://dummyjson.com/products"
    params = {"limit": 5, "select": "title,price,rating,category,stock"}
    headers = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
        "Accept": "application/json, text/plain, */*",
        "Referer": "https://dummyjson.com/",
    }

    products: list[ProductItem] = []

    with httpx.Client(headers=headers, timeout=10.0) as client:
        response = client.get(endpoint, params=params)

        if response.status_code == 200:
            payload: dict[str, Any] = response.json()
            api_data = ProductApiResponse.model_validate(payload)
            products = api_data.products

            print(f"Successfully fetched {len(products)} records (Total: {api_data.total}):")
            for p in products:
                print(f"• [{p.category}] {p.title} | ${p.price:.2f} | Rating: {p.rating}★")

    return products


if __name__ == "__main__":
    fetch_api_catalog()
```

---

# 5. Top 3 Real-World Pitfalls & How to Fix Them

### Pitfall 1: `HTTP 403 Forbidden` from Missing CORS / Referer Headers
- **Cause:** Backend API checks the `Referer` or `Origin` header to verify request originates from their frontend application.
- **2-Line Fix:**
```python
client.headers.update({"Referer": "https://targetsite.com/shop", "Origin": "https://targetsite.com"})
```

### Pitfall 2: `pydantic_core.ValidationError: Extra inputs are not permitted`
- **Cause:** Backend adds new or unmapped fields to JSON response, crashing default strict validators.
- **2-Line Fix:**
```python
class MyModel(BaseModel):
    model_config = ConfigDict(extra="ignore")
```

### Pitfall 3: Expired Authentication Token / CSRF Mismatch
- **Cause:** Long crawls fail after 15–30 minutes because authentication cookie or CSRF token invalidated.
- **2-Line Fix:**
```python
if response.status_code in (401, 403):
    client.cookies.clear(); refresh_session(client)
```
