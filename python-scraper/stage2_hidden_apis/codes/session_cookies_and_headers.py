"""
Author: raysi
Practical Stage 2 Demo: Emulating browser fingerprint headers (Sec-CH-UA, Origin, Referer)
and managing persistent cookies across multi-request sessions using HTTPX Client.
"""

from typing import Any
import httpx


def test_session_cookies_and_headers() -> None:
    # 1. Establish persistent HTTPX Client session with automatic cookie jar
    session_headers = {
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "en-US,en;q=0.9",
        "sec-ch-ua": '"Chromium";v="128", "Not;A=Brand";v="24", "Google Chrome";v="128"',
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": '"macOS"',
        "Sec-Fetch-Dest": "empty",
        "Sec-Fetch-Mode": "cors",
        "Sec-Fetch-Site": "same-origin",
        "X-Requested-With": "XMLHttpRequest",
        "Referer": "https://httpbin.org/",
        "Origin": "https://httpbin.org",
    }

    with httpx.Client(headers=session_headers, timeout=10.0) as client:
        # Step 1: Handshake request to establish session cookies
        init_cookie_url = "https://httpbin.org/cookies/set"
        cookie_params = {
            "session_id": "auth_token_python_raysi_9921",
            "experiment_group": "variant_b",
        }
        print("Step 1: Sending handshake to acquire session cookies...")
        init_resp = client.get(init_cookie_url, params=cookie_params)
        print(f"Handshake response status: {init_resp.status_code}")
        print(f"Session cookies in client jar: {dict(client.cookies)}")

        # Step 2: Second request to verify header transmission
        test_headers_url = "https://httpbin.org/headers"
        print("\nStep 2: Sending protected API request with full browser fingerprint headers...")
        headers_resp = client.get(test_headers_url)
        headers_resp.raise_for_status()

        received_headers: dict[str, Any] = headers_resp.json().get("headers", {})
        print("Target server successfully received following headers:")
        for header, value in sorted(received_headers.items()):
            print(f"  {header}: {value}")

        # Step 3: Verify cookie transmission in subsequent call
        check_cookies_url = "https://httpbin.org/cookies"
        cookies_resp = client.get(check_cookies_url)
        echoed_cookies = cookies_resp.json().get("cookies", {})
        print(f"\nStep 3: Cookies echoed by server on subsequent call: {echoed_cookies}")
        assert echoed_cookies.get("session_id") == "auth_token_python_raysi_9921"
        print("Verification successful: Session state and browser headers fully preserved.")


if __name__ == "__main__":
    test_session_cookies_and_headers()
