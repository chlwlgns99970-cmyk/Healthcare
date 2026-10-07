"""Targeted source integrity checks; no Gradle, DB migration or broad regressions."""
import contextlib
import hashlib
import io
import json
import unittest
from pathlib import Path
from collections import Counter
import import_delivery_chains as delivery
from import_franchise_expansion import read_csv
from generate_franchise_brand_audit import normalize, parse_catalog

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/"data-source/franchise"
BASELINE = ROOT/"app/build/catalog-qa/baseline"


class DeliveryChainExpansionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.menus = read_csv(SOURCE/"delivery-menu-snapshot.csv")
        cls.nutrition = read_csv(SOURCE/"delivery-nutrition.csv")
        cls.evidence = read_csv(SOURCE/"delivery-metadata-evidence.csv")
        cls.registry = json.loads((SOURCE/"delivery-chain-registry.json").read_text(encoding="utf-8"))
        cls.pages = {r["key"]:r for r in json.loads(delivery.RAW.read_text(encoding="utf-8"))}

    def test_mandatory_and_broad_brands_have_real_official_identities(self):
        counts = Counter(r["brand"] for r in self.menus+self.nutrition)
        self.assertEqual(26,len(self.registry))
        self.assertTrue({"피자스쿨","파파존스","청년피자","노랑통닭","지코바","처갓집양념치킨"} <= set(counts))
        for brand in self.registry:
            self.assertGreater(counts[brand["name"]],0)
            self.assertEqual(counts[brand["name"]],brand["menuCount"])
        audit = json.loads((SOURCE/"delivery-chain-candidate-audit.json").read_text(encoding="utf-8"))
        self.assertEqual(18,len(audit["industries"]))
        self.assertTrue(all(audit["industries"].values()))
        failures = [row for row in audit["candidates"] if row["discoveryStatus"]=="UNRESOLVED"]
        self.assertTrue(failures and all(row["unresolvedReason"] for row in failures))
        parked = next(row for row in failures if row["brand"]=="땅스부대찌개")
        self.assertIn("parked",parked["unresolvedReason"])

    def test_source_hashes_and_menu_only_unknowns_are_preserved(self):
        for page in self.pages.values():
            if "text" in page:
                self.assertEqual(page["textSha256"],hashlib.sha256(page["text"].encode()).hexdigest().upper())
        for row in self.menus:
            self.assertEqual(["","",""],[row[k] for k in ("energyKcal","servingAmount","servingUnit")])
            self.assertEqual(row["sourceHash"],self.pages[row["sourcePageKey"]]["originalSha256"])
            self.assertTrue(row["name"] and row["sourceUrl"].startswith(("http://","https://")))
            self.assertNotIn(row["name"],{"MENU","고기류","메뉴","사이드","피자","NEW","신선합니다","맛있습니다","건강합니다"})
        ids = [row["id"] for row in self.menus+self.nutrition]
        self.assertEqual(len(ids),len(set(ids)))
        identities = [(r["brand"],normalize(r["name"])) for r in self.menus+self.nutrition]
        self.assertEqual(len(identities),len(set(identities)))

    def test_per_menu_official_categories_do_not_inherit_brand_industry(self):
        papa = next(r for r in self.menus if r["brand"]=="파파존스" and normalize(r["name"])=="더블치즈버거")
        self.assertEqual("피자",papa["mappedCategory"])
        sides = [r for r in self.menus if r["brand"]=="노랑통닭" and "치즈볼" in r["name"]]
        self.assertTrue(sides)
        self.assertTrue(all(r["mappedCategory"]!="치킨" for r in sides))
        pasta = [r for r in self.menus if "스파게티" in r["name"]]
        self.assertTrue(pasta)
        self.assertTrue(all(r["mappedCategory"]=="파스타" for r in pasta))
        chicken_pizza = next(r for r in self.menus if r["brand"]=="피자마루" and r["name"]=="고추마요치킨")
        self.assertEqual("피자",chicken_pizza["mappedCategory"])
        tender = next(r for r in self.menus if r["brand"]=="피자마루" and r["name"]=="텐더 치킨")
        self.assertEqual("사이드",tender["sourceCategory"])
        self.assertNotEqual("피자",tender["mappedCategory"])

    def test_explicit_nutrition_basis_does_not_invent_missing_macros(self):
        for row in self.nutrition:
            self.assertGreater(float(row["referenceAmount"]),0)
            self.assertGreaterEqual(float(row["energyKcal"]),0)
            self.assertEqual("g",row["unit"])
            self.assertEqual("",row["carbohydrateGrams"])
            if row["brand"]!="지코바":
                self.assertEqual("",row["fatGrams"])
            self.assertTrue(row["sourceHash"] and row["menuIdentitySourceUrl"])
        gcova = [r for r in self.nutrition if r["brand"]=="지코바"]
        self.assertEqual(5,len(gcova))
        self.assertTrue(all(r["referenceAmount"]=="100" for r in gcova))
        self.assertEqual("8.04",next(r for r in gcova if r["name"]=="양념치킨")["fatGrams"])
        self.assertFalse(any(r["brand"] in ("빽다방","요아정") for r in self.nutrition))
        # These two zeros are literal official labels, never missing-value defaults.
        zeros = {r["name"] for r in self.nutrition if r["energyKcal"]=="0"}
        self.assertEqual({"샤인머스캣 그린티 제로","산베네데토 워터"},zeros)
        household = [r for r in self.evidence if r.get("householdUnit")]
        self.assertEqual(36,len(household))
        for row in household:
            self.assertIn(row["brand"],{"청년피자","피자스쿨"})
            self.assertEqual("조각",row["householdUnit"])
            self.assertTrue(row["servingSourceReference"].startswith(("https://","http://")))
            if row["brand"]=="청년피자":
                self.assertEqual("OFFICIAL_SERVING",row["servingEvidenceKind"])
                self.assertIn("1조각 = "+row["basisAmountPerUnit"]+"g",row["servingSourceSize"])
            else:
                self.assertEqual("VERIFIED_CONVERSION",row["servingEvidenceKind"])
                self.assertIn("명시 조각수로 나눈 1조각 "+row["basisAmountPerUnit"]+"g",row["servingSourceSize"])

    def test_positive_allergens_remain_partial_and_exactly_joined(self):
        ids = {r["id"] for r in self.menus+self.nutrition}
        self.assertEqual(ids,{r["foodItemId"] for r in self.evidence})
        identities = {r["id"]:r for r in self.menus+self.nutrition}
        for evidence in self.evidence:
            row = identities[evidence["foodItemId"]]
            expected = row.get("sourceFoodCode",row["id"].removeprefix("official-").upper())
            self.assertEqual(expected,evidence["sourceFoodCode"])
            self.assertEqual(row["name"],evidence["name"])
        shooting = next(r for r in self.evidence if r["name"]=="슈팅스타")
        self.assertEqual({"우유","대두","밀"},set(shooting["allergens"].split("|")))
        self.assertEqual("PARTIAL_INGREDIENT_EVIDENCE",shooting["allergenStatus"])
        self.assertFalse(any(r["ingredientStatus"]=="COMPLETE_DECLARATION" or r["allergenStatus"]=="CONFIRMED_LABEL" for r in self.evidence))
        self.assertEqual("",delivery.positive_allergens("참깨, 오트밀, 파인애플"))

    def test_original_registry_and_bon_65_sources_are_unchanged(self):
        relative = "app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt"
        before = parse_catalog((BASELINE/relative).read_text(encoding="utf-8"))
        after = {r["name"]:r for r in parse_catalog((ROOT/relative).read_text(encoding="utf-8"))}
        self.assertEqual(63,len(before))
        for row in before:
            self.assertEqual(row,after[row["name"]])
        relative = "data-source/franchise/official-menu-snapshot.csv"
        self.assertEqual((BASELINE/relative).read_bytes(),(ROOT/relative).read_bytes())
        self.assertEqual(65,sum(r["brand"]=="본죽" for r in read_csv(ROOT/relative)))

    def test_offline_outputs_are_deterministic(self):
        paths = [SOURCE/name for name in ("delivery-menu-snapshot.csv","delivery-nutrition.csv","delivery-metadata-evidence.csv","delivery-chain-candidate-audit.json","delivery-chain-registry.json")]+[delivery.CATALOG]
        before = {path:path.read_bytes() for path in paths}
        with contextlib.redirect_stdout(io.StringIO()):
            delivery.run()
        for path in paths:
            self.assertEqual(before[path],path.read_bytes(),path.name)


if __name__ == "__main__":
    unittest.main()
