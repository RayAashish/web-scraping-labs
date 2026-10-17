package com.raysi.scraper.stage1_static_html.codes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Author: raysi
 * Practical Stage 1 Demo: Multi-page crawler traversing pagination links
 * and parsing HTML table / card structures with loop protection and absolute URL resolution.
 */
public class PaginationTableScraper {

    public record BookItem(String title, double price, String inStock, String pageSourceUrl) {}

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final int MAX_PAGES = 3; // Safety cap to avoid infinite loops during scraping runs

    public static void main(String[] args) {
        String currentUrl = "https://books.toscrape.com/catalogue/page-1.html";
        List<BookItem> results = new ArrayList<>();
        Set<String> visitedUrls = new HashSet<>();
        int pageCount = 0;

        System.out.println("Starting multi-page scraper for up to " + MAX_PAGES + " pages...");

        while (currentUrl != null && !currentUrl.isBlank() && pageCount < MAX_PAGES) {
            if (visitedUrls.contains(currentUrl)) {
                System.out.println("Loop detected for URL: " + currentUrl + ". Terminating crawl.");
                break;
            }

            visitedUrls.add(currentUrl);
            pageCount++;
            System.out.println("\n[Scraping Page " + pageCount + "] " + currentUrl);

            try {
                Document doc = Jsoup.connect(currentUrl)
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .timeout(12_000)
                        .get();

                // Extract all books on this page
                Elements items = doc.select("article.product_pod");
                for (Element item : items) {
                    String title = extractAttr(item, "h3 a", "title");
                    String priceRaw = extractText(item, "p.price_color");
                    double price = parsePrice(priceRaw);
                    String stock = extractText(item, "p.instock.availability");

                    results.add(new BookItem(title, price, stock, currentUrl));
                }

                // Discover next page link using abs:href to resolve relative paths cleanly
                Element nextButton = doc.selectFirst("li.next a");
                if (nextButton != null && nextButton.hasAttr("href")) {
                    currentUrl = nextButton.attr("abs:href"); // Resolves e.g. "page-2.html" to full URI
                } else {
                    System.out.println("No 'next' page link found. Crawl completed naturally.");
                    currentUrl = null;
                }

                // Respect the target host with polite random jitter
                if (currentUrl != null) {
                    politeSleep(1200, 2500);
                }

            } catch (IOException e) {
                System.err.println("HTTP error while crawling " + currentUrl + ": " + e.getMessage());
                break;
            }
        }

        System.out.println("\n================ CRAWL SUMMARY ================");
        System.out.println("Total pages scraped: " + pageCount);
        System.out.println("Total items captured: " + results.size());
        results.stream().limit(5).forEach(b -> System.out.println("Sample: " + b));
    }

    private static String extractText(Element parent, String cssQuery) {
        Element el = parent.selectFirst(cssQuery);
        return (el != null) ? el.text().trim() : "N/A";
    }

    private static String extractAttr(Element parent, String cssQuery, String attr) {
        Element el = parent.selectFirst(cssQuery);
        return (el != null && el.hasAttr(attr)) ? el.attr(attr).trim() : "N/A";
    }

    private static double parsePrice(String rawPrice) {
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
