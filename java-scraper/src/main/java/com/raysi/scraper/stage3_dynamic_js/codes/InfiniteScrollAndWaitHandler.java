package com.raysi.scraper.stage3_dynamic_js.codes;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: raysi
 * Practical Stage 3 Demo: Handling Infinite Scroll feeds in Playwright
 * by executing incremental scroll evaluations, polling DOM expansion, and enforcing loop exit guards.
 */
public class InfiniteScrollAndWaitHandler {

    public record ScrollItem(String text, String author) {}

    private static final int MAX_SCROLL_PASSES = 5;

    public static void main(String[] args) {
        // Dedicated infinite scroll sandbox
        String scrollUrl = "https://quotes.toscrape.com/scroll";
        List<ScrollItem> accumulatedItems = new ArrayList<>();

        System.out.println("Starting Playwright Infinite Scroll Crawler...");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();

            page.navigate(scrollUrl);
            page.waitForSelector("div.quote");

            int scrollPass = 0;
            int previousElementCount = 0;

            while (scrollPass < MAX_SCROLL_PASSES) {
                scrollPass++;

                // 1. Measure current DOM elements loaded
                List<ElementHandle> currentElements = page.querySelectorAll("div.quote");
                int currentCount = currentElements.size();
                System.out.printf("[Pass %d/%d] Currently rendered quotes in DOM: %d\n", 
                        scrollPass, MAX_SCROLL_PASSES, currentCount);

                // 2. Scroll to bottom of window to trigger scroll event listener
                page.evaluate("() -> window.scrollTo(0, document.body.scrollHeight)");

                // 3. Wait for network response / DOM expansion
                page.waitForTimeout(2000); // 2 second pause for AJAX payload execution

                // 4. Verify whether new items were appended
                int newCount = page.querySelectorAll("div.quote").size();
                if (newCount == previousElementCount && scrollPass > 1) {
                    System.out.println("No new items loaded after scroll. Bottom of feed reached.");
                    break;
                }
                previousElementCount = newCount;
            }

            // Extract all loaded elements from the hydrated DOM
            List<ElementHandle> finalQuotes = page.querySelectorAll("div.quote");
            System.out.println("\nFinal extraction: Processing " + finalQuotes.size() + " accumulated items:");

            for (ElementHandle card : finalQuotes) {
                ElementHandle textEl = card.querySelector("span.text");
                ElementHandle authorEl = card.querySelector("small.author");

                String text = (textEl != null) ? textEl.innerText().trim() : "N/A";
                String author = (authorEl != null) ? authorEl.innerText().trim() : "N/A";

                accumulatedItems.add(new ScrollItem(text, author));
            }

            accumulatedItems.stream().limit(6).forEach(item -> 
                System.out.printf("• %s — %s\n", item.author(), item.text())
            );

        } catch (Exception e) {
            System.err.println("Infinite scroll process failed: " + e.getMessage());
        }
    }
}
