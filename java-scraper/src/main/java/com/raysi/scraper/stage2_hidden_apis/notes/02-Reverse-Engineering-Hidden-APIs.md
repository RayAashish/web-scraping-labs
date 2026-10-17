# Stage 2: Reverse-Engineering Hidden JSON APIs (Java)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use This Approach When:**
  - Chrome DevTools (`F12`) -> **Network** tab -> **Fetch/XHR** reveals asynchronous background calls delivering structured JSON.
  - The website is a Single Page Application (React, Angular, Vue, Next.js) that fetches data on initial mount or on user scroll/pagination.
  - You want **10x–50x higher throughput** and near-zero CPU/memory footprint compared to parsing HTML or launching Chromium.

- **AVOID This Approach When:**
  - The site generates static HTML entirely on the backend and sends zero JSON XHR payloads (use Stage 1 JSoup instead).
  - The API requests are protected by short-lived cryptographic request signatures (e.g., custom HMAC computed inside obfuscated WebAssembly or heavily minified JS). If reverse-engineering the signing algorithm takes more than 2 hours, fall back to Stage 3 Playwright.
  - The endpoint is walled behind Cloudflare Turnstile or Akamai Bot Manager that invalidates raw HTTP TLS handshakes.

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | Java 21 HttpClient / Jackson Pattern | Engineering Best Practice |
|---|---|---|
| **Initialize HTTP/2 Client** | `HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build()` | Enables multiplexing and matches modern browser networking. |
| **Manage Cookies Across Requests** | `CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);` | Passes session identifiers automatically between handshakes. |
| **Set Standard Browser Headers** | `req.header("User-Agent", "...").header("Accept", "application/json")` | Never send requests with Java's default runtime User-Agent. |
| **Set Fetch Metadata Headers** | `req.header("Sec-Fetch-Site", "same-origin").header("Sec-Fetch-Mode", "cors")` | Critical for modern WAFs filtering non-browser API consumers. |
| **Set Referer & Origin** | `req.header("Referer", "https://site.com/").header("Origin", "https://site.com")` | Backend APIs reject requests missing expected origins. |
| **Map JSON to Modern Java Record** | `MAPPER.readValue(response.body(), ProductResponse.class)` | Fast type-safe deserialization without boilerplate getters. |
| **Ignore Unexpected JSON Keys** | `@JsonIgnoreProperties(ignoreUnknown = true)` | Prevents `UnrecognizedPropertyException` when backend APIs add fields. |
| **Parse Dynamic Subtree (No POJO)**| `JsonNode root = MAPPER.readTree(response.body());` | Ideal for fast ad-hoc field extraction from deeply nested responses. |
| **Handle Query Pagination** | `URI.create("https://api.site.com/items?page=" + page + "&limit=50")` | Clean parameterization of pagination loops. |
| **Handle HTTP 429 Backoff** | `if (resp.statusCode() == 429) { politeSleep(5000, 10000); }` | Avoids aggressive bans upon encountering rate-limit barriers. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Filter Network Traffic in DevTools
1. Open DevTools (`F12`), switch to the **Network** tab, and toggle the **Fetch/XHR** filter button.
2. Clear the log, reload the target page or click "Page 2" / "Load More".
3. Look for items returning `Content-Type: application/json`.
4. Click on the request -> switch to **Preview** tab. If you see arrays of product records or listings, you found the jackpot.

### Step 2: Test API Replay via "Copy as cURL"
1. Right-click the request in DevTools -> **Copy** -> **Copy as cURL**.
2. Run the cURL command in your terminal. If it returns the same JSON:
   - Strip headers one by one (cookies, auth headers, custom tokens) to find the **minimal set of mandatory headers** required by the server.
   - Typically, only `User-Agent`, `Referer`, and occasionally a session cookie or `x-csrf-token` are strictly mandatory.

### Step 3: Implement Direct Client in Java
Define a Java `record` matching only the fields you need, configure an `HttpClient` with matching headers, and deserialize the payload cleanly.

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`NetworkTabApiDirectCaller.java`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/java-scraper/src/main/java/com/raysi/scraper/stage2_hidden_apis/codes/NetworkTabApiDirectCaller.java):

```java
package com.raysi.scraper.stage2_hidden_apis.codes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Author: raysi
 * Practical Stage 2 Demo: Reverse-engineering the DevTools Network Tab (Fetch/XHR)
 * to query the backend JSON API directly using Java 21 HttpClient & Jackson.
 */
public class NetworkTabApiDirectCaller {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(int id, String title, double price, double rating, String category, int stock) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductApiResponse(List<Product> products, int total, int skip, int limit) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String apiEndpoint = "https://dummyjson.com/products?limit=5&select=title,price,rating,category,stock";

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://dummyjson.com/")
                .GET()
                .build();

        try {
            System.out.println("Sending direct API request to: " + apiEndpoint);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ProductApiResponse result = MAPPER.readValue(response.body(), ProductApiResponse.class);

                System.out.println("\nSuccessfully fetched " + result.products().size() + " records (Total: " + result.total() + "):");
                for (Product p : result.products()) {
                    System.out.printf("• [%s] %s | $%.2f | Rating: %.1f★ | Stock: %d\n",
                            p.category(), p.title(), p.price(), p.rating(), p.stock());
                }

                politeSleep(1000, 2500);

            } else {
                System.err.println("API call failed with HTTP Status: " + response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Network request failed: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
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

### Pitfall 1: `HTTP 403 Forbidden` / Missing CORS Origin
- **Cause:** Backend API checks the incoming `Origin` and `Referer` headers against its CORS whitelist; missing or mismatched headers trigger instant rejection.
- **2-Line Fix:**
```java
HttpRequest req = builder.header("Referer", "https://target-domain.com/catalog")
                         .header("Origin", "https://target-domain.com").build();
```

### Pitfall 2: `UnrecognizedPropertyException` on Jackson Deserialization
- **Cause:** Backend deploys an update adding unexpected keys (e.g. `discount_badge_v2`) to the JSON payload, breaking standard POJO mappings.
- **2-Line Fix:**
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductRecord(int id, String name, double price) {}
```

### Pitfall 3: Expired Session Cookie / Bearer Token
- **Cause:** Scraping succeeds for 15 minutes, then abruptly fails with `HTTP 401 Unauthorized` because the session cookie or JWT token expired.
- **2-Line Fix:**
```java
// Re-trigger an automated handshake method whenever 401 is received:
if (resp.statusCode() == 401) { refreshSessionCookies(client); return retryRequest(); }
```
