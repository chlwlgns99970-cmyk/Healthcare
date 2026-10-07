"""The 24 requested data categories, against real assets and preservation evidence."""
import csv,hashlib,io,json,math,unittest,zipfile
from pathlib import Path
from collections import Counter
from pypdf import PdfReader
from import_kfind_foods import normalize_name
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-93';AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
class Residual93Test(unittest.TestCase):
 @classmethod
 def setUpClass(c):
  c.six=load(OUT/'approved-six-final.json');c.ledger=load(OUT/'all-87-final-evidence-ledger.json')
  c.foods={r['id']:r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)}
  c.asset=rows(AS/'official_recipe_reference_estimates.csv');c.new=[r for r in c.asset if r['foodId'].startswith('rda-menuzen-')]
  c.refs={r['recipeId']:r for r in load(ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json')}
  c.states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json');c.evidence=load(OUT/'approved-new-food-evidence.json')
 def test_01_six_duplicate_scan(self):
  self.assertEqual(6,len(self.six))
  for r in self.six:self.assertEqual(67354,r['duplicateRowsScanned']);self.assertEqual([],r['lastExactNormalizedAliasMatches'])
 def test_02_id_uniqueness(self):
  allrows=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
  self.assertEqual(67356,len(allrows));self.assertEqual(len(allrows),len({r['id'] for r in allrows}))
  for e in self.evidence:self.assertEqual(e['sourceType'].lower()+'-'+e['sourceFoodCode'].lower(),e['foodId'])
 def test_03_food_reference_mapping(self):
  self.assertEqual(20,len(self.new))
  for e in self.evidence:
   items=[r for r in self.new if r['foodId']==e['foodId']];self.assertTrue(items)
   self.assertEqual({e['referenceId']},{r['recipeId'] for r in items})
 def test_04_entire_queue(self):
  self.assertEqual(87,len(self.ledger));self.assertEqual(87,len({r['recipeId'] for r in self.ledger}))
  self.assertTrue(all(r['currentExecutedSearchReceipts'] and r['finalDisposition'] for r in self.ledger))
 def test_05_canonical_alias(self):
  for e in self.evidence:
   f=self.foods[e['foodId']];self.assertEqual(normalize_name(f['name']),f['normalizedName'])
   self.assertIn(e['reportedNutritionHeader']['fdNm'],f['aliases'].split('|'))
 def test_06_candidate_selection(self):
  for e in self.evidence:self.assertTrue(self.refs[e['referenceId']]['complete'])
  self.assertTrue(all(r['decision']=='NOT_PUBLISHED' and r['reason'] for r in load(OUT/'primary-candidate-adjudications.json')))
 def test_07_actual_public_parser(self):
  docs=load(OUT/'new-document-index.json');pdf=next(d for d in docs if 'kdca.go.kr' in d['url'])
  self.assertEqual(111,len(PdfReader(ROOT/pdf['rawFile']).pages))
  self.assertIn('도토리묵밥',PdfReader(ROOT/pdf['rawFile']).pages[66].extract_text())
 def test_08_servings_not_guessed(self):
  for e in self.evidence:
   f=self.foods[e['foodId']];self.assertIn('조리 후 중량 아님',f['servingDescription']);self.assertNotIn('1인분',f['servingDescription'])
  school=[r for r in load(OUT/'primary-candidate-adjudications.json') if 'cbe.go.kr' in r['sourceUrl'] and r['page']==196]
  self.assertTrue(school);self.assertTrue(all('밥' in r['reason'] for r in school))
 def test_09_amount(self):
  for e in self.evidence:
   ref=self.refs[e['referenceId']];self.assertAlmostEqual(float(e['reportedNutritionHeader']['totalFoodWgh']),sum(i['amountGrams'] for i in ref['inputs']))
 def test_10_unit(self):
  for e in self.evidence:self.assertEqual('g',self.foods[e['foodId']]['unit'])
  for r in self.new:self.assertGreater(float(r['amountGrams']),0)
 def test_11_portion_conversion(self):
  for e in self.evidence:
   for i in self.refs[e['referenceId']]['inputs']:
    self.assertEqual(i['amountGrams'],i['originalRecord']['foodWgh'])
  self.assertFalse(any(r['residual93Resolution'] for r in load(OUT/'current-212-conflict-review.json')))
 def test_12_independent_nutrients(self):
  for e in self.evidence:
   f=self.foods[e['foodId']];h=e['reportedNutritionHeader']
   for key,hkey in [('carbohydrateGrams','totalCarbohydrate'),('proteinGrams','totalProtein'),('fatGrams','totalFat')]:self.assertEqual(float(h[hkey]),float(f[key]))
   self.assertEqual('',f['sodiumMilligrams'])
 def test_13_official_and_reference_kcal(self):
  for e in self.evidence:self.assertEqual(float(e['reportedNutritionHeader']['totalEnergy']),float(self.foods[e['foodId']]['energyKcal']))
  for e in self.evidence:
   for i in self.refs[e['referenceId']]['inputs']:self.assertAlmostEqual(i['amountGrams']*i['nutrient']['energyKcal']/100,i['estimatedKcal'])
 def test_14_provenance(self):
  for e in self.evidence:self.assertEqual(e['sourceSha256'],hashlib.sha256((ROOT/e['rawFile']).read_bytes()).hexdigest())
  for r in self.new:self.assertTrue(r['sourceInstitution']);self.assertTrue(r['nutrientUrl']);self.assertEqual(64,len(r['recipeSha256']))
 def test_15_original_separate(self):
  self.assertEqual(41,sum(r['state']=='ORIGINAL_COMPLETE' for r in self.states))
  self.assertEqual((OUT/'baseline-recipe_ingredient_estimates.csv').read_bytes(),(AS/'recipe_ingredient_estimates.csv').read_bytes())
 def test_16_reference_complete(self):
  self.assertEqual(388,sum(r['state']=='REFERENCE_COMPLETE' for r in self.states));self.assertEqual(425,sum(r['appCompleteAvailable'] for r in self.states))
  self.assertTrue(all(r['recipeComplete']=='true' for r in self.new))
 def test_17_partial(self):self.assertEqual(81,sum(r['finalDisposition']=='PARTIAL_WITH_EXHAUSTED_EVIDENCE' for r in self.ledger))
 def test_18_unresolved(self):self.assertEqual(6,sum(r['finalDisposition']=='UNRESOLVED_WITH_EXHAUSTED_EVIDENCE' for r in self.ledger))
 def test_19_survey_label(self):
  self.assertTrue(all(r['compositionKind']=='SURVEY_AVERAGE' for r in self.asset if r['recipeId']=='KDCA-281-6394'))
  self.assertIn('공공 조사 평균 참고 구성',(ROOT/'app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt').read_text(encoding='utf-8'))
 def test_20_no_fake_values(self):
  pending=[r for r in self.six if not r['appComplete']];self.assertEqual(4,len(pending));self.assertTrue(all(r['foodId'] is None and r['remainingReason'] for r in pending))
  for e in self.evidence:self.assertTrue(math.isfinite(float(self.foods[e['foodId']]['energyKcal'])));self.assertGreater(float(self.foods[e['foodId']]['energyKcal']),0)
 def test_21_no_duplicate_food(self):
  self.assertEqual(2,len(self.evidence));self.assertEqual(2,len({e['foodId'] for e in self.evidence}))
  self.assertEqual(len(self.asset),len({(r['foodId'],r['recipeId'],r['ingredientName']) for r in self.asset}))
 def test_22_all_existing_food_rows(self):
  total=0
  for name in ('food_items.csv','product_items.csv','franchise_official_items.csv'):
   old=rows(OUT/('baseline-'+name));total+=len(old)
   for r in old:self.assertEqual(r,self.foods[r['id']])
  self.assertEqual(67354,total)
 def test_23_nutrition_and_packaged_assets(self):
  with zipfile.ZipFile(ROOT/'app/build/outputs/apk/qa/app-qa.apk') as z:
   for name in ('food_items.csv','food_metadata.csv','official_recipe_reference_estimates.csv'):
    self.assertEqual((AS/name).read_bytes(),z.read('assets/fooddata/'+name))
  baseline={ (r['foodId'],r['recipeId'],r['ingredientName']):r for r in rows(OUT/'baseline-official_recipe_reference_estimates.csv')}
  after={ (r['foodId'],r['recipeId'],r['ingredientName']):r for r in self.asset}
  for k,v in baseline.items():self.assertEqual(v,after[k])
 def test_24_MealRecord_and_private_preservation(self):
  r=load(ROOT/'app/build/recipe-residual-93/preservation-verify.json');self.assertEqual('PASS',r['status']);self.assertEqual([],r['changedTables']);self.assertEqual([8,8],r['databaseVersions']);self.assertTrue(r['allAllowlistedPrivateFileBytesIdentical']);self.assertTrue(r['productIdentityUnchanged'])
  self.assertIn('OK (2 tests)',(ROOT/'app/build/recipe-residual-93/samsung-flow-output.txt').read_text(encoding='utf-8-sig'))
if __name__=='__main__':
 stream=io.StringIO();r=unittest.TextTestRunner(stream=stream,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Residual93Test))
 (OUT/'data-24-test-output.txt').write_text(stream.getvalue(),encoding='utf-8');print(stream.getvalue());raise SystemExit(not r.wasSuccessful())
