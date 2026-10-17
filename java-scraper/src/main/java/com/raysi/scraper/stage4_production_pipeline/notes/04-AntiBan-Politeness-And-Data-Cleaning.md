# Stage 4: Anti-Ban, Politeness & Production Pipelines (Java)
> **Author:** raysi | Practical Web Extraction, DOM Parsing, Hidden APIs & Data Pipelines

---

# 1. When to Use This Approach (The 30-Second Decision Rule)

- **Use This Approach When:**
  - You are transitioning from single-page exploratory scripts to large-scale crawlers processing 1,000+ URLs.
  - The target website enforces rate-limits, temporarily blocks IPs with HTTP `429 Too Many Requests`, or drops connections under load.
  - You need maximum I/O concurrency without crashing system memory, leveraging **Java 21 Virtual Threads**.
  - You need to clean raw strings, coerce types, and export sanitized records to structured storage (CSV, JSON, SQL).

- **AVOID This Approach When:**
  - You are only scraping 5 or 10 static pages one-off: Adding concurrency queues and retry wrappers over-engineers trivial tasks.
  - You need real-time browser automation: Virtual threads excel at blocking network I/O (`HttpClient`, `JSoup`), but you cannot launch 10,000 headless browser processes concurrently without exhausting physical system RAM.

---

# 2. The Core Cheatsheet (80/20 Rule)

| Goal | Modern Java 21 Pipeline Pattern | Engineering Best Practice |
|---|---|---|
| **Virtual Thread Pool** | `Executors.newVirtualThreadPerTaskExecutor()` | Lightweight JVM threads with zero OS thread kernel overhead. |
| **Throttle Inbound Concurrency** | `Semaphore limiter = new Semaphore(concurrencyLimit);` | Prevents overwhelming target servers and socket pool exhaustion. |
| **Polite Randomized Jitter** | `Thread.sleep(ThreadLocalRandom.current().nextInt(min, max));` | Breaks robotic timing patterns that trigger anti-scraping heuristics. |
| **Exponential Backoff Formula** | `delay = baseMs * Math.pow(2, attempt - 1) + jitterMs;` | Backs off progressively when receiving HTTP 429 or 503. |
| **Thread-Safe Results Accumulator**| `ConcurrentLinkedQueue<Record> queue = new ConcurrentLinkedQueue<>();` | Lock-free collection for concurrent scraper workers. |
| **Sanitize Malformed Strings** | `raw.replaceAll("[^0-9.]", "").trim()` | Robust normalization for numerical and currency fields. |
| **CSV Streaming Export** | `CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)` | Handles quotes, line breaks, and commas safely without corruption. |
| **Indented JSON Export** | `MAPPER.enable(SerializationFeature.INDENT_OUTPUT)` | Human-readable and schema-compliant JSON storage. |
| **Fail-Fast Socket Timeouts** | `HttpRequest.newBuilder().timeout(Duration.ofSeconds(10))` | Eliminates hanging connections on unresponsive servers. |
| **Graceful Executor Shutdown** | `try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor())` | Auto-closes and blocks until all virtual threads finish tasks. |

---

# 3. Step-by-Step Practical Workflow (DevTools to Code)

### Step 1: Establish the Concurrency Envelope
Never crawl with unbounded concurrency. Determine target server tolerance:
- Start with 2 concurrent workers and measure response latency.
- If response latency increases or HTTP 429 codes appear, scale back immediately.
- Use a `Semaphore(N)` to regulate the maximum number of simultaneous requests.

### Step 2: Implement Jittered Exponential Backoff
A fixed delay (e.g. exactly 1000ms every time) is easily detected by modern Web Application Firewalls (WAFs):
- Use `ThreadLocalRandom.current().nextInt(1200, 2800)` for polite scraping.
- Upon receiving `429 Too Many Requests` or `503 Service Unavailable`, multiply delay by $2^{\text{attempt}}$ and retry up to 3 times before discarding.

### Step 3: Sanitize and Export Data Models
Raw extracted text contains HTML entities (`&amp;`, `&#39;`), non-breaking spaces (`\u00A0`), currency symbols, and extra whitespace:
- Clean strings before instantiating immutable records.
- Persist structured datasets directly into CSV and JSON using battle-tested libraries (`commons-csv` and `jackson-databind`).

---

# 4. Production-Ready Code (`author: raysi`)

Below is the production implementation located at [`VirtualThreadsConcurrentScraper.java`](file:///Users/aashishkumarray/Desktop/web-scraping-labs/java-scraper/src/main/java/com/raysi/scraper/stage4_production_pipeline/codes/VirtualThreadsConcurrentScraper.java):

```java
package com.raysi.scraper.stage4_production_pipeline.codes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Author: raysi
 * Practical Stage 4 Demo: High-throughput, polite concurrent web scraping using Java 21 Virtual Threads
 * (Executors.newVirtualThreadPerTaskExecutor) regulated by a Semaphore rate-limiter.
 */
public class VirtualThreadsConcurrentScraper {

    public record PageScrapeResult(String url, int statusCode, int titleLength, long responseTimeMs) {}

    public static void main(String[] args) {
        List<String> targetUrls = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            targetUrls.add("https://books.toscrape.com/catalogue/page-" + i + ".html");
        }

        Semaphore concurrencyLimiter = new Semaphore(2);
        ConcurrentLinkedQueue<PageScrapeResult> results = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();

            for (String url : targetUrls) {
                Future<?> future = executor.submit(() -> {
                    try {
                        concurrencyLimiter.acquire();
                        Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1500));

                        long taskStart = System.currentTimeMillis();
                        Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0...").timeout(10000).get();
                        long duration = System.currentTimeMillis() - taskStart;

                        results.add(new PageScrapeResult(url, 200, doc.title().length(), duration));
                    } catch (Exception e) {
                        System.err.println("Failed: " + url);
                    } finally {
                        concurrencyLimiter.release();
                    }
                });
                futures.add(future);
            }

            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }

        results.forEach(System.out::println);
    }
}
```

---

# 5. Top 3 Real-World Pitfalls & How to Fix Them

### Pitfall 1: Socket & File Descriptor Exhaustion
- **Cause:** Spawning thousands of unthrottled requests causes `java.net.SocketException: Too many open files` at the OS level.
- **2-Line Fix:**
```java
// Limit simultaneous outbound sockets with a Semaphore:
Semaphore socketThrottle = new Semaphore(20);
```

### Pitfall 2: Memory Leak Accumulating Unbounded Results
- **Cause:** Storing millions of raw HTML strings in an in-memory `List` until the JVM runs out of heap (`OutOfMemoryError`).
- **2-Line Fix:**
```java
// Stream and flush parsed records to disk in batches rather than accumulating raw DOMs:
if (batch.size() >= 500) { csvPrinter.flush(); batch.clear(); }
```

### Pitfall 3: Robotic Request Signatures Triggering Cloudflare
- **Cause:** Every request arrives at exact 1000ms intervals with identical headers in the exact same order.
- **2-Line Fix:**
```java
int jitter = ThreadLocalRandom.current().nextInt(1500, 3500);
Thread.sleep(jitter);
```
