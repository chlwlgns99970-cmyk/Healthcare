#!/usr/bin/env python3
"""Compare deterministic bundled product samples with the unmodified K-FIND workbook."""

from __future__ import annotations

import csv
import json
from pathlib import Path

import openpyxl

from import_kfind_processed_foods import VERSION, clean, compact_number, number, parse_amount


QUERIES = ("신라면", "진라면", "피자", "햄버거", "치킨", "코카콜라", "서울우유", "새우깡", "삼각김밥", "햇반")


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    asset = root / "app" / "src" / "main" / "assets" / "fooddata" / "product_items.csv"
    source = root / "data-source" / "kfind" / f"kfind-processed-food-db-{VERSION}.xlsx"
    with asset.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))
    samples = []
    for query in QUERIES:
        matches = [row for row in rows if query in row["name"]]
        if not matches:
            raise AssertionError(f"No bundled sample for {query}")
        samples.append(matches[0])
    expected = {row["sourceFoodCode"]: row for row in samples}

    workbook = openpyxl.load_workbook(source, read_only=True, data_only=True)
    sheet = workbook.active
    values = sheet.iter_rows(values_only=True)
    headers = [clean(value) for value in next(values)]
    index = {header: position for position, header in enumerate(headers)}
    found = {}
    for row in values:
        code = clean(row[index["식품코드"]])
        if code in expected:
            found[code] = row
        if len(found) == len(expected):
            break

    comparisons = {
        "name": lambda row: clean(row[index["식품명"]]),
        "brand": lambda row: "" if clean(row[index["제조사명"]]) in {"해당없음", "해당 없음", "-"} else clean(row[index["제조사명"]]),
        "energyKcal": lambda row: compact_number(number(row[index["에너지(kcal)"]])),
        "carbohydrateGrams": lambda row: compact_number(number(row[index["탄수화물(g)"]])),
        "proteinGrams": lambda row: compact_number(number(row[index["단백질(g)"]])),
        "fatGrams": lambda row: compact_number(number(row[index["지방(g)"]])),
        "sodiumMilligrams": lambda row: compact_number(number(row[index["나트륨(mg)"]])),
    }
    verified = []
    for code, bundled in expected.items():
        original = found.get(code)
        if original is None:
            raise AssertionError(f"Source row missing: {code}")
        basis = parse_amount(original[index["영양성분함량기준량"]])
        if basis is None or bundled["referenceAmount"] != compact_number(basis[0]) or bundled["unit"] != basis[1]:
            raise AssertionError(f"Nutrition basis mismatch: {code}")
        for field, extract in comparisons.items():
            if bundled[field] != extract(original):
                raise AssertionError(f"{field} mismatch: {code}")
        verified.append({"query": next(query for query in QUERIES if query in bundled["name"]), "code": code, "name": bundled["name"]})
    print(json.dumps({"verifiedSamples": len(verified), "samples": verified}, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
