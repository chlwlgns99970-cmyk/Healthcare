"""Tests for this request's scope, new mapping, guards, and preservation."""
import collections
import hashlib
import unittest
from audit_recipe_residual_97 import ROOT, OUT, FINAL, ASSETS, load, rows
from recipe_composition_validation import composition_problem
from recipe_context_identity import supported_title_keys


class Residual97Test(unittest.TestCase):
    def test_01_all_97_have_unique_final_decisions_and_fresh_receipts(self):
        records = load(OUT / 'all-97-final-evidence-ledger.json')
        self.assertEqual(97, len(records))
        self.assertEqual(97, len({r['recipeId'] for r in records}))
        self.assertEqual({'A','B','C','D'}, {r['baselineClassification'] for r in records})
        for r in records:
            self.assertTrue(r['newExecutedSearchReceipts'])
            self.assertTrue(r['nextAction'])
            self.assertFalse(r['wholeInternetExhausted'])
            for name in r['newExecutedSearchReceipts']:
                receipt = load(OUT / name)
                self.assertIn(r['recipeId'], {f['recipeId'] for f in receipt['foods']})

    def test_02_frozen_419_assets_never_lost_or_rewritten(self):
        after = rows(ASSETS / 'official_recipe_reference_estimates.csv')
        bykey = {(r['foodId'],r['recipeId'],r['ingredientName']): r for r in after}
        for r in rows(OUT / 'baseline-official_recipe_reference_estimates.csv'):
            self.assertEqual(r, bykey[r['foodId'],r['recipeId'],r['ingredientName']])
        for name, expected in load(OUT / 'protected-sha256.json').items():
            self.assertEqual(expected, hashlib.sha256((ASSETS / name).read_bytes()).hexdigest())

    def test_03_every_new_mapping_has_whole_source_and_real_food(self):
        foods = {r['id']: r for r in rows(ASSETS / 'food_items.csv')}
        asset = rows(ASSETS / 'official_recipe_reference_estimates.csv')
        refs = {r['recipeId']: r for r in load(FINAL / 'validated-reference-compositions.json')}
        new = load(OUT / 'new-app-complete-foods.json')
        self.assertEqual({'RDA-91542','MFDS-317'}, {r['recipeId'] for r in new})
        for r in new:
            m = r['selectedMapping']; ref = refs[m['selectedId']]
            self.assertTrue(ref['complete'])
            self.assertEqual(64,len(m['originalSourceSha256']))
            for fid in m['foodIds']:
                self.assertFalse(foods[fid]['brand'])
                group = [a for a in asset if a['foodId']==fid and a['recipeId']==ref['recipeId']]
                self.assertTrue(group)
                self.assertTrue(all(a['recipeComplete']=='true' for a in group))
                self.assertAlmostEqual(sum(i['amountGrams'] for i in ref['inputs']),sum(float(a['amountGrams']) for a in group))

    def test_04_contradictory_titles_need_original_cooking_proof(self):
        self.assertEqual({'낙지볶음'},supported_title_keys('낙지볶음(낙지전골)','팬에 양념을 넣고 센 불에서 볶는다.'))
        self.assertFalse(supported_title_keys('낙지볶음(낙지전골)','육수를 넣고 끓인다.'))
        self.assertFalse(supported_title_keys('낙지볶음(낙지전골)','팬에 볶는다. 육수를 넣고 끓인다.'))
        self.assertFalse(supported_title_keys('곤달비밥(곤드레밥, 곤드레나물밥)','팬에 볶는다.'))
        self.assertFalse(supported_title_keys('갑오징어볶음(오징어볶음)','팬에 볶는다.'))

    def test_05_punctuation_cannot_hide_self_composite(self):
        ref = dict(name='딸기잼',compositionKind='REFERENCE_RECIPE',inputs=[dict(ingredient='딸기, 잼')])
        self.assertTrue(composition_problem(ref))
        qualified=dict(name='샐러드(감자)',compositionKind='REFERENCE_RECIPE',inputs=[dict(ingredient='감자 샐러드')])
        self.assertTrue(composition_problem(qualified))
        ref['compositionKind']='SURVEY_AVERAGE'
        self.assertIsNone(composition_problem(ref))
        actual = next(r for r in load(FINAL/'validated-reference-compositions.json') if r['recipeId']=='MENUZEN-D212044')
        self.assertFalse(actual['complete'])
        self.assertFalse(any(r['recipeId']=='MENUZEN-D212044' and r['recipeComplete']=='true'
                             for r in rows(ASSETS/'official_recipe_reference_estimates.csv')))

    def test_06_new_food_questions_have_complete_sources_and_no_fake_ids(self):
        questions=load(OUT/'question-required-new-food.json')
        self.assertEqual(6,len(questions))
        for r in questions:
            self.assertFalse(r['appComplete']);self.assertFalse(r['verifiedFoodIds'])
            self.assertTrue(r['originalComplete'] or r['verifiedCompleteReferenceIds'])

    def test_07_all_212_conflicts_recompared_without_global_measure(self):
        review=load(OUT/'current-212-conflict-review.json')
        self.assertEqual(212,len(review))
        self.assertEqual(212,len({(r['recipeId'],r['ingredientIndex']) for r in review}))
        for r in review:
            self.assertEqual('UNRESOLVED',r['finalStatus'])
            self.assertFalse(r['averageUsed']);self.assertIsNone(r['selectedNewConversion'])
            self.assertTrue(r['comparisons']);self.assertTrue(r['newSourcesReviewed'])
            for p in r['newSourceComparisons']:self.assertFalse(p['sameRecipe'])

    def test_08_new_candidate_sources_match_captured_bytes(self):
        for r in load(OUT/'new-primary-candidate-reviews.json'):
            self.assertEqual(r['sourceSha256'],hashlib.sha256((ROOT/r['rawFile']).read_bytes()).hexdigest())
            self.assertNotEqual('PUBLISH',r['decision'])

    def test_09_counts_originals_and_nutrition_are_independent(self):
        summary=load(OUT/'summary.json')
        self.assertEqual(421,summary['afterAppComplete'])
        self.assertEqual(95,summary['remaining'])
        self.assertEqual(97,sum(summary['finalScopeDispositions'].values()))
        self.assertEqual(load(OUT/'baseline-ingredient-progress.json'),summary['originalIngredientProgress'])
        states=load(FINAL/'recipe-final-states.json')
        self.assertEqual(421,sum(s['appCompleteAvailable'] for s in states))
        self.assertEqual(6,sum((s['originalComplete'] or s['referenceComplete']) and not s['appCompleteAvailable'] for s in states))


if __name__=='__main__':
    unittest.main(verbosity=2)
