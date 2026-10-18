# Web Scraping Mastery (Java & Python)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

A comprehensive, production-grade repository demonstrating web scraping workflows across both **Java 21 (JSoup, HttpClient, Playwright, Virtual Threads)** and **Python 3.11+ (HTTPX, BeautifulSoup4, Playwright, Pandas, Asyncio)**.

---

## ⚠️ The Pre-Flight Checklist: 6 Things to Check BEFORE Scraping a Website

Never write a scraper blindly. Before sending a single HTTP request, spend 3 minutes running through this checklist to save hours of wasted effort, IP bans, or legal trouble:

### 1. Check `robots.txt` & Terms of Service (Legal & Ethical Boundaries)
* Append `/robots.txt` to the root domain (e.g., `https://example.com/robots.txt`).
* Look at `User-agent: *`, `Disallow:` paths, and `Crawl-delay:` directives.
* **Golden Rule:** Never scrape Personal Identifiable Information (PII), private data behind a paid login wall without permission, or copyrighted content for commercial republication.

### 2. Inspect the `Network` Tab (Fetch/XHR) Before Parsing HTML!
* Beginners immediately parse messy HTML tags. Pros open **Chrome DevTools (`F12`) -> Network Tab -> Filter by `Fetch/XHR`**, then reload or scroll the page.
* **Why:** 70% of modern e-commerce and directory websites load data via clean background JSON APIs. If you find the underlying API endpoint, you can skip HTML parsing and headless browsers entirely, fetching structured JSON 50x faster.

### 3. Check `View Page Source` (`Ctrl + U`) vs. `Inspect Element`
* Right-click the page and click **View Page Source** (not Inspect Element). Search (`Ctrl + F`) for a piece of text visible on the screen (like a product price).
* **If it IS in the page source:** The site is Server-Side Rendered (SSR). Use fast, lightweight HTTP + HTML parsers (**Java `JSoup`** or **Python `HTTPX/BeautifulSoup`**).
* **If it is NOT in the page source:** The data is injected dynamically via JavaScript. You either need to find the hidden XHR API (Step 2) or use a Headless Browser (**Playwright**).

### 4. Look for Embedded State JSON (`__NEXT_DATA__` or `application/ld+json`)
* Even on static HTML pages, check the `<script>` tags in the page source.
* Many modern sites (built on Next.js, Nuxt, or Shopify) dump the entire page's database object into a single `<script id="__NEXT_DATA__" type="application/json">` or SEO `<script type="application/ld+json">` tag. Extracting that one tag gives you every field cleanly without writing 20 CSS selectors.

### 5. Identify Anti-Bot & WAF Protections Early
* Check the response headers in DevTools (`Server: cloudflare`, `x-datadome`, `akamai`).
* If a site uses aggressive WAF challenges (Cloudflare Turnstile, CAPTCHAs), a basic `HttpClient` or `requests.get()` with default User-Agents (`Java-http-client/...` or `python-requests/...`) will get blocked on request #1. Always send realistic browser headers (`User-Agent`, `Accept-Language`, `Referer`).

### 6. Respect Server Load (Rate-Limiting & Exponential Backoff)
* Scraping too fast is indistinguishable from a DDoS attack and will get your IP banned.
* Always implement:
  1. **Randomized Jitter Delays** (e.g., sleeping 1.5 to 3.5 seconds between requests instead of a robotic exact 1.0s).
  2. **HTTP Status Handling:** Immediately pause and back off if you receive `429 Too Many Requests` or `503 Service Unavailable`.
  3. **Local Caching:** Save raw HTML/JSON responses locally while developing your parser so you don't hit the live website 50 times while fixing a typo in your CSS selector.

---

## 📁 Repository Blueprint

```
web-scraping-mastery/
├── README.md                                        # Master Guide: Legal/Ethical Rules & Pre-Scrape Checklist
│
├── java-scraper/                                    # 1. Java Ecosystem (JSoup, HttpClient, Playwright, Virtual Threads)
│   ├── pom.xml                                      # Maven dependencies (jsoup, playwright, jackson)
│   └── src/main/java/com/raysi/scraper/
│       ├── stage1_static_html/                      # Server-Side Rendered (SSR) Pages
│       │   ├── codes/
│       │   │   ├── JsoupCssSelectorExtractor.java
│       │   │   └── PaginationTableScraper.java
│       │   └── notes/
│       │       └── 01-Static-DOM-Parsing-With-JSoup.md
│       │
│       ├── stage2_hidden_apis/                      # Reverse-Engineering XHR/Fetch JSON APIs
│       │   ├── codes/
│       │   │   ├── NetworkTabApiDirectCaller.java
│       │   │   └── CustomHeadersAndCookiesScraper.java
│       │   └── notes/
│       │       └── 02-Reverse-Engineering-Hidden-APIs.md
│       │
│       ├── stage3_dynamic_js/                       # Client-Side Rendered (React, Infinite Scroll)
│       │   ├── codes/
│       │   │   ├── PlaywrightHeadlessScraper.java
│       │   │   └── InfiniteScrollAndWaitHandler.java
│       │   └── notes/
│       │       └── 03-Headless-Browsers-With-Playwright.md
│       │
│       └── stage4_production_pipeline/              # Concurrency, Anti-Ban & Export
│           ├── codes/
│           │   ├── PoliteRetryAndJitterClient.java
│           │   ├── VirtualThreadsConcurrentScraper.java
│           │   └── CsvAndJsonExporter.java
│           └── notes/
│               └── 04-AntiBan-Politeness-And-Data-Cleaning.md
│
└── python-scraper/                                  # 2. Python Ecosystem (HTTPX, BS4, Playwright, Pandas)
    ├── requirements.txt                             # beautifulsoup4, httpx, playwright, pandas, pydantic
    ├── stage1_static_html/
    │   ├── codes/
    │   │   ├── bs4_css_selector_scraper.py
    │   │   └── pagination_table_scraper.py
    │   └── notes/
    │       └── 01-Static-Scraping-Requests-And-BS4.md
    │
    ├── stage2_hidden_apis/
    │   ├── codes/
    │   │   ├── xhr_json_api_extractor.py
    │   │   └── session_cookies_and_headers.py
    │   └── notes/
    │       └── 02-Intercepting-Hidden-JSON-APIs.md
    │
    ├── stage3_dynamic_js/
    │   ├── codes/
    │   │   ├── playwright_dynamic_scraper.py
    │   │   └── infinite_scroll_handler.py
    │   └── notes/
    │       └── 03-Dynamic-JS-Rendering-With-Playwright.md
    │
    └── stage4_production_pipeline/
        ├── codes/
        │   ├── async_httpx_rate_limited_scraper.py
        │   └── pandas_cleaning_and_export.py
        └── notes/
            └── 04-Async-Pipelines-And-AntiBot-Basics.md
```

---

## 🧭 The 4-Stage Architecture Matrix

| Stage | Target Page Type | Java Stack | Python Stack | Cost & Overhead | Speed (req/s) |
|---|---|---|---|---|---|
| **Stage 1: Static HTML** | Server-Side Rendered (SSR), raw HTML | `JSoup` | `HTTPX` + `BeautifulSoup4` | Low (CPU & network only) | 50 - 200 |
| **Stage 2: Hidden APIs** | SPAs / dynamic sites with backend JSON APIs | `java.net.http.HttpClient` + `Jackson` | `HTTPX` + `Pydantic` | Extremely low (bypasses HTML parsing) | 100 - 500+ |
| **Stage 3: Dynamic JS** | Heavy React/Vue/Angular, DOM hydration, canvas, bots | `Playwright Java` | `Playwright Python` | High (launches browser processes) | 2 - 10 |
| **Stage 4: Pipeline** | Scale, resilience, anti-ban politeness, data export | Virtual Threads (Java 21) + Semaphore | Asyncio + HTTPX + Pandas | Scalable, resilient | Configurable |

---

## 🚀 Quickstart Guides

### ☕ Java Ecosystem (`java-scraper/`)
Requires **Java 21+** and **Maven 3.9+**.

```bash
cd java-scraper

# Compile project
mvn clean compile

# Execute Stage 1 Jsoup CSS Selector Extractor
mvn exec:java -Dexec.mainClass="com.raysi.scraper.stage1_static_html.codes.JsoupCssSelectorExtractor"

# Execute Stage 2 Hidden API Direct Caller
mvn exec:java -Dexec.mainClass="com.raysi.scraper.stage2_hidden_apis.codes.NetworkTabApiDirectCaller"

# Execute Stage 4 Virtual Threads Scraper
mvn exec:java -Dexec.mainClass="com.raysi.scraper.stage4_production_pipeline.codes.VirtualThreadsConcurrentScraper"
```

### 🐍 Python Ecosystem (`python-scraper/`)
Requires **Python 3.10+**.

```bash
cd python-scraper

# Create virtual environment & activate
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Install Playwright browser binaries (one-time setup)
playwright install chromium

# Run Stage 1 Static Scraper
python stage1_static_html/codes/bs4_css_selector_scraper.py

# Run Stage 2 Hidden API Extractor
python stage2_hidden_apis/codes/xhr_json_api_extractor.py

# Run Stage 4 Async HTTPX Pipeline
python stage4_production_pipeline/codes/async_httpx_rate_limited_scraper.py
```

---

## ⚖️ Legal & Ethical Scraping Guidelines
1. **Private vs. Public:** Only scrape publicly accessible data. If data requires user authentication or bypassing a paywall, obtain written authorization.
2. **Frequency:** Do not hammer target servers. Keep requests within reasonable human thresholds with jittered delays.
3. **Identification:** Set a clear `User-Agent` contact string in production crawlers (`User-Agent: MyScraperBot/1.0 (+https://example.com/contact)`).
4. **Data Retention:** Comply with regional data privacy laws (GDPR, CCPA) regarding personal information.
