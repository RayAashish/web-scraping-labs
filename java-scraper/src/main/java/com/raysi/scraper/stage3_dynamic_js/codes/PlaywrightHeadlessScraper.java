package com.raysi.scraper.stage3_dynamic_js.codes;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: raysi
 * Practical Stage 3 Demo: Scraping Client-Side Rendered (CSR) JavaScript pages
 * using Microsoft Playwright in headless Chromium with explicit DOM condition waits.
 */
public class PlaywrightHeadlessScraper {

    public record Quote(String text, String author, List<String> tags) {}

    public static void main(String[] args) {
        // Target is the JavaScript-rendered version of quotes.toscrape.com
        // View-source (Ctrl+U) reveals an empty page; DOM is populated entirely via client-side script
        String targetUrl = "https://quotes.toscrape.com/js/";
        List<Quote> scrapedQuotes = new ArrayList<>();

        System.out.println("Launching headless Playwright browser instance...");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setSlowMo(50));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .setViewportSize(1280, 800)
                    .setLocale("en-US"));

            Page page = context.newPage();
            System.out.println("Navigating to JS-rendered URL: " + targetUrl);

            // Navigate and wait for DOM content loaded
            page.navigate(targetUrl, new Page.NavigateOptions().setTimeout(30_000));

            // CRITICAL: Wait for dynamic JS hydration to finish rendering elements into DOM
            page.waitForSelector("div.quote", new Page.WaitForSelectorOptions().setTimeout(10_000));

            List<ElementHandle> quoteElements = page.querySelectorAll("div.quote");
            System.out.println("Discovered " + quoteElements.size() + " dynamically rendered quotes in DOM.\n");

            for (ElementHandle quoteCard : quoteElements) {
                ElementHandle textEl = quoteCard.querySelector("span.text");
                ElementHandle authorEl = quoteCard.querySelector("small.author");
                List<ElementHandle> tagEls = quoteCard.querySelectorAll("div.tags a.tag");

                String text = (textEl != null) ? textEl.innerText().trim() : "N/A";
                String author = (authorEl != null) ? authorEl.innerText().trim() : "N/A";

                List<String> tags = new ArrayList<>();
                for (ElementHandle t : tagEls) {
                    tags.add(t.innerText().trim());
                }

                scrapedQuotes.add(new Quote(text, author, tags));
            }

            // Display results
            scrapedQuotes.forEach(q -> 
                System.out.printf("“%s”\n  — %s | Tags: %s\n\n", q.text(), q.author(), q.tags())
            );

        } catch (Exception e) {
            System.err.println("Playwright scraping execution failed: " + e.getMessage());
        }
    }
}
