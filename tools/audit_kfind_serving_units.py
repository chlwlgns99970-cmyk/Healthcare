#!/usr/bin/env python3
"""Reproducible, read-only K-FIND source/asset and solid-volume safety audit.

The classifications describe *recording safety*, not whether K-FIND is wrong.
No density, household serving, or nutrition value is inferred here.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import re
from collections import Counter, defaultdict
from pathlib import Path

import openpyxl

from import_kfind_foods import (
    EXPECTED_FOOD_COUNT,
    EXPECTED_SOURCE_SHA256,
    REFERENCE_PATTERN,
    compact_number,
    normalize_name,
    number,
    parse_amount,
)


ROOT = Path(__file__).resolve().parents[1]
FLUID_OR_MIXED = {
    "음료 및 차류", "국 및 탕류", "찌개 및 전골류", "죽 및 스프류",
    "장류, 양념류", "유제품류 및 빙과류",
}
# A name hint is *not* proof that a volume serving is appropriate. It instead
# routes a mismatched category to manual review. Check a whole name component
# to avoid false matches such as 탕수육, 국수, 차돌박이, 죽순.
POSSIBLE_LIQUID_COMPONENT = re.compile(r"(?:국|탕|찌개|스프|죽|음료|주스|육수)$")
LIQUID_SUBCATEGORY = {"국", "탕", "찌개", "스프", "죽", "음료", "주스", "육수"}
FIELDS = (
    "sourceRow", "sourceFoodCode", "name", "majorCategory", "middleCategory",
    "minorCategory", "detailCategory", "sourceReference", "sourceKcal",
    "assetReferenceAmount", "assetUnit", "assetKcal", "classification",
    "reasonCodes", "massAlternativeCodes", "verifiedHouseholdSource",
    "directRecordEnabled", "searchRankDemoted", "sourceAssetMismatch",
)


def rows_from_source(path: Path):
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    try:
        sheet = workbook.active
        iterator = sheet.iter_rows(values_only=True)
        headers = next(iterator)
        positions = {str(value).strip(): index for index, value in enumerate(headers) if value}
        needed = (
            "식품코드", "식품명", "식품대분류명", "식품중분류명", "식품소분류명",
            "식품세분류명", "영양성분함량기준량", "에너지(kcal)",
        )
        missing = set(needed) - positions.keys()
        if missing:
            raise ValueError(f"Missing source columns: {sorted(missing)}")
        for row_number, values in enumerate(iterator, start=2):
            yield row_number, {
                key: values[positions[key]] for key in needed
            }
    finally:
        workbook.close()


def read_assets(path: Path):
    with path.open(encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source))


def classify(source: dict, mass_codes: list[str]):
    if mass_codes:
        return "MASS_ALTERNATIVE_AVAILABLE", "MASS_ALTERNATIVE_FOUND"
    base_name = re.split(r"[_·\s]+", str(source["식품명"] or ""))[0]
    categories = (source["식품중분류명"], source["식품소분류명"], source["식품세분류명"])
    category_hint = any(str(value or "").strip() in LIQUID_SUBCATEGORY for value in categories)
    name_hint = bool(POSSIBLE_LIQUID_COMPONENT.search(base_name)) and not base_name.endswith("그라탕")
    if name_hint or category_hint:
        return "CATEGORY_REVIEW_REQUIRED", "CATEGORY_AMBIGUOUS"
    return "VOLUME_ONLY_UNRESOLVED", "VOLUME_ONLY_NO_SAFE_ALTERNATIVE"


def write_csv(path: Path, fields, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def audit(source_path: Path, asset_path: Path):
    source_sha = hashlib.sha256(source_path.read_bytes()).hexdigest().upper()
    if source_sha != EXPECTED_SOURCE_SHA256:
        raise ValueError(f"Unexpected K-FIND workbook SHA-256: {source_sha}")
    assets = read_assets(asset_path)
    by_code = defaultdict(list)
    grams_by_name = defaultdict(list)
    for asset in assets:
        by_code[asset["sourceFoodCode"]].append(asset)
        if asset["unit"].lower() == "g":
            grams_by_name[normalize_name(asset["name"])].append(asset["sourceFoodCode"])

    suspects = []
    discrepancies = []
    source_issues = []
    source_count = 0
    source_codes = set()
    for row_number, source in rows_from_source(source_path):
        source_count += 1
        code = str(source["식품코드"] or "").strip()
        name = str(source["식품명"] or "").strip()
        category = str(source["식품대분류명"] or "").strip()
        raw_reference = str(source["영양성분함량기준량"] or "").strip()
        energy = number(source["에너지(kcal)"])
        if code in source_codes:
            source_issues.append((row_number, code, "DUPLICATE_SOURCE_CODE"))
        source_codes.add(code)
        try:
            amount, unit = parse_amount(raw_reference, REFERENCE_PATTERN)
        except ValueError:
            source_issues.append((row_number, code, "INVALID_SOURCE_REFERENCE"))
            discrepancies.append((row_number, code, "UNPARSABLE_SOURCE_REFERENCE"))
            continue
        if not code or not name or amount <= 0 or energy is None or energy < 0:
            source_issues.append((row_number, code, "INVALID_SOURCE_VALUE"))
        matches = by_code.get(code, [])
        mismatch = []
        if len(matches) != 1:
            mismatch.append("ASSET_CODE_CARDINALITY")
        else:
            asset = matches[0]
            for field, actual, expected in (
                ("NAME", asset["name"], name),
                ("CATEGORY", asset["category"], category),
                ("REFERENCE_AMOUNT", asset["referenceAmount"], compact_number(amount)),
                ("UNIT", asset["unit"].lower(), unit),
                ("KCAL", asset["energyKcal"], compact_number(energy)),
            ):
                if actual != expected:
                    mismatch.append(field)
        if mismatch:
            discrepancies.append((row_number, code, ";".join(mismatch)))
        if unit != "ml" or category in FLUID_OR_MIXED:
            continue
        mass_codes = sorted(set(grams_by_name.get(normalize_name(name), [])) - {code})
        classification, specific_reason = classify(source, mass_codes)
        reasons = [
            "SOLID_WITH_VOLUME_BASIS", specific_reason, "SOURCE_UNIT_PRESERVED",
            "DIRECT_RECORD_DISABLED", "SEARCH_RANK_DEMOTED",
        ]
        if classification == "CATEGORY_REVIEW_REQUIRED":
            reasons.append("MANUAL_REVIEW_REQUIRED")
        asset = matches[0] if len(matches) == 1 else {}
        suspects.append({
            "sourceRow": row_number,
            "sourceFoodCode": code,
            "name": name,
            "majorCategory": category,
            "middleCategory": str(source["식품중분류명"] or ""),
            "minorCategory": str(source["식품소분류명"] or ""),
            "detailCategory": str(source["식품세분류명"] or ""),
            "sourceReference": raw_reference,
            "sourceKcal": compact_number(energy),
            "assetReferenceAmount": asset.get("referenceAmount", ""),
            "assetUnit": asset.get("unit", ""),
            "assetKcal": asset.get("energyKcal", ""),
            "classification": classification,
            "reasonCodes": ";".join(reasons),
            "massAlternativeCodes": ";".join(mass_codes),
            "verifiedHouseholdSource": "",
            "directRecordEnabled": "false",
            "searchRankDemoted": "true",
            "sourceAssetMismatch": ";".join(mismatch),
        })
    for code in by_code.keys() - source_codes:
        discrepancies.append(("", code, "ASSET_CODE_MISSING_IN_SOURCE"))
    metrics = Counter(row["classification"] for row in suspects)
    summary = {
        "source_sha256": source_sha,
        "source_rows": source_count,
        "asset_rows": len(assets),
        "suspect_rows": len(suspects),
        "mass_alternative": metrics["MASS_ALTERNATIVE_AVAILABLE"],
        "verified_household_alternative": metrics["VERIFIED_HOUSEHOLD_ALTERNATIVE"],
        "volume_appropriate_confirmed": metrics["VOLUME_APPROPRIATE"],
        "volume_only_unresolved": metrics["VOLUME_ONLY_UNRESOLVED"],
        "category_review": metrics["CATEGORY_REVIEW_REQUIRED"],
        "unclassified": sum(not row["classification"] for row in suspects),
        "direct_record_disabled": sum(row["directRecordEnabled"] == "false" for row in suspects),
        "only_rank_demoted": sum(
            row["searchRankDemoted"] == "true" and row["directRecordEnabled"] != "false"
            for row in suspects
        ),
        "source_asset_mismatches": len(discrepancies),
        "source_asset_unit_mismatches": sum("UNIT" in item[2].split(";") for item in discrepancies),
        "confirmed_import_errors": len(discrepancies),
        "confirmed_source_value_issues": len(source_issues),
    }
    return suspects, discrepancies, source_issues, summary


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=ROOT / "data-source/kfind/kfind-food-db-2026-08-28.xlsx")
    parser.add_argument("--asset", type=Path, default=ROOT / "app/src/main/assets/fooddata/food_items.csv")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/kfind-serving-unit-audit.csv")
    args = parser.parse_args()
    suspects, discrepancies, source_issues, summary = audit(args.source, args.asset)
    write_csv(args.output, FIELDS, suspects)
    write_csv(args.output.with_name("kfind-serving-unit-discrepancies.csv"),
              ("sourceRow", "sourceFoodCode", "discrepancyCodes"),
              (dict(zip(("sourceRow", "sourceFoodCode", "discrepancyCodes"), row)) for row in discrepancies))
    lines = [
        "# K-FIND 고형 범주 부피 기준 전수 감사", "",
        f"- 원본 SHA-256: `{summary['source_sha256']}`",
        "- 원본의 `g`/`ml` 및 kcal는 수정하지 않았습니다. `MASS_ALTERNATIVE_AVAILABLE`도 같은 음식명에 별도 g 행이 있음을 뜻할 뿐 영양값의 교환·환산 근거가 아닙니다.",
        "- `CATEGORY_REVIEW_REQUIRED`는 음식명/세부 분류에 액체 유사 단서가 있으나 안전한 부피 분량이 검증되지 않았다는 뜻입니다. 직접 기록 차단은 유지합니다.",
        "- 검증된 공식 제공량/생활 단위의 출처 매핑이 없어 해당 분류는 0입니다. 원본 문제나 ml 표기 오류로 확정하지 않았습니다.",
        "", "| 항목 | 개수 |", "| --- | ---: |",
    ]
    labels = (
        ("원본 항목", "source_rows"), ("앱 항목", "asset_rows"),
        ("고형 범주 ml", "suspect_rows"), ("동일 정규화명 g 대안", "mass_alternative"),
        ("검증된 생활 단위 대안", "verified_household_alternative"),
        ("부피 단위 적합 확정", "volume_appropriate_confirmed"),
        ("부피 기준만 있어 미해결", "volume_only_unresolved"),
        ("카테고리 수동 검토", "category_review"), ("미분류", "unclassified"),
        ("직접 기록 차단", "direct_record_disabled"), ("순위만 하향", "only_rank_demoted"),
        ("원본-앱 전체 불일치", "source_asset_mismatches"),
        ("원본-앱 단위 불일치", "source_asset_unit_mismatches"),
        ("확인된 가져오기 오류", "confirmed_import_errors"),
        ("확인된 원본 값 문제", "confirmed_source_value_issues"),
    )
    lines.extend(f"| {label} | {summary[key]:,} |" for label, key in labels)
    lines.extend([
        "", "판정 순서: 동일 정규화 음식명에 g 행이 있으면 mass 대안; 없고 음식명 또는 중·소·세분류에 액체 유사 단서가 있으면 카테고리 검토; 나머지는 volume-only 미해결. 어떤 경우도 의심 ml 행을 직접 기록하지 않습니다.",
        "", f"항목별 근거: `{args.output.name}`. 원본-앱 불일치: `kfind-serving-unit-discrepancies.csv`.", "",
    ])
    args.output.with_name("kfind-serving-unit-audit-summary.md").write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(f"{key}={value}" for key, value in summary.items()))
    if source_issues:
        print(f"First source issue: {source_issues[0]}")
    return int(
        summary["source_rows"] != EXPECTED_FOOD_COUNT or
        summary["suspect_rows"] != 2382 or
        summary["unclassified"] or
        summary["source_asset_mismatches"] or
        summary["confirmed_source_value_issues"]
    )


if __name__ == "__main__":
    raise SystemExit(main())
