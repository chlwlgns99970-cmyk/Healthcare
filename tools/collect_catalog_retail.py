"""Capture public manufacturer responses with exact request/response hashes.

No key, account, shopper data, retry loop or nutrient inference is used. Cached
responses permit an offline, deterministic import. Nutrition update dates remain
separate from the date the product catalogue was checked.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path
from lxml import html

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data-source/catalog-retail"
DATE = "2026-10-04"
VERSION = "catalog-retail-public-v1"
BING = "https://www.bing.co.kr"
CHAPAGETTI = "https://nongshimmall.com/product/%EC%98%AC%EB%A6%AC%EB%B8%8C%EC%A7%9C%ED%8C%8C%EA%B2%8C%ED%8B%B0140g5/2674/category/43/display/1/"


def capture(url, params=None, force=False):
    method = "POST" if params else "GET"
    canonical = json.dumps({"method": method, "url": url, "params": params or {}}, sort_keys=True)
    key = hashlib.sha256(canonical.encode()).hexdigest()[:20]
    path = OUT / "raw" / (key + ".raw")
    meta_path = path.with_suffix(".json")
    path.parent.mkdir(parents=True, exist_ok=True)
    if not force and path.exists() and meta_path.exists():
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        assert hashlib.sha256(path.read_bytes()).hexdigest() == meta["sourceHash"]
        return path.read_bytes(), meta
    try:
        # Avoid bursts of DNS/socket creation; a failed request is not retried in
        # this run. A later explicit invocation resumes verified cached successes.
        time.sleep(0.2)
        request = urllib.request.Request(url, data=urllib.parse.urlencode(params).encode() if params else None,
                                         headers={"User-Agent": "Healthcare-official-catalog-audit/1.0"})
        with urllib.request.urlopen(request, timeout=25) as response:
            content = response.read()
        path.write_bytes(content)
        meta = {"url": url, "method": method, "params": params or {}, "checkedAt": DATE,
                "parserVersion": VERSION, "sourceHash": hashlib.sha256(content).hexdigest(),
                "path": str(path.relative_to(ROOT)).replace("\\", "/"), "status": "OK"}
    except Exception as error:
        meta = {"url": url, "method": method, "params": params or {}, "checkedAt": DATE,
                "parserVersion": VERSION, "status": "UNRESOLVED", "reason": type(error).__name__ + ": " + str(error)}
        content = b""
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return content, meta


def bing_json(endpoint, params, force=False):
    content, meta = capture(BING + "/product/" + endpoint, params | {"lang": "KO"}, force)
    return (json.loads(content) if content else {}), meta


def collect(force=False):
    catalog, meta = bing_json("getProductList", {"pdt_code": "1", "page_cnt": "1000", "search_name": "", "tag_type": "1"}, force)
    entries, checks = [], [meta]
    for family in catalog.get("list", []):
        family_id = family["FAMILY_IDX"]
        url = BING + "/product/detail?PDT=" + str(family_id)
        content, page_meta = capture(url, force=force)
        checks.append(page_meta)
        if not content: continue
        page = html.fromstring(content.decode("utf-8"))
        category_ids = sorted({int(match.group(1)) for value in page.xpath("//@onclick")
                               if (match := re.search(r"choose_cate\((\d+)\)", value))})
        products = {int(match.group(1)) for value in page.xpath("//@onclick")
                    if (match := re.search(r"get_volume_list\((\d+)\)", value))}
        for category_id in category_ids:
            details, details_meta = bing_json("get_family_ind_info", {"family_ind_idx": category_id, "PDT": family_id}, force)
            checks.append(details_meta)
            products.update(int(value["PROD_IDX"]) for value in details.get("prodList", []))
        print(f"Binggrae family {family_id}: {len(products)} variants", flush=True)
        for product_id in sorted(products):
            volumes, volume_meta = bing_json("get_volume_list", {"prod_idx": product_id}, force)
            checks.append(volume_meta)
            for volume in volumes.get("list", []):
                value_id = volume["PROD_VAL_IDX"]
                nutrition, nutrition_meta = bing_json("get_idt_info", {"prod_val_idx": value_id, "prod_idx": family_id}, force)
                checks.append(nutrition_meta)
                entries.append({"family": family, "productId": product_id, "volume": volume,
                                "nutrition": nutrition, "pageSource": page_meta,
                                "volumeSource": volume_meta, "nutritionSource": nutrition_meta})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "binggrae-products.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    content, chap_meta = capture(CHAPAGETTI, force=force)
    checks.append(chap_meta)
    if content:
        page = html.fromstring(content.decode("utf-8"))
        for element in page.xpath('//*[@id="prdDetail"]//img[@ec-data-src]'):
            address = urllib.parse.urljoin(CHAPAGETTI, element.get("ec-data-src"))
            if "/product_info/" in address:
                _, label_meta = capture(address, force=force)
                checks.append(label_meta)
    report = {"checkedAt": DATE, "parserVersion": VERSION, "binggraeFamilies": len(catalog.get("list", [])),
              "binggraePackageVariants": len(entries), "checks": checks,
              "sourcePolicy": "Public manufacturer catalogue only; no API key required. Existing K-FIND codes never name-join official variants."}
    (OUT / "source-checks.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"packages": len(entries), "checks": len(checks), "failures": sum(x["status"] != "OK" for x in checks)}, ensure_ascii=False))


if __name__ == "__main__":
    args = argparse.ArgumentParser()
    args.add_argument("--refresh", action="store_true", help="Explicitly refresh public source responses.")
    collect(args.parse_args().refresh)
