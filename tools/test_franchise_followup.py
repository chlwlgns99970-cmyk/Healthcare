"""Focused checks for fields restored in the second food-quality task."""
import hashlib
import json
import unittest

import import_franchise_quality as quality
from import_franchise_expansion import read_csv
import research_franchise_followup as followup


class FranchiseFollowupTest(unittest.TestCase):
    def test_poke_cards_restore_five_exact_nutrition_identities_without_zero_or_guessed_macros(self):
        rows = [r for r in read_csv(followup.SOURCE / 'quality-nutrition.csv') if r['brand'] == '포케올데이']
        expected = {'닭가슴살 밸런스 박스': ('446','536','57','45','14'),
                    '훈제오리 밸런스 박스': ('446','684','58','38','33'),
                    '두부버섯 밸런스 박스': ('436','501','67','22','16'),
                    '닭다리살 밸런스 박스': ('446','603','62','36','23'),
                    '소고기 밸런스 박스': ('446','698','57','42','34')}
        self.assertEqual(set(expected), {r['name'] for r in rows})
        menu_ids = {r['id'] for r in read_csv(followup.SOURCE / 'quality-menu-snapshot.csv')}
        for row in rows:
            self.assertEqual(expected[row['name']], tuple(row[k] for k in ('referenceAmount','energyKcal','carbohydrateGrams','proteinGrams','fatGrams')))
            self.assertEqual('g', row['unit'])
            self.assertNotIn(row['id'], menu_ids)
            self.assertEqual('', row['sodiumMilligrams'])

    def test_existing_nutrition_source_values_are_preserved(self):
        previous = {r['id']:r for r in read_csv(followup.ROOT / 'app/build/food-quality-followup/franchise_official_items.csv')}
        current = {r['id']:r for r in read_csv(followup.ROOT / 'app/src/main/assets/fooddata/franchise_official_items.csv')}
        keys = ('name','brand','sourceFoodCode','referenceAmount','unit','energyKcal','carbohydrateGrams','proteinGrams','fatGrams')
        for identity, old in previous.items():
            self.assertIn(identity, current)
            for key in keys:
                self.assertEqual(old[key], current[identity][key], (identity,key))

    def test_current_missing_audit_uses_captured_identity_count_and_keeps_specific_failures(self):
        audit = json.loads((followup.SOURCE / 'followup-menu-information-audit.json').read_text(encoding='utf-8'))
        baseline = json.loads((followup.ROOT / 'data-source/food-quality/followup-baseline.json').read_text(encoding='utf-8'))
        self.assertEqual(baseline['franchiseMenu']['nutritionMissingMenuCount'], len(audit))
        self.assertEqual(len(audit), len({r['menuId'] for r in audit}))
        self.assertEqual(5, sum(r['nutritionResolution'].startswith('RESOLVED') for r in audit))
        self.assertTrue(all(r['sourceFoodCode'] and r['reason'] and r['reviewedSources'] for r in audit))
        self.assertTrue(any(r['reason'] == 'OFFICIAL_ENERGY_MACRO_CONTRADICTION' for r in audit))
        self.assertTrue(any(r['reason'] == 'WEIGHT_UNIT_NOT_DECLARED' for r in audit))
        self.assertTrue(all(r['officialNutrition'] is None for r in audit if r['nutritionResolution']=='UNRESOLVED'))

    def test_structured_menu_compounds_remain_partial_with_original_strings(self):
        foods, _ = followup.actual_identities()
        rows = followup.salady_evidence(foods)
        ingredients = [r for r in rows if r['ingredients']]
        self.assertEqual(80, len(ingredients))
        example = next(r for r in ingredients if r['brand']=='샐러디' and r['name']=='탄단지 샐러디')
        self.assertIn('시즈닝 닭가슴살', example['ingredients'].split('|'))
        self.assertIn('기본 드레싱: 오리엔탈', example['ingredientText'])
        self.assertTrue(all(r['ingredientStatus']=='PARTIAL_MENU_COMPONENTS' for r in ingredients))
        # No hidden ingredient in a compound sauce is synthesized.
        self.assertNotIn('대두', example['ingredients'].split('|'))

    def test_allergen_chart_omissions_and_conflicts_do_not_certify_safe(self):
        rows = read_csv(followup.SOURCE / 'food-metadata-followup.csv')
        known = [r for r in rows if r['allergens']]
        self.assertTrue(known)
        self.assertTrue(all(r['allergenStatus']=='PARTIAL_DECLARATION' for r in known))
        self.assertTrue(all(r['allergenText'] and r['sourceHash'] for r in known))
        conflict = next(r for r in rows if r['brand']=='홍익돈까스' and r['name']=='더블치즈돈까스')
        self.assertIn('CONFLICT', conflict['evidenceKind'])
        self.assertIn('조개류', conflict['allergens'].split('|'))
        udon = next(r for r in rows if r['brand']=='홍익돈까스' and r['name']=='홍익우동')
        self.assertIn('메밀 성분', udon['crossContactText'])
        self.assertNotIn('밀', udon['mayContainAllergens'].split('|'))
        self.assertEqual('달걀|밀', quality.allergens('알류, 밀'))
        self.assertEqual('', quality.allergens('메밀, 참깨, 밀크티향'))

    def test_offline_generation_and_reviewed_binary_integrity(self):
        output = followup.SOURCE / 'food-metadata-followup.csv'
        before = output.read_bytes()
        manifest = json.loads((followup.RAW / 'followup-binary-manifest.json').read_text(encoding='utf-8'))
        for source in manifest.values():
            if source.get('path'):
                raw = (followup.SOURCE / source['path']).read_bytes()
                self.assertEqual(source['sha256'], hashlib.sha256(raw).hexdigest().upper())
                self.assertEqual(source['byteCount'], len(raw))
        followup.generate()
        self.assertEqual(before, output.read_bytes())


if __name__ == '__main__':
    unittest.main()
