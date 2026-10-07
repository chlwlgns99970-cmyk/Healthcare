"""Finite public official-page capture for the delivery-chain expansion.

This is discovery/capture only. No delivery-app endpoints, crawling, authentication,
CAPTCHA bypass or guessed menu/nutrition values are used. Offline import lives in
import_delivery_chains.py. Keep failures alongside successful original pages.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import re
import subprocess
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data-source/franchise"
RAW = SOURCE / "raw/delivery-chain-pages.json"
DATE = "2026-10-04"


def fetch(target):
    brand, key, url, *options = target
    parts = urllib.parse.urlsplit(url)
    encoded = urllib.parse.urlunsplit((parts.scheme, parts.netloc,
        urllib.parse.quote(parts.path, safe="/%"), urllib.parse.quote(parts.query, safe="=&+/%:?"), parts.fragment))
    row = dict(brand=brand, key=key, sourceUrl=url, checkedAt=DATE, sourceType="OFFICIAL_BRAND_WEB_PAGE")
    try:
        request = urllib.request.Request(encoded, headers={"User-Agent": "Mozilla/5.0", "Accept": "text/html,application/json"})
        if options == ["NATIVE_TLS"]:
            # Windows' certificate store supports these official legacy TLS sites.
            # No insecure flag is used; authentication and CAPTCHA are untouched.
            result = subprocess.run(["curl.exe", "--fail", "--silent", "--show-error", "--location", "--max-time", "15", encoded],
                capture_output=True, check=True)
            payload = result.stdout
            match = re.search(br'charset=["\s\']*([a-zA-Z0-9_-]+)', payload[:15000])
            text = payload.decode(match.group(1).decode() if match else "utf-8", errors="replace")
            row.update(status=200, resolvedUrl=url, byteCount=len(payload),
                originalSha256=hashlib.sha256(payload).hexdigest().upper(), text=text,
                textSha256=hashlib.sha256(text.encode()).hexdigest().upper(), captureTransport="WINDOWS_SYSTEM_TRUST_TLS")
            return row
        with urllib.request.urlopen(request, timeout=15) as response:
            payload = response.read()
            encoding = response.headers.get_content_charset()
            match = re.search(br'charset=["\s\']*([a-zA-Z0-9_-]+)', payload[:15000])
            encoding = encoding or (match.group(1).decode() if match else "utf-8")
            try:
                text = payload.decode(encoding)
            except (UnicodeDecodeError, LookupError):
                text = payload.decode("cp949", errors="replace")
            row.update(status=response.status, resolvedUrl=response.url, byteCount=len(payload),
                originalSha256=hashlib.sha256(payload).hexdigest().upper(), text=text,
                textSha256=hashlib.sha256(text.encode()).hexdigest().upper())
            if "Please prove that you are human" in text:
                row["failureReason"] = "OFFICIAL_PAGE_SECURITY_CHALLENGE"
    except Exception as error:
        row["failureReason"] = f"{type(error).__name__}: {error}"
    return row


def capture(targets):
    previous = json.loads(RAW.read_text(encoding="utf-8")) if RAW.exists() else []
    merged = {row["key"]: row for row in previous}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        for row in executor.map(fetch, targets):
            merged[row["key"]] = row
            print(row["brand"], row["key"], row.get("status", "FAILED"), row.get("byteCount", 0), row.get("failureReason", ""), flush=True)
    RAW.parent.mkdir(parents=True, exist_ok=True)
    RAW.write_text(json.dumps(sorted(merged.values(), key=lambda row: row["key"]), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--targets", type=Path, required=True, help="Reviewed finite JSON array of [brand, key, official URL]")
    args = parser.parse_args()
    capture(json.loads(args.targets.read_text(encoding="utf-8-sig")))
