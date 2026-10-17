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
    # Target URL renders all quote cards via client-side JavaScript execution
    target_url = "https://quotes.toscrape.com/js/"
    quotes: list[QuoteRecord] = []

    print("Launching headless Chromium via Playwright...")

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            user_agent=(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            ),
            viewport={"width": 1280, "height": 800},
            locale="en-US",
        )
        page: Page = context.new_page()

        print(f"Navigating to JS-rendered URL: {target_url}")
        page.goto(target_url, timeout=30000)

        # CRITICAL: Wait for client-side JS hydration to inject quote cards
        page.wait_for_selector("div.quote", timeout=10000)

        cards = page.query_selector_all("div.quote")
        print(f"Discovered {len(cards)} dynamically rendered quotes in DOM.\n")

        for card in cards:
            text_el = card.query_selector("span.text")
            author_el = card.query_selector("small.author")
            tag_els = card.query_selector_all("div.tags a.tag")

            text = text_el.inner_text().strip() if text_el else "N/A"
            author = author_el.inner_text().strip() if author_el else "N/A"
            tags = [t.inner_text().strip() for t in tag_els]

            quotes.append(QuoteRecord(text=text, author=author, tags=tags))

        for q in quotes:
            print(f"“{q.text}”\n  — {q.author} | Tags: {q.tags}\n")

        browser.close()

    return quotes


if __name__ == "__main__":
    scrape_dynamic_quotes()
