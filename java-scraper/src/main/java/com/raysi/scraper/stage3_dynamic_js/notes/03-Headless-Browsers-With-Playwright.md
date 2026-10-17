# Stage 3: Headless Browsers with Playwright (Java)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use Playwright When:**
  - The target website is a heavy **Client-Side Rendered (CSR)** application (React, Next.js client components, Vue, Angular, Svelte) where data is not present in initial HTML and there is no accessible hidden JSON API.
  - User interactions are mandatory before data is rendered (e.g., clicking tabs, entering inputs, triggering dropdowns, expanding accordions, or solving human-behavior verification).
  - The page utilizes **Infinite Scroll** feeds that require dynamic browser viewport scrolling and JavaScript event listener triggers.
  - The target generates dynamic canvas graphs, SVG charts, or requires DOM screenshot extraction.

- **AVOID Playwright When:**
  - The page content is present in **View Page Source (`Ctrl+U`)**: Launching a browser process wastes 100x more CPU and RAM. Use Stage 1 JSoup.
  - You can find the data in **DevTools -> Network -> Fetch/XHR**: Reverse-engineering the JSON API (Stage 2) is 50x faster and requires zero browser overhead.
  - You need to scrape millions of pages on a limited budget: Managing hundreds of headless browser instances demands substantial RAM (150MB–300MB per browser tab).

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | Playwright Java Method | Engineering Best Practice |
|---|---|---|
| **Launch Headless Browser** | `playwright.chromium().launch(new LaunchOptions().setHeadless(true));` | Run headless in production; toggle `setHeadless(false)` for local debugging. |
| **Realistic Browser Context** | `browser.newContext(new NewContextOptions().setUserAgent("...").setViewportSize(1280, 800));` | Isolate cookies, storage, and viewport per crawling session. |
| **Navigate & Wait for Network** | `page.navigate(url, new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));` | Faster than waiting for `NETWORKIDLE` on sites with continuous analytics beacons. |
| **Explicit Selector Wait** | `page.waitForSelector("div.product-card", new WaitForSelectorOptions().setTimeout(10000));` | Never use static `Thread.sleep()` to wait for dynamic elements. |
| **Query Multiple Elements** | `List<ElementHandle> items = page.querySelectorAll(".product-pod");` | Iterates over hydrated DOM node handles safely. |
| **Execute Client-Side JS** | `page.evaluate("() -> window.scrollTo(0, document.body.scrollHeight)");` | Trigger scroll events, inspect window globals, or remove overlays. |
| **Intercept & Block Heavy Assets**| `page.route("**/*.{png,jpg,jpeg,svg,woff2}", Route::abort);` | **Speed optimization:** Cuts page load time by 60% by skipping image downloads. |
| **Click Dynamic Element** | `page.click("button#load-more-btn");` | Automatically scrolls element into view and waits for actionability. |
| **Type into Input Field** | `page.fill("input[name='search']", "laptops");` | Clears previous text and enters string cleanly. |
| **Clean Resource Shutdown** | `try (Playwright p = Playwright.create()) { ... }` | Ensures no orphaned `chrome.exe` processes remain after crash. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Detect Client-Side JS Rendering
1. Open page in Chrome. Right-click text -> **Inspect Element**. You see elements.
2. Press `Ctrl + U` (View Source). Search for the same text.
3. If search fails, the DOM is dynamically injected by client-side JavaScript.

### Step 2: Identify the Anchor Selector for Hydration
Before reading any text from the page in Playwright, identify the container selector that indicates the page has completed data hydration (e.g. `div.quote` or `article[data-testid='tweet']`).
Set an explicit wait:
```java
page.waitForSelector("div.quote", new Page.WaitForSelectorOptions().setTimeout(10_000));
```

### Step 3: Handle Dynamic Infinite Feeds
To scrape infinite scroll pages:
1. Capture current element count: `page.querySelectorAll(".item").size()`.
2. Scroll to bottom: `page.evaluate("() -> window.scrollTo(0, document.body.scrollHeight)")`.
3. Wait for network response or DOM expansion.
4. Exit loop when item count stops increasing across successive scroll passes.

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`PlaywrightHeadlessScraper.java`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/java-scraper/src/main/java/com/raysi/scraper/stage3_dynamic_js/codes/PlaywrightHeadlessScraper.java):

```java
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
        String targetUrl = "https://quotes.toscrape.com/js/";
        List<Quote> scrapedQuotes = new ArrayList<>();

        System.out.println("Launching headless Playwright browser instance...");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .setViewportSize(1280, 800));

            Page page = context.newPage();
            page.navigate(targetUrl);

            // Wait for JS hydration
            page.waitForSelector("div.quote", new Page.WaitForSelectorOptions().setTimeout(10_000));

            List<ElementHandle> quoteElements = page.querySelectorAll("div.quote");
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

            scrapedQuotes.forEach(q -> 
                System.out.printf("“%s” — %s | Tags: %s\n", q.text(), q.author(), q.tags())
            );

        } catch (Exception e) {
            System.err.println("Playwright scraping execution failed: " + e.getMessage());
        }
    }
}
```

---

# 5. Top 3 Real-World Pitfalls & How to Fix Them

### Pitfall 1: `PlaywrightException: Timeout 30000ms exceeded while waiting for selector`
- **Cause:** Selector was incorrect, element lives inside an `<iframe>`, or client-side JS errored and never rendered the element.
- **2-Line Fix:**
```java
// Check frame boundaries or fallback gracefully if element is absent:
ElementHandle el = page.waitForSelector("div.card", new Page.WaitForSelectorOptions().setTimeout(5000));
```

### Pitfall 2: Zombie Chromium Processes Leaking Memory
- **Cause:** Unhandled exceptions terminate Java without executing `browser.close()` or `playwright.close()`, leaving zombie browser binaries hogging GBs of RAM.
- **2-Line Fix:**
```java
// Always wrap Playwright, Browser, and Context in Java's try-with-resources:
try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) { ... }
```

### Pitfall 3: `TimeoutError: page.waitForNavigation: Timeout exceeded`
- **Cause:** Using `page.waitForNavigation()` on Single Page Applications (Next.js/React router) where URL changes via HTML5 History API without triggering a full page reload.
- **2-Line Fix:**
```java
page.click("a.nav-link");
page.waitForURL("**/dashboard**"); // Wait for URL match instead of browser reload
```
