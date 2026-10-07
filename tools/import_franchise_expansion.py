"""Reproduce official menu-only catalog and Salady standard-menu nutrition.

Default mode is offline and uses reviewed source CSVs. --capture-inputs reads the
public BON JSON responses and linked Salady PDF already downloaded to app/build.
No missing calories, macros, package mass or discontinued status are invented.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import re
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "data-source/franchise"
MENU_SOURCE = SOURCES / "official-menu-snapshot.csv"
NUTRITION_SOURCE = SOURCES / "salady-nutrition-2026-09.csv"
ADDITIONAL_MENU_SOURCE = SOURCES / "additional-menu-snapshot.csv"
LEGACY_NUTRITION_SOURCE = SOURCES / "sinjeon-nutrition-2018-11.csv"
QUALITY_MENU_SOURCE = SOURCES / "quality-menu-snapshot.csv"
QUALITY_NUTRITION_SOURCE = SOURCES / "quality-nutrition.csv"
DELIVERY_MENU_SOURCE = SOURCES / "delivery-menu-snapshot.csv"
DELIVERY_NUTRITION_SOURCE = SOURCES / "delivery-nutrition.csv"
BON_CODES = ("BF101", "BF102", "BF104", "BF105", "BF107", "BF111", "BF113")
PDF_URL = "https://salady.com/pdf/nutrition.pdf?ver=260915"
DATE = "2026-10-02"
SGS_REPORT_HASHES = {
    1: "010947E65202E63565F6BDD0BE08B01E46C7A01CA156D614947879ED38BEADD5",
    2: "6907DF6B1B2A2D36310248B5E43F6C2C4F2F93EEF29FFB3F8022F7AD1986A857",
}
MENU_HEADERS = ("id", "brand", "name", "sourceFoodCode", "sourceUrl", "sourceDate", "verifiedAt", "saleState",
                "energyKcal", "servingAmount", "servingUnit")
NUTRITION_HEADERS = ("id", "brand", "name", "category", "referenceAmount", "unit", "energyKcal",
                     "carbohydrateGrams", "proteinGrams", "fatGrams", "sodiumMilligrams", "sourceUrl",
                     "sourceDate", "verifiedAt", "saleState", "sourcePage")


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def write_csv(path, headers, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=headers, extrasaction="ignore", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def capture_inputs():
    menus = []
    for code in BON_CODES:
        payload = json.loads((ROOT / f"app/build/bon-api-{code.lower()}.json").read_text(encoding="utf-8-sig"))
        if payload.get("status") != "success":
            raise ValueError(f"Official BON response failed: {code}")
        brand = payload["data"]["brand"]
        if brand["brdCd"] != code:
            raise ValueError("Official brand identity mismatch")
        for menu in payload["data"]["menuList"]:
            if menu["brdCd"] != code or not menu["cmdtNm"].strip():
                raise ValueError("Official menu identity mismatch")
            menus.append(dict(id=f"official-bon-{code.lower()}-{menu['cmdtIdx']}", brand=brand["brdNm"],
                name=menu["cmdtNm"].strip(), sourceFoodCode=f"BON-{code}-{menu['cmdtIdx']}",
                sourceUrl=f"https://www.bonif.co.kr/brand/menu/detail?brdCd={code}&cmdtIdx={menu['cmdtIdx']}",
                sourceDate=payload["date"].split(" ")[0], verifiedAt=DATE, saleState="CURRENT_MENU_LISTED",
                energyKcal="", servingAmount="", servingUnit=""))
    write_csv(MENU_SOURCE, MENU_HEADERS, menus)

    import pdfplumber
    pdf_path = ROOT / "app/build/salady-nutrition-current.pdf"
    nutrition = []
    categories = {"SALADY": "샐러드", "GRAIN BOWL": "곡물볼", "NOODLE BOWL": "누들볼",
                  "PROTEIN BOX": "프로틴박스", "WRAP": "랩", "SANDWICH": "샌드위치", "SIDE": "사이드"}
    with pdfplumber.open(pdf_path) as document:
        for page_number in (0, 1, 3, 4):
            page = document.pages[page_number]
            if "2026.09" not in page.extract_text():
                raise ValueError("Unexpected nutrition PDF revision")
            brand = "샐러디" if page_number < 3 else "샐러디&샌드위치"
            namespace = "salady" if page_number < 3 else "salady-sandwich"
            category = ""
            for table in page.extract_tables():
                for row in table[1:]:
                    if len(row) != 10:
                        raise ValueError("Unexpected nutrition table width")
                    if row[0]:
                        category = row[0].replace("\n", " ").strip()
                    if category not in categories or not row[2] or row[1].startswith("(베이스)"):
                        continue
                    name = row[1].replace("\n", " ").strip()
                    values = [str(value).strip() for value in row[2:]]
                    # A long label on PDF p.5 crosses the table border; only move
                    # literal Hangul glyphs back to the menu name, retaining 300g.
                    overflow = re.fullmatch(r"([가-힣]+)\s+([0-9]+(?:\.[0-9]+)?)", values[0])
                    if overflow:
                        name += overflow.group(1)
                        values[0] = overflow.group(2)
                    if not all(re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", value) for value in values):
                        raise ValueError(f"Invalid official nutrition values: {row}")
                    identity = hashlib.sha256(re.sub(r"\s+", "", name).encode()).hexdigest()[:12]
                    nutrition.append(dict(id=f"official-{namespace}-{identity}", brand=brand, name=name,
                        category=categories[category], referenceAmount=values[0], unit="g", energyKcal=values[1],
                        carbohydrateGrams=values[2], proteinGrams=values[4], fatGrams=values[5],
                        sodiumMilligrams=values[7], sourceUrl=PDF_URL, sourceDate="2026-09", verifiedAt=DATE,
                        saleState="CURRENT_OFFICIAL_NUTRITION_SNAPSHOT", sourcePage=page_number + 1))
    write_csv(NUTRITION_SOURCE, NUTRITION_HEADERS, nutrition)
    (SOURCES / "expansion-sources.json").write_text(json.dumps({
        "verifiedAt": DATE,
        "bon": {"officialMenuApi": "https://api.bonif.co.kr/brand/v1/menu?brdCd={brandCode}",
                "officialDetailApi": "https://api.bonif.co.kr/brand/v1/menu/detail?brdCd={brandCode}&cmdtIdx={menuId}",
                "brandCodes": list(BON_CODES), "nutritionProvided": False,
                "detailReviewed": ["BF101:7", "BF101:11"],
                "policy": "Only published menu identities; prices, health marketing and guessed portions are omitted."},
        "salady": {"officialNutritionPage": "https://salady.com/menu/content2", "sourceUrl": PDF_URL,
                   "sourceMonth": "2026-09", "sha256": hashlib.sha256(pdf_path.read_bytes()).hexdigest().upper(),
                   "includedPages": [1, 2, 4, 5], "includedCategories": list(categories),
                   "policy": "Copy original standard-menu g basis, kcal, total macros and sodium. Exclude base replacements, dressings, drinks and separate ingredient toppings; never treat saturated fat as total fat."}
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def text_content(value):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", value))).strip()


def capture_additional_inputs():
    """Review current visible official lists; commented retired menu markup is excluded."""
    rows, provenance = [], []
    for number, expected in SGS_REPORT_HASHES.items():
        actual = hashlib.sha256((ROOT / f"app/build/sinjeon-report-{number}.jpg").read_bytes()).hexdigest().upper()
        if actual != expected:
            raise ValueError("Official SGS report changed; review the image and original numbers before importing")
    groups = [
        ("김가네", "gimgane", [("menu", "https://www.gimgane.co.kr/board/index.php?board=menu_01&sca=2")]
         + [(str(i), f"https://www.gimgane.co.kr/board/index.php?board=menu_01&sca={i}") for i in (3, 4, 6)]),
        ("고봉민김밥인", "kobongmin", [("menu", "https://kobongmin.com/renewal/03_menu/02.php")]
         + [(str(i), f"https://kobongmin.com/renewal/03_menu/{i:02}.php") for i in (3, 4, 5, 6, 7)]),
        ("신전떡볶이", "sinjeon", [("nutrition", "https://www.sinjeon.co.kr/doc/menu03.php")]
         + [(str(i), f"https://www.sinjeon.co.kr/doc/menu0{i}.php") for i in (4, 5, 6)]),
        ("두찜", "twozzim", [("detail", "https://www.twozzim.com/bbs/content.php?co_id=menu")]),
    ]
    for brand, namespace, pages in groups:
        names = set()
        for suffix, url in pages:
            path = ROOT / f"app/build/{namespace}-{suffix}.html"
            original = path.read_text(encoding="utf-8-sig")
            markup = re.sub(r"<!--.*?-->", "", original, flags=re.S)
            if namespace == "gimgane":
                found = [(text_content(name), url) for name in re.findall(r'<p class="menu_name">(.*?)</p>', markup, re.S)]
            elif namespace == "kobongmin":
                found = [(text_content(name), url) for name in re.findall(r'<span class="name">(.*?)</span>', markup, re.S)]
            elif namespace == "sinjeon":
                # Menu cards, not navigation/category labels. kcal text elsewhere
                # on these pages has no declared basis and is not transcribed.
                found = [(text_content(name), url) for name in re.findall(r'<p>\s*<span>(.*?)</span>', markup, re.S)]
            else:
                found = [(text_content(name), html.unescape(link)) for link, name in re.findall(
                    r'<a href="([^"]*bo_table=menu[^\"]*)" class="d_sLightBox2 iframe2">(.*?)</a>', markup, re.S)]
            page_count = 0
            for name, source_url in found:
                normalized = re.sub(r"\s+", "", name)
                if not normalized or normalized in names:
                    continue
                names.add(normalized)
                identity = hashlib.sha256(normalized.encode()).hexdigest()[:12]
                rows.append(dict(id=f"official-menu-{namespace}-{identity}", brand=brand, name=name,
                    sourceFoodCode=f"{namespace.upper()}-{identity.upper()}", sourceUrl=source_url,
                    sourceDate="", verifiedAt="2026-10-04", saleState="CURRENT_MENU_LISTED",
                    energyKcal="", servingAmount="", servingUnit=""))
                page_count += 1
            provenance.append(dict(brand=brand, sourceUrl=url, verifiedAt="2026-10-04", sourceDate="",
                sha256=hashlib.sha256(path.read_bytes()).hexdigest().upper(), uniqueAdded=page_count))
        if not names:
            raise ValueError(f"No menu cards parsed from official pages: {brand}")
    write_csv(ADDITIONAL_MENU_SOURCE, MENU_HEADERS, rows)
    (SOURCES / "additional-menu-sources.json").write_text(json.dumps({
        "verifiedAt": "2026-10-04", "pages": provenance,
        "policy": "Visible official menu cards only; remove HTML comments, deduplicate normalized brand/name. Menu identity is not proof of kcal or household serving mass. Current listing does not guarantee store stock.",
        "sinjeon": "Menu pages show kcal without a basis. Only separately dated SGS official reports with explicit kcal/100g provide recordable nutrition; menu-only rows retain missing nutrition.",
        "sinjeonReports": {
            "officialListUrl": "https://www.sinjeon.co.kr/doc/info07.php",
            "sourceDate": "2018-11-16", "verifiedAt": "2026-10-04",
            "saleState": "LEGACY_OFFICIAL_NUTRITION", "basis": "100g",
            "currentListedCaloriesMatchHistoricReports": True, "currentRecipeUnchangedVerified": False,
            "reports": [{"sourceUrl": f"https://www.sinjeon.co.kr/img/sub/info07_img{number}.jpg", "sha256": expected}
                        for number, expected in SGS_REPORT_HASHES.items()]
        }
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # Visually verified original official SGS images linked by info07.php.
    # These are historic results, not a claim that the 2026 recipe is unchanged.
    nutrition = []
    for number, name, kcal, carbohydrate, protein, fat, sodium in (
        (1, "떡볶이(순한맛)", "139", "29.9", "3.77", "0.461", "482"),
        (2, "떡볶이(매운맛)", "137", "29.2", "3.74", "0.609", "515"),
    ):
        nutrition.append(dict(id=f"official-sinjeon-sgs-20181116-{number}", brand="신전떡볶이", name=name,
            category="떡볶이", referenceAmount="100", unit="g", energyKcal=kcal,
            carbohydrateGrams=carbohydrate, proteinGrams=protein, fatGrams=fat,
            sodiumMilligrams=sodium, sourceUrl=f"https://www.sinjeon.co.kr/img/sub/info07_img{number}.jpg",
            sourceDate="2018-11-16", verifiedAt="2026-10-04", saleState="LEGACY_OFFICIAL_NUTRITION",
            sourcePage="1"))
    write_csv(LEGACY_NUTRITION_SOURCE, NUTRITION_HEADERS, nutrition)


def generate_menu_kotlin(rows):
    if len({row["id"] for row in rows}) != len(rows):
        raise ValueError("Duplicate official menu ID")
    if any(row["energyKcal"] or row["servingAmount"] or row["servingUnit"] for row in rows):
        raise ValueError("Menu-only source must not fabricate nutrition or serving")
    chunks = [rows[index:index + 200] for index in range(0, len(rows), 200)]
    lines = ["package com.example.healthcare.domain", "", "// Generated by tools/import_franchise_expansion.py from reviewed official menu identities.",
             "internal object OfficialFranchiseMenus {", "    val entries: List<FranchiseMenu> = buildList {"]
    for index in range(len(chunks)):
        lines.append(f"        addAll(chunk{index}())")
    lines += ["    }", ""]
    for index, chunk in enumerate(chunks):
        lines.append(f"    private fun chunk{index}(): List<FranchiseMenu> = listOf(")
        for row in chunk:
            args = ", ".join(json.dumps(row[key], ensure_ascii=False) for key in
                             ("id", "brand", "name", "sourceUrl", "verifiedAt", "sourceDate", "saleState"))
            category = row.get("menuCategory") or row.get("mappedCategory") or ""
            lines.append(f"        FranchiseMenu({args}, category = {json.dumps(category, ensure_ascii=False)}),")
        lines += ["    )", ""]
    lines += ["}", ""]
    (ROOT / "app/src/main/java/com/example/healthcare/domain/OfficialFranchiseMenus.kt").write_text(
        "\n".join(lines), encoding="utf-8")


def generate_nutrition(rows):
    path = ROOT / "app/src/main/assets/fooddata/franchise_official_items.csv"
    existing = read_csv(path)
    headers = tuple(existing[0].keys())
    managed_ids = {row["id"] for row in rows}
    preserved = [row for row in existing if row["id"] not in managed_ids]
    for row in rows:
        name = row["name"]
        normalized = re.sub(r"[^0-9a-z가-힣]", "", name.lower())
        identity = row["id"].removeprefix("official-").upper()
        timestamp = int(datetime.fromisoformat(row["verifiedAt"] + "T00:00:00+09:00").timestamp() * 1000)
        description = (f"공식 영양 기준량 {row['referenceAmount']}{row['unit']} · 공식 메뉴 영양표 · 개정일 {row['sourceDate'] or '미제공'} · {row['sourceUrl']}"
                       if row['id'].startswith(('official-quality-', 'official-delivery-')) else
                       f"공식 제공량 1인분 {row['referenceAmount']}g · 표준 메뉴 구성 · 영양성분표 {row['sourceDate']} p.{row['sourcePage']} · {row['sourceUrl']}"
                       if row["saleState"] != "LEGACY_OFFICIAL_NUTRITION" else
                       f"공식 시험성적서 {row['sourceDate']} · 100g당 영양 · 현재 조리법 동일 여부 미확인 · {row['sourceUrl']}")
        preserved.append(dict(row, sourceType="OFFICIAL-BRAND-NUTRITION", sourceFoodCode=identity,
            normalizedName=normalized, aliases=f"|{re.sub(r'[^0-9a-z가-힣]', '', row['brand'].lower())}|",
            servingDescription=description,
            barcode="", dataVersion=row["verifiedAt"], createdAt=timestamp, updatedAt=timestamp))
    if len({row["id"] for row in preserved}) != len(preserved):
        raise ValueError("Duplicate official nutrition ID")
    write_csv(path, headers, preserved)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture-inputs", action="store_true")
    parser.add_argument("--capture-additional-inputs", action="store_true")
    args = parser.parse_args()
    if args.capture_inputs:
        capture_inputs()
    if args.capture_additional_inputs:
        capture_additional_inputs()
    menus, nutrition = read_csv(MENU_SOURCE), read_csv(NUTRITION_SOURCE)
    if ADDITIONAL_MENU_SOURCE.exists():
        menus += read_csv(ADDITIONAL_MENU_SOURCE)
    if LEGACY_NUTRITION_SOURCE.exists():
        nutrition += read_csv(LEGACY_NUTRITION_SOURCE)
    if QUALITY_MENU_SOURCE.exists():
        menus += read_csv(QUALITY_MENU_SOURCE)
    if QUALITY_NUTRITION_SOURCE.exists():
        nutrition += read_csv(QUALITY_NUTRITION_SOURCE)
    if DELIVERY_MENU_SOURCE.exists():
        menus += read_csv(DELIVERY_MENU_SOURCE)
    completion_menu_source = ROOT / 'data-source/recipe-menu-completion/nolboo-public-menu-snapshot.csv'
    if completion_menu_source.exists():
        menus += read_csv(completion_menu_source)
    if DELIVERY_NUTRITION_SOURCE.exists():
        nutrition += read_csv(DELIVERY_NUTRITION_SOURCE)
    generate_menu_kotlin(menus)
    generate_nutrition(nutrition)
    print(f"Generated {len(menus)} menu-only references and {len(nutrition)} official nutrition menus.")
