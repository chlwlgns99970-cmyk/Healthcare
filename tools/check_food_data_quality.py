#!/usr/bin/env python3
"""Read-only audit of the bundled K-FIND foods and template ingredient units."""

import csv
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path


ASSETS = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "fooddata"
FLUID_OR_MIXED = {
    "음료 및 차류", "국 및 탕류", "찌개 및 전골류", "죽 및 스프류",
    "장류, 양념류", "유제품류 및 빙과류",
}


def read_csv(name):
    with (ASSETS / name).open(encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source))


def positive_number(value):
    try:
        return math.isfinite(float(value)) and float(value) > 0
    except ValueError:
        return False


def nonnegative_number(value):
    try:
        return math.isfinite(float(value)) and float(value) >= 0
    except ValueError:
        return False


def main():
    base_foods = read_csv("food_items.csv")
    product_foods = read_csv("product_items.csv")
    franchise_foods = read_csv("franchise_official_items.csv")
    foods = base_foods + product_foods + franchise_foods
    ingredients = read_csv("meal_template_ingredients.csv")
    by_id = {food["id"]: food for food in foods}
    codes = defaultdict(set)
    for food in foods:
        codes[food["sourceFoodCode"]].add(food["unit"])
    compact_names = Counter(food["normalizedName"] for food in foods if food["normalizedName"])
    findings = {
        "totalFoods": len(foods),
        "baseFoods": len(base_foods),
        "productFoods": len(product_foods),
        "franchiseFoods": len(franchise_foods),
        "kcalUsable": sum(nonnegative_number(food["energyKcal"]) for food in foods),
        "macroComplete": sum(all(nonnegative_number(food[field]) for field in
            ("carbohydrateGrams", "proteinGrams", "fatGrams")) for food in foods),
        "officialServing": sum(
            "공식 총내용량" in food["servingDescription"] or bool(re.search(
                r"공식\s*(?:(?:HOT|ICE)\s+)?(?:제공량\s*)?[0-9.]+\s*(개|줄|봉|병|캔|팩|컵|잔|조각|인분)\s*[0-9.]+\s*(g|ml)",
                food["servingDescription"], re.I))
            for food in foods),
        "productsWithOfficialTotal": sum(
            "공식 총내용량" in food["servingDescription"] for food in product_foods
        ),
        "productsWithVerifiedNamedPackageUnit": sum(
            "포장단위 " in food["servingDescription"] for food in product_foods
        ),
        "solidCategoryVolumeBasisNeedsReview": sum(
            food["sourceType"] == "K-FIND" and food["unit"].lower() == "ml" and food["category"] not in FLUID_OR_MIXED
            for food in foods
        ),
        "fluidCategoryMissingUnit": sum(
            food["category"] in FLUID_OR_MIXED and not food["unit"].strip()
            for food in foods
        ),
        "invalidReferenceAmount": sum(not positive_number(food["referenceAmount"]) for food in foods),
        "invalidEnergyKcal": sum(not nonnegative_number(food["energyKcal"]) for food in foods),
        "emptyName": sum(not food["name"].strip() for food in foods),
        "emptyCompactNormalizedName": sum(not food["normalizedName"].strip() for food in foods),
        "duplicateCompactNameKeys": sum(count > 1 for count in compact_names.values()),
        "duplicateCompactNameRows": sum(count for count in compact_names.values() if count > 1),
        "sourceCodeUnitConflicts": sum(len(units) > 1 for units in codes.values()),
        "duplicateFoodIds": len(foods) - len(by_id),
        "templateIngredientUnitMismatches": sum(
            ingredient["foodItemId"] not in by_id or
            ingredient["unit"].lower() != by_id[ingredient["foodItemId"]]["unit"].lower()
            for ingredient in ingredients
        ),
    }
    print(json.dumps(findings, ensure_ascii=False, indent=2))
    failures = (
        "invalidReferenceAmount", "invalidEnergyKcal", "emptyName", "emptyCompactNormalizedName",
        "sourceCodeUnitConflicts", "duplicateFoodIds", "templateIngredientUnitMismatches",
    )
    return int(any(findings[key] for key in failures))


if __name__ == "__main__":
    raise SystemExit(main())
