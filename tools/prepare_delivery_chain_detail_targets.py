"""Prepare a bounded official detail list from reviewed captured menu cards."""
import json
import re
from pathlib import Path
from urllib.parse import urljoin
from lxml import html
from capture_delivery_chain_sources import ROOT, SOURCE, RAW


def prepare():
    targets = []
    previous = json.loads(RAW.read_text(encoding="utf-8"))
    # Retain actual menu details, remove navigation pages selected by the retired
    # broad CSS selector. Those pages are unrelated to food/menu provenance.
    pages = [p for p in previous if not (p["key"].startswith("detail-baskin-ice-")
        and not p["key"].startswith("detail-baskin-ice-view-php-seq-"))]
    for page in pages:
        if page["key"] == "norang-chicken-http":
            page["brand"] = "노랑통닭"
    if pages != previous:
        RAW.write_text(json.dumps(pages,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    for page in pages:
        if "text" not in page:
            continue
        key = page["key"]
        tree = html.fromstring(page["text"])
        nodes = []
        if key == "pizzaschool-menu":
            nodes = tree.xpath("//h3[contains(@class,'grid-entry-title')]/a")
            # Add edible main/sides; accessory topping options are not nutrition variants.
            nodes = [n for n in nodes if any(word in n.text_content() for word in
                ("피자", "스파게티", "치즈볼", "치킨텐더", "치킨스틱", "새우링", "꽈배기", "그라탕"))]
        elif key in ("youngman-menu", "youngman-original", "youngman-side"):
            nodes = tree.xpath("//*[contains(@class,'menu_name')]/ancestor::a[1]")
        elif key == "baskin-ice":
            nodes = tree.xpath("//strong[@class='menu-list__title']/../a[@href]")
        elif key == "paris-menu":
            nodes = tree.xpath("//h3[@class='product-name']/ancestor::a[1]")
        for node in nodes:
            link = node.get("href", "")
            if not link or link.startswith("javascript:"):
                continue
            url = urljoin(page["sourceUrl"], link)
            identity = re.sub(r"[^a-z0-9]", "-", link.lower()).strip("-")[-100:]
            targets.append([page["brand"], f"detail-{key}-{identity}", url])
    deduped = {row[2]: row for row in targets}
    result = sorted(deduped.values(), key=lambda row: row[1])
    if len(result) > 200:
        raise ValueError("Review new bounded detail scope before increasing the 200-page limit")
    output = SOURCE / "delivery-chain-detail-targets.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {len(result)} exact official menu detail URLs")


if __name__ == "__main__":
    prepare()
