"""Reproducible brand/source audit. 100g nutrition basis is not a household portion."""
from __future__ import annotations
import argparse
import csv
import io
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = "app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt"
FOODS = "app/src/main/assets/fooddata/food_items.csv"
OFFICIAL = "app/src/main/assets/fooddata/franchise_official_items.csv"
SOURCES = "data-source/franchise"
SERVING = re.compile(r"(?:공식|검증된|확인된)\s*(?:(?:HOT|ICE)\s+)?(?:제공량\s*)?([0-9]+(?:\.[0-9]+)?)\s*(개|줄|봉|봉지|병|캔|팩|컵|잔|조각|인분|공기|장)\s*[=(（(]?\s*([0-9]+(?:\.[0-9]+)?)\s*(g|ml)", re.I)


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def git_text(root, path):
    return subprocess.run(["git", "show", f"HEAD:{path}"], cwd=root, check=True, capture_output=True).stdout.decode("utf-8-sig")


def parse_catalog(text):
    entries = []
    for match in re.finditer(r"\bFranchiseBrand\(", text):
        start = match.end()
        depth, quoted, escaped, end = 1, False, False, start
        for end in range(start, len(text)):
            character = text[end]
            if escaped:
                escaped = False
            elif character == "\\" and quoted:
                escaped = True
            elif character == '"':
                quoted = not quoted
            elif not quoted:
                depth += (character == "(") - (character == ")")
                if depth == 0:
                    break
        block = text[start:end]
        values = [json.loads(value) for value in re.findall(r'"(?:\\.|[^"\\])*"', block)]
        if len(values) < 3:
            continue
        name, category, url = values[:3]
        aliases = re.search(r"setOf\((.*?)\)", block, re.S)
        references = re.search(r"officialMenuNames\s*=\s*listOf\((.*?)\)", block, re.S)
        strings = lambda found: [json.loads(value) for value in re.findall(r'"(?:\\.|[^"\\])*"', found.group(1))] if found else []
        entries.append(dict(name=name, category=category, officialUrl=url,
            aliases=strings(aliases), references=strings(references), machineReadable=bool(re.search(r",\s*true\s*[,)]?", block))))
    if len({row["name"] for row in entries}) != len(entries):
        raise ValueError("Duplicate brand registration")
    return entries


def normalize(name):
    return re.sub(r"[^a-z0-9가-힣]", "", name.lower())


def audit(entries, foods, official, menus, nutrition_sources):
    metadata = {row["id"]: row for row in nutrition_sources}
    audited = []
    for brand in entries:
        name = brand["name"]
        kfind = [row for row in foods if row["sourceType"] == "K-FIND" and row["brand"] == name]
        nutrition = kfind + [row for row in official if row["brand"] == name]
        menu_rows = [row for row in menus if row["brand"] == name]
        references = {normalize(row["name"]): row["name"] for row in menu_rows}
        for reference in brand["references"]:
            references.setdefault(normalize(reference), reference)
        official_nutrition = [metadata[row["id"]] for row in nutrition if row["id"] in metadata]
        audited.append(dict(category=brand["category"], officialBrandName=name,
            aliases="|".join(brand["aliases"]), officialUrl=brand["officialUrl"],
            verifiedAt=max([row["verifiedAt"] for row in menu_rows + official_nutrition] or ["2026-09-27"]),
            nutritionSource=" + ".join(sorted({row["sourceType"] for row in nutrition})) or "OFFICIAL-BRAND-REGISTRY",
            totalMenuCount=len(nutrition) + len(references), recordableMenuCount=len(nutrition),
            uniqueCombinedMenuIdentityCount=len({normalize(row["name"]) for row in nutrition} | set(references)),
            knownNutritionMenuReferenceOverlapCount=len({normalize(row["name"]) for row in nutrition} & set(references)),
            kcalMenuCount=sum(row["energyKcal"] != "" for row in nutrition),
            macroCompleteMenuCount=sum(all(row[key] != "" for key in ("carbohydrateGrams", "proteinGrams", "fatGrams")) for row in nutrition),
            verifiedServingMenuCount=sum(bool(SERVING.search(row["servingDescription"])) for row in nutrition),
            nutritionBasisMenuCount=sum(bool(row["referenceAmount"] and row["unit"]) for row in nutrition),
            nutritionMissingMenuCount=len(references), officialMenuNameReferenceCount=len(references),
            officialMenuNameReferences="|".join(references.values()), currentMenuListedCount=len(menu_rows),
            currentDatedNutritionSnapshotCount=sum(row["saleState"] == "CURRENT_OFFICIAL_NUTRITION_SNAPSHOT" for row in official_nutrition),
            legacyOfficialNutritionCount=sum(row["saleState"] == "LEGACY_OFFICIAL_NUTRITION" for row in official_nutrition),
            sourceDateUnknownNutritionCount=len(nutrition) - len(official_nutrition),
            datedNutritionSources="|".join(sorted({row["sourceDate"] for row in official_nutrition})),
            officialNutritionSourceUrls="|".join(sorted({row["sourceUrl"] for row in official_nutrition})),
            officialSourceForm="official nutrition page/file or K-FIND snapshot" if brand["machineReadable"] else "official website/menu page",
            runtimeAutoUpdate="false", updateMode="snapshot-only",
            snapshotOnlyReason="Bundled reviewed snapshots; runtime does not fetch or overwrite menu/nutrition rows"))
    return audited


def summarize(rows):
    fields = ("totalMenuCount", "uniqueCombinedMenuIdentityCount", "knownNutritionMenuReferenceOverlapCount", "recordableMenuCount", "kcalMenuCount", "macroCompleteMenuCount",
              "verifiedServingMenuCount", "nutritionBasisMenuCount", "nutritionMissingMenuCount",
              "currentMenuListedCount", "currentDatedNutritionSnapshotCount", "legacyOfficialNutritionCount",
              "sourceDateUnknownNutritionCount")
    return dict(brandCount=len(rows), zeroMenuBrandCount=sum(row["totalMenuCount"] == 0 for row in rows),
        **{field: sum(row[field] for row in rows) for field in fields})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", type=Path, default=ROOT)
    root = parser.parse_args().project_root
    menus = read_csv(root / SOURCES / "official-menu-snapshot.csv") + read_csv(root / SOURCES / "additional-menu-snapshot.csv")
    sources = read_csv(root / SOURCES / "salady-nutrition-2026-09.csv") + read_csv(root / SOURCES / "sinjeon-nutrition-2018-11.csv")
    menus += read_csv(root / SOURCES / "quality-menu-snapshot.csv")
    sources += read_csv(root / SOURCES / "quality-nutrition.csv")
    foods = read_csv(root / FOODS)
    current = audit(parse_catalog((root / CATALOG).read_text(encoding="utf-8-sig")), foods, read_csv(root / OFFICIAL), menus, sources)
    baseline = json.loads((root / SOURCES / "quality-baseline.json").read_text(encoding="utf-8"))
    baseline = dict(baseline, totalMenuCount=baseline["sourceEntryCount"], recordableMenuCount=baseline["recordableCount"],
        kcalMenuCount=baseline["recordableCount"], macroCompleteMenuCount=baseline["macroCompleteCount"],
        verifiedServingMenuCount=baseline["verifiedLivingServingCount"])
    with (root / SOURCES / "brand-audit.csv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=list(current[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(current)
    summary = dict(baseline=baseline, final=summarize(current),
        provenance="Baseline is the captured start of this food-quality task (quality-baseline.json). Existing K-FIND/official nutrition rows are preserved; new reviewed exact official identities are appended. Dates distinguish capture/publication/legacy nutrition; sourceDateUnknown does not mean discontinued.",
        servingCountPolicy="Only explicit cited household quantity plus g/ml mass; 100g nutrition basis alone is not a serving.",
        zeroMenuBrands=[row["officialBrandName"] for row in current if row["totalMenuCount"] == 0])
    (root / SOURCES / "expansion-audit-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"baseline": summary["baseline"], "final": summary["final"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
