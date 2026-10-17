"""
Author: raysi
Practical Stage 2 Demo: Reverse-engineering the DevTools Network Tab (Fetch/XHR)
to query backend JSON endpoints directly in Python using HTTPX and structured models.
"""

import random
import time
from typing import Any
import httpx
from pydantic import BaseModel, ConfigDict


class ProductItem(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: int
    title: str
    price: float
    rating: float
    category: str
    stock: int


class ProductApiResponse(BaseModel):
    model_config = ConfigDict(extra="ignore")
    products: list[ProductItem]
    total: int
    skip: int
    limit: int


def polite_sleep(min_sec: float = 1.0, max_sec: float = 2.5) -> None:
    time.sleep(random.uniform(min_sec, max_sec))


def fetch_api_catalog() -> list[ProductItem]:
    # Discovered via Chrome DevTools -> Network -> Fetch/XHR filter
    endpoint = "https://dummyjson.com/products"
    params = {
        "limit": 5,
        "select": "title,price,rating,category,stock",
    }
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": "https://dummyjson.com/",
    }

    products: list[ProductItem] = []

    print(f"Sending direct API request to: {endpoint} with params={params}")

    try:
        with httpx.Client(headers=headers, timeout=10.0) as client:
            response = client.get(endpoint, params=params)

            if response.status_code == 200:
                payload: dict[str, Any] = response.json()
                api_data = ProductApiResponse.model_validate(payload)

                products = api_data.products
                print(f"\nSuccessfully fetched {len(products)} records (Total in DB: {api_data.total}):")
                for p in products:
                    print(f"• [{p.category}] {p.title} | ${p.price:.2f} | Rating: {p.rating}★ | Stock: {p.stock}")

                polite_sleep(1.0, 2.5)

            elif response.status_code == 429:
                print("Received HTTP 429 Too Many Requests. Applying backoff...")
            else:
                print(f"API request failed with status: {response.status_code}")

    except httpx.HTTPError as err:
        print(f"Network error during API extraction: {err}")

    return products


if __name__ == "__main__":
    fetch_api_catalog()
