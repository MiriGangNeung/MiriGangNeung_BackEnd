#!/usr/bin/env python3
"""Measure place JSON and image delivery without third-party dependencies."""

from __future__ import annotations

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
from typing import Any


USER_AGENT = "MiriGangNeung-image-benchmark/1.0"


def percentile(values: list[float], percentage: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((percentage / 100) * (len(ordered) - 1))))
    return ordered[index]


def fetch(url: str, timeout: float) -> tuple[dict[str, Any] | None, bytes, float, float, str | None]:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            headers_received = time.perf_counter()
            body = response.read()
            completed = time.perf_counter()
            return (
                dict(response.headers.items()),
                body,
                (headers_received - started) * 1000,
                (completed - started) * 1000,
                None,
            )
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        completed = time.perf_counter()
        return None, b"", 0.0, (completed - started) * 1000, type(error).__name__


def normalized_urls(place: dict[str, Any]) -> list[str]:
    candidates = [place.get("thumbnailUrl"), *(place.get("imageUrls") or [])]
    result: list[str] = []
    for candidate in candidates:
        if not isinstance(candidate, str):
            continue
        value = candidate.strip()
        if value and value not in result:
            result.append(value)
    return result[:5]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", "--api-base", default="http://localhost:8080/api/v1")
    parser.add_argument("--sample-places", type=int, default=10)
    parser.add_argument("--images-per-place", type=int, default=5)
    parser.add_argument("--passes", type=int, default=1)
    parser.add_argument("--timeout", type=float, default=15.0)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    list_url = f"{base_url}/places?page=0&size=100"
    list_headers, list_body, list_ttfb, list_total, list_error = fetch(list_url, args.timeout)

    places: list[dict[str, Any]] = []
    if list_body:
        try:
            payload = json.loads(list_body.decode("utf-8"))
            places = payload.get("content", []) if isinstance(payload, dict) else []
        except (UnicodeDecodeError, json.JSONDecodeError):
            places = []

    image_urls: list[str] = []
    for place in places[: max(0, args.sample_places)]:
        for image_url in normalized_urls(place)[: max(0, args.images_per_place)]:
            if image_url not in image_urls:
                image_urls.append(image_url)

    ttfb_values: list[float] = []
    total_values: list[float] = []
    byte_values: list[int] = []
    error_count = 0
    passes = max(1, args.passes)
    for _ in range(passes):
        for image_url in image_urls:
            headers, body, ttfb, total, error = fetch(image_url, args.timeout)
            if error or headers is None or not headers.get("Content-Type", "").lower().startswith("image/"):
                error_count += 1
                continue
            ttfb_values.append(ttfb)
            total_values.append(total)
            byte_values.append(len(body))

    attempts = len(image_urls) * passes
    successes = len(byte_values)
    output = {
        "list_ttfb_ms": round(list_ttfb, 3),
        "list_total_ms": round(list_total, 3),
        "list_bytes": len(list_body),
        "list_error": list_error,
        "image_count": len(image_urls),
        "image_attempts": attempts,
        "image_success_rate": round(successes / attempts, 4) if attempts else 0.0,
        "image_error_count": error_count,
        "image_bytes_total": sum(byte_values),
        "image_bytes_mean": round(statistics.mean(byte_values)) if byte_values else 0,
        "image_ttfb_p50_ms": round(percentile(ttfb_values, 50), 3),
        "image_ttfb_p95_ms": round(percentile(ttfb_values, 95), 3),
        "image_total_p50_ms": round(percentile(total_values, 50), 3),
        "image_total_p95_ms": round(percentile(total_values, 95), 3),
    }
    print(json.dumps(output, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
