# Stage 1: Static DOM Parsing with JSoup (Java)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use JSoup When:**
  - The target website is **Server-Side Rendered (SSR)** or plain static HTML (e.g., Wikipedia, classic WordPress, traditional e-commerce catalogs, government data portals).
  - You open **View Page Source (`Ctrl+U`)** and the required text/numbers are already present in the raw HTML returned by the server.
  - You need fast, ultra-low-memory scraping (thousands of pages per minute on standard JVM heaps).

- **AVOID JSoup When:**
  - **A hidden JSON API exists:** If DevTools -> Network -> Fetch/XHR reveals the frontend is pulling data from a JSON endpoint, skip JSoup completely and use `HttpClient` directly (Stage 2).
  - **The page is a Client-Side Rendered (CSR) SPA:** If the page source contains only an empty `<div id="root"></div>` or `<div id="app"></div>` and requires a full JavaScript engine to execute React/Vue/Angular, you must use **Playwright** (Stage 3).
  - **Complex bot walls exist:** If the site triggers Cloudflare Turnstile or perimeter challenges on initial TLS handshake, JSoup's basic connection will be blocked.

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | JSoup Method / Pattern | Engineering Best Practice |
|---|---|---|
| **Connect with Browser Headers** | `Jsoup.connect(url).userAgent("...").header("Accept-Language", "...").timeout(10000).get()` | Never omit `User-Agent` or `timeout`. |
| **Select Multiple Elements** | `Elements cards = doc.select("article.product_pod");` | Use semantic tag + class combinations. |
| **Safely Select Single Element** | `Element el = card.selectFirst("h3 > a");` | Always check `el != null` before calling methods. |
| **Extract Clean Text** | `String text = el.text().trim();` | Strips tags, normalizes whitespace, decodes HTML entities. |
| **Extract Attribute (Direct)** | `String title = el.attr("title");` | Useful when displayed text is truncated with ellipsis (`...`). |
| **Extract Absolute URL** | `String fullLink = el.attr("abs:href");` | Resolves relative paths (`/item/1`) into absolute URLs automatically. |
| **Query by Attribute Prefix** | `doc.select("a[href^='/catalogue/']");` | Matches links starting with specific URL prefixes. |
| **Query by Data Attribute** | `doc.select("div[data-testid='product-card']");` | Highly stable against CSS stylesheet refactoring. |
| **Extract Embedded Script Text** | `doc.selectFirst("script#__NEXT_DATA__").data();` | Retrieves raw JSON payload stored inside `<script>` tags. |
| **Null-Safe String Fallback** | `Optional.ofNullable(el).map(Element::text).orElse("N/A")` | Functional fallback preventing `NullPointerException`. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Inspect vs. View Source Verification
Open Chrome DevTools (`F12`), press `Ctrl + U` (View Source), and search for the target price or title:
- If present in `Ctrl + U`, confirm the selector container in Elements tab.
- Identify the parent repeating card container (e.g., `<article class="product_pod">`).

### Step 2: Formulate Break-Proof Selectors
Avoid machine-generated hashed classes produced by CSS Modules or styled-components (e.g., `.sc-1g4k9x-1` or `.css-1dbjc4n`).
- **Fragile Selector:** `div.css-1dbjc4n > div:nth-child(3) > span` (breaks on the next frontend build).
- **Resilient Selector:** `article.product_pod h3 > a[title]` or `[data-component='price-tag']`.

### Step 3: Handle Irregular / Missing Fields
In e-commerce, some items have strike-through prices, out-of-stock badges, or no customer reviews.
Never do:
```java
// ❌ CRASH: Throws NullPointerException if any item has no badge
String badge = card.selectFirst(".badge").text();
```
Always isolate extraction inside null-safe utilities:
```java
// ✅ RESILIENT: Default fallback guarantees zero crash rate across 100k items
String badge = extractTextSafely(card, ".badge", "Standard");
```

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`JsoupCssSelectorExtractor.java`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/java-scraper/src/main/java/com/raysi/scraper/stage1_static_html/codes/JsoupCssSelectorExtractor.java):

```java
package com.raysi.scraper.stage1_static_html.codes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Author: raysi
 * Practical Stage 1 Demo: Extracting structured e-commerce data from static HTML
 * using resilient CSS selectors, browser headers, and null-safe helper methods.
 */
public class JsoupCssSelectorExtractor {

    public record BookProduct(String title, double price, String availability) {}

    public static void main(String[] args) {
        String targetUrl = "https://books.toscrape.com/catalogue/category/books/travel_2/index.html";
        List<BookProduct> catalog = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(targetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(10_000)
                    .get();

            Elements productCards = doc.select("article.product_pod");

            for (Element card : productCards) {
                String title = extractAttrSafely(card, "h3 > a", "title");
                String rawPrice = extractTextSafely(card, "p.price_color");
                double price = parsePriceSafely(rawPrice);
                String availability = extractTextSafely(card, "p.instock.availability");

                catalog.add(new BookProduct(title, price, availability));
            }

            System.out.println("Extracted " + catalog.size() + " books successfully:");
            catalog.forEach(System.out::println);

            politeSleep(1500, 3000);

        } catch (IOException e) {
            System.err.println("Failed to fetch or parse URL: " + e.getMessage());
        }
    }

    private static String extractTextSafely(Element parent, String cssQuery) {
        Element el = parent.selectFirst(cssQuery);
        return (el != null) ? el.text().trim() : "N/A";
    }

    private static String extractAttrSafely(Element parent, String cssQuery, String attrKey) {
        Element el = parent.selectFirst(cssQuery);
        return (el != null && el.hasAttr(attrKey)) ? el.attr(attrKey).trim() : "N/A";
    }

    private static double parsePriceSafely(String rawPrice) {
        String cleaned = rawPrice.replaceAll("[^0-9.]", "");
        return cleaned.isEmpty() ? 0.0 : Double.parseDouble(cleaned);
    }

    private static void politeSleep(int minMs, int maxMs) {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

# 5. Top 3 Real-World Pitfalls & How to Fix Them

### Pitfall 1: `org.jsoup.HttpStatusException: HTTP error fetching URL. Status=403`
- **Cause:** Default JSoup connection lacks standard desktop request headers; target server detects `User-Agent: Jsoup/...` or missing language headers.
- **2-Line Fix:**
```java
Connection conn = Jsoup.connect(url).userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                       .header("Accept-Language", "en-US,en;q=0.9").header("Referer", "https://www.google.com/");
```

### Pitfall 2: Broken Relative Pagination Links (`href="page-2.html"`)
- **Cause:** Calling `el.attr("href")` returns a relative subpath, failing on the subsequent HTTP request when passed directly to `Jsoup.connect()`.
- **2-Line Fix:**
```java
// Prefix the attribute key with 'abs:' so JSoup resolves the base URI automatically
String absoluteNextUrl = nextButtonElement.attr("abs:href");
```

### Pitfall 3: Character Encoding Scrambles (`Â£45.17` instead of `£45.17`)
- **Cause:** Website serves response with mislabeled Content-Type or non-UTF8 charset header while body contains UTF-8 bytes.
- **2-Line Fix:**
```java
Connection.Response response = Jsoup.connect(url).execute();
Document doc = Jsoup.parse(response.bodyAsBytes(), "UTF-8", url);
```
