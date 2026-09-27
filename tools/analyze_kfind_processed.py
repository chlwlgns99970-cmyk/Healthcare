#!/usr/bin/env python3
"""Read-only audit for the official K-FIND processed-food workbook."""

from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import openpyxl


SOURCE = (
    Path(__file__).resolve().parents[1]
    / "data-source"
    / "kfind"
    / "kfind-processed-food-db-2026-08-28.xlsx"
)
PACKAGE_PATTERN = re.compile(
    r"(?:\[|\()\s*(봉지|봉|캔|병|팩|개|조각)\s*(?:\]|\))\s*$|(?:^|\s)(봉지|봉|캔|병|팩|개|조각)\s*$"
)
AMOUNT_PATTERN = re.compile(r"^\s*([0-9]+(?:\.[0-9]+)?)\s*(g|ml)\s*$", re.IGNORECASE)
TERMS = ("라면", "신라면", "진라면", "피자", "햄버거", "버거", "치킨", "콜라", "우유", "과자", "빵")


def clean(value: object) -> str:
    return str(value or "").strip()


def main() -> None:
    workbook = openpyxl.load_workbook(SOURCE, read_only=True, data_only=True)
    worksheet = workbook.active
    rows = worksheet.iter_rows(values_only=True)
    headers = [clean(value) for value in next(rows)]
    index = {header: position for position, header in enumerate(headers)}
    required = (
        "식품코드", "식품명", "식품대분류명", "대표식품명", "영양성분함량기준량",
        "에너지(kcal)", "1회 섭취참고량", "식품중량", "제조사명", "데이터기준일자",
    )
    missing = [header for header in required if header not in index]
    if missing:
        raise ValueError(f"Missing columns: {missing}")

    package_counts: Counter[str] = Counter()
    category_counts: Counter[str] = Counter()
    matched_package_rows = 0
    compatible_package_rows = 0
    term_samples: dict[str, list[dict[str, str]]] = defaultdict(list)
    package_samples: dict[str, list[dict[str, str]]] = defaultdict(list)
    total = 0
    for row in rows:
        total += 1
        name = clean(row[index["식품명"]])
        category = clean(row[index["식품대분류명"]])
        category_counts[category] += 1
        marker = PACKAGE_PATTERN.search(name)
        if marker:
            unit = marker.group(1) or marker.group(2)
            package_counts[unit] += 1
            matched_package_rows += 1
            basis = AMOUNT_PATTERN.fullmatch(clean(row[index["영양성분함량기준량"]]))
            weight = AMOUNT_PATTERN.fullmatch(clean(row[index["식품중량"]]))
            if basis and weight and basis.group(2).lower() == weight.group(2).lower():
                compatible_package_rows += 1
                if len(package_samples[unit]) < 5:
                    package_samples[unit].append(sample(row, index))
        normalized = name.replace(" ", "")
        for term in TERMS:
            if term.replace(" ", "") in normalized and len(term_samples[term]) < 8:
                term_samples[term].append(sample(row, index))

    output = {
        "source": str(SOURCE),
        "worksheet": worksheet.title,
        "rows": total,
        "columns": len(headers),
        "packageMarkerRows": matched_package_rows,
        "compatiblePackageRows": compatible_package_rows,
        "packageUnits": package_counts,
        "largestCategories": category_counts.most_common(30),
        "packageSamples": package_samples,
        "termSamples": term_samples,
    }
    print(json.dumps(output, ensure_ascii=True, indent=2, default=str))


def sample(row: tuple[object, ...], index: dict[str, int]) -> dict[str, str]:
    fields = (
        "식품코드", "식품명", "대표식품명", "식품대분류명", "식품중분류명", "식품소분류명",
        "영양성분함량기준량", "에너지(kcal)", "탄수화물(g)", "단백질(g)", "지방(g)",
        "나트륨(mg)", "1회 섭취참고량", "식품중량", "품목제조보고번호", "제조사명", "데이터기준일자",
    )
    return {field: clean(row[index[field]]) for field in fields}


if __name__ == "__main__":
    main()
