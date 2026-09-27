"""Read-only regression checks for the complete K-FIND volume-basis audit."""

import csv
import hashlib
import unittest
from collections import defaultdict

from audit_kfind_serving_units import ROOT, audit, normalize_name


SOURCE = ROOT / "data-source/kfind/kfind-food-db-2026-08-28.xlsx"
ASSET = ROOT / "app/src/main/assets/fooddata/food_items.csv"


class ServingUnitAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
        cls.rows, cls.discrepancies, cls.source_issues, cls.summary = audit(SOURCE, ASSET)
        cls.after = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
        with ASSET.open(encoding="utf-8", newline="") as source:
            cls.assets = list(csv.DictReader(source))
        cls.by_code = {item["sourceFoodCode"]: item for item in cls.assets}
        cls.by_name = defaultdict(list)
        for item in cls.assets:
            cls.by_name[normalize_name(item["name"])].append(item)

    def test_original_and_all_rows_reconcile(self):
        self.assertEqual(self.before, self.after)
        self.assertEqual(19_617, self.summary["source_rows"])
        self.assertEqual(19_617, self.summary["asset_rows"])
        self.assertEqual([], self.discrepancies)
        self.assertEqual([], self.source_issues)

    def test_every_suspect_is_classified_and_blocked(self):
        self.assertEqual(2_382, len(self.rows))
        self.assertEqual(0, self.summary["unclassified"])
        self.assertEqual(2_382, self.summary["direct_record_disabled"])
        self.assertEqual(0, self.summary["only_rank_demoted"])
        for row in self.rows:
            with self.subTest(row=row["sourceFoodCode"]):
                self.assertIn(row["classification"], {
                    "MASS_ALTERNATIVE_AVAILABLE", "VOLUME_ONLY_UNRESOLVED",
                    "CATEGORY_REVIEW_REQUIRED",
                })
                self.assertEqual("false", row["directRecordEnabled"])
                self.assertEqual("true", row["searchRankDemoted"])
                self.assertIn("DIRECT_RECORD_DISABLED", row["reasonCodes"])
                self.assertEqual("ml", self.by_code[row["sourceFoodCode"]]["unit"])

    def test_every_mass_alternative_is_separate_matching_gram_row(self):
        mass_rows = [row for row in self.rows if row["classification"] == "MASS_ALTERNATIVE_AVAILABLE"]
        self.assertEqual(728, len(mass_rows))
        for row in mass_rows:
            for code in row["massAlternativeCodes"].split(";"):
                with self.subTest(source=row["sourceFoodCode"], alternative=code):
                    alternative = self.by_code[code]
                    self.assertEqual("g", alternative["unit"])
                    self.assertEqual(normalize_name(row["name"]), normalize_name(alternative["name"]))
                    self.assertNotEqual(row["sourceFoodCode"], code)

    def test_tuna_gimbap_source_value_is_preserved_and_gram_alternative_exists(self):
        suspect = next(row for row in self.rows if row["sourceFoodCode"] == "D401-007450000-0001")
        self.assertEqual("MASS_ALTERNATIVE_AVAILABLE", suspect["classification"])
        self.assertEqual("100ml", suspect["sourceReference"])
        self.assertEqual("128", suspect["sourceKcal"])
        self.assertIn("D101-007450000-0001", suspect["massAlternativeCodes"].split(";"))
        alternative = self.by_code["D101-007450000-0001"]
        self.assertEqual("g", alternative["unit"])
        self.assertEqual("174", alternative["energyKcal"])

    def test_liquid_category_milliliters_are_not_blanket_blocked(self):
        soup = next(item for item in self.assets if item["category"] == "찌개 및 전골류" and item["unit"] == "ml")
        self.assertNotIn(soup["sourceFoodCode"], {row["sourceFoodCode"] for row in self.rows})


if __name__ == "__main__":
    unittest.main()
