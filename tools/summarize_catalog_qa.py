"""Read-only catalog metrics, using this request's 31,849-food captured baseline."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import json
import math
from pathlib import Path
import re

from generate_franchise_brand_audit import parse_catalog, normalize

ROOT = Path(__file__).resolve().parents[1]
ASSET_PATH = Path("app/src/main/assets/fooddata")
DOMAIN_PATH = Path("app/src/main/java/com/example/healthcare/domain")
FOOD_FILES = ("food_items.csv", "product_items.csv", "franchise_official_items.csv")
REQUIRED_BRANDS = ("피자스쿨", "파파존스", "청년피자", "노랑통닭", "지코바", "처갓집양념치킨")
MEALS = ("BREAKFAST", "LUNCH", "DINNER", "SNACK")
VERSION = "catalog-qa-final-summary-v1"


class Inputs:
    def __init__(self, root):
        self.root, self.hashes = root, {}

    def bytes(self, path):
        full = self.root / path
        data = full.read_bytes()
        self.hashes[full.relative_to(self.root).as_posix()] = hashlib.sha256(data).hexdigest()
        return data

    def text(self, path):
        return self.bytes(path).decode("utf-8-sig")

    def json(self, path):
        return json.loads(self.text(path))

    def csv(self, path):
        # Fields are single-line generated evidence, but the normal CSV reader
        # also retains escaped quotes and the original ingredient text.
        import io
        return list(csv.DictReader(io.StringIO(self.text(path), newline="")))


def finite_nonnegative(value):
    try:
        number = float(value)
        return math.isfinite(number) and number >= 0
    except (TypeError, ValueError):
        return False


def evidence_counts(ids, metadata):
    rows = [metadata[i] for i in ids if i in metadata]
    known = sum(bool(r.get("ingredients")) for r in rows)
    complete = sum(r.get("ingredientStatus") == "COMPLETE_DECLARATION" for r in rows)
    return {
        "identities": len(ids), "linkedMetadata": len(rows),
        "ingredientPartial": sum(bool(r.get("ingredients")) and r.get("ingredientStatus") != "COMPLETE_DECLARATION" for r in rows),
        "ingredientComplete": complete, "ingredientKnown": known,
        "ingredientUnknown": len(ids) - known,
        "allergenEvidence": sum(r.get("allergenStatus", "UNKNOWN") != "UNKNOWN" for r in rows),
        "allergenCompleteLabel": sum(r.get("allergenStatus") == "CONFIRMED_LABEL" for r in rows),
        "allergenUnknown": sum(r.get("allergenStatus", "UNKNOWN") == "UNKNOWN" for r in rows),
        "crossContactEvidence": sum(bool(r.get("crossContactText") or r.get("mayContainAllergens")) for r in rows),
        "foodGroups": sum(bool(r.get("foodGroups")) for r in rows),
        "provenance": sum(bool(r.get("sourceReference")) for r in rows),
        "sourceHashLinked": sum(bool(r.get("sourceHash")) for r in rows),
        "ingredientStatuses": dict(sorted(Counter(r.get("ingredientStatus", "UNKNOWN") for r in rows).items())),
        "allergenStatuses": dict(sorted(Counter(r.get("allergenStatus", "UNKNOWN") for r in rows).items())),
        "sourceStatuses": dict(sorted(Counter(r.get("sourceStatus") or "UNKNOWN" for r in rows).items())),
        "availabilityStatuses": dict(sorted(Counter(r.get("availabilityStatus") or "UNKNOWN" for r in rows).items())),
    }


def food_counts(foods, metadata):
    ids = {r["id"] for r in foods}
    assert len(ids) == len(foods), "Duplicate food ID in packaged inputs"
    return dict(total=len(foods),
        kcal=sum(finite_nonnegative(r.get("energyKcal")) for r in foods),
        macroComplete=sum(all(finite_nonnegative(r.get(k)) for k in ("carbohydrateGrams", "proteinGrams", "fatGrams")) for r in foods),
        genuineReportedZeroKcal=sum(finite_nonnegative(r.get("energyKcal")) and float(r["energyKcal"]) == 0 for r in foods),
        sourceTypes=dict(sorted(Counter(r["sourceType"] for r in foods).items())),
        **evidence_counts(ids, metadata))


def generated_menus(text, catalog):
    menus = {}
    for block in re.findall(r"FranchiseMenu\(([^\n]+)\)", text):
        values = [json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"', block)]
        assert len(values) >= 5, "Unsupported generated official menu syntax"
        row = dict(zip(("id", "brand", "name", "sourceUrl", "verifiedAt", "sourceDate", "saleState"), values[:7]))
        category = re.search(r'category\s*=\s*("(?:\\.|[^"\\])*")', block)
        row["category"] = json.loads(category[1]) if category else ""
        menus.setdefault((row["brand"], normalize(row["name"])), row)
    for brand in catalog:
        for name in brand["references"]:
            menus.setdefault((brand["name"], normalize(name)), {
                "id": f"official-menu-{normalize(brand['name'])}-{normalize(name)}",
                "brand": brand["name"], "name": name, "sourceUrl": brand["officialUrl"],
                "verifiedAt": "2026-09-27", "sourceDate": "", "saleState": "SOURCE_DATE_UNKNOWN", "category": ""})
    return list(menus.values())


def franchise_counts(catalog, foods, menus, metadata):
    brands = {b["name"] for b in catalog}
    nutrition = [r for r in foods if r.get("brand") in brands and r["sourceType"] in ("K-FIND", "OFFICIAL-BRAND-NUTRITION")]
    nutrition_by_brand, menus_by_brand = defaultdict(list), defaultdict(list)
    for r in nutrition: nutrition_by_brand[r["brand"]].append(r)
    for r in menus: menus_by_brand[r["brand"]].append(r)
    rows = []
    for brand in catalog:
        n, m = nutrition_by_brand[brand["name"]], menus_by_brand[brand["name"]]
        n_ids, m_ids = {r["id"] for r in n}, {r["id"] for r in m}
        n_names, m_names = {normalize(r["name"]) for r in n}, {normalize(r["name"]) for r in m}
        rows.append(dict(brand=brand["name"], industry=brand["category"], aliases=brand["aliases"], officialUrl=brand["officialUrl"],
            sourceMenuEntries=len(n_ids | m_ids), nutritionIdentities=len(n_ids),
            officialMenuNameIdentities=len(m_ids), nutritionMissing=len(m_ids - n_ids),
            uniqueMenuNames=len(n_names | m_names), nutritionAndReferenceNameOverlap=len(n_names & m_names),
            macroComplete=sum(all(finite_nonnegative(r.get(k)) for k in ("carbohydrateGrams", "proteinGrams", "fatGrams")) for r in n),
            **evidence_counts(n_ids | m_ids, metadata)))
    all_ids = {r["id"] for r in nutrition + menus}
    industries = {}
    for industry in sorted({r["industry"] for r in rows}):
        rr = [r for r in rows if r["industry"] == industry]
        industries[industry] = dict(brands=len(rr), brandNames=[r["brand"] for r in rr],
            menus=sum(r["sourceMenuEntries"] for r in rr), kcal=sum(r["nutritionIdentities"] for r in rr),
            nutritionMissing=sum(r["nutritionMissing"] for r in rr),
            zeroMenuBrands=[r["brand"] for r in rr if r["sourceMenuEntries"] == 0])
    return dict(brands=len(catalog), menus=len(all_ids), kcal=len({r["id"] for r in nutrition}),
        nutritionMissing=len(all_ids) - len({r["id"] for r in nutrition}), menuOnly=len(menus),
        **evidence_counts(all_ids, metadata), industries=industries, perBrand=rows,
        zeroMenuBrands=[r["brand"] for r in rows if r["sourceMenuEntries"] == 0])


def recommendation_counts(templates, links, metadata, groups, research):
    by_template = defaultdict(list)
    for row in links: by_template[row["mealTemplateId"]].append(row["foodItemId"])
    template_ids = {r["id"] for r in templates}
    assert template_ids == set(by_template), "Every recommendation needs exact ingredient links"
    eligible = {r["stableTemplateId"] for r in groups if r.get("slowStyleEligible") == "true"}
    assert eligible <= template_ids
    return dict(total=len(templates), ingredientLinks=len(links), uniqueLinkedFoodIds=len({r["foodItemId"] for r in links}),
        ingredients=sum(any(metadata.get(i, {}).get("ingredients") for i in by_template[t]) for t in template_ids),
        foodGroups=sum(any(metadata.get(i, {}).get("foodGroups") for i in by_template[t]) for t in template_ids),
        officialAllergens=sum(all(metadata.get(i, {}).get("allergenStatus") == "CONFIRMED_LABEL" for i in by_template[t]) for t in template_ids),
        ingredientCompleteLabels=sum(all(metadata.get(i, {}).get("ingredientStatus") == "COMPLETE_DECLARATION" for i in by_template[t]) for t in template_ids),
        positiveAllergenEvidence=sum(any(metadata.get(i, {}).get("allergenStatus", "UNKNOWN") != "UNKNOWN" for i in by_template[t]) for t in template_ids),
        styleEligible=len(eligible), perMeal={meal: sum(r["id"] in eligible and meal in r["supportedMealTypes"].split("|") for r in templates) for meal in MEALS},
        researchRows=len(research), researchStatuses=dict(sorted(Counter(r["researchStatus"] for r in research).items())),
        researchAllergenStatuses=dict(sorted(Counter(r["allergenStatus"] for r in research).items())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, default=Path("data-source/catalog-qa/final-audit.json"))
    args = parser.parse_args()
    inputs = Inputs(args.project_root.resolve())
    baseline = inputs.json(Path("data-source/catalog-qa/baseline.json"))
    assert baseline["foods"]["total"] == 31849, "Only this request's 31,849-food baseline is allowed"
    before_root = Path(baseline["immutableInputDirectory"].replace("\\", "/"))
    manifest = dict(line.split("=", 1) for line in inputs.text(ASSET_PATH / "food_data_manifest.properties").splitlines() if "=" in line)
    before_foods = [r for name in FOOD_FILES for r in inputs.csv(before_root / ASSET_PATH / name)]
    foods = [r for name in FOOD_FILES for r in inputs.csv(ASSET_PATH / name)]
    before_meta = {r["foodItemId"]: r for r in inputs.csv(before_root / ASSET_PATH / "food_metadata.csv")}
    meta = {r["foodItemId"]: r for r in inputs.csv(ASSET_PATH / "food_metadata.csv")}
    before_metrics, metrics = food_counts(before_foods, before_meta), food_counts(foods, meta)
    for key in baseline["foods"]: assert before_metrics[key] == baseline["foods"][key], ("Baseline mismatch", key)
    before_ids, ids = {r["id"] for r in before_foods}, {r["id"] for r in foods}

    catalog = parse_catalog(inputs.text(DOMAIN_PATH / "FranchiseCatalog.kt"))
    before_catalog = parse_catalog(inputs.text(before_root / DOMAIN_PATH / "FranchiseCatalog.kt"))
    menus = generated_menus(inputs.text(DOMAIN_PATH / "OfficialFranchiseMenus.kt"), catalog)
    before_menus = generated_menus(inputs.text(before_root / DOMAIN_PATH / "OfficialFranchiseMenus.kt"), before_catalog)
    franchise = franchise_counts(catalog, foods, menus, meta)
    before_franchise = franchise_counts(before_catalog, before_foods, before_menus, before_meta)
    assert before_franchise["brands"] == baseline["franchise"]["brands"] == 63
    assert before_franchise["menus"] == baseline["franchise"]["menus"]

    def rec(base, metadata):
        return recommendation_counts(inputs.csv(base / ASSET_PATH / "meal_templates.csv"),
            inputs.csv(base / ASSET_PATH / "meal_template_ingredients.csv"), metadata,
            inputs.csv(base / "data-source/recommendation/verified-food-groups.csv"),
            inputs.csv(base / "data-source/recommendation/recommendation-ingredient-research.csv"))
    recommendation = rec(Path(), meta)
    before_rec = rec(before_root, before_meta)
    rec_source = inputs.json(Path("data-source/catalog-recommendation/summary.json"))
    assert recommendation["ingredients"] == rec_source["after"]["sourcedIngredients"]
    assert recommendation["foodGroups"] == rec_source["after"]["sourcedGroups"]
    assert recommendation["styleEligible"] == rec_source["after"]["styleEligible"]
    assert recommendation["perMeal"] == rec_source["after"]["perMeal"]

    build = Path("app/build/catalog-qa")
    serving = inputs.json(build / "final-serving-summary.json")
    before_serving = inputs.json(build / "baseline-serving-summary.json")
    serving_rows = inputs.csv(build / "final-serving-audit.csv")
    food_categories = inputs.csv(build / "final-food-category-audit.csv")
    product_categories = inputs.csv(build / "final-product-category-audit.csv")
    menu_categories = inputs.csv(build / "final-menu-category-summary.csv")
    menu_rows = inputs.csv(build / "final-franchise-menu-category-audit.csv")
    raw_quality = inputs.json(build / "final-food-quality.json")
    retail_source = inputs.json(Path("data-source/catalog-retail/summary.json"))
    source_validation = inputs.json(Path("data-source/catalog-retail/validation.json"))
    products = [r for r in foods if r["sourceType"] in ("K-FIND-PRODUCT", "OFFICIAL-RETAIL-PRODUCT")]
    before_products = [r for r in before_foods if r["sourceType"] in ("K-FIND-PRODUCT", "OFFICIAL-RETAIL-PRODUCT")]
    ice = [r for r in products if r["category"] == "빙과류"]
    ice_ids = {r["id"] for r in ice}
    runtime_ice = [r for r in serving_rows if r["foodItemId"] in ice_ids]
    retail = dict(before=len(before_products), after=len(products), additional=len(products)-len(before_products),
        beforeEvidence=evidence_counts({r["id"] for r in before_products}, before_meta),
        afterEvidence=evidence_counts({r["id"] for r in products}, meta),
        familiesBefore=retail_source["beforeRetailFamilies"], familiesAfter=retail_source["afterRetailFamilies"],
        additionalSourceTypes=retail_source["additionalSourceTypes"], sourceRowsScanned=retail_source["sourceRowsScanned"],
        excludedSourceRows=retail_source["excludedSourceRows"], exclusionReasons=retail_source["exclusionReasons"],
        distributionGroups=retail_source["distributionGroups"], officialNutritionUnresolved=retail_source["officialNutritionUnresolved"],
        ice=dict(before=sum(r["category"] == "빙과류" for r in before_products), after=len(ice),
            additionalForms=retail_source["additionalIceCreamForms"],
            metadataHouseholdUnits=dict(sorted(Counter(meta[r["id"]].get("householdUnit") or "UNKNOWN" for r in ice).items())),
            runtimeDefaultUnits=dict(sorted(Counter(r["servingUnit"] or "UNRESOLVED" for r in runtime_ice).items())),
            sourceStatus= "Mixed historical public snapshot and current official catalog; availability UNKNOWN remains unknown"),
        sourceValidation=source_validation)

    validation_path = Path("data-source/catalog-qa/validation.json")
    validation = inputs.json(validation_path) if (inputs.root / validation_path).exists() else dict(
        status="PENDING", build="PENDING_ROOT_CERTIFICATION", samsung="PENDING_ROOT_CERTIFICATION",
        note="Root owns actual build/device and read-only user-data certification.")
    report = dict(checkedAt=baseline["checkedAt"], parserVersion=VERSION, currentManifest=manifest,
        baselineFile="data-source/catalog-qa/baseline.json", baselineFoods=31849,
        before=dict(foods=before_metrics, franchise=before_franchise, recommendation=before_rec, runtimeServing=before_serving,
            readOnlyFoodQuality=baseline["readOnlyFoodQualityAudit"]),
        after=dict(foods=metrics, retail=retail, franchise=franchise, recommendation=recommendation,
            runtimeServing=serving, readOnlyFoodQuality=raw_quality,
            runtimeCategories=dict(foodRowsByCategory=dict(sorted(Counter(r["category"] for r in food_categories).items())),
                productRows=len(product_categories), menuSummary=menu_categories,
                menuRows=len(menu_rows), categorySuspicion=dict(sorted(Counter(r["suspicion"] or "NONE" for r in menu_rows).items())))),
        identityChanges=dict(preservedFoods=len(before_ids & ids), addedFoods=len(ids-before_ids), removedFoods=sorted(before_ids-ids),
            removedBrands=sorted({r["name"] for r in before_catalog}-{r["name"] for r in catalog})),
        requiredSixBrands={brand: next(r for r in franchise["perBrand"] if r["brand"] == brand) for brand in REQUIRED_BRANDS},
        inputConsistency=dict(runtimeServingFoodCountMatchesAssets=serving["foods"] == len(foods),
            runtimeServingRowsMatchAssets=len(serving_rows) == len(foods),
            productCategoryRowsMatchAssets=len(product_categories) == len(products),
            readOnlyQualityFoodCountMatchesAssets=raw_quality["totalFoods"] == len(foods),
            baselineServingMatchesCapturedFoods=before_serving["foods"] == len(before_foods),
            manifestFoodCountMatchesAssets=int(manifest["totalFoodCount"]) == len(foods),
            manifestMetadataCountMatchesAssets=int(manifest["metadataCount"]) == len(meta)),
        sourcePolicy="Exact IDs and current generated application menu registry. Full labels, partial descriptions, public recipe composition, positive allergens and cross-contact are separate. Source date, capture date and retail availability are separate; unknown is never safe or a guessed zero.",
        servingCountPolicy="runtimeServing executes FoodAmountPolicy and is authoritative; readOnlyFoodQuality officialServing is source-string/shape evidence and may differ.",
        validation=validation)
    report["inputHashes"] = dict(sorted(inputs.hashes.items()))
    output = args.output if args.output.is_absolute() else inputs.root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(output=str(output), foods=metrics["total"], products=retail["after"], brands=franchise["brands"],
        menus=franchise["menus"], recommendation=recommendation, validation=report["validation"], inputConsistency=report["inputConsistency"]), ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
