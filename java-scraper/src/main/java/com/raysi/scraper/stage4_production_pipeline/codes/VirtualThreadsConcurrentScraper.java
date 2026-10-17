package com.raysi.scraper.stage4_production_pipeline.codes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
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
        // Target URLs to scrape concurrently
        List<String> targetUrls = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            targetUrls.add("https://books.toscrape.com/catalogue/page-" + i + ".html");
        }

        // Regulate concurrency: Limit simultaneous active outbound sockets to 3
        Semaphore concurrencyLimiter = new Semaphore(3);
        ConcurrentLinkedQueue<PageScrapeResult> results = new ConcurrentLinkedQueue<>();

        System.out.println("Starting Java 21 Virtual Threads Scraper for " + targetUrls.size() + " tasks...");
        long startTime = System.currentTimeMillis();

        // Java 21: Virtual Thread per task executor
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();

            for (String url : targetUrls) {
                Future<?> future = executor.submit(() -> {
                    try {
                        // Acquire permit before initiating I/O
                        concurrencyLimiter.acquire();

                        // Randomized polite jitter between 500ms and 1500ms
                        int jitter = ThreadLocalRandom.current().nextInt(500, 1500);
                        Thread.sleep(jitter);

                        long taskStart = System.currentTimeMillis();
                        Document doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                                .timeout(10_000)
                                .get();

                        long duration = System.currentTimeMillis() - taskStart;
                        String pageTitle = doc.title();

                        results.add(new PageScrapeResult(url, 200, pageTitle.length(), duration));
                        System.out.printf("[Virtual Thread %s] Completed: %s (took %d ms)\n",
                                Thread.currentThread(), url, duration);

                    } catch (IOException e) {
                        System.err.printf("[Virtual Thread Error] Failed %s: %s\n", url, e.getMessage());
                        results.add(new PageScrapeResult(url, 500, 0, 0));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // Release permit so next virtual thread can run
                        concurrencyLimiter.release();
                    }
                });

                futures.add(future);
            }

            // Wait for all virtual thread tasks to complete
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    System.err.println("Task execution failed: " + e.getMessage());
                }
            }
        } // Executor auto-closes and awaits termination cleanly

        long totalElapsed = System.currentTimeMillis() - startTime;
        System.out.println("\n================ BATCH PIPELINE COMPLETE ================");
        System.out.printf("Total elapsed time: %d ms | Total pages scraped: %d\n", totalElapsed, results.size());
        results.forEach(r -> System.out.printf("• %s -> Status: %d (Title length: %d chars)\n", r.url(), r.statusCode(), r.titleLength()));
    }
}
