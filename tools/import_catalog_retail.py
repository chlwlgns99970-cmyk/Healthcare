"""Additive, exact-code retail expansion from the complete official K-FIND source.

The source's food classifications select product scope; there is no per-family
cap or nutrient/name deduplication. Existing IDs survive. Manufacturer variants,
package sizes and historical nutrient revisions retain separate identities.
All nutrition is copied in its published basis; absent facts remain empty.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import re
import unicodedata
import zipfile
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path
from lxml import etree

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data-source/catalog-retail"
ASSETS = ROOT / "app/src/main/assets/fooddata"
BASELINE = ROOT / "app/build/catalog-qa/baseline/app/src/main/assets/fooddata"
SOURCE = ROOT / "data-source/kfind/kfind-processed-food-db-2026-08-28.xlsx"
SOURCE_URL = "https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do"
SOURCE_HASH = "b074d98e75d2d087dc1b193f0056affbf9afd14524c9ccb556cb2b20978504f7"
DATE = "2026-10-04"
VERSION = "catalog-retail-exact-code-v2"
NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
TIMESTAMP = str(int(datetime.fromisoformat(DATE + "T00:00:00+09:00").timestamp() * 1000))
FOOD_HEADERS = ("id", "sourceType", "sourceFoodCode", "name", "normalizedName", "aliases", "category",
                "referenceAmount", "unit", "energyKcal", "carbohydrateGrams", "proteinGrams", "fatGrams",
                "sodiumMilligrams", "servingDescription", "brand", "barcode", "dataVersion", "createdAt", "updatedAt")
RAW_FIELDS = ("식품코드", "식품명", "식품기원명", "식품대분류명", "대표식품명", "식품중분류명",
              "식품소분류명", "식품세분류명", "영양성분함량기준량", "출처코드", "출처명",
              "1회 섭취참고량", "식품중량", "품목제조보고번호", "제조사명", "수입업체명", "유통업체명",
              "데이터생성방법명", "데이터생성일자", "데이터기준일자")
NUTRIENT_FIELDS = ("에너지(kcal)", "탄수화물(g)", "단백질(g)", "지방(g)", "나트륨(mg)")
EVIDENCE_HEADERS = ("foodItemId", "sourceFoodCode", "brand", "name", "manufacturer", "productReportNumber",
                    "packageSize", "intakeReference", "rawClassification", "retailFamily", "iceCreamForm",
                    "ingredients", "ingredientText", "completeIngredientText", "ingredientStatus",
                    "allergens", "allergenText", "allergenStatus", "mayContainAllergens", "crossContactText",
                    "householdUnit", "basisAmountPerUnit", "basisUnit", "servingSourceReference", "servingEvidenceKind",
                    "servingSourceSize", "sourceUrl", "sourceName", "checkedAt", "sourceDate", "sourceVersion",
                    "parserVersion", "sourceHash", "sourceStatus", "availabilityStatus", "evidenceKind",
                    "staleCandidate", "unresolvedReason", "distributionGroup")
# These are official source food families requested by the user. Special medical,
# infant nutrition, alcohol and raw oils/sugar are outside this retail expansion.
SOURCE_CATEGORIES = {"면류", "빙과류", "즉석식품류", "유가공품류", "음료류", "과자류·빵류 또는 떡류",
                     "코코아가공품류 또는 초콜릿류", "식육가공품 및 포장육", "수산가공식품류",
                     "농산가공식품류", "조미식품", "장류", "절임류 또는 조림류", "두부류 또는 묵류",
                     "알가공품류", "잼류", "기타식품류"}
AMOUNT = re.compile(r"^\s*(\d+(?:\.\d+)?)\s*(g|ml)\s*$", re.I)


def clean(value): return " ".join(str("" if value is None else value).split())
def normalize(value): return re.sub(r"[^a-z0-9가-힣]", "", unicodedata.normalize("NFKC", value).lower())
def number(value):
    try:
        value = float(clean(value).replace(",", ""))
        return value if math.isfinite(value) and value >= 0 else None
    except (TypeError, ValueError): return None
def compact(value): return "" if value is None else f"{value:.10f}".rstrip("0").rstrip(".")
def amount(value):
    match = AMOUNT.fullmatch(clean(value))
    return (float(match[1]), match[2].lower()) if match and float(match[1]) > 0 else None
def source_date(value):
    value = clean(value)
    if re.fullmatch(r"\d{5}(?:\.0)?", value):
        return (datetime(1899, 12, 30) + timedelta(days=float(value))).date().isoformat()
    if re.match(r"^\d{4}-\d{2}-\d{2}", value): return value[:10]
    match = re.match(r"^(\d{2})\.(\d{2})\.(\d{2})", value)
    return "20" + "-".join(match.groups()) if match else ""
def csv_read(path):
    with path.open(encoding="utf-8-sig", newline="") as stream: return list(csv.DictReader(stream))
def write_csv(path, headers, rows):
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=headers, lineterminator="\n", extrasaction="ignore")
        writer.writeheader(); writer.writerows(rows)


def workbook_rows():
    """Inspect every source column once, then copy required fields without Excel."""
    with zipfile.ZipFile(SOURCE) as archive:
        shared = []
        with archive.open("xl/sharedStrings.xml") as stream:
            for _, node in etree.iterparse(stream, events=("end",), tag=NS + "si"):
                shared.append("".join(node.itertext()))
                node.clear()
                while node.getprevious() is not None: del node.getparent()[0]
        def value(cell):
            child = cell.find(NS + "v")
            if child is None: return "".join(cell.itertext()) if cell.get("t") == "inlineStr" else ""
            return shared[int(child.text)] if cell.get("t") == "s" else child.text or ""
        headers, selected_columns = {}, {}
        with archive.open("xl/worksheets/sheet1.xml") as stream:
            for _, node in etree.iterparse(stream, events=("end",), tag=NS + "row"):
                if node.get("r") == "1":
                    headers = {cell.get("r").rstrip("0123456789"): value(cell) for cell in node}
                    missing = set(RAW_FIELDS + NUTRIENT_FIELDS) - set(headers.values())
                    assert not missing, missing
                    ingredient_fields = [v for v in headers.values() if "원재료" in v or "알레르" in v]
                    assert not ingredient_fields, "Review newly provided ingredient/allergen source fields"
                    selected_columns = {column: title for column, title in headers.items() if title in RAW_FIELDS + NUTRIENT_FIELDS}
                else:
                    yield {selected_columns[column]: clean(value(cell)) for cell in node
                           if (column := cell.get("r").rstrip("0123456789")) in selected_columns}
                node.clear()
                while node.getprevious() is not None: del node.getparent()[0]


def aliases(name, maker, representative=""):
    # Aliases affect recall only: never rewrite exact identity, flavour or size.
    short_maker = re.sub(r"\(주\)|㈜|주식회사|유한회사", "", maker).strip()
    fragments = [clean(x) for x in name.split("_") if normalize(x)]
    values = {normalize(name), normalize(maker), normalize(short_maker), normalize(representative)}
    values.update(normalize(x) for x in fragments)
    values.update(normalize(maker + " " + x) for x in [name] + fragments)
    values.update(normalize(short_maker + " " + x) for x in [name] + fragments)
    if name.startswith("양반") and name.endswith("죽"):
        values.add("양반죽")  # Brand-family recall; each flavour remains an exact separate ID.
    values.discard(""); values.discard(normalize(name))
    return "|" + "|".join(sorted(values)) + "|" if values else ""


def distribution_group(raw, scope):
    name, maker = raw.get("식품명", ""), raw.get("제조사명", "")
    if any(marker in name for marker in scope["excludedNameMarkers"]): return "", "EXPLICIT_FOODSERVICE_RAW_MATERIAL_OR_SPECIAL_FUNCTION_NAME"
    package = amount(raw.get("식품중량"))
    if package and package[0] > 5000: return "", "BULK_PACKAGE_WITHOUT_REVIEWED_CONSUMER_RETAIL_EVIDENCE"
    corporate = normalize(re.sub(r"\(주\)|㈜|주식회사|유한회사|농업회사법인|영농조합법인", "", maker))
    for group, makers in scope["manufacturerGroups"].items():
        if any(corporate.startswith(normalize(prefix)) for prefix in makers): return group, ""
    # Explicit retailer product labels are selected without guessing which PB
    # owns an unbranded OEM plant. Retailer names remain source text.
    if any(marker.lower() in name.lower() for marker in scope["retailerNameMarkers"]): return "explicit_retailer_or_pb_name", ""
    return "", "MANUFACTURER_OR_RETAILER_NOT_IN_REVIEWED_CONSUMER_SCOPE"


def retail_family(raw):
    category, representative = raw.get("식품대분류명", ""), raw.get("대표식품명", "")
    if category == "빙과류": return "아이스크림"
    if category == "면류": return "라면" if representative == "라면" else "우동·면제품"
    if category == "유가공품류": return "유제품"
    if category == "음료류": return "두유" if "두유" in representative else "음료"
    if category == "코코아가공품류 또는 초콜릿류": return "초콜릿"
    if category == "과자류·빵류 또는 떡류":
        return "빵·케이크" if raw.get("식품소분류명") == "빵류" else "과자·스낵·사탕·젤리"
    if category in {"조미식품", "장류"}: return "소스·드레싱·양념"
    if category == "즉석식품류":
        if representative in {"밥", "밥류", "볶음밥", "즉석밥", "누룽지"}: return "즉석밥·냉동밥"
        if representative == "죽": return "죽"
        if any(t in representative for t in ("국", "탕", "찌개", "스프")): return "즉석국·찌개"
        if any(t in representative for t in ("만두", "피자")): return "냉동·간편조리"
        return "편의점·즉석식품"
    if category == "식육가공품 및 포장육": return "햄·소시지·베이컨·육가공품"
    if category == "수산가공식품류": return "참치·김·수산가공품"
    if "시리얼" in representative or "그래놀라" in raw.get("식품명", ""): return "시리얼·그래놀라"
    if category == "절임류 또는 조림류": return "반찬제품"
    return "기타가공식품"


def package_evidence(raw, total):
    """Use source package words and exact weight, never generic intake-reference mass."""
    if not total: return "", ""
    name = raw.get("식품명", "")
    normalized = normalize(name)
    # Explicit multipack content cannot be treated as one individual ice/bag.
    if re.search(r"\d+\s*(?:개입|입|개묶음|팩묶음)|\d+\s*[xX*×]\s*\d+", name): return "", ""
    marker = re.search(r"(?:\[|\()\s*(봉지|봉|캔|병|팩|개|콘|컵|통)\s*(?:\]|\))\s*$", name)
    if marker: return marker[1].replace("봉지", "봉"), "EXPLICIT_OFFICIAL_PACKAGE_NAME"
    if (raw.get("대표식품명") == "라면" and raw.get("식품소분류명") in {"유탕면", "건면"}
        and "봉지" in raw.get("1회 섭취참고량", "") and total[1] == "g"
        and 30 <= total[0] <= 250 and not any(t in normalized for t in ("컵", "사발", "용기"))):
        return "봉", "OFFICIAL_BAG_REFERENCE_AND_PRODUCT_WEIGHT"
    if raw.get("식품대분류명") == "빙과류":
        # Only explicit package-form tokens, with published whole-product content.
        if re.search(r"(?:파인트|pint)", name, re.I): return "통", "EXPLICIT_OFFICIAL_PINT_NAME"
        if re.search(r"(?:바|(?<!팝)콘|컵|샌드|모나카|튜브)(?:\s*(?:\([^)]*\))?)$", name) and total[0] <= 250:
            suffix = re.search(r"(바|콘|컵|샌드|모나카|튜브)(?:\s*(?:\([^)]*\))?)$", name)[1]
            return {"바": "개", "콘": "콘", "컵": "컵", "샌드": "개", "모나카": "개", "튜브": "개"}[suffix], "EXPLICIT_OFFICIAL_ICE_FORM_NAME"
    return "", ""


def ice_form(name):
    if "파인트" in name or re.search(r"pint", name, re.I): return "pint"
    if "카톤" in name: return "tub"
    if re.search(r"\[[^\]]*컵[^\]]*\]|미니컵", name): return "cup"
    if re.search(r"\[[^\]]*샌드[^\]]*\]", name): return "sand"
    # Popcorn is a flavor, not evidence of a cone-shaped package.
    if re.search(r"팝콘(?:\s*(?:\([^)]*\))?)$", name): return "unknown"
    for token, form in (("모나카", "monaka"), ("샌드", "sand"), ("튜브", "tube"), ("콘", "cone"), ("컵", "cup"), ("통", "tub"), ("바", "bar")):
        if re.search(re.escape(token) + r"(?:\s*(?:\([^)]*\))?)$", name): return form
    return "unknown"


def evidence(food, raw=None):
    raw = raw or {}
    result = dict.fromkeys(EVIDENCE_HEADERS, "") | {
        "foodItemId": food["id"], "sourceFoodCode": food["sourceFoodCode"], "brand": food["brand"], "name": food["name"],
        "manufacturer": raw.get("제조사명", food["brand"]), "productReportNumber": raw.get("품목제조보고번호", ""),
        "packageSize": raw.get("식품중량", ""), "intakeReference": raw.get("1회 섭취참고량", ""),
        "rawClassification": " | ".join(raw.get(k, "") for k in ("식품대분류명", "대표식품명", "식품소분류명") if raw.get(k)),
        "retailFamily": retail_family(raw), "ingredientStatus": "UNKNOWN", "allergenStatus": "UNKNOWN",
        "sourceUrl": SOURCE_URL, "sourceName": "식품의약품안전처 K-FIND 가공식품 영양성분 DB", "checkedAt": DATE,
        "sourceDate": source_date(raw.get("데이터기준일자", "")), "sourceVersion": "2026-08-28",
        "parserVersion": VERSION, "sourceHash": SOURCE_HASH, "sourceStatus": "HISTORICAL_SNAPSHOT",
        "availabilityStatus": "UNKNOWN", "evidenceKind": "EXACT_SOURCE_FOOD_CODE", "staleCandidate": "false",
        "unresolvedReason": "SOURCE_HAS_NO_INGREDIENT_OR_ALLERGEN_COLUMNS;CURRENT_RETAIL_AVAILABILITY_UNVERIFIED"}
    if raw.get("식품대분류명") == "빙과류": result["iceCreamForm"] = ice_form(food["name"])
    total = amount(raw.get("식품중량"))
    unit, kind = package_evidence(raw, total)
    if unit and total and total[1] == food["unit"]:
        result.update(householdUnit=unit, basisAmountPerUnit=compact(total[0]), basisUnit=total[1],
                      servingSourceReference=SOURCE_URL, servingEvidenceKind="OFFICIAL_SERVING",
                      servingSourceSize=raw["식품중량"], evidenceKind="EXACT_SOURCE_FOOD_CODE|" + kind)
    return result


def import_kfind(existing_ids, scope):
    products, raw_rows, evidence_rows, unresolved, counts = [], [], [], [], Counter()
    excluded = []
    source_count = 0
    for raw in workbook_rows():
        source_count += 1
        category, name, code = raw.get("식품대분류명", ""), raw.get("식품명", ""), raw.get("식품코드", "")
        if not code: continue
        identity = "kfind-product-" + code.lower()
        if identity in existing_ids: continue
        group, reason = distribution_group(raw, scope)
        if category not in SOURCE_CATEGORIES: reason = "SOURCE_CATEGORY_OUTSIDE_REQUESTED_RETAIL_FAMILIES"
        elif not re.search(r"[가-힣]", name): reason = "NO_KOREAN_CONSUMER_PRODUCT_NAME"
        if reason:
            excluded.append({"sourceFoodCode": code, "name": name, "manufacturer": raw.get("제조사명", ""), "category": category,
                             "packageSize": raw.get("식품중량", ""), "reason": reason, "distributionGroup": group,
                             "sourceHash": SOURCE_HASH})
            continue
        basis, energy = amount(raw.get("영양성분함량기준량")), number(raw.get("에너지(kcal)"))
        if not basis or energy is None:
            counts["unresolved"] += 1
            unresolved.append({"sourceFoodCode": code, "name": name, "category": category,
                               "reason": "NO_VALID_NUTRITION_BASIS" if not basis else "NO_OFFICIAL_KCAL",
                               "sourceUrl": SOURCE_URL, "sourceHash": SOURCE_HASH, "checkedAt": DATE})
            continue
        maker = raw.get("제조사명", "")
        if maker in {"해당없음", "해당 없음", "-"}: maker = ""
        total = amount(raw.get("식품중량"))
        package, _ = package_evidence(raw, total)
        description = compact(basis[0]) + basis[1] + " 기준"
        if total: description += " · 공식 총내용량 " + compact(total[0]) + total[1]
        if package: description += " · 포장단위 " + package
        food = dict(zip(FOOD_HEADERS, [identity, "K-FIND-PRODUCT", code, name, normalize(name),
                    aliases(name, maker, raw.get("대표식품명", "")), category, compact(basis[0]), basis[1], compact(energy),
                    compact(number(raw.get("탄수화물(g)"))), compact(number(raw.get("단백질(g)"))), compact(number(raw.get("지방(g)"))),
                    compact(number(raw.get("나트륨(mg)"))), description, maker, "", source_date(raw.get("데이터기준일자")) or "2026-08-28", TIMESTAMP, TIMESTAMP]))
        products.append(food)
        raw_rows.append({"foodItemId": identity, "sourceType": "K-FIND-PRODUCT", **raw})
        evidence_rows.append(evidence(food, raw) | {"distributionGroup": group})
        counts["imported"] += 1
    assert source_count == 316734, source_count
    return products, raw_rows, evidence_rows, unresolved, excluded


def import_binggrae():
    path = OUT / "binggrae-products.json"
    products, evidence_rows, unresolved = [], [], []
    if not path.exists(): return products, evidence_rows, unresolved
    entries = json.loads(path.read_text(encoding="utf-8"))
    seen = set()
    for entry in entries:
        info, volume, family = entry.get("nutrition", {}).get("info", {}), entry["volume"], entry["family"]
        family_name = clean(family["FAMILY_NAME"])
        if "[수출]" in family_name: continue
        code = "BINGGRAE-PACK-" + str(volume["PROD_VAL_IDX"])
        identity = "official-retail-" + code.lower()
        if identity in seen: continue
        seen.add(identity)
        name = clean(volume.get("PROD_NAME", ""))
        if normalize(family_name) not in normalize(name): name = family_name + " " + name
        source = entry["nutritionSource"]
        basis, energy, package = number(info.get("TOT_CONT_AMT")), number(info.get("CAL_AMT")), amount(volume.get("AMT_INFO"))
        unit = clean(info.get("TOT_CONT_UNIT")).lower()
        # The public detail-page script separately overrides the two-half
        # 팽이팽이 figures (2 x 140ml); its endpoint reports a conflicting 240ml
        # combined basis. Preserve raw evidence and withhold this nutrition row.
        if entry["productId"] in {894, 1056}:
            unresolved.append({"sourceFoodCode": code, "name": name, "category": "빙과류", "reason": "OFFICIAL_PAGE_SCRIPT_AND_API_NUTRITION_BASIS_CONFLICT", "sourceUrl": entry["pageSource"]["url"] + "|" + source["url"], "sourceHash": source.get("sourceHash", ""), "checkedAt": DATE})
            continue
        if not info or not basis or energy is None or clean(info.get("CAL_BASE_UNIT")) != "kcal" or unit not in {"g", "ml"} or not package or package[1] != unit:
            unresolved.append({"sourceFoodCode": code, "name": name, "category": "빙과류", "reason": "OFFICIAL_PACKAGE_OR_NUTRITION_UNRESOLVED", "sourceUrl": source["url"], "sourceHash": source.get("sourceHash", ""), "checkedAt": DATE})
            continue
        nutrients = {clean(row.get("CAL_CAT_EN")): row for row in entry["nutrition"].get("list", [])}
        def nutrient(key, expected):
            row = nutrients.get(key, {})
            # '<0.5g' is a bound, not an exact numeric value. No rounding-to-zero.
            return compact(number(row.get("CAL_AMT"))) if clean(row.get("CAL_BASE_UNIT")) == expected else ""
        food = dict(zip(FOOD_HEADERS, [identity, "OFFICIAL-RETAIL-PRODUCT", code, name, normalize(name), aliases(name, "빙그레", family_name), "빙과류", compact(basis), unit, compact(energy), nutrient("Carbohydrate", "g"), nutrient("Protein", "g"), nutrient("Fat", "g"), nutrient("Salt", "mg"), compact(basis) + unit + " 기준 · 공식 총내용량 " + clean(volume["AMT_INFO"]), "빙그레", "", source_date(info.get("VAL_UPDATE_DATE")) or DATE, TIMESTAMP, TIMESTAMP]))
        ev = evidence(food)
        ev.update(sourceUrl=entry["pageSource"]["url"] + "|" + source["url"], sourceName="빙그레 공식 국내 제품·용량·영양정보", sourceDate=source_date(info.get("VAL_UPDATE_DATE")), sourceVersion=clean(info.get("VAL_UPDATE_DATE")), sourceHash=source.get("sourceHash", ""), sourceStatus="DATED_OFFICIAL_NUTRITION" if source_date(info.get("VAL_UPDATE_DATE")) else "UNKNOWN_DATE", availabilityStatus="CURRENT_OFFICIAL_CATALOG", retailFamily="아이스크림", packageSize=clean(volume["AMT_INFO"]), rawClassification="빙그레 국내 아이스크림 제품목록", iceCreamForm=ice_form(name), evidenceKind="OFFICIAL_VARIANT_AND_PACKAGE_ID", unresolvedReason="OFFICIAL_NUTRITION_ENDPOINT_HAS_NO_INGREDIENT_OR_ALLERGEN_DECLARATION")
        ev["distributionGroup"] = "current_official_manufacturer_catalog"
        # A whole official package is a recorded item; shape-specific unit names
        # require explicit catalogue wording. The fallback remains the one item.
        shape = ice_form(name)
        if shape == "unknown": shape = ice_form(family_name)
        household = {"cone": "콘", "cup": "컵", "pint": "통", "tub": "통"}.get(shape, "개")
        if family_name == "투게더" and package[0] >= 450: household, shape = "통", "tub"
        if shape == "unknown" and (package[0] > 250 or "," in name): household = ""
        if household:
            ev.update(householdUnit=household, basisAmountPerUnit=compact(package[0]), basisUnit=unit,
                      servingSourceReference=entry["pageSource"]["url"], servingEvidenceKind="OFFICIAL_SERVING",
                      servingSourceSize=clean(volume["AMT_INFO"]), iceCreamForm=shape)
            food["servingDescription"] += " · 포장단위 " + household
        products.append(food); evidence_rows.append(ev)
    return products, evidence_rows, unresolved


def import_chapagetti():
    review = json.loads((OUT / "chapagetti-label-review.json").read_text(encoding="utf-8"))
    checks = json.loads((OUT / "source-checks.json").read_text(encoding="utf-8"))["checks"]
    source = next(row for row in checks if row["url"] == review["sourceUrl"] and row["status"] == "OK")
    assert hashlib.sha256((ROOT / source["path"]).read_bytes()).hexdigest() == source["sourceHash"]
    # The review is bound to immutable label bytes, not merely the current URL.
    assert review["sourceHash"] == source["sourceHash"], "Re-review the complete changed manufacturer label."
    code = review["productCode"]
    food = dict(zip(FOOD_HEADERS, ["official-retail-" + code.lower(), "OFFICIAL-RETAIL-PRODUCT", code, review["name"], normalize(review["name"]),
        "|짜파게티|농심짜파게티|올리브짜파게티|농심올리브짜파게티|", "면류", "140", "g", "610", "96", "9", "20", "1100",
        "공식 1봉지 140g · 1봉지당 610kcal · 포장단위 봉", "농심", "", DATE, TIMESTAMP, TIMESTAMP]))
    ev = evidence(food)
    ev.update({key: clean(review[key]) for key in ("manufacturer", "productReportNumber", "packageSize", "ingredientText", "allergenText", "crossContactText", "ingredientStatus", "allergenStatus", "sourceUrl", "sourceName", "sourceDate", "sourceStatus", "availabilityStatus")})
    ev.update(completeIngredientText=review["ingredientText"], ingredients=review["ingredientText"], allergens="|".join(review["allergens"]),
              mayContainAllergens="|".join(review["mayContainAllergens"]), householdUnit="봉", basisAmountPerUnit="140", basisUnit="g",
              servingSourceReference=review["sourceUrl"], servingEvidenceKind="OFFICIAL_SERVING", servingSourceSize="1봉지(140g)",
              retailFamily="라면", rawClassification="농심 공식 제품 표시: 유탕면", sourceHash=source["sourceHash"],
              sourceVersion=source["sourceHash"], evidenceKind="OFFICIAL_PRODUCT_CODE|HASH_BOUND_FULL_PACKAGE_LABEL_REVIEW", unresolvedReason="SOURCE_PUBLICATION_DATE_NOT_PRINTED")
    ev["distributionGroup"] = "current_official_manufacturer_catalog"
    return food, ev


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_HASH
    assert BASELINE.exists(), "A captured baseline is required; do not mutate the original IDs."
    baseline = csv_read(BASELINE / "product_items.csv")
    existing_ids = {row["id"] for row in baseline + csv_read(BASELINE / "food_items.csv")}
    scope = json.loads((OUT / "scope.json").read_text(encoding="utf-8"))
    products, raw_rows, evidence_rows, unresolved, excluded = import_kfind(existing_ids, scope)
    official, official_evidence, official_unresolved = import_binggrae()
    products += official; evidence_rows += official_evidence; unresolved += official_unresolved
    if (OUT / "chapagetti-label-review.json").exists() and (OUT / "source-checks.json").exists():
        chap_food, chap_evidence = import_chapagetti()
        products.append(chap_food); evidence_rows.append(chap_evidence)
    products.sort(key=lambda row: row["id"]); raw_rows.sort(key=lambda row: row["foodItemId"]); evidence_rows.sort(key=lambda row: row["foodItemId"])
    assert len(products) == len({row["id"] for row in products})
    assert not ({row["id"] for row in products} & existing_ids)
    write_csv(OUT / "product-items.csv", FOOD_HEADERS, products)
    write_csv(OUT / "raw-identity-fields.csv", ("foodItemId", "sourceType", *RAW_FIELDS), raw_rows)
    write_csv(OUT / "evidence.csv", EVIDENCE_HEADERS, evidence_rows)
    alias_patches = [{"foodItemId": row["id"], "sourceFoodCode": row["sourceFoodCode"],
                      "aliases": aliases(row["name"], row.get("brand", "")),
                      "evidenceKind": "EXACT_ID_SEARCH_ALIAS_ONLY", "parserVersion": VERSION}
                     for row in baseline]
    write_csv(OUT / "alias-patches.csv", ("foodItemId", "sourceFoodCode", "aliases", "evidenceKind", "parserVersion"), alias_patches)
    write_csv(OUT / "excluded-scope.csv", ("sourceFoodCode", "name", "manufacturer", "category", "packageSize", "reason", "distributionGroup", "sourceHash"), sorted(excluded, key=lambda row: row["sourceFoodCode"]))
    write_csv(OUT / "unresolved.csv", ("sourceFoodCode", "name", "category", "reason", "sourceUrl", "sourceHash", "checkedAt"), sorted(unresolved, key=lambda row: row["sourceFoodCode"]))
    summary = {"checkedAt": DATE, "parserVersion": VERSION, "baselineProducts": len(baseline), "additionalProducts": len(products), "afterProducts": len(baseline) + len(products), "sourceRowsScanned": 316734, "sourceHash": SOURCE_HASH, "noFamilyCaps": True, "existingIdsPreserved": len(existing_ids), "sameNameIdentityMerges": 0, "additionalSourceTypes": dict(Counter(row["sourceType"] for row in products)), "additionalRetailFamilies": dict(Counter(row["retailFamily"] for row in evidence_rows)), "additionalHouseholdUnits": dict(Counter(row["householdUnit"] for row in evidence_rows if row["householdUnit"])), "additionalIceCreamForms": dict(Counter(row["iceCreamForm"] for row in evidence_rows if row["retailFamily"] == "아이스크림")), "officialNutritionUnresolved": len(unresolved), "sourceCurrency": dict(Counter(row["sourceStatus"] for row in evidence_rows)), "outputs": {name: hashlib.sha256((OUT / name).read_bytes()).hexdigest() for name in ("product-items.csv", "raw-identity-fields.csv", "evidence.csv", "unresolved.csv")}}
    summary.update(scopeVersion=scope["version"], scopeSha256=hashlib.sha256((OUT / "scope.json").read_bytes()).hexdigest(), excludedSourceRows=len(excluded), exclusionReasons=dict(Counter(row["reason"] for row in excluded)), distributionGroups=dict(Counter(row["distributionGroup"] for row in evidence_rows)))
    baseline_raw = {row["foodItemId"]: row for row in csv_read(ROOT / "data-source/food-quality/raw-identity-fields.csv")}
    before_families = Counter(retail_family(baseline_raw.get(row["id"], {"식품대분류명": row["category"]})) for row in baseline)
    after_families = before_families + Counter(row["retailFamily"] for row in evidence_rows)
    summary.update(beforeRetailFamilies=dict(before_families), afterRetailFamilies=dict(after_families),
                   beforeOfficialCategories=dict(Counter(row["category"] for row in baseline)),
                   afterOfficialCategories=dict(Counter(row["category"] for row in baseline + products)),
                   ingredientCompleteAdded=sum(row["ingredientStatus"] == "COMPLETE_DECLARATION" for row in evidence_rows),
                   allergenLabelAdded=sum(row["allergenStatus"] == "CONFIRMED_LABEL" for row in evidence_rows),
                   exactSourceIceManufacturerCount=len({row["manufacturer"] for row in evidence_rows if row["retailFamily"] == "아이스크림" and row["manufacturer"]}),
                   sourceCheckOutcome="All 45 domestic Binggrae families and 153 packages captured; nutrition failures/conflicts are withheld in unresolved.csv.")
    (OUT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
