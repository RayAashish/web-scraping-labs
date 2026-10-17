"""
Author: raysi
Practical Stage 4 Demo: High-throughput, polite asynchronous web scraping
using asyncio, HTTPX AsyncClient, Semaphore concurrency throttling, and jittered exponential backoff.
"""

import asyncio
import random
import time
from dataclasses import dataclass
from typing import Optional
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
    """Fetches target page using a Semaphore permit with exponential backoff on 429/503."""
    async with semaphore:
        attempt = 0
        while True:
            attempt += 1
            start_time = time.perf_counter()

            # Add polite random jitter (300ms to 900ms) before hitting network
            await asyncio.sleep(random.uniform(0.3, 0.9))

            try:
                response = await client.get(url)
                duration_ms = int((time.perf_counter() - start_time) * 1000)

                # Success
                if 200 <= response.status_code < 300:
                    soup = BeautifulSoup(response.text, "html.parser")
                    page_title = soup.title.get_text(strip=True) if soup.title else "Untitled"
                    return ScrapedPageSummary(
                        url=url,
                        status_code=response.status_code,
                        title=page_title,
                        duration_ms=duration_ms,
                    )

                # Retryable status codes (429 Too Many Requests, 503 Service Unavailable)
                if response.status_code in (429, 502, 503, 504):
                    if attempt > max_retries:
                        print(f"[Attempt {attempt}/{max_retries}] Exceeded retries for {url} (HTTP {response.status_code})")
                        return ScrapedPageSummary(url=url, status_code=response.status_code, title="Failed", duration_ms=duration_ms)

                    backoff = (base_delay_sec * (2 ** (attempt - 1))) + random.uniform(0.2, 0.8)
                    print(f"[Attempt {attempt}/{max_retries}] HTTP {response.status_code} on {url}. Backing off {backoff:.2f}s...")
                    await asyncio.sleep(backoff)
                    continue

                return ScrapedPageSummary(url=url, status_code=response.status_code, title="Client Error", duration_ms=duration_ms)

            except (httpx.RequestError, httpx.TimeoutException) as exc:
                if attempt > max_retries:
                    print(f"Connection failed after {max_retries} attempts: {exc}")
                    return ScrapedPageSummary(url=url, status_code=500, title="Connection Error", duration_ms=0)

                backoff = (base_delay_sec * (2 ** (attempt - 1))) + random.uniform(0.2, 0.8)
                print(f"[Attempt {attempt}/{max_retries}] Network error ({exc}). Backing off {backoff:.2f}s...")
                await asyncio.sleep(backoff)


async def main() -> None:
    urls = [f"https://books.toscrape.com/catalogue/page-{i}.html" for i in range(1, 11)]
    # Limit simultaneous active connections to 3 to stay polite
    concurrency_limit = asyncio.Semaphore(3)

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "en-US,en;q=0.9",
    }

    print(f"Starting async scraper for {len(urls)} URLs with Semaphore limit=3...")
    start_total = time.perf_counter()

    async with httpx.AsyncClient(headers=headers, timeout=10.0) as client:
        tasks = [fetch_page_with_retry(client, url, concurrency_limit) for url in urls]
        results: list[ScrapedPageSummary] = await asyncio.gather(*tasks)

    total_time = (time.perf_counter() - start_total) * 1000
    print("\n================ ASYNC SCRAPING RUN COMPLETE ================")
    print(f"Total time elapsed: {total_time:.0f} ms | Pages processed: {len(results)}")
    for r in results:
        print(f"• [{r.status_code}] {r.url} (took {r.duration_ms}ms) -> Title: {r.title[:40]}...")


if __name__ == "__main__":
    asyncio.run(main())
