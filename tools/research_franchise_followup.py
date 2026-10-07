"""Bounded official/public source review of the actual current franchise identities.

New public requests are cached with failures. Generation is offline and exact-ID
only; no guessed nutrient, allergen, serving, or complete ingredient declaration.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urljoin, urlsplit
import urllib.request

from capture_franchise_quality_sources import fetch
from import_franchise_quality import captured_pages, document, content, allergens, ingredient_tokens, METADATA_HEADERS, make_metadata
from import_franchise_expansion import read_csv, write_csv
from generate_franchise_brand_audit import normalize

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data-source/franchise"
RAW = SOURCE / "raw"
CACHE = RAW / "followup-source-responses.json"
VERSION = "franchise-followup-2026-10-04-v1"
DATE = "2026-10-04"
INITIAL = [
    ("샐러디", "https://salady.com/menu/content2"),
    ("샐러디", "https://salady.com/kor/menu/nutrition"),
    ("놀부부대찌개", "https://m.nolboo.co.kr/"),
    ("놀부부대찌개", "http://nolboo.co.kr/"),
    ("놀부부대찌개", "https://www.instagram.com/nolboo_official/"),
    ("놀부부대찌개", "https://english.visitkorea.or.kr/svc/contents/contentsView.do?vcontsId=57804"),
]


def cached():
    return json.loads(CACHE.read_text(encoding="utf-8")) if CACHE.exists() else []


def capture(requests):
    import concurrent.futures
    previous = cached()
    old = {(r["brand"], r["url"]) for r in previous}
    jobs = [(b, u) for b, u in requests if (b, u) not in old]
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(fetch, u): (b, u) for b, u in jobs}
        for future in concurrent.futures.as_completed(futures):
            brand, url = futures[future]
            row = dict(brand=brand, parserVersion=VERSION, **future.result())
            previous.append(row)
            print(brand, url, row.get("status", row.get("error")), len(row.get("text", "")), flush=True)
    CACHE.write_text(json.dumps(sorted(previous, key=lambda r: (r["brand"], r["url"])), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def salady_detail_requests():
    foods = read_csv(ROOT / "app/src/main/assets/fooddata/franchise_official_items.csv")
    identities = {(r["brand"], normalize(r["name"])) for r in foods}
    requests = set()
    for row in cached():
        if row["url"] not in ("https://salady.com/menu/list_1", "https://salady.com/menu2/list_1?menu2=1") or not row.get("text"):
            continue
        brand = "샐러디&샌드위치" if "/menu2/" in row["url"] else "샐러디"
        for node in document(row["text"]).xpath('//li[.//h6]/a[@href]'):
            names = node.getparent().xpath('.//h6')
            if not names or (brand, normalize(content(names[0]))) not in identities:
                continue
            target = urljoin(row["url"], node.get("href"))
            if "/menu/view_1" in target or "/menu2/view_1" in target:
                requests.add((brand, target))
    return sorted(requests)


def links():
    for row in cached():
        if not row.get("text"):
            continue
        root = document(row["text"])
        selected = []
        for node in root.xpath("//a[@href]"):
            label = content(node)
            target = urljoin(row["url"], node.get("href"))
            if re.search("알레르기|원재료|영양|nutrition|allerg|ingredient|pdf|메뉴", label + " " + target, re.I):
                selected.append((label, target))
        print(row["brand"], row["url"], json.dumps(selected[:30], ensure_ascii=False))


def capture_binary(url, label):
    manifest = RAW / "followup-binary-manifest.json"
    rows = json.loads(manifest.read_text(encoding="utf-8")) if manifest.exists() else {}
    if url in rows:
        return
    row = dict(url=url, checkedAt=DATE, parserVersion=VERSION)
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read()
            row.update(status=response.status, resolvedUrl=response.url,
                sha256=hashlib.sha256(raw).hexdigest().upper(), byteCount=len(raw),
                path=f"raw/followup-{label}-{hashlib.sha256(url.encode()).hexdigest()[:10]}{Path(urlsplit(url).path).suffix or '.raw'}")
        (SOURCE / row["path"]).write_bytes(raw)
    except Exception as error:
        row["error"] = f"{type(error).__name__}: {error}"
    rows[url] = row
    manifest.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(row, ensure_ascii=False))


def actual_identities():
    foods = []
    for filename in ("food_items.csv", "product_items.csv", "franchise_official_items.csv"):
        foods += read_csv(ROOT / "app/src/main/assets/fooddata" / filename)
    menus = []
    for filename in ("official-menu-snapshot.csv", "additional-menu-snapshot.csv", "quality-menu-snapshot.csv", "legacy-menu-snapshot.csv"):
        menus += read_csv(SOURCE / filename)
    return foods, menus


def salady_evidence(foods):
    import pdfplumber
    exact = {}
    for food in foods:
        exact.setdefault((food["brand"], normalize(food["name"])), []).append(food)
    output = []
    manifest = json.loads((RAW / "followup-binary-manifest.json").read_text(encoding="utf-8"))
    source = manifest["https://salady.com/pdf/allergy.pdf?ver=261001"]
    pdf = SOURCE / source["path"]
    if hashlib.sha256(pdf.read_bytes()).hexdigest().upper() != source["sha256"]:
        raise ValueError("Reviewed Salady allergen source changed")
    labels = ("달걀", "우유", "메밀", "땅콩", "대두", "밀", "잣", "호두", "게", "새우", "오징어", "고등어", "조개류", "복숭아", "토마토", "닭고기", "돼지고기", "소고기", "아황산류")
    with pdfplumber.open(pdf) as doc:
        for page_number, brand in ((0, "샐러디"), (1, "샐러디&샌드위치")):
            page = doc.pages[page_number]
            if "2026.10" not in page.extract_text() or "일부표시는생략" not in page.extract_text():
                raise ValueError("Salady table version or omission policy changed")
            table = page.extract_tables()[0]
            if len(table[1]) != 21 or table[1][2:6] != list(labels[:4]):
                raise ValueError("Salady allergen table column order changed")
            for cells in table[2:]:
                if len(cells) != 21 or not cells[1]:
                    raise ValueError("Salady allergen table row schema changed")
                name = re.sub(r"\s+", " ", cells[1]).strip()
                # This PDF's long sandwich name crosses the first allergen
                # cell border. Retain its literal Hangul glyphs and marker.
                if name == "[프로틴] 선데이 아보카도 치킨 샌드" and cells[2] == "위치●":
                    name += "위치"
                    cells[2] = "●"
                direct = ", ".join(label for label, value in zip(labels, cells[2:]) if value == "●")
                if any(value not in ("", "●", None) for value in cells[2:]):
                    raise ValueError("Unrecognized allergen marker")
                for food in exact.get((brand, normalize(name)), []):
                    menu = dict(food, sourceFoodCode=food["sourceFoodCode"])
                    capture = dict(url=source["url"], checkedAt=source["checkedAt"], sha256=source["sha256"])
                    row = make_metadata(menu, capture, direct=direct,
                        status="PARTIAL_DECLARATION" if direct else "UNKNOWN", date="2026-10")
                    row.update(parserVersion=VERSION,
                        evidenceKind=f"OFFICIAL_ALLERGEN_PDF_PAGE_{page_number+1}_POLICY_ALLOWS_NAME_OBVIOUS_OMISSIONS",
                        staleCandidate="true")
                    output.append(row)
    # Structured menu components are preserved verbatim. Compound recipe
    # subingredients are not disclosed, so this is not a full declaration.
    descriptions = {}
    for source in cached():
        if not re.search(r"/menu2?/view_1", source["url"]) or not source.get("text"):
            continue
        root = document(source["text"])
        headers = root.xpath('//h3')
        if not headers:
            continue
        name = content(headers[0])
        components = [(content(n.xpath('./strong')[0]), content(n.xpath('./p')[0]))
            for n in root.xpath('//div[@class="right_txt"]/div[@class="text"]')
            if n.xpath('./strong') and n.xpath('./p')]
        components = [(label, value) for label, value in components if label in ("베이스", "토핑", "기본 드레싱", "드레싱") and value]
        if not components:
            continue
        key = (source["brand"], normalize(name))
        raw = " · ".join(f"{label}: {value}" for label, value in components)
        if key in descriptions and descriptions[key][0] != raw:
            raise ValueError("Exact Salady menu component disagreement")
        descriptions.setdefault(key, (raw, components, source))
    for key, (raw, components, source) in sorted(descriptions.items()):
        for food in exact.get(key, []):
            row = make_metadata(food, dict(url=source["url"], checkedAt=source["checkedAt"], sha256=source["sha256"]), ingredients=raw)
            # The actual structured component names are already source facts;
            # preserve them instead of throwing away unknown compound tokens.
            tokens = sorted({value.strip() for _, values in components for value in values.split(",") if value.strip()})
            row.update(ingredients="|".join(tokens), ingredientStatus="PARTIAL_MENU_COMPONENTS",
                parserVersion=VERSION, evidenceKind="OFFICIAL_STRUCTURED_MENU_BASE_TOPPING_DEFAULT_DRESSING_SUBINGREDIENTS_UNPUBLISHED",
                staleCandidate="true")
            output.append(row)
    return output


def poke_allergen_evidence(identities):
    captures = [row for row in captured_pages() if row["brand"] == "포케올데이" and row["url"].endswith("/allergy") and row.get("text")]
    if not captures:
        return []
    source = captures[-1]
    # Literal public HTML script records; do not execute remote JavaScript.
    from lxml import html
    root = html.fromstring(source["text"])
    declarations = {}
    wanted = {normalize(r["name"]) for r in identities if r["brand"] == "포케올데이"}
    for section in root.xpath('//div[contains(@class,"allergy_sort_item_wrap")]'):
        balance = "wrap5" in section.get("class", "").split()
        for script in section.xpath('./script'):
            for name, quote, direct in re.findall(r"name\s*:\s*['\"]([^'\"]+)['\"]\s*,\s*value\s*:\s*(['\"])(.*?)\2", script.text or "", re.S):
                name = content(document(name)) + (" 밸런스 박스" if balance else "")
                key = normalize(name)
                if key not in wanted:
                    continue
                if key in declarations and declarations[key] != direct:
                    raise ValueError("Poke exact declaration conflict")
                declarations[key] = direct
    output = []
    for menu in identities:
        if menu["brand"] != "포케올데이":
            continue
        raw = declarations.get(normalize(menu["name"]))
        if raw is None or raw.strip() in ("", "-"):
            continue
        row = make_metadata(menu, source, direct=raw, status="PARTIAL_DECLARATION")
        row.update(parserVersion=VERSION, evidenceKind="OFFICIAL_LITERAL_JS_ALLERGEN_TABLE_CATEGORY_QUALIFIED_IDENTITY_CROSS_CONTACT_UNPUBLISHED", staleCandidate="true")
        output.append(row)
    return output


def generate():
    foods, menus = actual_identities()
    output = salady_evidence(foods) + poke_allergen_evidence(foods + menus) + schoolfood_evidence(foods + menus) + hongik_evidence(foods + menus)
    write_csv(SOURCE / "food-metadata-followup.csv", METADATA_HEADERS,
              sorted(output, key=lambda row: (row["foodItemId"], row["sourceUrl"])))
    print("Followup evidence", len(output), "unique", len({r["foodItemId"] for r in output}),
          "ingredients", len({r["foodItemId"] for r in output if r["ingredients"]}),
          "allergens", len({r["foodItemId"] for r in output if r["allergens"]}))
    audit_all_missing(output)


def schoolfood_evidence(identities):
    source = next(row for row in cached() if row["brand"] == "스쿨푸드" and row["url"].endswith("/pop/pop_orgin.html"))
    labels = {}
    for name, raw in re.findall(r"■([^:<]+)\s*:\s*([^<]+)", source["text"]):
        key = normalize(content(document(name)))
        direct = content(document(raw))
        if key in labels and labels[key] != direct:
            raise ValueError("Schoolfood exact declaration conflict")
        labels[key] = direct
    output = []
    for identity in identities:
        if identity["brand"] != "스쿨푸드":
            continue
        raw = labels.get(normalize(identity["name"]))
        if not raw:
            continue
        row = make_metadata(identity, source, direct=raw, status="PARTIAL_DECLARATION")
        row.update(parserVersion=VERSION, evidenceKind="OFFICIAL_ALLERGEN_TEXT_EXACT_NAME_CURRENT_RECIPE_VERSION_UNVERIFIED", staleCandidate="true")
        output.append(row)
    return output


def hongik_evidence(identities):
    review = json.loads((SOURCE / "reviewed-followup-hongik-allergens.json").read_text(encoding="utf-8"))
    binary = json.loads((RAW / "followup-binary-manifest.json").read_text(encoding="utf-8"))
    by_path = {row.get("path"): row for row in binary.values()}
    declarations = {}
    for table in review["tables"]:
        if hashlib.sha256((SOURCE / table["path"]).read_bytes()).hexdigest().upper() != table["sha256"]:
            raise ValueError("Reviewed Hongik source image changed")
        source = by_path[table["path"]]
        for label in table["rows"]:
            raw = ", ".join(review["labels"][value-1] for value in label["positive"])
            for name in label["names"]:
                key = normalize(name)
                if key in declarations:
                    raise ValueError("Duplicate reviewed Hongik exact label")
                declarations[key] = (raw, table.get("crossContactText", ""), label.get("conflict", ""), source)
    output = []
    for identity in identities:
        if identity["brand"] != "홍익돈까스":
            continue
        label = declarations.get(normalize(identity["name"]))
        if label is None:
            continue
        raw, cross, conflict, source = label
        capture = dict(url=source["url"], checkedAt=source["checkedAt"], sha256=source["sha256"])
        row = make_metadata(identity, capture, direct=raw, cross=cross, status="PARTIAL_DECLARATION")
        row.update(parserVersion=VERSION, evidenceKind="OFFICIAL_REVIEWED_MAIN_DISH_ALLERGEN_IMAGE_SIDES_NOT_INCLUDED" + ("_POSITIVE_COLUMN_NUMBER_CONFLICT" if conflict else ""), staleCandidate="true")
        output.append(row)
    return output


def audit_all_missing(followup_evidence):
    """Current snapshot identities, resolved append-only foods, and exact failures."""
    from collections import Counter
    foods, menus = actual_identities()
    baseline_root = ROOT / "app/build/food-quality-followup"
    baseline_food_ids = set()
    for filename in ("food_items.csv", "product_items.csv", "franchise_official_items.csv"):
        baseline_food_ids.update(r["id"] for r in read_csv(baseline_root / filename))
    # Restore starting menu-only identities that moved into nutrition rows.
    restored = [r for r in read_csv(SOURCE / "quality-nutrition.csv") if r["id"] not in baseline_food_ids]
    target = {r["id"]: r for r in menus + restored}
    expected = json.loads((ROOT / "data-source/food-quality/followup-baseline.json").read_text(encoding="utf-8"))["franchiseMenu"]["nutritionMissingMenuCount"]
    if len(target) != expected:
        raise ValueError("Actual start menu identities no longer reconcile to captured followup baseline")
    metadata = {}
    for row in read_csv(SOURCE / "food-metadata-evidence.csv") + followup_evidence:
        metadata.setdefault(row["foodItemId"], []).append(row)
    excluded = {(r["brand"], normalize(r["name"])):r for r in json.loads((SOURCE / "quality-nutrition-exclusions.json").read_text(encoding="utf-8"))}
    captures = captured_pages() + cached()
    bon = json.loads((RAW / "bon-details.json").read_text(encoding="utf-8"))["menus"]
    bon_by_id = {r["menuId"]:r for r in bon}
    restored_by_id = {r["id"]:r for r in restored}
    rows = []
    for menu_id, menu in sorted(target.items()):
        reviewed = [r for r in captures if r["brand"] == menu["brand"]]
        evidence = metadata.get(menu_id, [])
        if menu_id in bon_by_id:
            reviewed = reviewed + [bon_by_id[menu_id]]
        reasons = []
        source_exclusion = excluded.get((menu["brand"], normalize(menu["name"])))
        if menu_id in restored_by_id:
            reasons.append("EXACT_VISIBLE_NUTRITION_CARD_FIELDS_PREVIOUSLY_DROPPED_BY_NAME_ONLY_PARSER_RESTORED")
        elif source_exclusion:
            reasons.append(source_exclusion["reason"])
        elif menu_id in bon_by_id:
            reasons.append("OFFICIAL_MENU_JSON_ORIGINAL_FIELDS_HAVE_NO_NUTRIENTS_OR_MEASURED_SERVING")
        elif menu["brand"] in ("두끼", "탕화쿵푸", "춘리마라탕", "채선당", "소담촌"):
            reasons.append("VARIABLE_SELECTION_OR_SIZE_NO_EXACT_FIXED_MENU_NUTRITION_BASIS")
        elif any("403" in r.get("error", "") for r in reviewed):
            reasons.append("OFFICIAL_DETAIL_OR_API_HTTP403_ALTERNATIVE_PUBLIC_LIST_CHECKED_NO_EXACT_NUTRITION_BASIS")
        else:
            reasons.append("OFFICIAL_CURRENT_MENU_AND_AVAILABLE_DETAIL_SOURCES_NO_EXACT_MENU_NUTRITION_BASIS_AND_MASS")
        rows.append(dict(menuId=menu_id, sourceFoodCode=menu.get("sourceFoodCode", menu_id.removeprefix("official-").upper()),
            brand=menu["brand"], name=menu["name"], checkedAt=DATE,
            nutritionResolution="RESOLVED_EXACT_OFFICIAL_CARD" if menu_id in restored_by_id else "UNRESOLVED",
            nutritionSource=restored_by_id.get(menu_id, {}).get("sourceUrl", ""),
            officialNutrition=restored_by_id.get(menu_id), reason=";".join(reasons),
            ingredientResolution="PARTIAL_SOURCE_COMPONENTS" if any(r["ingredients"] for r in evidence) else "UNKNOWN",
            completeIngredientResolution="ORIGINAL_SOURCES_DO_NOT_PUBLISH_WHOLE_COMPOUND_INGREDIENT_DECLARATION",
            allergenResolution="POSITIVE_OFFICIAL_DECLARATION" if any(r["allergens"] for r in evidence) else "UNKNOWN",
            sourceFieldsRestored=menu_id in restored_by_id,
            reviewedSources=[dict(url=r["url"],status=r.get("status"),error=r.get("error",""),sourceHash=r.get("sha256",""),checkedAt=r.get("checkedAt",DATE)) for r in reviewed],
            sourceMetadata=[dict(sourceUrl=r["sourceUrl"],sourceHash=r["sourceHash"],ingredientStatus=r["ingredientStatus"],allergenStatus=r["allergenStatus"],parserVersion=r["parserVersion"]) for r in evidence]))
    if any(not r["reviewedSources"] for r in rows):
        raise ValueError("A current missing menu brand has no audited official source")
    (SOURCE / "followup-menu-information-audit.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    summary = dict(checkedAt=DATE,parserVersion=VERSION,
        auditedStartMissingMenuCount=len(rows),resolvedNutritionCount=len(restored),remainingNutritionMissingCount=len(rows)-len(restored),
        brandCount=len({r["brand"] for r in rows}),reasonCounts=dict(sorted(Counter(r["reason"] for r in rows).items())),
        ingredientPartialCount=sum(r["ingredientResolution"] != "UNKNOWN" for r in rows),ingredientCompleteCount=0,
        allergenEvidenceCount=sum(r["allergenResolution"] != "UNKNOWN" for r in rows),
        followupIngredientIdentityCount=len({r["foodItemId"] for r in followup_evidence if r["ingredients"]}),
        followupAllergenIdentityCount=len({r["foodItemId"] for r in followup_evidence if r["allergens"]}),
        completeIngredientDiagnosis="Official original menus have descriptions/components and allergen charts, but not whole compound ingredient declarations. Literal component strings, HTML JS declarations, and PDF/image positive columns are now retained; none is falsely COMPLETE.",
        zeroBrandReview=[dict(url=r["url"],status=r.get("status"),error=r.get("error",""),sourceHash=r.get("sha256",""),reason="Public profile/linktree only connects delivery apps; KTO direct alternative returns HTTP400. No verified current Korean menu identity or nutrient values." if r.get("status")==200 else "Official mobile/domain/public tourism alternative unsuccessful") for r in cached() if r["brand"]=="놀부부대찌개"])
    (SOURCE / "followup-audit-summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("Current missing audit",len(rows),"resolved",len(restored),"remaining",len(rows)-len(restored))


def capture_hongik_images():
    for row in cached():
        if row["brand"] != "홍익돈까스" or not row["url"].endswith(("allergy_table", "allergy_detail")):
            continue
        for url in sorted(set(re.findall(r'https://contents\.sixshop\.com/thumbnails/uploadedFiles/39154/default/image_(?:178841268\d+|178763962\d+|178841273\d+)_1500\.png', row.get("text", "").replace('\\/', '/')))):
            capture_binary(url, "hongik")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture", action="store_true")
    parser.add_argument("--links", action="store_true")
    parser.add_argument("--request", nargs=2, action="append")
    parser.add_argument("--binary", nargs=2)
    parser.add_argument("--salady-details", action="store_true")
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--hongik-images", action="store_true")
    args = parser.parse_args()
    if args.capture:
        capture(args.request or INITIAL)
    if args.links:
        links()
    if args.binary:
        capture_binary(*args.binary)
    if args.salady_details:
        capture(salady_detail_requests())
    if args.generate:
        generate()
    if args.hongik_images:
        capture_hongik_images()
