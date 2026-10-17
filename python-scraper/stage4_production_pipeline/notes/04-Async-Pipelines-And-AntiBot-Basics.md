# Stage 4: Async Pipelines & Anti-Bot Basics (Python)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use This Approach When:**
  - You need to scrape hundreds or thousands of static or API URLs concurrently with minimal execution time.
  - You want non-blocking asynchronous network I/O (`asyncio` + `httpx.AsyncClient`) with strict concurrency limits (`asyncio.Semaphore`).
  - Target servers return transient HTTP `429 Too Many Requests` or `503 Service Unavailable`, demanding jittered exponential backoff and polite crawling policies.
  - You need to clean, transform, deduplicate, and persist unstructured raw records using **Pandas**.

- **AVOID This Approach When:**
  - You are running a small, 5-page one-off extraction: Writing async loops, gather tasks, and semaphore controls is unnecessary complexity.
  - The website requires JavaScript execution for every single page: Async HTTP clients do not execute JavaScript. You must manage browser contexts with Playwright instead.

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | Python Async / Pandas Pattern | Engineering Best Practice |
|---|---|---|
| **Reusable Async Client** | `async with httpx.AsyncClient(timeout=10.0) as client:` | Maintains persistent HTTP keep-alive connection pool. |
| **Throttle Concurrency** | `semaphore = asyncio.Semaphore(5)` | Prevents outbound socket exhaustion and server DDoS. |
| **Gather Async Tasks** | `results = await asyncio.gather(*tasks)` | Fires non-blocking requests concurrently and awaits list. |
| **Polite Randomized Jitter** | `await asyncio.sleep(random.uniform(0.5, 1.5))` | Non-blocking sleep that breaks robotic request cadences. |
| **Exponential Backoff Formula** | `delay = base_delay * (2 ** attempt) + random.uniform(0.2, 0.8)` | Progressively slows request frequency upon hitting 429 limits. |
| **Clean Malformed Strings** | `df["price"] = df["raw_price"].str.replace(r"[^\d.]", "", regex=True).astype(float)` | Sanitizes currency symbols across entire column vectorized. |
| **Deduplicate Records** | `df.drop_duplicates(subset=["id"], keep="last", inplace=True)` | Prevents storing identical rows from paginated overlaps. |
| **Fill Missing Data** | `df["category"] = df["category"].fillna("Uncategorized")` | Eliminates downstream database null constraint failures. |
| **Export to Clean CSV** | `df.to_csv("output.csv", index=False)` | Generates clean CSV without unneeded numerical index column. |
| **Export to Formatted JSON** | `df.to_json("output.json", orient="records", indent=2)` | Serializes rows as standard list of dictionaries. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Benchmark and Regulate Concurrency
Never run unconstrained `asyncio.gather(*[client.get(url) for url in 10000_urls])`. Unbounded concurrency triggers instant IP blacklisting and exhausts OS file descriptors.
Always wrap the request in `async with semaphore:`.

### Step 2: Implement Jittered Retry Handling
Handle transient HTTP status codes (`429`, `502`, `503`, `504`):
- Increment retry counter up to 3 times.
- Calculate backoff with added random jitter so simultaneous requests do not retry in synchronization (avoiding the "thundering herd" problem).

### Step 3: Sanitize with Pandas Vectorized Operations
Rather than manually looping over Python dictionaries:
1. Load list of dicts directly into `df = pd.DataFrame(raw_records)`.
2. Strip whitespace with `.str.strip()`.
3. Normalize numbers and booleans with `.astype()`.
4. Export directly to CSV and JSON formats.

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`async_httpx_rate_limited_scraper.py`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/python-scraper/stage4_production_pipeline/codes/async_httpx_rate_limited_scraper.py):

```python
"""
Author: raysi
Practical Stage 4 Demo: High-throughput, polite asynchronous web scraping
using asyncio, HTTPX AsyncClient, Semaphore concurrency throttling, and jittered exponential backoff.
"""

import asyncio
import random
import time
from dataclasses import dataclass
import httpx
from bs4 import BeautifulSoup


@dataclass(frozen=True)
class ScrapedPageSummary:
    url: str
    status_code: int
    title: str
    duration_ms: int


async def fetch_page_with_retry(
    client: httpx.AsyncClient,
    url: str,
    semaphore: asyncio.Semaphore,
    max_retries: int = 3,
    base_delay_sec: float = 1.0,
) -> ScrapedPageSummary:
    async with semaphore:
        attempt = 0
        while True:
            attempt += 1
            start_time = time.perf_counter()
            await asyncio.sleep(random.uniform(0.3, 0.9))

            try:
                response = await client.get(url)
                duration_ms = int((time.perf_counter() - start_time) * 1000)

                if 200 <= response.status_code < 300:
                    soup = BeautifulSoup(response.text, "html.parser")
                    title = soup.title.get_text(strip=True) if soup.title else "Untitled"
                    return ScrapedPageSummary(url, response.status_code, title, duration_ms)

                if response.status_code in (429, 502, 503, 504) and attempt <= max_retries:
                    backoff = (base_delay_sec * (2 ** (attempt - 1))) + random.uniform(0.2, 0.8)
                    await asyncio.sleep(backoff)
                    continue

                return ScrapedPageSummary(url, response.status_code, "Failed", duration_ms)

            except (httpx.RequestError, httpx.TimeoutException) as exc:
                if attempt > max_retries:
                    return ScrapedPageSummary(url, 500, "Error", 0)
                await asyncio.sleep((base_delay_sec * (2 ** (attempt - 1))) + 0.5)


async def main() -> None:
    urls = [f"https://books.toscrape.com/catalogue/page-{i}.html" for i in range(1, 6)]
    semaphore = asyncio.Semaphore(2)
    headers = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"}

    async with httpx.AsyncClient(headers=headers, timeout=10.0) as client:
        tasks = [fetch_page_with_retry(client, url, semaphore) for url in urls]
        results = await asyncio.gather(*tasks)

    for r in results:
        print(f"• [{r.status_code}] {r.url} (took {r.duration_ms}ms) -> Title: {r.title}")


if __name__ == "__main__":
    asyncio.run(main())
```

---

# 5. Top 3 Real-World Pitfalls & How to Fix Them

### Pitfall 1: `RuntimeError: Event loop is closed` / Resource Leaks
- **Cause:** Using `httpx.AsyncClient()` without an `async with` context manager or mixing asyncio runtimes across threads.
- **2-Line Fix:**
```python
async with httpx.AsyncClient() as client:
    response = await client.get(url)
```

### Pitfall 2: Memory Overflow with Millions of Rows in Memory
- **Cause:** Reading all extracted pages into a single giant DataFrame before writing, blowing past system RAM limits.
- **2-Line Fix:**
```python
# Stream batches directly to CSV with mode='a' (append) and header=False:
df.to_csv("master_dataset.csv", mode="a", index=False, header=not os.path.exists("master_dataset.csv"))
```

### Pitfall 3: Thundering Herd Problem on Exponential Backoff
- **Cause:** 50 concurrent workers all receive HTTP 429 at the exact same second, all sleep for exactly 4.0s, and all retry at the exact same millisecond.
- **2-Line Fix:**
```python
# Always inject random jitter into every backoff calculation:
jittered_delay = (base_delay * (2 ** attempt)) + random.uniform(0.5, 2.0)
```
