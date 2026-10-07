"""Ten targeted recipe linkage and provenance checks; no catalog/menu suite."""
import csv,hashlib,json,math,unittest
from pathlib import Path
from recipe_ingredient_parser import parse
from maximize_recipe_evidence import additional_nutrients,resolve_identity,portion_conversion
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-linkage-maximization'
ASSETS=ROOT/'app/src/main/assets/fooddata'
def read(name):return json.loads((OUT/name).read_text(encoding='utf-8'))
def csvrows(path):
    with path.open(encoding='utf-8-sig',newline='') as stream:return list(csv.DictReader(stream))
class RecipeLinkageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit=read('recipe-final-audit.json');cls.decisions=read('ingredient-decisions.json')
        cls.references=csvrows(ASSETS/'recipe_ingredient_estimates.csv')
        cls.nutrients={n['code']:n for n in json.loads((ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json').read_text(encoding='utf-8'))}
        cls.nutrients.update(additional_nutrients())
    def test_01_alias_and_normalization(self):
        from reviewed_recipe_identities import RDA_ALIASES
        self.assertEqual(RDA_ALIASES['계란'],RDA_ALIASES['달걀'])
        self.assertEqual(2,parse('다진 마늘2(g)')[0]['quantity'])
        self.assertEqual('F1910040000a',resolve_identity('다진파','다진 대파를 넣는다.',{'recipeId':'RDA-DIET-89329-4'},self.nutrients)[0])
        self.assertEqual((None,None),resolve_identity('다진파','다진 대파를 넣는다.',{'recipeId':'OTHER'},self.nutrients))
    def test_02_canonical_generic_not_arbitrary_variety(self):
        code,_=resolve_identity('현미','현미를 씻어 밥을 짓는다.',{},self.nutrients)
        self.assertEqual('A008000A060a',code)
        self.assertEqual((None,None),resolve_identity('쇠고기','쇠고기를 볶는다.',{},self.nutrients))
        self.assertEqual((None,None),resolve_identity('땅콩','땅콩을 넣는다.',{},self.nutrients))
    def test_03_cooking_state(self):
        code,_=resolve_identity('쌀밥','',{},self.nutrients)
        self.assertEqual('멥쌀, 백미, 밥',self.nutrients[code]['name'])
        self.assertEqual((None,None),resolve_identity('고구마','찐 고구마를 으깬다.',{},self.nutrients))
    def test_04_food_specific_portions(self):
        sugar=portion_conversion('설탕',parse('설탕 1작은술')[0],'169655')
        oil=portion_conversion('참기름',parse('참기름 1작은술')[0],'N0200000009a')
        self.assertAlmostEqual(4.2,sugar['grams']);self.assertAlmostEqual(4.5,oil['grams'])
    def test_05_official_fallback(self):
        code,_=resolve_identity('건표고버섯','',{},self.nutrients)
        self.assertEqual('Mushrooms, shiitake, dried',self.nutrients[code]['name'])
        self.assertTrue(self.nutrients[code]['archiveUrl'].startswith('https://fdc.nal.usda.gov/'))
    def test_06_complete_detection(self):
        complete=[r for r in self.audit['audit'] if r['complete']]
        self.assertGreater(len(complete),13)
        for r in complete:
            for d in r['ingredients']:
                self.assertIn(d['status'],('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM'))
                if d['status']=='LINKED':self.assertGreater(d['amountGrams'],0);self.assertIn(d['nutrientId'],self.nutrients)
        self.assertEqual('UNLINKABLE',next(r for r in self.audit['audit'] if r['recipeId']=='MFDS-223')['status'])
    def test_07_partial_and_missing_inputs(self):
        for r in self.audit['audit']:
            if r['status']=='PARTIAL_LINKED':
                self.assertTrue(any(d['status']=='LINKED' for d in r['ingredients']))
                self.assertTrue(any(d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM') for d in r['ingredients']))
        k=next(r for r in self.audit['audit'] if r['recipeId']=='RDA-DIET-89289-0')
        self.assertFalse(k['complete'])
        self.assertTrue(any(d['ingredient']=='참기름' and d['amountGrams'] is None for d in k['ingredients']))
    def test_08_no_fake_amounts(self):
        for text in ('설탕 약간','설탕 1~2큰술','설탕 1컵','마늘 2개','식용유 1큰술'):
            self.assertIsNone(portion_conversion(text.split()[0],parse(text)[0],'169655'))
        for d in self.decisions:
            if d['quantity'] is None and d['status']=='LINKED':self.fail(d['originalSpan'])
    def test_09_all_provenance_and_original_516(self):
        baseline=read('baseline-recipe-final-audit.json')
        self.assertEqual({r['recipeId'] for r in baseline['audit']},{r['recipeId'] for r in self.audit['audit']})
        self.assertEqual(516,len(self.audit['audit']))
        for d in self.decisions:
            self.assertEqual(64,len(d['contextSha256']));self.assertTrue(d['identityReason']);self.assertTrue(d['unitReason'])
            if d['status']=='LINKED':
                self.assertTrue(d['nutritionProvenance']['sourceUrl'].startswith('https://'))
            if d['conversionProvenance']:
                c=d['conversionProvenance']
                if c.get('source')=='RDA_OFFICIAL_RECIPE_STANDARD_PORTION':
                    self.assertGreaterEqual(len({e['recipeId'] for e in c['recipeEquivalences']}),5)
                    self.assertEqual(1,len({round(e['gramsPerUnit'],8) for e in c['recipeEquivalences']}))
                else:self.assertTrue(c['portionRows']);self.assertEqual(64,len(c['archiveSha256']))
        for c in read('additional-source-captures.json'):
            if c['status']=='CAPTURED':self.assertEqual(c['sha256'],hashlib.sha256((ROOT/c['rawFile']).read_bytes()).hexdigest())
    def test_10_official_kcal_immutable(self):
        expected={'food_items.csv':'ccf6a9b2322ad5e75fe29f13ffb7c4bf719c2cca5cd3b459f104d659231261b4',
            'product_items.csv':'3f659d78f279b65115e17d8194bd686560bca018dea701aaa1a9585261bbe18e',
            'franchise_official_items.csv':'4a44591f8d8c6ce8276bee5449e90ced1ca513555c2920718ba9722e5ada06e3'}
        for name,digest in expected.items():self.assertEqual(digest,hashlib.sha256((ASSETS/name).read_bytes()).hexdigest())
        foods={r['id']:r for r in csvrows(ASSETS/'food_items.csv')}
        for row in self.references:
            self.assertEqual(foods[row['foodId']]['energyKcal'],row['foodReferenceKcal'])
            self.assertTrue(math.isfinite(float(row['amountGrams'])));self.assertGreater(float(row['amountGrams']),0)
if __name__=='__main__':unittest.main(verbosity=2)
