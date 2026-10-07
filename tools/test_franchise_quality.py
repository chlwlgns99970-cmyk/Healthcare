"""Focused checks for the newly reviewed food-quality franchise expansion."""
import hashlib
import json
import math
import tempfile
import unittest
from pathlib import Path

import import_franchise_expansion as expansion
import import_franchise_quality as quality
from generate_franchise_brand_audit import SERVING, normalize


class FranchiseQualityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = quality.SOURCE
        cls.menus = expansion.read_csv(cls.source / 'quality-menu-snapshot.csv')
        cls.nutrients = expansion.read_csv(cls.source / 'quality-nutrition.csv')
        cls.metadata = expansion.read_csv(cls.source / 'food-metadata-evidence.csv')
        cls.bundled = {row['id']: row for row in expansion.read_csv(quality.ROOT / 'app/src/main/assets/fooddata/franchise_official_items.csv')}

    def test_all_27_originally_empty_brands_have_specific_source_results(self):
        reviews = json.loads((self.source / 'zero-brand-investigation.json').read_text(encoding='utf-8'))
        baseline = json.loads((self.source / 'quality-baseline.json').read_text(encoding='utf-8'))
        self.assertEqual(set(baseline['zeroMenuBrands']), {r['brand'] for r in reviews})
        self.assertEqual(27, len(reviews))
        unresolved = [r for r in reviews if r['result'] == 'UNRESOLVED']
        self.assertEqual(['놀부부대찌개'], [r['brand'] for r in unresolved])
        for row in reviews:
            self.assertTrue(row['reviewedSources'] and row['reason'])
        self.assertTrue(all(r['error'] for r in unresolved[0]['reviewedSources']))

    def test_all_578_original_menu_ids_keep_explicit_unknown_nutrition(self):
        audit = json.loads((self.source / 'menu-information-audit.json').read_text(encoding='utf-8'))
        self.assertEqual(578, len(audit))
        self.assertEqual(578, len({row['menuId'] for row in audit}))
        self.assertEqual(15, len(expansion.read_csv(self.source / 'legacy-menu-snapshot.csv')))
        ids = {r['foodItemId'] for r in self.metadata}
        for row in audit:
            self.assertIn(row['menuId'], ids)
            self.assertTrue(row['sourceUrl'] and row['checkedAt'] and row['reason'])
            self.assertFalse(row['nutritionFieldsProvided'])
            self.assertFalse(row['servingFieldsProvided'])
            self.assertEqual('UNRESOLVED', row['nutritionResolution'])

    def test_new_menu_only_rows_do_not_invent_calories_or_portions(self):
        self.assertEqual(694, len(self.menus))
        for row in self.menus:
            self.assertEqual(['', '', ''], [row[key] for key in ('energyKcal', 'servingAmount', 'servingUnit')])
            self.assertNotIn(row['id'], self.bundled)
            self.assertTrue(row['sourceFoodCode'] and row['sourceUrl'] and row['verifiedAt'])
        self.assertEqual(len(self.menus), len({(r['brand'], normalize(r['name'])) for r in self.menus}))

    def test_nutrition_basis_and_partial_macros_are_copied_without_serving_claims(self):
        self.assertEqual(169, len(self.nutrients))
        for source in self.nutrients:
            item = self.bundled[source['id']]
            for key in ('brand', 'name', 'referenceAmount', 'unit', 'energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams', 'sodiumMilligrams'):
                self.assertEqual(source[key], item[key], (source['id'], key))
            self.assertEqual('g', item['unit'])
            self.assertGreater(float(item['referenceAmount']), 0)
            self.assertIsNone(SERVING.search(item['servingDescription']))
            for key in ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams'):
                if item[key]:
                    self.assertTrue(math.isfinite(float(item[key])) and float(item[key]) >= 0)
            if source['brand'] in ('써브웨이', '에그드랍'):
                self.assertEqual('', item['carbohydrateGrams'])
                self.assertEqual('', item['fatGrams'])

    def test_source_errors_are_audited_and_excluded(self):
        excluded = json.loads((self.source / 'quality-nutrition-exclusions.json').read_text(encoding='utf-8'))
        self.assertEqual(12, sum(row['reason'] == 'OFFICIAL_ENERGY_MACRO_CONTRADICTION' for row in excluded))
        self.assertEqual(15, sum(row['reason'] == 'WEIGHT_UNIT_NOT_DECLARED' for row in excluded))
        usable = {(row['brand'], row['name']) for row in self.nutrients}
        for row in excluded:
            self.assertNotIn((row['brand'], row['name']), usable)
            self.assertTrue(row['sourceUrl'])

    def test_direct_labels_and_cross_contact_are_distinct_with_partial_lunchboxes(self):
        bon = [r for r in self.metadata if r['foodItemId'].startswith('official-bon-')]
        self.assertEqual(371, len(bon))
        partial = [r for r in bon if r['brand'] == '본도시락' and r['allergenText']]
        self.assertTrue(partial)
        self.assertTrue(all(r['allergenStatus'] == 'PARTIAL_DECLARATION' for r in partial))
        self.assertTrue(any(r['mayContainAllergens'] and r['allergens'] != r['mayContainAllergens'] for r in self.metadata))
        for row in self.metadata:
            if row['allergenStatus'] == 'CONFIRMED_LABEL':
                self.assertTrue(row['allergenText'] and row['sourceHash'])
            if row['ingredients']:
                self.assertTrue(row['ingredientText'])
                self.assertEqual('PARTIAL_DESCRIPTION', row['ingredientStatus'])
        self.assertEqual('', quality.allergens('참깨, 메밀, 밀크티향'))
        self.assertEqual('게|달걀|밀', quality.allergens('게, 난류(계란), 밀'))

    def test_final_totals_use_this_task_baseline_and_reconcile(self):
        summary = json.loads((self.source / 'expansion-audit-summary.json').read_text(encoding='utf-8'))
        self.assertEqual(3116, summary['baseline']['totalMenuCount'])
        self.assertEqual(27, summary['baseline']['zeroMenuBrandCount'])
        self.assertEqual(3979, summary['final']['totalMenuCount'])
        self.assertEqual(1, summary['final']['zeroMenuBrandCount'])
        self.assertEqual(2707, summary['final']['recordableMenuCount'])
        audit = expansion.read_csv(self.source / 'brand-audit.csv')
        for key in ('totalMenuCount', 'recordableMenuCount', 'nutritionMissingMenuCount', 'verifiedServingMenuCount'):
            self.assertEqual(summary['final'][key], sum(int(r[key]) for r in audit))

    def test_generated_menu_and_nutrition_are_reproducible(self):
        oldroot = expansion.ROOT
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            asset = root / 'app/src/main/assets/fooddata/franchise_official_items.csv'
            kotlin = root / 'app/src/main/java/com/example/healthcare/domain/OfficialFranchiseMenus.kt'
            asset.parent.mkdir(parents=True)
            kotlin.parent.mkdir(parents=True)
            expected_asset = (oldroot / asset.relative_to(root)).read_bytes()
            asset.write_bytes(expected_asset)
            expansion.ROOT = root
            try:
                menus = expansion.read_csv(self.source / 'official-menu-snapshot.csv') + expansion.read_csv(self.source / 'additional-menu-snapshot.csv') + self.menus
                nutrients = expansion.read_csv(self.source / 'salady-nutrition-2026-09.csv') + expansion.read_csv(self.source / 'sinjeon-nutrition-2018-11.csv') + self.nutrients
                expansion.generate_menu_kotlin(menus)
                expansion.generate_nutrition(nutrients)
                self.assertEqual((oldroot / kotlin.relative_to(root)).read_bytes(), kotlin.read_bytes())
                self.assertEqual(expected_asset, asset.read_bytes())
            finally:
                expansion.ROOT = oldroot


if __name__ == '__main__':
    unittest.main()
