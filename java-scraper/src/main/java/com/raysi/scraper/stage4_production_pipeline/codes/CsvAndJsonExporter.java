package com.raysi.scraper.stage4_production_pipeline.codes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Author: raysi
 * Practical Stage 4 Demo: Exporting, sanitizing, and formatting scraped data models
 * into production-ready CSV and indented JSON files.
 */
public class CsvAndJsonExporter {

    public record CleanProduct(String id, String title, double priceUsd, String category, boolean inStock) {}

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        // Sample extracted data with raw impurities
        List<CleanProduct> products = List.of(
                new CleanProduct("SKU-1001", "A Light in the Attic", 51.77, "Poetry", true),
                new CleanProduct("SKU-1002", "Tipping the Velvet", 53.74, "Historical Fiction", false),
                new CleanProduct("SKU-1003", "Soumission", 50.10, "Fiction", true),
                new CleanProduct("SKU-1004", "Sharp Objects", 47.82, "Mystery", true)
        );

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File csvFile = new File(outputDir, "scraped_products.csv");
        File jsonFile = new File(outputDir, "scraped_products.json");

        try {
            // 1. Export to CSV using Apache Commons CSV
            exportToCsv(products, csvFile);
            System.out.println("Exported CSV successfully to: " + csvFile.getAbsolutePath());

            // 2. Export to pretty-printed JSON using Jackson
            exportToJson(products, jsonFile);
            System.out.println("Exported JSON successfully to: " + jsonFile.getAbsolutePath());

            // 3. Print verification
            System.out.println("\nCSV Content Preview:\n" + Files.readString(csvFile.toPath()));

        } catch (IOException e) {
            System.err.println("Export operation failed: " + e.getMessage());
        }
    }

    public static void exportToCsv(List<CleanProduct> items, File targetFile) throws IOException {
        CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setHeader("ID", "Title", "Price_USD", "Category", "In_Stock")
                .build();

        try (FileWriter out = new FileWriter(targetFile);
             CSVPrinter printer = new CSVPrinter(out, format)) {
            for (CleanProduct item : items) {
                printer.printRecord(
                        item.id(),
                        item.title(),
                        String.format("%.2f", item.priceUsd()),
                        item.category(),
                        item.inStock()
                );
            }
        }
    }

    public static void exportToJson(List<CleanProduct> items, File targetFile) throws IOException {
        MAPPER.writeValue(targetFile, items);
    }
}
