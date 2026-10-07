"""Focused integrity tests for official franchise sources and emitted app data."""
import csv
import hashlib
import json
import math
import unittest
import tempfile
import shutil
from pathlib import Path

from generate_franchise_brand_audit import SERVING, normalize, parse_catalog
import import_franchise_expansion as importer

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "data-source/franchise"
ASSETS = ROOT / "app/src/main/assets/fooddata"


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


class FranchiseExpansionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.menus = rows(SOURCES / "official-menu-snapshot.csv") + rows(SOURCES / "additional-menu-snapshot.csv")
        cls.salady = rows(SOURCES / "salady-nutrition-2026-09.csv")
        cls.legacy = rows(SOURCES / "sinjeon-nutrition-2018-11.csv")
        cls.nutrition = cls.salady + cls.legacy
        cls.bundled = {row["id"]: row for row in rows(ASSETS / "franchise_official_items.csv")}

    def test_menu_identity_and_provenance_are_complete_without_fake_values(self):
        self.assertEqual(563, len(self.menus))
        self.assertEqual(len(self.menus), len({row["id"] for row in self.menus}))
        self.assertEqual(len(self.menus), len({(row["brand"], normalize(row["name"])) for row in self.menus}))
        for row in self.menus:
            self.assertTrue(row["sourceFoodCode"] and row["sourceUrl"].startswith("https://"))
            self.assertIn(row["verifiedAt"], {"2026-10-02", "2026-10-04"})
            self.assertEqual("CURRENT_MENU_LISTED", row["saleState"])
            self.assertEqual(["", "", ""], [row[k] for k in ("energyKcal", "servingAmount", "servingUnit")])
            self.assertNotIn(row["id"], self.bundled)

    def test_bundled_values_are_exact_copies_of_reviewed_original_nutrition(self):
        self.assertEqual(98, len(self.nutrition))
        for source in self.nutrition:
            bundled = self.bundled[source["id"]]
            for key in ("name", "brand", "referenceAmount", "unit", "energyKcal", "carbohydrateGrams",
                        "proteinGrams", "fatGrams", "sodiumMilligrams"):
                self.assertEqual(source[key], bundled[key], (source["id"], key))
            self.assertEqual("OFFICIAL-BRAND-NUTRITION", bundled["sourceType"])
            self.assertIn(source["sourceDate"], bundled["servingDescription"])
            self.assertIn(source["sourceUrl"], bundled["servingDescription"])
            self.assertGreater(float(bundled["referenceAmount"]), 0)
            for key in ("energyKcal", "carbohydrateGrams", "proteinGrams", "fatGrams"):
                self.assertTrue(math.isfinite(float(bundled[key])) and float(bundled[key]) >= 0)

    def test_source_revision_and_household_serving_are_not_conflated(self):
        self.assertEqual(96, len(self.salady))
        for row in self.salady:
            self.assertEqual("2026-09", row["sourceDate"])
            self.assertEqual("g", row["unit"])
            self.assertIsNotNone(SERVING.search(self.bundled[row["id"]]["servingDescription"]))
        for row in self.legacy:
            self.assertEqual("2018-11-16", row["sourceDate"])
            self.assertEqual("LEGACY_OFFICIAL_NUTRITION", row["saleState"])
            self.assertIsNone(SERVING.search(self.bundled[row["id"]]["servingDescription"]))
        mild = self.bundled["official-sinjeon-sgs-20181116-1"]
        self.assertEqual("0.461", mild["fatGrams"])  # Total fat, not 0.152g saturated fat.

    def test_salady_pdf_overflow_label_keeps_original_300g_serving(self):
        row = next(row for row in self.salady if row["name"] == "[프로틴]선데이 아보카도 치킨 샌드위치")
        self.assertEqual("샐러디&샌드위치", row["brand"])
        self.assertEqual("300", row["referenceAmount"])
        self.assertEqual("625.3", row["energyKcal"])
        self.assertEqual("5", row["sourcePage"])

    def test_audit_totals_reconcile_and_new_brands_are_not_empty(self):
        audit = rows(SOURCES / "brand-audit.csv")
        summary = json.loads((SOURCES / "expansion-audit-summary.json").read_text(encoding="utf-8"))
        self.assertEqual(63, summary["baseline"]["brandCount"])
        self.assertEqual(27, summary["baseline"]["zeroMenuBrandCount"])
        self.assertEqual(63, len(audit))
        self.assertEqual(1, sum(int(row["totalMenuCount"]) == 0 for row in audit))
        for field in ("totalMenuCount", "kcalMenuCount", "verifiedServingMenuCount", "nutritionMissingMenuCount"):
            self.assertEqual(summary["final"][field], sum(int(row[field]) for row in audit))
        self.assertEqual(3979, summary["final"]["totalMenuCount"])
        self.assertEqual(2702, summary["final"]["kcalMenuCount"])
        self.assertEqual(111, summary["final"]["verifiedServingMenuCount"])
        catalog = parse_catalog((ROOT / "app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt").read_text(encoding="utf-8-sig"))
        self.assertEqual({row["name"] for row in catalog}, {row["officialBrandName"] for row in audit})
        for name in ("본죽&비빔밥", "본설렁탕", "본우리반상", "멘지", "본흑염소·능이삼계탕", "샐러디&샌드위치"):
            self.assertGreater(int(next(row for row in audit if row["officialBrandName"] == name)["totalMenuCount"]), 0)

    def test_source_html_hashes_match_when_local_capture_files_are_available(self):
        source = json.loads((SOURCES / "additional-menu-sources.json").read_text(encoding="utf-8"))
        self.assertFalse(source["sinjeonReports"]["currentRecipeUnchangedVerified"])
        for number, report in enumerate(source["sinjeonReports"]["reports"], 1):
            path = ROOT / f"app/build/sinjeon-report-{number}.jpg"
            self.assertEqual(importer.SGS_REPORT_HASHES[number], report["sha256"])
            if path.exists():
                self.assertEqual(report["sha256"], hashlib.sha256(path.read_bytes()).hexdigest().upper())
        for page in source["pages"]:
            namespace = {"김가네": "gimgane", "고봉민김밥인": "kobongmin", "신전떡볶이": "sinjeon", "두찜": "twozzim"}[page["brand"]]
            if namespace == "gimgane":
                suffix = "menu" if "sca=2" in page["sourceUrl"] else page["sourceUrl"].split("sca=")[-1]
            elif namespace == "kobongmin":
                number = int(page["sourceUrl"].split("/")[-1].split(".")[0])
                suffix = "menu" if number == 2 else str(number)
            elif namespace == "sinjeon":
                number = int(page["sourceUrl"].split("menu0")[-1].split(".")[0])
                suffix = "nutrition" if number == 3 else str(number)
            else:
                suffix = "detail"
            path = ROOT / f"app/build/{namespace}-{suffix}.html"
            if path.exists():
                self.assertEqual(page["sha256"], hashlib.sha256(path.read_bytes()).hexdigest().upper())

    def test_offline_generation_preserves_other_brands_and_is_reproducible(self):
        relative_csv = Path("app/src/main/assets/fooddata/franchise_official_items.csv")
        relative_kotlin = Path("app/src/main/java/com/example/healthcare/domain/OfficialFranchiseMenus.kt")
        with tempfile.TemporaryDirectory() as temporary:
            fixture = Path(temporary)
            (fixture / relative_csv).parent.mkdir(parents=True)
            (fixture / relative_kotlin).parent.mkdir(parents=True)
            shutil.copyfile(ROOT / relative_csv, fixture / relative_csv)
            previous_root = importer.ROOT
            try:
                importer.ROOT = fixture
                importer.generate_menu_kotlin(self.menus + rows(SOURCES / "quality-menu-snapshot.csv"))
                importer.generate_nutrition(self.nutrition + rows(SOURCES / "quality-nutrition.csv"))
                self.assertEqual((ROOT / relative_csv).read_bytes(), (fixture / relative_csv).read_bytes())
                self.assertEqual((ROOT / relative_kotlin).read_bytes(), (fixture / relative_kotlin).read_bytes())
            finally:
                importer.ROOT = previous_root

    def test_generic_kimbap_remains_unresolved_after_identity_specific_original_research(self):
        research = json.loads((SOURCES / "unresolved-kimbap-research.json").read_text(encoding="utf-8"))
        self.assertEqual([19617, 316734], [row["rowsScanned"] for row in research["originalSources"]])
        self.assertEqual([160, 166], [row["allColumnsScanned"] for row in research["originalSources"]])
        self.assertEqual(0, research["addedGenericServingConversions"])
        self.assertIsNone(research["decisions"][0]["verifiedServing"])
        original = research["originalSources"][0]["matches"]
        self.assertEqual(3, len(original))
        bundled = {row["sourceFoodCode"]: row for row in rows(ASSETS / "food_items.csv")}
        for row in original:
            fields = row["fields"]
            self.assertEqual("", fields["1인(회)분량 참고량"])
            self.assertEqual("100ml", fields["영양성분함량기준량"])
            self.assertEqual("400ml", fields["식품중량"])
            self.assertEqual("ml", bundled[fields["식품코드"]]["unit"])
            self.assertEqual(fields["에너지(kcal)"], bundled[fields["식품코드"]]["energyKcal"])


if __name__ == "__main__":
    unittest.main()
