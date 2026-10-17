"""
Author: raysi
Practical Stage 4 Demo: Cleaning raw scraped records, converting types,
deduplicating, and exporting into production-ready CSV & formatted JSON using Pandas.
"""

from pathlib import Path
import pandas as pd


def clean_and_export_scraped_data() -> None:
    # Simulated raw records extracted from exploratory scrapers
    raw_records = [
        {"id": "SKU-1001", "title": "  A Light in the Attic \n", "raw_price": "£51.77", "category": "Poetry", "availability": "In stock (22 available)"},
        {"id": "SKU-1002", "title": "Tipping the Velvet", "raw_price": "£53.74", "category": "Historical Fiction", "availability": "In stock (20 available)"},
        {"id": "SKU-1003", "title": "Soumission", "raw_price": "£50.10", "category": "Fiction", "availability": "In stock (20 available)"},
        {"id": "SKU-1004", "title": "Sharp Objects", "raw_price": "£47.82", "category": "Mystery", "availability": "In stock (20 available)"},
        # Duplicate record that should be deduplicated
        {"id": "SKU-1001", "title": "A Light in the Attic", "raw_price": "£51.77", "category": "Poetry", "availability": "In stock"},
        # Malformed record with missing category
        {"id": "SKU-1005", "title": "The Requiem Red", "raw_price": "£22.65", "category": None, "availability": "In stock"},
    ]

    print("Initial raw dataset shape:", len(raw_records))
    df = pd.DataFrame(raw_records)

    # 1. Clean whitespace from text columns
    df["title"] = df["title"].astype(str).str.strip()
    df["category"] = df["category"].fillna("Uncategorized").str.strip()

    # 2. Extract numeric price as float (removing currency symbol '£', '$', etc.)
    df["price_gbp"] = df["raw_price"].str.replace(r"[^\d.]", "", regex=True).astype(float)
    df.drop(columns=["raw_price"], inplace=True)

    # 3. Standardize boolean stock status
    df["is_in_stock"] = df["availability"].str.lower().str.contains("in stock")
    df.drop(columns=["availability"], inplace=True)

    # 4. Deduplicate by unique SKU ID, keeping the latest entry
    df.drop_duplicates(subset=["id"], keep="last", inplace=True)

    # 5. Ensure output directory exists
    output_dir = Path("output")
    output_dir.mkdir(parents=True, exist_ok=True)

    csv_path = output_dir / "clean_products.csv"
    json_path = output_dir / "clean_products.json"

    # Export to CSV
    df.to_csv(csv_path, index=False)
    print(f"Exported clean CSV to: {csv_path.resolve()}")

    # Export to pretty JSON
    df.to_json(json_path, orient="records", indent=2)
    print(f"Exported clean JSON to: {json_path.resolve()}")

    # Print DataFrame summary
    print("\nCleaned DataFrame Preview:")
    print(df.to_string(index=False))


if __name__ == "__main__":
    clean_and_export_scraped_data()
