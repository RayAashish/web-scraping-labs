package com.raysi.scraper.stage4_production_pipeline.codes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Author: raysi
 * Practical Stage 4 Demo: Resilient HTTP client wrapper implementing exponential backoff,
 * randomized jitter, and intelligent retry logic for HTTP 429 (Too Many Requests) & 503 errors.
 */
public class PoliteRetryAndJitterClient {

    private final HttpClient client;
    private final int maxRetries;
    private final int baseDelayMs;

    public PoliteRetryAndJitterClient(int maxRetries, int baseDelayMs) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Executes GET request with exponential backoff and randomized jitter on rate-limits or transient failures.
     */
    public HttpResponse<String> executeWithRetry(String targetUrl) throws IOException, InterruptedException {
        int attempt = 0;

        while (true) {
            attempt++;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                // 200 OK -> Immediate success
                if (status >= 200 && status < 300) {
                    return response;
                }

                // Retryable status codes: 429 Too Many Requests, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout
                if (status == 429 || status == 502 || status == 503 || status == 504) {
                    if (attempt > maxRetries) {
                        System.err.printf("[Attempt %d/%d] Max retries exceeded for %s (Status: %d)\n", attempt, maxRetries, targetUrl, status);
                        return response;
                    }

                    long delay = calculateExponentialBackoffWithJitter(attempt, baseDelayMs);
                    System.out.printf("[Attempt %d/%d] Received HTTP %d. Backing off for %d ms before retry...\n",
                            attempt, maxRetries, status, delay);
                    Thread.sleep(delay);
                    continue;
                }

                // Non-retryable client errors (404 Not Found, 401 Unauthorized, etc.)
                return response;

            } catch (IOException e) {
                if (attempt > maxRetries) {
                    throw e;
                }
                long delay = calculateExponentialBackoffWithJitter(attempt, baseDelayMs);
                System.out.printf("[Attempt %d/%d] Connection error (%s). Backing off for %d ms...\n",
                        attempt, maxRetries, e.getMessage(), delay);
                Thread.sleep(delay);
            }
        }
    }

    /**
     * Formula: delay = baseDelay * (2 ^ attempt) + randomJitter(200ms, 800ms)
     */
    private long calculateExponentialBackoffWithJitter(int attempt, int baseMs) {
        long exponential = (long) (baseMs * Math.pow(2, attempt - 1));
        int jitter = ThreadLocalRandom.current().nextInt(200, 800);
        return exponential + jitter;
    }

    public static void main(String[] args) throws Exception {
        PoliteRetryAndJitterClient scraperClient = new PoliteRetryAndJitterClient(3, 1000);

        // Test with a responsive endpoint
        String testUrl = "https://httpbin.org/status/200";
        System.out.println("Testing Polite Client against: " + testUrl);
        HttpResponse<String> resp = scraperClient.executeWithRetry(testUrl);
        System.out.println("Response Status: " + resp.statusCode());

        // Test with simulated 429 rate limit
        String rateLimitTestUrl = "https://httpbin.org/status/429";
        System.out.println("\nTesting Rate-Limit Retry Handling against: " + rateLimitTestUrl);
        HttpResponse<String> rateLimitResp = scraperClient.executeWithRetry(rateLimitTestUrl);
        System.out.println("Final Result Status after retries: " + rateLimitResp.statusCode());
    }
}
