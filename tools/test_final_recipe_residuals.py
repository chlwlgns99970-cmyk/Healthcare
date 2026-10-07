"""Targeted evidence/asset checks; original values and private data never mutated."""
import csv
import hashlib
import json
import math
import unittest
from collections import Counter,defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-full-reference'
FINAL=ROOT/'data-source/recipe-final-residual'
ASSETS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def csvrows(p):
    with p.open(encoding='utf-8-sig',newline='') as stream:return list(csv.DictReader(stream))

class FinalResidualEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows=csvrows(ASSETS/'official_recipe_reference_estimates.csv')
        cls.refs=load(FINAL/'validated-reference-compositions.json')+load(FINAL/'additional-original-compositions.json')
        cls.byid={r['recipeId']:r for r in cls.refs}
        cls.states=load(FINAL/'recipe-final-states.json')
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
        self.assertTrue(all(r.get('originalAmountOverridden',False) is False for r in self.refs))
    def test_06_positive_amount_and_finite_nutrition(self):
        self.assertTrue(all(float(r['amountGrams'])>0 and math.isfinite(float(r['amountGrams'])) and
            math.isfinite(float(r['kcalPer100g'])) and float(r['kcalPer100g'])>=0 for r in self.rows))
    def test_07_whole_source_mass_matches(self):
        groups=defaultdict(list)
        frozen=defaultdict(list)
        for r in csvrows(FINAL/'baseline-official_recipe_reference_estimates.csv'):frozen[r['foodId'],r['recipeId']].append(r)
        for r in self.rows:groups[r['foodId'],r['recipeId']].append(r)
        for (_,rid),rows in groups.items():
            if not all(r['recipeComplete']=='true' for r in rows):
                baseline=frozen[rows[0]['foodId'],rid]
                if baseline:
                    self.assertEqual(baseline,[{k:v for k,v in r.items() if k in baseline[0]} for r in rows])
                    continue
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
            if r['recipeComplete']=='true':self.assertTrue(self.byid[r['recipeId']]['complete'])
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

    def test_17_food_queue_and_unpublished_coverage(self):
        self.assertEqual(254,len(load(FINAL/'food-work-queue.json')))
        self.assertEqual(246,len(load(FINAL/'unpublished-reference-dispositions.json')))
        self.assertEqual(load(FINAL/'progress.json')['newCompleteFoods'],len(load(FINAL/'food-mapping-decisions.json')))
    def test_18_residual_reassessment_and_local_mass(self):
        rows=load(FINAL/'residual-row-reassessments.json');self.assertEqual(1722,len(rows))
        self.assertEqual(2,sum(r['afterStatus']=='LINKED' for r in rows))
        direct=load(FINAL/'direct-original-mass-decisions.json')
        self.assertEqual({'RDA-89555':250.0,'RDA-91102':100.0},{r['recipeId']:r['amountGrams'] for r in direct})
        self.assertTrue(all(r['conversionProvenance']['scope']=='THIS_RECIPE_ROW_ONLY' for r in direct))
    def test_19_all_conflicts_compared(self):
        rows=load(FINAL/'portion-conflict-reassessments.json');self.assertEqual(213,len(rows))
        self.assertEqual(1,sum(r['finalStatus']=='RESOLVED_THIS_RECIPE_DIRECT_GRAMS' for r in rows))
        self.assertTrue(all(r['comparisons'] and r['finalReason'] for r in rows))
    def test_20_previously_published_rows_frozen(self):
        self.assertTrue(all(r in self.rows for r in csvrows(FINAL/'baseline-official_recipe_reference_estimates.csv')))
    def test_21_no_self_composite_or_missing_named_main_ingredient(self):
        byid={r['recipeId']:r for r in self.refs}
        for rid in ['MENUZEN-D135005','MENUZEN-D063032','MENUZEN-D063035']:
            self.assertFalse(byid[rid]['complete']);self.assertTrue(byid[rid]['compositionValidationProblem'])
            self.assertFalse(any(r['recipeId']==rid and r['recipeComplete']=='true' for r in self.rows))
    def test_22_food_mapping_never_uses_branded_product_or_other_plant(self):
        foods={r['id']:r for r in csvrows(ASSETS/'food_items.csv')}
        for r in load(FINAL/'food-mapping-decisions.json'):
            for fid in r['foodIds']:
                self.assertFalse(foods[fid]['brand']);self.assertEqual('K-FIND',foods[fid]['sourceType'])
        q={r['name']:r for r in load(FINAL/'food-work-queue.json')}
        self.assertFalse(q['곤달비밥(곤드레밥, 곤드레나물밥)']['completeInApp'])

    def test_23_generic_salt_requires_exact_public_identity_and_basis(self):
        r=self.byid['KDCA-281-13104']
        self.assertTrue(r['complete']);self.assertEqual('SURVEY_AVERAGE',r['compositionKind'])
        salt=next(x for x in r['inputs'] if x['ingredient']=='소금')['nutrient']
        self.assertEqual('소금',salt['name']);self.assertEqual('R318-020000000-0000',salt['code'])
        self.assertEqual(100,salt['referenceAmount']);self.assertEqual('g',salt['referenceUnit'])
        self.assertEqual('농촌진흥청(국가표준식품성분표)',salt['sourceName'])
        state=next(x for x in self.states if x['recipeId']=='MFDS-172')
        self.assertTrue(state['appCompleteAvailable']);self.assertFalse(state['originalComplete'])

    def test_24_public_fallback_keeps_all_original_qualifiers(self):
        expected={'오이, 개량종, 생것':('R106-148010001-0000','오이_개량종_생것'),
            '키위, 생것':('MFDS-LEGACY-2096','키위, 생것'),
            '조미료':('MFDS-LEGACY-4574','조미료')}
        for rid in ['KDCA-281-3306','KDCA-281-3710','KDCA-281-7626']:
            r=self.byid[rid];self.assertTrue(r['complete'])
            self.assertEqual('SURVEY_AVERAGE',r['compositionKind'])
            for x in r['inputs']:
                if x['ingredient'] in expected:
                    code,name=expected[x['ingredient']];n=x['nutrient']
                    self.assertEqual(code,n['code']);self.assertEqual(name,n['name'])
                    self.assertEqual(100,n['referenceAmount']);self.assertEqual('g',n['referenceUnit'])
                    self.assertEqual(x['originalRecord']['grams'],x['amountGrams'])
    def test_25_followup_rows_preserved_and_prepared_cake_rejected(self):
        before=csvrows(ROOT/'data-source/recipe-final-residual-followup/before-reference-asset.csv')
        current={(x['foodId'],x['recipeId'],x['ingredientName']):x for x in self.rows}
        for x in before:self.assertEqual(x,current[x['foodId'],x['recipeId'],x['ingredientName']])
        for rid in ['MENUZEN-D230002','MENUZEN-D230003']:
            self.assertFalse(self.byid[rid]['complete'])
            self.assertFalse(any(x['recipeId']==rid and x['recipeComplete']=='true' for x in self.rows))

    def test_26_finalization_baseline_rows_and_original_food_values_frozen(self):
        before=csvrows(ROOT/'data-source/recipe-reference-finalization/before-reference-asset.csv')
        current={(x['foodId'],x['recipeId'],x['ingredientName']):x for x in self.rows}
        for x in before:self.assertEqual(x,current[x['foodId'],x['recipeId'],x['ingredientName']])
    def test_27_every_published_state_is_actual_complete_asset_group(self):
        groups=defaultdict(list)
        for r in self.rows+csvrows(ASSETS/'recipe_ingredient_estimates.csv'):groups[r['foodId'],r['recipeId']].append(r)
        for m in load(FINAL/'food-mapping-decisions.json'):
            for fid in m['foodIds']:
                rs=groups[fid,m['selectedId']]
                self.assertTrue(rs,m['originalName']);self.assertTrue(all(r['recipeComplete']=='true' for r in rs),m['originalName'])
    def test_28_candidates_have_ten_ordered_criteria_and_never_publish_by_score(self):
        queue=load(ROOT/'data-source/recipe-reference-finalization/reference-first-work-queue.json')
        self.assertEqual(155,len(queue));self.assertEqual(155,len({q['recipeId'] for q in queue}))
        for q in queue:
            for c in q['rankedExistingReferences']:self.assertEqual(10,len(c['score']))
        tool=(ROOT/'tools/audit_reference_first_candidates.py').read_text(encoding='utf-8')
        self.assertNotIn('official_recipe_reference_estimates.csv',tool)
    def test_29_verified_clam_synonym_and_real_missing_primary_are_distinguished(self):
        self.assertTrue(self.byid['MENUZEN-D052158']['complete'])
        for rid in ['MENUZEN-D015074','MENUZEN-D051212','MENUZEN-D135038','MENUZEN-D082045']:
            self.assertFalse(self.byid[rid]['complete'])
            self.assertFalse(any(r['recipeId']==rid and r['recipeComplete']=='true' for r in self.rows))
        capture=next(c for c in load(OUT/'source-captures.json') if 'trgtWordNo=2002119' in c['url'])
        raw=(ROOT/capture['rawFile']).read_bytes();self.assertEqual(capture['sha256'],hashlib.sha256(raw).hexdigest())
        self.assertIn('모시조개',raw.decode('utf-8'));self.assertIn('가무락조개',raw.decode('utf-8'))
    def test_30_survey_ui_never_calls_average_an_official_recipe(self):
        code=(ROOT/'app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt').read_text(encoding='utf-8')
        self.assertIn('재료별 예상 열량 · 공공 조사 평균 참고 구성',code)
        self.assertIn('!surveyAverage',code)
        self.assertNotIn('공식 조사 평균',code)
    def test_31_entire_155_scope_has_receipts_and_remaining_food_reasons(self):
        finalization=ROOT/'data-source/recipe-reference-finalization'
        ledger=load(finalization/'all-155-food-evidence-ledger.json')
        self.assertEqual(155,len(ledger));self.assertEqual(155,len({r['recipeId'] for r in ledger}))
        # The 155-food ledger is immutable historical evidence for 361 -> 419.
        # New residual work has its own 419 baseline and separate ledger.
        self.assertEqual(419-361,sum(r['appComplete'] for r in ledger))
        for r in ledger:
            self.assertFalse(r['wholeInternetExhausted']);self.assertTrue(r['originalSource']['sha256'])
            if not r['appComplete']:
                self.assertGreaterEqual(len(r['executedSearchReceipts']),2)
                self.assertTrue(r['remainingReason']);self.assertTrue(r['exhaustedMeaning'])
            else:self.assertTrue(r['selectedMapping']['foodIds'])
        self.assertEqual(212,len(load(finalization/'current-212-conflict-review.json')))
if __name__=='__main__':unittest.main(verbosity=2)

