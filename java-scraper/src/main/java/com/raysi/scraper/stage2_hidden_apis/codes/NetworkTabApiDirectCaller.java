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

    // Clean immutable records matching the inspected JSON response schema
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(int id, String title, double price, double rating, String category, int stock) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductApiResponse(List<Product> products, int total, int skip, int limit) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        // Discovered via Chrome DevTools -> Network -> Fetch/XHR filter
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

                System.out.println("\nSuccessfully fetched " + result.products().size() + " records (Total in DB: " + result.total() + "):");
                for (Product p : result.products()) {
                    System.out.printf("• [%s] %s | $%.2f | Rating: %.1f★ | Stock: %d\n",
                            p.category(), p.title(), p.price(), p.rating(), p.stock());
                }

                // Polite jitter before subsequent calls
                politeSleep(1000, 2500);

            } else if (response.statusCode() == 429) {
                System.err.println("Rate limit reached (HTTP 429). Backing off...");
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
