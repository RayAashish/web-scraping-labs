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

    // Clean immutable data carrier using Java Record
    public record BookProduct(String title, double price, String availability) {}

    public static void main(String[] args) {
        // Safe public sandbox specifically built for scraping practice
        String targetUrl = "https://books.toscrape.com/catalogue/category/books/travel_2/index.html";
        List<BookProduct> catalog = new ArrayList<>();

        try {
            // 1. Always mimic a real browser User-Agent & set a strict timeout
            Document doc = Jsoup.connect(targetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(10_000) // 10 seconds max wait
                    .get();

            // 2. Select all product cards on the page
            Elements productCards = doc.select("article.product_pod");

            for (Element card : productCards) {
                // Use attribute 'title' instead of truncated inner text!
                String title = extractAttrSafely(card, "h3 > a", "title");
                
                // Clean raw string "£45.17" -> 45.17
                String rawPrice = extractTextSafely(card, "p.price_color");
                double price = parsePriceSafely(rawPrice);

                String availability = extractTextSafely(card, "p.instock.availability");

                catalog.add(new BookProduct(title, price, availability));
            }

            // Print extracted records
            System.out.println("Extracted " + catalog.size() + " books successfully:");
            catalog.forEach(System.out::println);

            // 3. Polite Jitter Delay before hitting the next page (1.5s to 3.0s)
            politeSleep(1500, 3000);

        } catch (IOException e) {
            System.err.println("Failed to fetch or parse URL: " + e.getMessage());
        }
    }

    // Helper 1: Prevents NullPointerException if a CSS selector doesn't match on one item
    private static String extractTextSafely(Element parent, String cssQuery) {
        Element el = parent.selectFirst(cssQuery);
        return (el != null) ? el.text().trim() : "N/A";
    }

    // Helper 2: Safely extracts an HTML attribute (like href, src, or title)
    private static String extractAttrSafely(Element parent, String cssQuery, String attrKey) {
        Element el = parent.selectFirst(cssQuery);
        return (el != null && el.hasAttr(attrKey)) ? el.attr(attrKey).trim() : "N/A";
    }

    // Helper 3: Strips currency symbols & parses double without crashing on malformed text
    private static double parsePriceSafely(String rawPrice) {
        String cleaned = rawPrice.replaceAll("[^0-9.]", "");
        return cleaned.isEmpty() ? 0.0 : Double.parseDouble(cleaned);
    }

    // Helper 4: Randomized delay so requests don't look like a robotic loop
    private static void politeSleep(int minMs, int maxMs) {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
