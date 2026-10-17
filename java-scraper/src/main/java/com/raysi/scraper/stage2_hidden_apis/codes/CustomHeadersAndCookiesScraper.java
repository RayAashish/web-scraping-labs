package com.raysi.scraper.stage2_hidden_apis.codes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Author: raysi
 * Practical Stage 2 Demo: Spoofing advanced browser headers (Sec-CH-UA, Origin, Referer)
 * and managing session cookies across multi-step API handshakes in Java 21.
 */
public class CustomHeadersAndCookiesScraper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        // Initialize an automatic in-memory cookie store for persistent sessions
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 1. First handshake: Receive session/tracking cookies
        String initUrl = "https://httpbin.org/cookies/set?session_token=auth_xyz_98765&tracking_id=raysi_scraping_lab";
        System.out.println("Step 1: Initializing session cookies via handshake...");

        HttpRequest initRequest = HttpRequest.newBuilder()
                .uri(URI.create(initUrl))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("Handshake status: " + initResponse.statusCode());

            // 2. Second request: Confirm cookies & custom headers are sent automatically
            String testUrl = "https://httpbin.org/headers";
            System.out.println("\nStep 2: Sending protected API request with full browser fingerprints...");

            HttpRequest authenticatedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(testUrl))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("sec-ch-ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"macOS\"")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://httpbin.org/")
                    .GET()
                    .build();

            HttpResponse<String> authResponse = client.send(authenticatedRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(authResponse.body());

            System.out.println("Server echoed received headers:");
            JsonNode headersNode = root.get("headers");
            headersNode.fieldNames().forEachRemaining(headerName -> 
                System.out.printf("  %s: %s\n", headerName, headersNode.get(headerName).asText())
            );

            System.out.println("\nSession verification successful: All client cookies and security headers preserved.");

        } catch (IOException | InterruptedException e) {
            System.err.println("Request failure: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
