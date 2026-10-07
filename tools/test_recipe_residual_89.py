"""22 requested categories against current assets and actual device preservation."""
import csv,hashlib,io,json,math,unittest,zipfile
from pathlib import Path
from collections import Counter
from pypdf import PdfReader
from openpyxl import load_workbook
from finish_recipe_reference_mapping import keys
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-89';AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
class Residual89Test(unittest.TestCase):
 @classmethod
 def setUpClass(c):
  c.ledger=load(OUT/'all-89-final-evidence-ledger.json');c.states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')
  c.asset=rows(AS/'official_recipe_reference_estimates.csv');c.baseline=rows(OUT/'baseline-official_recipe_reference_estimates.csv')
  prior={(r['foodId'],r['recipeId'],r['ingredientName']) for r in c.baseline}
  c.new=[r for r in c.asset if (r['foodId'],r['recipeId'],r['ingredientName']) not in prior]
  c.refs={r['recipeId']:r for r in load(ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json')}
 def test_01_queue(self):
  self.assertEqual(89,len(self.ledger));self.assertEqual(89,len({r['recipeId'] for r in self.ledger}))
  self.assertEqual(Counter(PARTIAL_WITH_EXHAUSTED_EVIDENCE=81,UNRESOLVED_WITH_EXHAUSTED_EVIDENCE=6,REFERENCE_COMPLETE=2),Counter(r['finalDisposition'] for r in self.ledger))
 def test_02_reference_matching(self):
  self.assertEqual({'DAEGU-LOW-SODIUM-P40','KDCA-281-6394'},{r['recipeId'] for r in self.new})
  self.assertFalse(any(r['recipeId'] in ('MENUZEN-D093012','MENUZEN-D132016') for r in self.new))
 def test_03_canonical_alias(self):
  self.assertIn('감자베이컨볶음',keys('감자볶음_감자_베이컨'))
  self.assertNotIn('감자베이컨볶음',keys('감자볶음_감자_햄'))
  self.assertNotIn('감자베이컨볶음',keys('감자조림_감자_베이컨'))
 def test_04_real_parsers(self):
  ref=self.refs['DAEGU-LOW-SODIUM-P40'];self.assertEqual(115,len(PdfReader(ROOT/ref['rawFile']).pages))
  self.assertEqual(6766,len(self.asset))
  # Actual official Food XLSX, not a synthetic mock workbook.
  wb=load_workbook(ROOT/'data-source/kfind/kfind-food-db-2026-08-28.xlsx',read_only=True,data_only=True)
  self.assertGreater(wb.active.max_row,10000);wb.close()
 def test_05_food_matcher(self):
  self.assertEqual(7,len({r['foodId'] for r in self.new}));self.assertEqual(79,len(self.new))
  food={r['id']:r for fn in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/fn)}
  for r in self.new:self.assertFalse(food[r['foodId']].get('brand'))
 def test_06_duplicates(self):self.assertEqual(len(self.asset),len({(r['foodId'],r['recipeId'],r['ingredientName']) for r in self.asset}))
 def test_07_amount(self):
  for r in self.new:self.assertGreater(float(r['amountGrams']),0)
  self.assertEqual(1120,sum(i['amountGrams'] for i in self.refs['DAEGU-LOW-SODIUM-P40']['inputs']))
 def test_08_unit(self):
  for rid in {r['recipeId'] for r in self.new}:
   for i in self.refs[rid]['inputs']:self.assertEqual('REFERENCE_RECIPE_EXACT',i['amountEvidenceStatus'])
 def test_09_conflicts(self):
  conflicts=load(OUT/'current-212-conflict-review.json');self.assertEqual(212,len(conflicts));self.assertFalse(any(r['newResolution'] for r in conflicts))
 def test_10_nutrition(self):
  for r in self.new:self.assertTrue(r['nutrientFoodId']);self.assertTrue(math.isfinite(float(r['kcalPer100g'])))
 def test_11_kcal(self):
  for rid in {r['recipeId'] for r in self.new}:
   for i in self.refs[rid]['inputs']:self.assertAlmostEqual(i['amountGrams']*i['nutrient']['energyKcal']/100,i['estimatedKcal'])
 def test_12_provenance(self):
  for r in self.new:self.assertTrue(r['recipeUrl'].startswith('https://'));self.assertEqual(64,len(r['recipeSha256']))
  ref=self.refs['DAEGU-LOW-SODIUM-P40'];self.assertEqual(ref['sourceSha256'],hashlib.sha256((ROOT/ref['rawFile']).read_bytes()).hexdigest())
 def test_13_original(self):self.assertEqual(41,sum(r['state']=='ORIGINAL_COMPLETE' for r in self.states))
 def test_14_reference(self):
  self.assertEqual(388,sum(r['state']=='REFERENCE_COMPLETE' for r in self.states));self.assertEqual(423,sum(r['appCompleteAvailable'] for r in self.states))
 def test_15_partial(self):
  partial=[r for r in self.ledger if r['state']=='PARTIAL'];self.assertEqual(81,len(partial))
  self.assertTrue(all(r['remainingReason'] and r['nextAction'] and len(r['newExecutedSearchReceipts'])>=3 for r in partial))
 def test_16_unresolved(self):
  unresolved=[r for r in self.ledger if r['state']=='UNRESOLVED'];self.assertEqual(6,len(unresolved));self.assertTrue(all(not r['appComplete'] for r in unresolved))
 def test_17_survey(self):
  self.assertTrue(all(r['compositionKind']=='SURVEY_AVERAGE' for r in self.new if r['recipeId']=='KDCA-281-6394'))
  self.assertIn('공공 조사 평균 참고 구성',(ROOT/'app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt').read_text(encoding='utf-8'))
 def test_18_fake_amount(self):
  before={ (r['foodId'],r['recipeId'],r['ingredientName']):r for r in self.baseline}
  after={ (r['foodId'],r['recipeId'],r['ingredientName']):r for r in self.asset}
  for k,v in before.items():self.assertEqual(v,after[k])
 def test_19_fake_unit(self):
  for r in load(OUT/'new-primary-candidate-reviews.json'):self.assertEqual('NOT_PUBLISHED',r['decision']);self.assertTrue(r['reason'])
  self.assertEqual('UNRESOLVED',load(OUT/'separate-nutrition-and-source-review.json')['source']['status'])
 def test_20_fake_foodId(self):
  six=load(OUT/'question-required-six-final.json');self.assertEqual(6,len(six));self.assertFalse(any(r['newFoodCreated'] or r['sameExistingFoodVerified'] for r in six))
 def test_21_official_kcal(self):
  protected=load(OUT/'protected-asset-sha256.json');total=0
  for fn,sha in protected.items():
   if fn=='official_recipe_reference_estimates.csv':continue
   self.assertEqual(sha,hashlib.sha256((AS/fn).read_bytes()).hexdigest())
   if fn in ('food_items.csv','product_items.csv','franchise_official_items.csv'):total+=len(rows(AS/fn))
  self.assertEqual(67354,total)
  with zipfile.ZipFile(ROOT/'app/build/outputs/apk/qa/app-qa.apk') as z:self.assertEqual((AS/'official_recipe_reference_estimates.csv').read_bytes(),z.read('assets/fooddata/official_recipe_reference_estimates.csv'))
 def test_22_MealRecord_preservation(self):
  r=load(ROOT/'app/build/recipe-residual-89/preservation-verify.json');self.assertEqual('PASS',r['status']);self.assertEqual([],r['changedTables']);self.assertEqual([8,8],r['databaseVersions']);self.assertTrue(r['allAllowlistedPrivateFileBytesIdentical']);self.assertTrue(r['productIdentityUnchanged'])
if __name__=='__main__':
 suite=unittest.defaultTestLoader.loadTestsFromTestCase(Residual89Test);stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
 (OUT/'data-22-test-output.txt').write_text(stream.getvalue(),encoding='utf-8');print(stream.getvalue());raise SystemExit(not result.wasSuccessful())
