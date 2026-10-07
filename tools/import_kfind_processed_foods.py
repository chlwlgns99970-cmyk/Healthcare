#!/usr/bin/env python3
"""Create a curated offline product asset from the official K-FIND processed-food DB.

Only source fields are copied. A household package unit is emitted only when the
official product name explicitly identifies that package, or when a ramen record
is a single-weight bag product and the official intake-reference field explicitly
names the bag form. Nutrition remains stored in the source g/ml basis; package
calories are calculated proportionally by the Android app.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import re
import unicodedata
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any

import openpyxl


VERSION = "2026-08-28"
EXPECTED_SOURCE_SHA256 = "B074D98E75D2D087DC1B193F0056AFFBF9AFD14524C9CCB556CB2B20978504F7"
EXPECTED_SOURCE_COUNT = 316_734
SOURCE_TYPE = "K-FIND-PRODUCT"
SOURCE_NAME = "식품영양성분 데이터베이스"
SOURCE_NAME_ENGLISH = "Korean Food Composition Database system(K-FCDB)"
AMOUNT_PATTERN = re.compile(r"^\s*([0-9]+(?:\.[0-9]+)?)\s*(g|ml)\s*$", re.IGNORECASE)
PACKAGE_PATTERN = re.compile(
    r"(?:\[|\()\s*(봉지|봉|캔|병|팩|개|조각)\s*(?:\]|\))\s*$|(?:^|\s)(봉지|봉|캔|병|팩|개|조각)\s*$"
)
NON_NAME_CHARACTERS = re.compile(r"[^0-9a-z가-힣]")
HANGUL_NAME = re.compile(r"[가-힣]")
CUP_RAMEN_MARKERS = ("컵", "cup", "사발", "용기")
SINGLE_KIMBAP_ROLL = re.compile(r"(?<![0-9])(?:한|1)줄")

# These are search-oriented product families requested for this app. They do not
# assign nutrients or serving sizes; they only decide which official rows to bundle.
PRODUCT_FAMILIES: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("라면", ("라면", "컵누들")),
    ("피자", ("피자",)),
    ("햄버거", ("햄버거", "버거")),
    ("치킨", ("치킨", "통닭", "닭강정")),
    ("음료·유제품", ("콜라", "사이다", "탄산음료", "주스", "우유", "두유", "요구르트", "요거트", "핫식스", "레드불")),
    ("과자·빵", ("감자칩", "스낵", "과자", "비스킷", "쿠키", "식빵", "단팥빵", "소보로빵", "크림빵", "케이크", "새우깡", "꼬깔콘", "포카칩", "빼빼로", "초코파이")),
    ("즉석식품", ("도시락", "삼각김밥", "김밥", "샌드위치", "즉석밥", "컵밥", "햇반")),
)
FAMILY_LIMITS = {
    "라면": 1_000,
    "피자": 1_000,
    "햄버거": 1_000,
    "치킨": 1_000,
    "음료·유제품": 3_000,
    "과자·빵": 3_000,
    "즉석식품": 2_000,
}

FOOD_HEADERS = (
    "id", "sourceType", "sourceFoodCode", "name", "normalizedName", "aliases", "category",
    "referenceAmount", "unit", "energyKcal", "carbohydrateGrams", "proteinGrams", "fatGrams",
    "sodiumMilligrams", "servingDescription", "brand", "barcode", "dataVersion", "createdAt", "updatedAt",
)


def normalize_name(value: str) -> str:
    return NON_NAME_CHARACTERS.sub("", unicodedata.normalize("NFKC", value).lower())


def clean(value: Any) -> str:
    return str(value or "").strip()


def number(value: Any) -> float | None:
    if value is None or clean(value) == "":
        return None
    try:
        result = float(clean(value).replace(",", ""))
        return result if math.isfinite(result) else None
    except ValueError:
        return None


def compact_number(value: float | None) -> str:
    if value is None:
        return ""
    return f"{value:.10f}".rstrip("0").rstrip(".")


def parse_amount(value: Any) -> tuple[float, str] | None:
    match = AMOUNT_PATTERN.fullmatch(clean(value))
    if not match:
        return None
    return float(match.group(1)), match.group(2).lower()


def token_field(values: set[str]) -> str:
    tokens = sorted(value for value in values if value)
    return f"|{'|'.join(tokens)}|" if tokens else ""


def family_for(name: str) -> str | None:
    # 제품명이 실제로 요청 범주를 드러내는 행만 번들링한다. 대표식품명만
    # 일치하는 외국어·원재료성 행까지 끌어오면 사용자 검색 품질이 떨어진다.
    searchable = normalize_name(name)
    for family, terms in PRODUCT_FAMILIES:
        if any(normalize_name(term) in searchable for term in terms):
            return family
    return None


def package_unit(
    name: str,
    representative: str,
    small_category: str,
    intake_reference: str,
    total: tuple[float, str] | None,
) -> str | None:
    if total is None:
        return None
    normalized_name = normalize_name(name)
    # Only a ready-to-eat product whose official name explicitly says one roll.
    # A generic kimbap name, 210g intake reference, kit, or ingredient is not roll evidence.
    if (
        representative == "주먹밥/김밥/초밥"
        and "김밥" in normalized_name
        and SINGLE_KIMBAP_ROLL.search(normalized_name)
        and not any(marker in normalized_name for marker in ("김밥용", "키트", "세트", "반제"))
        and total[1] == "g"
        and total[0] > 0
    ):
        return "줄"
    marker = PACKAGE_PATTERN.search(name)
    if marker:
        unit = (marker.group(1) or marker.group(2)).replace("봉지", "봉")
        if unit == "조각":
            intake = parse_amount(intake_reference)
            # "조각"이라는 제품명과 실제 포장 중량이 모두 있어도 대용량 벌크는
            # 한 조각으로 볼 수 없다. 1회 참고량과 유사한 단일 포장만 채택한다.
            if intake is None or intake[1] != total[1] or not (0.5 <= total[0] / intake[0] <= 2.0):
                return None
        return unit

    is_ramen = normalize_name(representative) == "라면" or "라면" in normalized_name
    is_bag_reference = "봉지" in intake_reference
    is_single_bag_weight = total[1] == "g" and 30.0 <= total[0] <= 250.0
    is_cup = any(marker in normalized_name for marker in CUP_RAMEN_MARKERS)
    if is_ramen and small_category in {"유탕면", "건면"} and is_bag_reference and is_single_bag_weight and not is_cup:
        return "봉"
    return None


def fingerprint(row: dict[str, str]) -> tuple[str, ...]:
    return (
        row["normalizedName"], normalize_name(row["brand"]), row["category"], row["referenceAmount"], row["unit"],
        row["energyKcal"], row["carbohydrateGrams"], row["proteinGrams"], row["fatGrams"],
        row["sodiumMilligrams"], row["servingDescription"],
    )


def base_fingerprints(path: Path) -> set[tuple[str, ...]]:
    with path.open(encoding="utf-8", newline="") as source:
        return {fingerprint(row) for row in csv.DictReader(source)}


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "data-source" / "kfind" / f"kfind-processed-food-db-{VERSION}.xlsx"
    asset_dir = project_root / "app" / "src" / "main" / "assets" / "fooddata"
    output_path = asset_dir / "product_items.csv"
    source_sha256 = hashlib.sha256(source_path.read_bytes()).hexdigest().upper()
    if source_sha256 != EXPECTED_SOURCE_SHA256:
        raise ValueError(f"Unexpected processed-food source SHA-256: {source_sha256}")

    workbook = openpyxl.load_workbook(source_path, read_only=True, data_only=True)
    worksheet = workbook.active
    source_rows = worksheet.iter_rows(values_only=True)
    headers = [clean(value) for value in next(source_rows)]
    index = {header: position for position, header in enumerate(headers)}
    required = {
        "식품코드", "식품명", "대표식품명", "식품대분류명", "식품소분류명",
        "영양성분함량기준량", "에너지(kcal)", "탄수화물(g)", "단백질(g)", "지방(g)",
        "나트륨(mg)", "1회 섭취참고량", "식품중량", "제조사명", "데이터기준일자",
    }
    missing = sorted(required.difference(index))
    if missing:
        raise ValueError(f"Required processed-food columns are missing: {missing}")

    created_at = int(datetime.fromisoformat(f"{VERSION}T00:00:00+09:00").timestamp() * 1000)
    candidates: list[tuple[dict[str, str], str, str | None]] = []
    source_count = 0
    for excel_row in source_rows:
        source_count += 1
        code = clean(excel_row[index["식품코드"]])
        name = clean(excel_row[index["식품명"]])
        representative = clean(excel_row[index["대표식품명"]])
        family = family_for(name)
        if not code or not name or not HANGUL_NAME.search(name) or family is None:
            continue
        basis = parse_amount(excel_row[index["영양성분함량기준량"]])
        energy = number(excel_row[index["에너지(kcal)"]])
        if basis is None or energy is None or energy <= 0:
            continue
        total = parse_amount(excel_row[index["식품중량"]])
        brand = clean(excel_row[index["제조사명"]])
        if brand in {"해당없음", "해당 없음", "-"}:
            brand = ""
        category = clean(excel_row[index["식품대분류명"]])
        small_category = clean(excel_row[index["식품소분류명"]])
        intake_reference = clean(excel_row[index["1회 섭취참고량"]])
        package = package_unit(name, representative, small_category, intake_reference, total)
        serving_parts = [f"{compact_number(basis[0])}{basis[1]} 기준"]
        if total is not None:
            serving_parts.append(f"공식 총내용량 {compact_number(total[0])}{total[1]}")
        if package is not None:
            serving_parts.append(f"포장단위 {package}")
        aliases = {normalize_name(representative), normalize_name(brand)}
        normalized_name = normalize_name(name)
        aliases.discard("")
        aliases.discard(normalized_name)
        raw_version = clean(excel_row[index["데이터기준일자"]])
        data_version = raw_version[:10] if re.match(r"^\d{4}-\d{2}-\d{2}", raw_version) else VERSION
        row = {
            "id": f"kfind-product-{code.lower()}",
            "sourceType": SOURCE_TYPE,
            "sourceFoodCode": code,
            "name": name,
            "normalizedName": normalized_name,
            "aliases": token_field(aliases),
            "category": category,
            "referenceAmount": compact_number(basis[0]),
            "unit": basis[1],
            "energyKcal": compact_number(energy),
            "carbohydrateGrams": compact_number(number(excel_row[index["탄수화물(g)"]])),
            "proteinGrams": compact_number(number(excel_row[index["단백질(g)"]])),
            "fatGrams": compact_number(number(excel_row[index["지방(g)"]])),
            "sodiumMilligrams": compact_number(number(excel_row[index["나트륨(mg)"]])),
            "servingDescription": " · ".join(serving_parts),
            "brand": brand,
            "barcode": "",
            "dataVersion": data_version,
            "createdAt": str(created_at),
            "updatedAt": str(created_at),
        }
        candidates.append((row, family, package))
    if source_count != EXPECTED_SOURCE_COUNT:
        raise ValueError(f"Expected {EXPECTED_SOURCE_COUNT} processed-food rows, found {source_count}")

    # Keep one deterministic row only when official name, maker, nutrient basis,
    # nutrients, total amount and package evidence are all identical.
    existing = base_fingerprints(asset_dir / "food_items.csv")
    deduplicated: dict[tuple[str, ...], tuple[dict[str, str], str, str | None]] = {}
    cross_source_skipped = 0
    for candidate in sorted(candidates, key=lambda value: value[0]["sourceFoodCode"]):
        key = fingerprint(candidate[0])
        if key in existing:
            cross_source_skipped += 1
            continue
        deduplicated.setdefault(key, candidate)
    by_family: dict[str, list[tuple[dict[str, str], str, str | None]]] = {}
    for value in deduplicated.values():
        by_family.setdefault(value[1], []).append(value)
    selected: list[tuple[dict[str, str], str, str | None]] = []
    for family, values in by_family.items():
        # Prefer rows with explicit package evidence, official total content and maker.
        # Shorter canonical names rank ahead of compound ingredient/product variants.
        values.sort(key=lambda value: (
            value[2] is None,
            "공식 총내용량" not in value[0]["servingDescription"],
            not bool(value[0]["brand"]),
            len(value[0]["normalizedName"]),
            value[0]["normalizedName"],
            value[0]["sourceFoodCode"],
        ))
        selected.extend(values[:FAMILY_LIMITS[family]])
    rows = [value[0] for value in selected]
    rows.sort(key=lambda row: (row["normalizedName"], normalize_name(row["brand"]), row["sourceFoodCode"]))
    with output_path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=FOOD_HEADERS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)

    family_counts = Counter(value[1] for value in selected)
    package_counts = Counter(value[2] for value in selected if value[2])
    summary = {
        "sourceRows": source_count,
        "selectedBeforeDeduplication": len(candidates),
        "eligibleAfterDeduplication": len(deduplicated),
        "selectedProducts": len(rows),
        "familyLimits": FAMILY_LIMITS,
        "withinSourceDuplicatesRemoved": len(candidates) - len(deduplicated) - cross_source_skipped,
        "crossSourceExactDuplicatesSkipped": cross_source_skipped,
        "families": dict(sorted(family_counts.items())),
        "verifiedPackageUnits": dict(sorted(package_counts.items())),
        "sourceSha256": source_sha256,
        "outputSha256": hashlib.sha256(output_path.read_bytes()).hexdigest().upper(),
    }
    print(json.dumps(summary, ensure_ascii=True, indent=2))
    # Keep classification, official product-report identity and upstream attribution
    # in a sidecar; Room v8 and the source nutrition basis do not change.
    from extract_food_identity_fields import main as restore_identity_fields
    restore_identity_fields()


if __name__ == "__main__":
    main()
