#!/usr/bin/env python3
"""Reproduce the bundled search ordering for the supported Korean QA queries."""

from __future__ import annotations

import csv
from pathlib import Path

from audit_kfind_serving_units import FLUID_OR_MIXED, ROOT, normalize_name


QUERIES = (
    "김밥", "참치김밥", "참치 김밥", "비빔밥", "쫄면", "라면", "된장찌개",
    "계란", "달걀", "닭가슴살", "닭 가슴살", "사과", "바나나", "식빵",
    "우유", "떡볶이", "불고기", "삼겹살",
)
ALTERNATES = {"참치김밥": "김밥참치", "계란": "달걀", "달걀": "계란", "흰밥": "쌀밥", "흰우유": "우유"}


def unsafe(food):
    return food["sourceType"].upper() == "K-FIND" and food["unit"].lower() == "ml" and food["category"] not in FLUID_OR_MIXED


def search_variant(foods, query):
    matches = [food for food in foods if query in food["normalizedName"] or f"|{query}|" in food["aliases"]]
    return sorted(matches, key=lambda food: (
        unsafe(food),
        0 if food["normalizedName"] == query else 1 if food["normalizedName"].startswith(query) else 2,
        food["name"],
    ))[:30]


def search(foods, query):
    primary = normalize_name(query)
    first = search_variant(foods, primary)
    alternate = ALTERNATES.get(primary)
    if not alternate:
        return first
    combined = list({food["id"]: food for food in first + search_variant(foods, alternate)}.values())
    return sorted(combined, key=lambda food: (unsafe(food), 0 if food["normalizedName"] == primary else 1))[:30]


def main():
    source = ROOT / "app/src/main/assets/fooddata/food_items.csv"
    with source.open(encoding="utf-8", newline="") as stream:
        foods = list(csv.DictReader(stream))
    lines = [
        "# K-FIND 음식 검색 회귀 매트릭스", "",
        "앱 CSV와 `FoodItemDao.observeSearch`/`NutritionRepository.search`의 필터·정렬을 재현했습니다. 직접 기록 가능은 K-FIND 고형 범주 ml 정책만을 뜻하며, 사용자가 실제 먹은 음식·양을 확인해야 합니다.",
        "", "| 검색어 | 표시 후보 | 첫 후보 | 단위 | 분류 | 직접 기록 | 위험 후보의 안전 후보 선행 |",
        "| --- | ---: | --- | --- | --- | --- | ---: |",
    ]
    failures = []
    for query in QUERIES:
        results = search(foods, query)
        if not results:
            failures.append(f"No results: {query}")
            lines.append(f"| {query} | 0 | — | — | — | — | — |")
            continue
        first = results[0]
        unsafe_before_safe = sum(
            unsafe(food) and any(not unsafe(later) for later in results[index + 1:])
            for index, food in enumerate(results)
        )
        if unsafe_before_safe:
            failures.append(f"Unsafe item ranked before safe item: {query}")
        lines.append(
            f"| {query} | {len(results)} | {first['name']} | {first['unit']} | "
            f"{first['category']} | {'아니요' if unsafe(first) else '예'} | {unsafe_before_safe} |"
        )
    for query in ("참치김밥", "참치 김밥"):
        result = search(foods, query)
        tuna = [food for food in result if food["name"] == "김밥_참치"]
        if not tuna or tuna[0]["unit"] != "g" or not any(food["unit"] == "ml" for food in tuna):
            failures.append(f"Tuna gimbap mass-first regression: {query}")
    lines.extend([
        "", "`100g`/`100ml`는 원본 영양성분 기준량이며 실제 섭취량이 아닙니다. 이름 부분 일치는 다른 조리식품이나 음료를 반환할 수 있으므로 자동 선택하지 않습니다.",
        "", f"검사 실패: {len(failures)}개" + (" (" + "; ".join(failures) + ")" if failures else ""), "",
    ])
    output = ROOT / "docs/kfind-search-regression.md"
    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"{len(QUERIES)} queries, {len(failures)} failures -> {output}")
    return int(bool(failures))


if __name__ == "__main__":
    raise SystemExit(main())
