"""Exhaustive source identity, safe enrichment, packaging and model checks."""
import collections,csv,hashlib,io,json,math,re,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];AS=ROOT/'app/src/main/assets/fooddata';OUT=ROOT/'data-source/all-food-detail-audit'
def rows(p):
    with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def load(p):return json.loads(p.read_text(encoding='utf-8-sig'))
class AllFoodDetailAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.foods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
        cls.fi={r['id']:r for r in cls.foods};cls.md=rows(AS/'food_metadata.csv');cls.mi={r['foodItemId']:r for r in cls.md}
        cls.original=rows(AS/'recipe_ingredient_estimates.csv');cls.refs=rows(AS/'official_recipe_reference_estimates.csv')
        cls.native=load(ROOT/'app/build/all-food-detail-audit/model-result.json')
    def test_01_unique_ids(self):self.assertEqual(67356,len(self.foods));self.assertEqual(67356,len(self.fi))
    def test_02_every_food_has_real_runtime_detail(self):self.assertEqual(67356,self.native['modelSuccess']);self.assertEqual(0,self.native['modelFailure'])
    def test_03_metadata_orphan_exact_catalog_namespace(self):self.assertEqual(0,self.native['metadataOrphan']);self.assertEqual(len(self.md),len(self.mi));self.assertTrue(set(self.fi)<=set(self.mi))
    def test_04_nutrition_orphan(self):self.assertEqual(set(self.fi),{r['id'] for r in self.foods if r['energyKcal']!=''})
    def test_05_reference_orphan(self):self.assertEqual(set(),{r['foodId'] for r in self.original+self.refs}-set(self.fi))
    def test_06_wrong_identity(self):
        for f in self.foods:
            m=self.mi[f['id']];self.assertEqual(f['sourceFoodCode'],m['sourceFoodCode']);self.assertEqual(f['normalizedName'],m['normalizedName'])
    def test_07_valid_calories(self):
        self.assertTrue(all(math.isfinite(float(r['energyKcal'])) and float(r['energyKcal'])>=0 for r in self.foods))
    def test_08_valid_basis(self):
        self.assertTrue(all(math.isfinite(float(r['referenceAmount'])) and float(r['referenceAmount'])>0 and r['unit'] for r in self.foods))
    def test_09_unknown_not_replaced_and_official_values_preserved(self):
        allow={r['foodId'] for name in ('new-verified-display-aliases.json','new-verified-seafood-aliases.json') for r in load(OUT/name)}
        for name in ('food_items.csv','product_items.csv','franchise_official_items.csv'):
            for old in rows(OUT/('baseline-'+name)):
                now=self.fi[old['id']]
                for key,value in old.items():
                    if key=='aliases' and old['id'] in allow:continue
                    self.assertEqual(value,now[key],(old['id'],key))
    def test_10_source_provenance_consistent(self):
        for m in self.md:self.assertTrue(m['sourceReference']);self.assertTrue(m['checkedAt'])
        for r in load(OUT/'new-public-recipe-text-links.json'):
            self.assertTrue(r['sourceUrl'].startswith('https://www.foodnuri.go.kr/'));self.assertFalse(r['officialFoodNutritionChanged']);self.assertFalse(r['referenceComplete'])
    def test_11_lifestyle_not_invented(self):
        before={r['foodItemId']:r for r in rows(OUT/'baseline-food_metadata.csv')}
        for m in self.md:
            for key in ('householdUnit','basisAmountPerUnit','basisUnit','servingSourceReference','servingEvidenceKind'):self.assertEqual(before[m['foodItemId']][key],m[key])
    def test_12_franchise_mapping(self):self.assertEqual(2685,self.native['catalogModelSuccess']);self.assertGreater(self.native['franchiseNutritionFoods'],0)
    def test_13_retail_jjapagetti(self):
        r=self.fi['official-retail-nongshim-p0000dyw-single-140g']
        self.assertEqual([140,610,96,9,20],[float(r[k]) for k in ('referenceAmount','energyKcal','carbohydrateGrams','proteinGrams','fatGrams')])
    def test_14_recipe_rows_and_remaining91(self):
        self.assertEqual(rows(OUT/'baseline-recipe_ingredient_estimates.csv'),self.original)
        self.assertEqual(91,len(load(ROOT/'data-source/recipe-residual-93/remaining-91-foods.json')))
        self.assertEqual(425,load(ROOT/'data-source/recipe-residual-93/final-progress.json')['appComplete'])
    def test_15_complete_reference_whole_recipe_preserved(self):
        old=rows(OUT/'baseline-official_recipe_reference_estimates.csv');self.assertEqual(old,self.refs[:len(old)])
        links=load(OUT/'new-exact-reference-links.json');self.assertEqual(26,len(links))
        by=collections.defaultdict(list)
        for r in self.refs:by[(r['foodId'],r['recipeId'])].append(r)
        for link in links:
            selected=by[(link['foodId'],link['referenceId'])];self.assertEqual(link['ingredientRows'],len(selected))
            self.assertTrue(all(r['recipeComplete']=='true' and r['recipeUrl']==link['sourceUrl'] and r['recipeSha256']==link['sourceSha256'] for r in selected))
    def test_16_display_names_searchable(self):
        for line in (OUT/'runtime-display-names.jsonl').read_text(encoding='utf-8-sig').splitlines():
            r=json.loads(line);food=self.fi[r['foodId']];self.assertTrue(r['displayQuery'] in food['normalizedName'] or r['displayQuery'] in food['aliases'])
        self.assertEqual(0,self.native['displayQueryMissing'])
    def test_17_packaged_common_assets(self):
        with zipfile.ZipFile(ROOT/'app/build/outputs/apk/qa/app-qa.apk') as z:
            for n in ('food_items.csv','product_items.csv','franchise_official_items.csv','food_metadata.csv','official_recipe_reference_estimates.csv'):
                self.assertEqual((AS/n).read_bytes(),z.read('assets/fooddata/'+n))
        self.assertEqual(0,self.native['dataExistsUiMissing']);self.assertEqual(len(load(OUT/'new-public-recipe-text-links.json')),self.native['publicRecipeTextFoods'])
    def test_18_private_records_and_product_unchanged(self):
        report=load(ROOT/'app/build/all-food-detail-audit/preservation-verify.json')
        self.assertEqual('PASS',report['status']);self.assertEqual([],report['changedTables']);self.assertTrue(report['allAllowlistedPrivateFileBytesIdentical']);self.assertTrue(report['productIdentityUnchanged']);self.assertEqual([8,8],report['databaseVersions'])
    def test_19_existing_metadata_all_fields_preserved(self):
        for old in rows(OUT/'baseline-food_metadata.csv'):
            for key,value in old.items():self.assertEqual(value,self.mi[old['foodItemId']][key],(old['foodItemId'],key))
    def test_20_public_image_transcriptions_retain_hashed_source(self):
        for r in load(OUT/'foodnuri/reviewed-image-transcriptions.json'):
            self.assertEqual(r['imageSha256'],hashlib.sha256((ROOT/r['imageFile']).read_bytes()).hexdigest())
            self.assertIn('1인분',r['ingredientText']);self.assertTrue(r['basis'])
if __name__=='__main__':
    output=io.StringIO();result=unittest.TextTestRunner(stream=output,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(AllFoodDetailAuditTest))
    (OUT/'data-test-output.txt').write_text(output.getvalue(),encoding='utf-8');print(output.getvalue());raise SystemExit(not result.wasSuccessful())
