"""Capture public official original fields before deterministic franchise parsing."""
from __future__ import annotations
import argparse
import concurrent.futures
import csv
import hashlib
import json
import re
import urllib.request
import urllib.parse
from pathlib import Path
from lxml import html
from generate_franchise_brand_audit import parse_catalog

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "data-source/franchise/raw"
DATE = "2026-10-04"


def fetch(url, body=None, json_body=False):
    try:
        parts = urllib.parse.urlsplit(url)
        url = urllib.parse.urlunsplit((parts.scheme, parts.netloc,
            urllib.parse.quote(parts.path, safe="/%"), urllib.parse.quote(parts.query, safe="=&+/%:?"), parts.fragment))
        headers={"User-Agent": "Mozilla/5.0", "Accept": "text/html,application/json,*/*"}
        if json_body:
            headers["Content-Type"]="application/json; charset=utf-8"
        request = urllib.request.Request(url, data=(json.dumps(body).encode() if json_body else urllib.parse.urlencode(body).encode()) if body is not None else None,
            headers=headers)
        with urllib.request.urlopen(request, timeout=14) as response:
            payload = response.read()
            encoding = response.headers.get_content_charset()
            if not encoding:
                match = re.search(br'charset=["\s\']*([a-zA-Z0-9_-]+)', payload[:12000])
                encoding = match.group(1).decode() if match else "utf-8"
            try:
                text = payload.decode(encoding)
            except (UnicodeDecodeError, LookupError):
                text = payload.decode("cp949", errors="replace")
            return dict(url=url, resolvedUrl=response.url, status=response.status, checkedAt=DATE,
                sha256=hashlib.sha256(payload).hexdigest().upper(), text=text)
    except Exception as error:
        return dict(url=url, checkedAt=DATE, error=f"{type(error).__name__}: {error}")


def capture_bon():
    menus = list(csv.DictReader((ROOT / "data-source/franchise/official-menu-snapshot.csv").open(encoding="utf-8-sig")))
    requests = []
    for menu in menus:
        code, number = menu["sourceFoodCode"].split("-")[1:]
        requests.append((menu, f"https://api.bonif.co.kr/brand/v1/menu/detail?brdCd={code}&cmdtIdx={number}"))
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        futures = {executor.submit(fetch, url): menu for menu, url in requests}
        for future in concurrent.futures.as_completed(futures):
            menu, fetched = futures[future], future.result()
            if "text" in fetched:
                try:
                    payload = json.loads(fetched.pop("text"))
                    info = payload["data"]["commodityDetailDto"]["commodityInfoDto"]
                    if info["cmdtNm"].strip() != menu["name"] or info["brdNm"] != menu["brand"]:
                        raise ValueError("Official menu identity changed")
                    fetched["apiDate"] = payload["date"]
                    fetched["originalFields"] = {key: info.get(key) for key in
                        ("cmdtIdx", "brdCd", "brdNm", "cmdtNm", "subExp", "menuFeature", "materialsStory", "allergyText", "originText")}
                except Exception as error:
                    fetched["error"] = str(error)
            results.append(dict(menuId=menu["id"], **fetched))
            if len(results) % 50 == 0:
                print(f"BON official details {len(results)}/{len(menus)}", flush=True)
    # Repeated brand-wide allergy tables are stored once, referenced by hash.
    tables = {}
    for result in results:
        if "originalFields" not in result:
            continue
        value = result["originalFields"].pop("allergyText", "") or ""
        if value:
            key = hashlib.sha256(value.encode()).hexdigest()
            tables[key] = value
            result["originalFields"]["allergyTableHash"] = key
    RAW.mkdir(parents=True, exist_ok=True)
    (RAW / "bon-details.json").write_text(json.dumps(dict(checkedAt=DATE, parserVersion="franchise-quality-v1",
        menus=sorted(results, key=lambda row: row["menuId"]), allergyTables=dict(sorted(tables.items()))), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"BON complete {sum('originalFields' in row for row in results)}/{len(results)}", flush=True)


def capture_zero():
    catalog = parse_catalog((ROOT / "app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt").read_text(encoding="utf-8-sig"))
    zero = json.loads((ROOT / "data-source/franchise/expansion-audit-summary.json").read_text(encoding="utf-8-sig"))["zeroMenuBrands"]
    pages = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=7) as executor:
        jobs = {executor.submit(fetch, brand["officialUrl"]): brand for brand in catalog if brand["name"] in zero}
        for future in concurrent.futures.as_completed(jobs):
            brand, page = jobs[future], future.result()
            pages.append(dict(brand=brand["name"], **page))
    RAW.mkdir(parents=True, exist_ok=True)
    (RAW / "zero-brand-homepages.json").write_text(json.dumps(sorted(pages, key=lambda row: row["brand"]), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for page in sorted(pages, key=lambda row: row["brand"]):
        links = []
        if "text" in page:
            document = html.fromstring(page["text"])
            for anchor in document.xpath("//a[@href]"):
                label = " ".join(anchor.itertext()).strip()
                href = anchor.get("href")
                if re.search(r"메뉴|영양|알레르기|menu|nutrition|allergen", label + " " + href, re.I):
                    from urllib.parse import urljoin
                    target = urljoin(page["resolvedUrl"], href)
                    if target.startswith("http") and target not in links:
                        links.append(target)
        print(json.dumps(dict(brand=page["brand"], size=len(page.get("text", "")), error=page.get("error"), links=links[:18]), ensure_ascii=False), flush=True)


def capture_pages(manifest):
    requests = json.loads(Path(manifest).read_text(encoding="utf-8-sig"))
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=7) as executor:
        futures = {executor.submit(fetch, row["url"], row.get("body"), row.get("jsonBody",False)): row for row in requests}
        for future in concurrent.futures.as_completed(futures):
            row = futures[future]
            results.append(dict(brand=row["brand"], purpose=row.get("purpose", "MENU"), requestBody=row.get("body"), **future.result()))
    target = RAW / (Path(manifest).stem + "-responses.json")
    target.write_text(json.dumps(sorted(results, key=lambda row: (row["brand"], row["url"], str(row.get("requestBody")))), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{target.name}: {sum('text' in row for row in results)}/{len(results)} fetched", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bon-details", action="store_true")
    parser.add_argument("--zero-sites", action="store_true")
    parser.add_argument("--pages")
    args = parser.parse_args()
    if args.zero_sites:
        capture_zero()
    if args.bon_details:
        capture_bon()
    if args.pages:
        capture_pages(args.pages)
