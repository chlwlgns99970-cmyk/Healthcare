"""Audit runtime category results, retaining every remaining ambiguous identity.

This script reports Kotlin's output; it does not reimplement the classifier or
force a brand industry onto an ambiguous menu.
"""
import argparse
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/"data-source/catalog-qa"
RUNTIME = ROOT/"app/build/catalog-qa/final-franchise-menu-category-audit.csv"
BEFORE = SOURCE/"menu-category-before.csv"


def read(path):
    with path.open(encoding="utf-8-sig",newline="") as file:
        return list(csv.DictReader(file))


def run(capture_before=False):
    if capture_before and not BEFORE.exists():
        BEFORE.write_bytes(RUNTIME.read_bytes())
    previous = read(BEFORE)
    current = read(RUNTIME)
    metadata = {r["foodItemId"]:r for r in read(ROOT/"app/src/main/assets/fooddata/food_metadata.csv")}
    old_unknown = {r["menuId"] for r in previous if not r["menuCategory"]}
    result = []
    for row in current:
        if row["menuCategory"]:
            continue
        m = metadata.get(row["menuId"],{})
        name = row["name"].replace(" ","")
        reason = "PUBLISHED_AMBIGUOUS_NAME_WITHOUT_INDIVIDUAL_DISH_GROUP"
        if m.get("rawClassification") or row.get("sourceCategory"):
            reason = "SOURCE_CLASSIFICATION_NOT_A_SUPPORTED_INDIVIDUAL_MENU_KIND"
        if any(word in name for word in ("수프","스프")):
            reason = "PUBLISHED_SOUP_FORM_WITHOUT_EXISTING_FRANCHISE_SOUP_CHIP"
        if any(word in name for word in ("토핑","추가","사리")) or any(char.isdigit() for char in name) and any(word in name for word in ("g","EA","개")):
            reason = "PUBLISHED_INGREDIENT_OR_TOPPING_NOT_ASSUMED_COMPLETE_DISH"
        result.append(row|dict(unresolvedReason=reason,exactSource=m.get("sourceReference",""),rawClassification=m.get("rawClassification","")))
    removed = [r for r in previous if r["menuId"] not in {n["menuId"] for n in current} and
        (r["name"].replace(" ","").startswith("가맹문의") or r["name"] in ("신선합니다","맛있습니다","건강합니다"))]
    headers = tuple(current[0])+("unresolvedReason","exactSource","rawClassification")
    with (SOURCE/"menu-category-residual-audit.csv").open("w",encoding="utf-8",newline="") as file:
        writer = csv.DictWriter(file,fieldnames=headers,lineterminator="\n")
        writer.writeheader();writer.writerows(result)
    summary = dict(beforeUnknown=len(old_unknown),afterUnknown=len(result),
        beforeVisibleIdentities=len(previous),afterVisibleIdentities=len(current),
        resolvedPreviousUnknown=sum(r["menuId"] in old_unknown and bool(r["menuCategory"]) for r in current),
        hiddenNonFoodReferences=[r|dict(reason="NON_FOOD_CONTACT_OR_MARKETING_REFERENCE_EXCLUDED_FROM_SEARCH_HISTORY_PRESERVED") for r in removed],
        residualByBrand=dict(sorted(Counter(r["brand"] for r in result).items())),
        residualByReason=dict(sorted(Counter(r["unresolvedReason"] for r in result).items())),
        runtimeCategoryCounts=dict(sorted(Counter(r["menuCategory"] or "UNKNOWN" for r in current).items())),
        policy="Runtime Kotlin results only; exact individual source group/name or metadata, never brand industry. Remaining ambiguous identities stay UNKNOWN.")
    (SOURCE/"menu-category-followup-summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(summary,ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser();parser.add_argument("--capture-before",action="store_true")
    args = parser.parse_args();run(args.capture_before)
