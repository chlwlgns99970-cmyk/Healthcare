"""Ten targeted checks for residual linkage; frozen linked rows remain identical."""
import unittest,json,hashlib,math,csv
from pathlib import Path
from relink_recipe_residuals import OUT,ROOT,EXCLUDED,identity_rule,additional_portion,key
from maximize_recipe_evidence import additional_nutrients

def load(name):return json.loads((OUT/name).read_text(encoding='utf-8'))
class ResidualTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load('baseline-ingredient-decisions.json');cls.after=load('ingredient-decisions.json')
        cls.new=load('newly-linked-ingredients.json');cls.audit=load('recipe-final-audit.json')
    def test_01_preserved_linked_and_excluded(self):
        fixed={key(d):d for d in self.before if d['status'] in ('LINKED',EXCLUDED)}
        self.assertEqual(2226,len(fixed))
        for d in self.after:
            if key(d) in fixed:self.assertEqual(fixed[key(d)],d)
    def test_02_all_residual_inputs_attempted_once(self):
        wanted={key(d) for d in self.before if d['status'] not in ('LINKED',EXCLUDED)}
        attempts=load('residual-attempts.json')
        self.assertEqual(2032,len(attempts));self.assertEqual(wanted,{key(a) for a in attempts})
        self.assertTrue(all(a['sameRecipeSourceVerified'] for a in attempts))
    def test_03_state_and_exact_identity(self):
        self.assertEqual('172430',identity_rule('생땅콩','',[])[0])
        self.assertIsNone(identity_rule('땅콩','',[])[0])
        self.assertEqual('G0080000001a',identity_rule('목이버섯','목이버섯은 불려서 씻는다.',[])[0])
        self.assertIsNone(identity_rule('목이버섯','목이버섯을 넣는다.',[])[0])
        self.assertIsNone(identity_rule('통깨','생 통깨를 씻는다.',[])[0])
        self.assertIsNone(identity_rule('파','파를 넣는다.',[])[0])
    def test_04_amounts_from_original_recipe(self):
        for d in self.new:
            self.assertGreater(d['amountGrams'],0)
            self.assertEqual(d['sourceUrl'],d['amountProvenance']['sourceUrl'])
            if not d.get('conversionProvenance'):
                self.assertIn(d['unit'],('g','kg'))
                self.assertEqual(d['quantity']*(1000 if d['unit']=='kg' else 1),d['amountGrams'])
    def test_05_food_specific_unit_and_modifier(self):
        d=dict(ingredient='다진 양파',unit='큰술',quantity=2,quantityRange=None,identityStatus='LINKED')
        c=additional_portion(d);self.assertEqual(20,c['grams'])
        self.assertEqual('tbsp chopped',c['portionRows'][0]['modifier'])
        self.assertIsNone(additional_portion(d|{'ingredient':'양파'}))
        self.assertIsNone(additional_portion(d|{'unit':'컵'}))
    def test_06_generic_nutrition_fallback_and_calculation(self):
        n=additional_nutrients()
        self.assertEqual('Seeds, sesame seeds, whole, roasted and toasted',n['170151']['name'])
        self.assertEqual('Beans, adzuki, mature seeds, raw',n['173727']['name'])
        for d in self.new:
            value=d['nutritionProvenance']['energyKcal']
            self.assertTrue(math.isfinite(value))
            self.assertAlmostEqual(d['amountGrams']*value/100,d['estimatedKcal'])
    def test_07_complete_and_partial_independent(self):
        self.assertEqual(516,len(self.audit['audit']))
        for r in self.audit['audit']:
            complete=all(d['status'] in ('LINKED',EXCLUDED) for d in r['ingredients'])
            self.assertEqual(complete,r['complete'])
            if r['status']=='PARTIAL_LINKED':
                self.assertTrue(any(d['status']=='LINKED' for d in r['ingredients']));self.assertFalse(complete)
        self.assertGreaterEqual(self.audit['recipeStates']['COMPLETE_LINKED'],29)
        chosen=load('selected-complete-references.json')
        self.assertEqual(1,len(chosen))
        recipe=next(r for r in self.audit['audit'] if r['recipeId']==chosen[0]['recipeId'])
        self.assertTrue(recipe['complete']);self.assertTrue(recipe['published'])
        self.assertEqual('SELECTED_COMPLETE_OFFICIAL_REFERENCE',recipe['identityReason'])
        self.assertFalse(recipe['foodIdentityUnique'])
    def test_08_provenance_and_definitions_hashes(self):
        for c in load('source-captures.json'):
            if c['status']=='CAPTURED':self.assertEqual(c['sha256'],hashlib.sha256((ROOT/c['rawFile']).read_bytes()).hexdigest())
        for d in self.new:
            self.assertTrue(d['nutritionProvenance']['sourceUrl'].startswith('https://'))
            self.assertTrue(d['sourceSha256']);self.assertTrue(d['checkedAt'])
            if d['ingredient']=='통깨':self.assertEqual(2,len(d['identityProvenance']))
    def test_09_no_fake_values_or_circular_jam(self):
        for d in self.after:
            if d['quantity'] is None:self.assertIsNone(d['amountGrams'])
        jam=next(d for d in self.after if d['recipeId']=='MFDS-223')
        self.assertEqual('RECIPE_SOURCE_INCOMPLETE',jam['status'])
        ink=next(d for d in self.after if d['ingredient']=='주꾸미먹물')
        self.assertEqual('NO_OFFICIAL_NUTRITION_MATCH',ink['status'])
    def test_10_official_food_kcal_ids_servings_unchanged(self):
        assets=ROOT/'app/src/main/assets/fooddata'
        expected={'food_items.csv':'ccf6a9b2322ad5e75fe29f13ffb7c4bf719c2cca5cd3b459f104d659231261b4',
          'product_items.csv':'3f659d78f279b65115e17d8194bd686560bca018dea701aaa1a9585261bbe18e',
          'franchise_official_items.csv':'4a44591f8d8c6ce8276bee5449e90ced1ca513555c2920718ba9722e5ada06e3'}
        for name,digest in expected.items():self.assertEqual(digest,hashlib.sha256((assets/name).read_bytes()).hexdigest())
        with (assets/'food_items.csv').open(encoding='utf-8-sig',newline='') as f:foods={r['id']:r for r in csv.DictReader(f)}
        with (assets/'recipe_ingredient_estimates.csv').open(encoding='utf-8-sig',newline='') as f:
            for r in csv.DictReader(f):
                self.assertEqual(foods[r['foodId']]['energyKcal'],r['foodReferenceKcal'])
                self.assertEqual(foods[r['foodId']]['referenceAmount'],r['foodReferenceAmount'])
                self.assertEqual(foods[r['foodId']]['unit'],r['foodReferenceUnit'])
if __name__=='__main__':unittest.main(verbosity=2)
