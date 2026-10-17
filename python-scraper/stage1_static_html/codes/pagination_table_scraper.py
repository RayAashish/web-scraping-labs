"""
Author: raysi
Practical Stage 1 Demo: Multi-page crawler in Python traversing pagination links,
resolving relative paths with urllib.parse.urljoin, and preventing infinite loops.
"""

import random
import re
import time
from dataclasses import dataclass
from urllib.parse import urljoin
import httpx
from bs4 import BeautifulSoup, Tag


@dataclass(frozen=True)
class BookListing:
    title: str
    price: float
    availability: str
    source_url: str


def parse_price(raw_text: str) -> float:
    cleaned = re.sub(r"[^0-9.]", "", raw_text)
    return float(cleaned) if cleaned else 0.0


def polite_sleep(min_sec: float = 1.0, max_sec: float = 2.5) -> None:
    time.sleep(random.uniform(min_sec, max_sec))


def crawl_paginated_catalog(max_pages: int = 3) -> list[BookListing]:
    current_url: str | None = "https://books.toscrape.com/catalogue/page-1.html"
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "en-US,en;q=0.9",
    }

    scraped_items: list[BookListing] = []
    visited_urls: set[str] = set()
    page_count = 0

    print(f"Starting multi-page scraper for up to {max_pages} pages...")

    with httpx.Client(headers=headers, timeout=12.0, follow_redirects=True) as client:
        while current_url and page_count < max_pages:
            if current_url in visited_urls:
                print(f"Cycle detected for {current_url}. Stopping crawl.")
                break

            visited_urls.add(current_url)
            page_count += 1
            print(f"\n[Scraping Page {page_count}] {current_url}")

            try:
                response = client.get(current_url)
                response.raise_for_status()

                soup = BeautifulSoup(response.text, "html.parser")
                cards = soup.select("article.product_pod")

                for card in cards:
                    title_tag = card.select_one("h3 a")
                    title = title_tag.get("title", "").strip() if title_tag else "N/A"

                    price_tag = card.select_one("p.price_color")
                    price = parse_price(price_tag.get_text(strip=True)) if price_tag else 0.0

                    stock_tag = card.select_one("p.instock.availability")
                    availability = stock_tag.get_text(strip=True) if stock_tag else "N/A"

                    scraped_items.append(
                        BookListing(
                            title=title,
                            price=price,
                            availability=availability,
                            source_url=current_url,
                        )
                    )

                # Locate next page link
                next_button = soup.select_one("li.next a")
                if next_button and next_button.get("href"):
                    relative_next = next_button["href"]
                    # Resolve relative URL safely
                    current_url = urljoin(current_url, relative_next)
                else:
                    print("No 'next' page link detected. Reached end of catalog.")
                    current_url = None

                if current_url:
                    polite_sleep(1.0, 2.5)

            except httpx.HTTPError as err:
                print(f"HTTP request error: {err}")
                break

    print(f"\n================ CRAWL SUMMARY ================")
    print(f"Total pages crawled: {page_count}")
    print(f"Total books captured: {len(scraped_items)}")
    for item in scraped_items[:5]:
        print(f"Sample: {item.title} -> £{item.price:.2f}")

    return scraped_items


if __name__ == "__main__":
    crawl_paginated_catalog()
