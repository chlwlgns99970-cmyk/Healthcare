"""Twelve targeted data checks for the new official-source audit."""
import collections
import csv
import hashlib
import math
import unittest
from index_recipe_amount_references import OUT, ROOT, load
from audit_recipe_book_portions import quantity


class AmountPriorityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load(OUT/'baseline-ingredient-decisions.json')
        cls.after=load(OUT/'ingredient-decisions.json')
        cls.alternates=load(OUT/'alternate-official-recipe-amounts.json')
        cls.survey=load(OUT/'survey-average-reference-audit.json')

    def test_01_alternate_amount_not_original(self):
        self.assertEqual(23,sum(len(r['inputs']) for r in self.alternates))
        self.assertTrue(all(not r['originalAmountOverridden'] for r in self.alternates))
        self.assertEqual(563,len(load(OUT/'amount-survey-reference-attempts.json')))

    def test_02_pdf_and_xlsx_amount_parser(self):
        self.assertAlmostEqual(1.5,quantity('1½'))
        self.assertAlmostEqual(1/3,quantity('1/3'))
        self.assertAlmostEqual(4/3,quantity('1⅓'))
        groups=load(OUT/'kdca-reference-compositions.json')
        self.assertEqual(613,len(groups))
        self.assertEqual(13165,sum(map(len,groups)))
        self.assertTrue(all(isinstance(r['grams'],(int,float)) and r['grams']>0 for g in groups for r in g))

    def test_03_canonical_name_not_code_only(self):
        mismatched=[i for r in self.survey for i in r['inputs'] if i['officialCode']=='N0080000009a' and i['ingredient']=='버터']
        self.assertTrue(mismatched)
        self.assertTrue(all(i['nutrient'] is None for i in mismatched))

    def test_04_context_preserves_input_state(self):
        eggs=[i for r in self.alternates for i in r['inputs'] if i['ingredient']=='달걀']
        self.assertEqual(2,len(eggs))
        self.assertTrue(all(i['identityStatus']=='CONTEXT_VERIFIED' and i['nutrient']['name']=='달걀, 생것' for i in eggs))

    def test_05_conflicting_portions_not_averaged(self):
        portions=load(OUT/'official-book-portion-candidates.json')
        chopped=next(r for r in portions if r['ingredient']=='다진 파' and r['unit']=='작은술')
        self.assertEqual([3.0,4.0,5.0],chopped['gramsPerUnitCandidates'])
        sesame=next(r for r in portions if r['ingredient']=='깨소금' and r['unit']=='작은술')
        self.assertEqual([3.0,4.0,6.0],sesame['gramsPerUnitCandidates'])
        self.assertFalse(any(r['originalAmountOverridden'] for r in portions))

    def test_06_bulk_index_keeps_rare_markers(self):
        source=load(OUT/'kdca-reference-compositions.json')
        self.assertTrue(any(i['marker']=='*' for g in source for i in g))
        self.assertTrue(any(i['marker']=='+' for g in source for i in g))
        self.assertEqual(43,len(self.survey))

    def test_07_alternate_recipe_linkage(self):
        linked=[i for r in self.alternates for i in r['inputs'] if i['nutritionLinked']]
        self.assertEqual(9,len(linked))
        for i in linked:
            self.assertIn(i['identityStatus'],('EXACT_VERIFIED','ALIAS_VERIFIED','CONTEXT_VERIFIED'))
            self.assertAlmostEqual(i['amountGrams']*i['nutrient']['energyKcal']/100,i['estimatedKcal'])

    def test_08_no_fake_amount_and_frozen_decisions(self):
        self.assertEqual(self.before,self.after)
        self.assertEqual(2511,sum(d['status'] in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM') for d in self.after))
        attempts=load(OUT/'amount-survey-reference-attempts.json')
        self.assertTrue(all(r['originalAmountEvidenceStatus']=='NO_SAFE_AMOUNT' for r in attempts))

    def test_09_no_fake_nutrition(self):
        gaps=load(OUT/'known-source-gaps.json')
        self.assertFalse(gaps['webfootOctopusInk']['resolved'])
        self.assertFalse(gaps['originalJam']['originalAmountOverridden'])
        unknown=[i for r in self.alternates for i in r['inputs'] if i['identityStatus']=='AMBIGUOUS']
        self.assertEqual(14,len(unknown))
        self.assertTrue(all(i['estimatedKcal'] is None and i['nutrient'] is None for i in unknown))

    def test_10_provenance_hashes_and_attempts(self):
        for source in load(OUT/'source-captures.json'):
            if source.get('rawFile'):
                self.assertEqual(source['sha256'],hashlib.sha256((ROOT/source['rawFile']).read_bytes()).hexdigest())
        attempts=load(OUT/'residual-attempts.json')
        self.assertEqual(1747,len(attempts))
        self.assertEqual(1747,len({(r['recipeId'],r['ingredientIndex']) for r in attempts}))
        self.assertTrue(all(r['checkedSources'] and r['query'] and r['result'] and r['checkedAt'] and r['nextPossibleSource'] for r in attempts))
        self.assertEqual(18,len(load(OUT/'kimbap-new-attempts.json')))

    def test_11_completion_not_inflated_by_survey(self):
        audit=load(OUT/'recipe-final-audit.json')
        self.assertEqual({'PARTIAL_LINKED':409,'COMPLETE_LINKED':41,'UNLINKABLE':66},audit['recipeStates'])
        self.assertEqual(475,len(load(OUT/'remaining-recipes.json')))
        self.assertEqual(5,sum(r['complete'] for r in self.survey))
        self.assertFalse(any(r['complete'] for r in self.alternates))

    def test_12_official_food_kcal_and_asset_preserved(self):
        expected={'food_items.csv':'ccf6a9b2322ad5e75fe29f13ffb7c4bf719c2cca5cd3b459f104d659231261b4',
            'product_items.csv':'3f659d78f279b65115e17d8194bd686560bca018dea701aaa1a9585261bbe18e',
            'franchise_official_items.csv':'4a44591f8d8c6ce8276bee5449e90ced1ca513555c2920718ba9722e5ada06e3',
            'recipe_ingredient_estimates.csv':'995810d5b8693598472c35c8320348f640496872eaf233f5a8f56fda6276e3ca'}
        for file,digest in expected.items():
            self.assertEqual(digest,hashlib.sha256((ROOT/'app/src/main/assets/fooddata'/file).read_bytes()).hexdigest())


if __name__=='__main__':unittest.main(verbosity=2)
