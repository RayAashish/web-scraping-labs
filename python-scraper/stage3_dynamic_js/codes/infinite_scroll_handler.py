"""
Author: raysi
Practical Stage 3 Demo: Handling infinite scroll feeds in Python using Playwright,
polling DOM element growth, and executing controlled scroll passes.
"""

from dataclasses import dataclass
from playwright.sync_api import sync_playwright, Page


@dataclass(frozen=True)
class InfiniteQuote:
    text: str
    author: str


def scrape_infinite_scroll(max_scroll_passes: int = 5) -> list[InfiniteQuote]:
    scroll_url = "https://quotes.toscrape.com/scroll"
    results: list[InfiniteQuote] = []

    print("Launching Playwright Infinite Scroll Crawler...")

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page: Page = browser.new_page()

        page.goto(scroll_url)
        page.wait_for_selector("div.quote")

        scroll_pass = 0
        previous_count = 0

        while scroll_pass < max_scroll_passes:
            scroll_pass += 1
            current_elements = page.query_selector_all("div.quote")
            current_count = len(current_elements)
            print(f"[Pass {scroll_pass}/{max_scroll_passes}] Currently rendered quotes: {current_count}")

            # Scroll to bottom
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)")

            # Wait for AJAX request to complete and DOM to expand
            page.wait_for_timeout(2000)

            new_elements = page.query_selector_all("div.quote")
            if len(new_elements) == previous_count and scroll_pass > 1:
                print("No new items loaded after scroll. Reached feed end.")
                break

            previous_count = len(new_elements)

        # Extract all quotes
        final_cards = page.query_selector_all("div.quote")
        print(f"\nFinished scrolling. Total items extracted: {len(final_cards)}")

        for card in final_cards:
            text_el = card.query_selector("span.text")
            author_el = card.query_selector("small.author")

            text = text_el.inner_text().strip() if text_el else "N/A"
            author = author_el.inner_text().strip() if author_el else "N/A"

            results.append(InfiniteQuote(text=text, author=author))

        browser.close()

    for item in results[:5]:
        print(f"• {item.author}: {item.text[:60]}...")

    return results


if __name__ == "__main__":
    scrape_infinite_scroll()
