"""Targeted evidence/asset checks; original values and private data never mutated."""
import csv
import hashlib
import json
import math
import unittest
from collections import Counter,defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-full-reference'
ASSETS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def csvrows(p):
    with p.open(encoding='utf-8-sig',newline='') as stream:return list(csv.DictReader(stream))

class FullRecipeEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows=csvrows(ASSETS/'official_recipe_reference_estimates.csv')
        cls.refs=load(OUT/'reference-composition-audit.json')
        cls.byid={r['recipeId']:r for r in cls.refs}
        cls.states=load(OUT/'recipe-final-states.json')
    def test_01_accepted_original_decisions_frozen(self):
        before=load(OUT/'baseline-ingredient-decisions.json');after=load(OUT/'ingredient-decisions.json')
        bykey={(d['recipeId'],d['ingredientIndex']):d for d in after}
        for d in before:
            if d['status'] in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM'):
                self.assertEqual(d,bykey[d['recipeId'],d['ingredientIndex']])
    def test_02_accepted_original_asset_values_frozen(self):
        before=csvrows(OUT/'baseline-recipe-ingredient-estimates.csv');after=csvrows(ASSETS/'recipe_ingredient_estimates.csv')
        key=lambda r:(r['foodId'],r['recipeId'],r['ingredientName'])
        bykey={key(r):r for r in after}
        for row in before:
            current=bykey[key(row)]
            for field in ('amountGrams','kcalPer100g','nutrientFoodId','nutrientName','foodReferenceKcal','foodReferenceAmount','foodReferenceUnit'):
                self.assertEqual(row[field],current[field])
    def test_03_all_residuals_disposed(self):
        a=load(OUT/'residual-row-dispositions.json');self.assertEqual(1747,len(a))
        self.assertEqual(1747,len({(x['recipeId'],x['ingredientIndex']) for x in a}))
    def test_04_complete_state_count(self):
        self.assertEqual(516,len(self.states));self.assertEqual(41,sum(x['originalComplete'] for x in self.states))
        self.assertEqual(516,sum(Counter(x['state'] for x in self.states).values()))
    def test_05_reference_never_overrides_original(self):
        self.assertTrue(all(r['originalAmountOverridden'] is False for r in self.refs))
    def test_06_positive_amount_and_finite_nutrition(self):
        self.assertTrue(all(float(r['amountGrams'])>0 and math.isfinite(float(r['amountGrams'])) and
            math.isfinite(float(r['kcalPer100g'])) and float(r['kcalPer100g'])>=0 for r in self.rows))
    def test_07_whole_source_mass_matches(self):
        groups=defaultdict(list)
        for r in self.rows:groups[r['foodId'],r['recipeId']].append(r)
        for (_,rid),rows in groups.items():
            source=self.byid[rid]
            expected=sum(i['amountGrams'] for i in source['inputs'] if i.get('nutrient') and i['amountGrams']>0)
            self.assertAlmostEqual(expected,sum(float(r['amountGrams']) for r in rows),places=4)
    def test_08_menuzen_code_and_qualified_name(self):
        for r in self.refs:
            if not r['recipeId'].startswith('MENUZEN'):continue
            for x in r['inputs']:
                if x['nutrient']:
                    self.assertEqual(x['originalRecord']['nationStdFoodCode'],x['nutrient']['code'])
                    self.assertEqual(x['originalRecord']['foodNm'],x['nutrient']['name'])
    def test_09_provenance(self):
        self.assertTrue(all(r['recipeUrl'].startswith('https://') and r['nutrientUrl'].startswith('https://') and
            len(r['recipeSha256'])==64 and r['checkedAt'] for r in self.rows))
    def test_10_no_fabricated_complete(self):
        for r in self.rows:
            self.assertEqual(str(self.byid[r['recipeId']]['complete']).lower(),r['recipeComplete'])
    def test_11_pdf_23_amounts_9_nutrients_published(self):
        refs=[r for r in self.refs if r['recipeId'].startswith('MFDS-BOOK3-')]
        self.assertEqual(23,sum(len(r['inputs']) for r in refs))
        self.assertEqual(9,sum(bool(i['nutrient']) for r in refs for i in r['inputs']))
        self.assertEqual({r['recipeId'] for r in refs},{r['recipeId'] for r in self.rows if r['recipeId'].startswith('MFDS-BOOK3-')})
    def test_12_conflicts_all_reviewed(self):
        p=load(OUT/'portion-reviews.json');self.assertEqual(332,len(p))
        self.assertEqual(238,sum(r['result']=='CONFLICTING_OFFICIAL_PORTIONS' for r in p))
        self.assertTrue(all('reviewReason' in r for r in p))
        conversions=load(OUT/'original-spoon-conversions.json');self.assertEqual(25,len(conversions))
        for row in conversions:
            self.assertEqual(12.6,row['provenance']['gramsPerUnit'])
            self.assertEqual('tsp',row['provenance']['portionRows'][0]['modifier'])
    def test_13_kimbap_reference_separate(self):
        rows=[r for r in self.rows if r['foodId']=='kfind-d101-007000000-0001']
        self.assertTrue(any(r['recipeComplete']=='true' for r in rows))
        self.assertTrue(all(r['compositionKind']=='REFERENCE_RECIPE' for r in rows))
    def test_14_official_kcal_and_macros_frozen(self):
        hashes={'food_items.csv':'ccf6a9b2322ad5e75fe29f13ffb7c4bf719c2cca5cd3b459f104d659231261b4',
            'product_items.csv':'3f659d78f279b65115e17d8194bd686560bca018dea701aaa1a9585261bbe18e',
            'franchise_official_items.csv':'4a44591f8d8c6ce8276bee5449e90ced1ca513555c2920718ba9722e5ada06e3'}
        for f,h in hashes.items():self.assertEqual(h,hashlib.sha256((ASSETS/f).read_bytes()).hexdigest())
    def test_15_database_and_production_versions(self):
        self.assertIn('version = 8',(ROOT/'app/src/main/java/com/example/healthcare/data/database/AppDatabase.kt').read_text(encoding='utf-8'))
        gradle=(ROOT/'app/build.gradle.kts').read_text(encoding='utf-8');self.assertIn('versionCode = 7',gradle);self.assertIn('versionName = "1.0.6"',gradle)
    def test_16_kcal_formula_uses_nutrient_basis_only(self):
        for r in self.rows:
            n=next(i['nutrient'] for i in self.byid[r['recipeId']]['inputs'] if i.get('nutrient') and i['nutrient']['name']==r['nutrientName'])
            self.assertAlmostEqual(n['energyKcal'],float(r['kcalPer100g']),places=3)
            self.assertEqual(100,n.get('referenceAmount',100))

if __name__=='__main__':unittest.main(verbosity=2)
