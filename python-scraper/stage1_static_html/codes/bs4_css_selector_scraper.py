"""
Author: raysi
Practical Stage 1 Demo: Extracting structured e-commerce data from static HTML
using BeautifulSoup4, HTTPX, resilient CSS selectors, and null-safe helpers.
"""

import random
import re
import time
from dataclasses import dataclass
from typing import Optional
import httpx
from bs4 import BeautifulSoup, Tag


@dataclass(frozen=True)
class BookProduct:
    title: str
    price: float
    availability: str


def extract_text_safely(parent: Tag, css_query: str, default: str = "N/A") -> str:
    """Safely extracts stripped inner text, preventing AttributeError if element is missing."""
    el = parent.select_one(css_query)
    return el.get_text(strip=True) if el else default


def extract_attr_safely(parent: Tag, css_query: str, attr_key: str, default: str = "N/A") -> str:
    """Safely extracts tag attribute value (e.g. href, title, src) without crashing."""
    el = parent.select_one(css_query)
    if el and el.has_attr(attr_key):
        val = el[attr_key]
        return val.strip() if isinstance(val, str) else " ".join(val).strip()
    return default


def parse_price_safely(raw_price: str) -> float:
    """Strips currency symbols ($, £, €) and parses float without ValueError."""
    cleaned = re.sub(r"[^0-9.]", "", raw_price)
    return float(cleaned) if cleaned else 0.0


def polite_sleep(min_sec: float = 1.5, max_sec: float = 3.0) -> None:
    """Randomized delay preventing robotic crawling cadence."""
    delay = random.uniform(min_sec, max_sec)
    time.sleep(delay)


def scrape_books_catalog() -> list[BookProduct]:
    target_url = "https://books.toscrape.com/catalogue/category/books/travel_2/index.html"
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "en-US,en;q=0.9",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    }

    catalog: list[BookProduct] = []

    try:
        with httpx.Client(headers=headers, timeout=10.0, follow_redirects=True) as client:
            response = client.get(target_url)
            response.raise_for_status()

            # Parse DOM
            soup = BeautifulSoup(response.text, "html.parser")
            product_cards = soup.select("article.product_pod")

            for card in product_cards:
                # Use attribute 'title' instead of truncated inner text!
                title = extract_attr_safely(card, "h3 > a", "title")
                raw_price = extract_text_safely(card, "p.price_color")
                price = parse_price_safely(raw_price)
                availability = extract_text_safely(card, "p.instock.availability")

                catalog.append(BookProduct(title=title, price=price, availability=availability))

            print(f"Extracted {len(catalog)} books successfully:")
            for item in catalog:
                print(f"• {item.title} | £{item.price:.2f} | {item.availability}")

            polite_sleep(1.5, 3.0)

    except httpx.HTTPError as err:
        print(f"HTTP request error while scraping: {err}")

    return catalog


if __name__ == "__main__":
    scrape_books_catalog()
