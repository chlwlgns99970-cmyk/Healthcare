"""Targeted retail evidence/identity/search checks; no Android or full test suite."""
from __future__ import annotations
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path
from import_catalog_retail import ROOT, OUT, BASELINE, FOOD_HEADERS, normalize, amount, SOURCE_HASH, SOURCE, distribution_group, ice_form


def read(path):
    with path.open(encoding="utf-8-sig", newline="") as stream: return list(csv.DictReader(stream))


def main():
    additions = read(OUT / "product-items.csv")
    before = read(BASELINE / "product_items.csv")
    evidence = {r["foodItemId"]: r for r in read(OUT / "evidence.csv")}
    scoped_raw = read(OUT / "raw-identity-fields.csv")
    excluded = read(OUT / "excluded-scope.csv")
    scope = json.loads((OUT / "scope.json").read_text(encoding="utf-8"))
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_HASH
    baseline_codes = {r["sourceFoodCode"] for r in before}
    adopted_codes = {r["sourceFoodCode"] for r in additions if r["sourceType"] == "K-FIND-PRODUCT"}
    excluded_codes = {r["sourceFoodCode"] for r in excluded}
    assert baseline_codes.isdisjoint(adopted_codes) and adopted_codes.isdisjoint(excluded_codes) and baseline_codes.isdisjoint(excluded_codes)
    assert len(baseline_codes | adopted_codes | excluded_codes) == 316734
    assert len(adopted_codes) == len(scoped_raw)
    assert all(distribution_group(row, scope) == (evidence[row["foodItemId"]]["distributionGroup"], "") for row in scoped_raw)
    assert len(additions) == len({r["id"] for r in additions})
    assert {r["id"] for r in additions}.isdisjoint({r["id"] for r in before})
    assert len(additions) > 20000
    assert all(set(r) == set(FOOD_HEADERS) for r in additions)
    assert all(r["energyKcal"] != "" and float(r["energyKcal"]) >= 0 for r in additions)
    assert all(r["sourceType"] in {"K-FIND-PRODUCT", "OFFICIAL-RETAIL-PRODUCT"} for r in additions)
    assert additions == sorted(additions, key=lambda row: row["id"])
    assert all(r["sourceUrl"] and r["sourceHash"] and r["checkedAt"] and r["parserVersion"] for r in evidence.values())
    assert all(r["ingredientStatus"] in {"UNKNOWN", "COMPLETE_DECLARATION"} for r in evidence.values())
    assert all(r["allergenStatus"] in {"UNKNOWN", "CONFIRMED_LABEL"} for r in evidence.values())
    unknown = [r for r in evidence.values() if r["allergenStatus"] == "UNKNOWN"]
    assert all(not r["allergens"] and not r["allergenText"] for r in unknown)
    # Blank macro fields are retained; a source-provided numeric zero is valid.
    assert any(any(r[k] == "" for k in ("carbohydrateGrams", "proteinGrams", "fatGrams")) for r in additions)
    assert all(r["servingEvidenceKind"] == "OFFICIAL_SERVING" and float(r["basisAmountPerUnit"]) > 0
               and r["basisUnit"] in {"g", "ml"} and r["servingSourceReference"].startswith("https://")
               for r in evidence.values() if r["householdUnit"])
    forms = Counter(r["iceCreamForm"] for r in evidence.values() if r["retailFamily"] == "아이스크림")
    assert all(forms[form] > 0 for form in ("bar", "cone", "cup", "sand", "monaka", "tube", "pint", "tub"))
    assert ice_form("붕어싸만코 팝콘") == "unknown"
    assert evidence["official-retail-binggrae-pack-963"]["householdUnit"] == "개"
    assert evidence["kfind-product-p102-101010100-0029"]["householdUnit"] == "콘"
    assert ice_form("슈퍼콘") == "cone"
    chap = next(r for r in additions if r["sourceFoodCode"] == "NONGSHIM-P0000DYW-SINGLE-140G")
    assert {normalize(q) for q in ("짜파게티", "농심 짜파게티", "농심_짜파게티", "올리브 짜파게티")} <= set(chap["aliases"].split("|")) | {chap["normalizedName"]}
    assert [chap[k] for k in ("referenceAmount", "unit", "energyKcal", "carbohydrateGrams", "proteinGrams", "fatGrams")] == ["140", "g", "610", "96", "9", "20"]
    ev = evidence[chap["id"]]
    assert ev["householdUnit"] == "봉" and ev["basisAmountPerUnit"] == "140"
    assert ev["ingredientStatus"] == "COMPLETE_DECLARATION" and ev["allergenStatus"] == "CONFIRMED_LABEL"
    assert ev["completeIngredientText"] == ev["ingredientText"] and len(ev["completeIngredientText"]) > 350
    assert "밀" in ev["allergens"] and "땅콩" in ev["mayContainAllergens"]
    assert "같은 시설" in ev["crossContactText"] and "땅콩" not in ev["allergens"]
    source_checks = json.loads((OUT / "source-checks.json").read_text(encoding="utf-8"))
    for source in source_checks["checks"]:
        if source["status"] == "OK":
            assert hashlib.sha256((ROOT / source["path"]).read_bytes()).hexdigest() == source["sourceHash"]
    assert source_checks["binggraeFamilies"] == 45 and source_checks["binggraePackageVariants"] == 153
    assert "BINGGRAE-PACK-888" not in {row["sourceFoodCode"] for row in additions}
    assert any(row["sourceFoodCode"] == "BINGGRAE-PACK-888" and row["reason"] == "OFFICIAL_PAGE_SCRIPT_AND_API_NUTRITION_BASIS_CONFLICT" for row in read(OUT / "unresolved.csv"))
    families = {
        "라면": ("짜파게티", "신라면", "진라면", "불닭볶음면", "너구리", "팔도비빔면"),
        "우동·면제품": ("생생우동", "메밀소바"),
        "우유": ("바나나맛우유", "서울우유", "매일우유"),
        "두유": ("베지밀", "삼육두유"),
        "탄산·음료": ("코카콜라", "칠성사이다", "포카리스웨트", "토레타", "제주삼다수"),
        "과자": ("새우깡", "포카칩", "꼬깔콘", "초코파이", "빼빼로"),
        "초콜릿": ("가나", "크런키", "킷캣"),
        "시리얼": ("콘푸로스트", "첵스초코", "그래놀라"),
        "즉석밥": ("햇반", "오뚜기밥"),
        "죽·국": ("비비고단호박죽", "양반전복죽", "양반죽", "비비고육개장"),
        "냉동제품": ("고향만두", "비비고왕교자", "사옹원"),
        "햄·통조림": ("스팸", "리챔", "동원참치"),
        "요거트·치즈": ("요플레", "서울우유체다"),
        "커피": ("레쓰비", "TOP", "바리스타룰스"),
        "소스": ("케찹", "마요네즈", "드레싱"),
        "아이스크림": ("메로나", "투게더", "부라보", "빵또아", "붕어싸만코", "설레임", "뽕따", "월드콘", "구구크러스터")}
    all_rows = before + additions
    addition_ids = {r["id"] for r in additions}
    smoke, missing = [], []
    for family, queries in families.items():
        for query in queries:
            q = normalize(query)
            matches = [r for r in all_rows if q in r["normalizedName"] or q in r["aliases"]]
            if not matches:
                missing.append({"family": family, "query": query, "reason": "NO_MATCH_IN_OFFICIAL_SOURCE_SELECTION"})
                continue
            matches.sort(key=lambda r: (r["normalizedName"] != q, r["sourceType"] != "OFFICIAL-RETAIL-PRODUCT", len(r["name"]), r["id"]))
            target = matches[0]
            query_variants = [query, " ".join(query), query.replace(" ", "_"), target["name"]]
            # Maker-prefixed query is covered by exact source aliases, not a
            # claim that a production plant is a consumer-facing brand.
            if target["id"] in addition_ids: query_variants.append(target["brand"] + " " + target["name"])
            for variant in dict.fromkeys(query_variants):
                normalized = normalize(variant)
                assert normalized in target["normalizedName"] or normalized in target["aliases"], (variant, target["id"])
                smoke.append({"family": family, "query": variant, "normalizedQuery": normalized, "expectedFoodItemId": target["id"], "expectedName": target["name"], "sourceType": target["sourceType"], "productCount": len(matches)})
    # Missing representative spelling is explicitly reported; category coverage
    # still requires at least two distinct products, avoiding a one-item fix.
    count = Counter(r["family"] for r in smoke)
    assert all(count[family] >= 2 for family in families), count
    assert all(len({row["expectedFoodItemId"] for row in smoke if row["family"] == family}) >= 2 for family in families)
    with (OUT / "search-smoke.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=tuple(smoke[0]), lineterminator="\n"); writer.writeheader(); writer.writerows(smoke)
    summary = {"status": "PASS", "additionalProducts": len(additions), "baselineIdsPreserved": len(before), "sourceRowsFullyAccountedFor": 316734, "scopedRowsVerified": len(scoped_raw), "officialResponseHashesVerified": sum(row["status"] == "OK" for row in source_checks["checks"]), "smokeQueries": len(smoke), "smokeFamilies": dict(count), "distinctSmokeProductsPerFamily": {family: len({row["expectedFoodItemId"] for row in smoke if row["family"] == family}) for family in families}, "missingRepresentativeSpellings": missing, "iceCreamForms": dict(forms), "chapagetti": {k: chap[k] for k in ("id", "name", "referenceAmount", "unit", "energyKcal", "carbohydrateGrams", "proteinGrams", "fatGrams")}, "testScope": "Python exact-ID source, metadata, alias recall, all eight ice forms; Android UI/ranking and Samsung are not certified by this check."}
    (OUT / "validation.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
