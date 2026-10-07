"""Ten targeted evidence and preservation tests for new bulk linkage."""
import csv,hashlib,json,math,unittest
from relink_recipe_strategy import OUT,ROOT,load,key,EXCLUDED,bulk_index,legacy_index,legacy_rule,state_rule

class StrategyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load(OUT/'baseline-ingredient-decisions.json');cls.after=load(OUT/'ingredient-decisions.json')
        cls.new=load(OUT/'newly-linked-ingredients.json');cls.legacy=legacy_index()
    def test_01_canonical_identity_and_frozen_decisions(self):
        fixed={key(d):d for d in self.before if d['status'] in ('LINKED',EXCLUDED)}
        self.assertEqual(2297,len(fixed))
        for d in self.after:
            if key(d) in fixed:self.assertEqual(fixed[key(d)],d)
        self.assertIsNone(legacy_rule('간장','',self.legacy)[0])
        self.assertIsNone(legacy_rule('후춧가루','',self.legacy)[0])
        self.assertEqual('MFDS-LEGACY-4487',legacy_rule('된장','',self.legacy)[0])
    def test_02_context_does_not_guess_variety(self):
        self.assertIsNone(legacy_rule('감자','감자를 담는다',self.legacy)[0])
        self.assertEqual('MFDS-LEGACY-545',legacy_rule('감자','감자는 껍질을 벗겨 썬다',self.legacy)[0])
        self.assertEqual('MFDS-LEGACY-545',legacy_rule('감자','감자는 반달모양으로 썬다',self.legacy)[0])
        self.assertIsNone(legacy_rule('파','양파는 채썰고 파프리카는 썬다',self.legacy)[0])
        self.assertEqual('MFDS-LEGACY-1676',legacy_rule('파','파는 어슷하게 썬다',self.legacy)[0])
        self.assertIsNone(state_rule('고춧잎','고춧잎을 담는다')[0])
        self.assertEqual('F019000B141a',state_rule('고춧잎','건고춧잎은 불려 헹군다')[0])
    def test_03_amount_source_fallback_keeps_original_amounts(self):
        for d,o in zip(self.after,self.before):
            if o['amountGrams']!=d['amountGrams']:
                c=d['conversionProvenance'];self.assertEqual('RDA_EXPLICIT_SAME_FORM_REFERENCE_PORTION',c['source'])
                self.assertEqual(o['quantity']*8,d['amountGrams']);self.assertEqual('큰술',o['unit'])
        jam=next(d for d in self.after if d['recipeId']=='MFDS-223')
        self.assertEqual('RECIPE_SOURCE_INCOMPLETE',jam['status'])
        rejected=[r for r in load(OUT/'amount-fallback-review.json') if r['sameIngredientOtherPhaseRejected']]
        self.assertEqual(1,len(rejected));self.assertEqual('NO_SAFE_AMOUNT',rejected[0]['amountEvidenceStatus'])
    def test_04_ingredient_specific_unit_conflicts_are_rejected(self):
        rows=load(OUT/'ingredient-specific-unit-review.json')
        self.assertEqual(338,len(rows))
        sesame=[r for r in rows if r['ingredient']=='깨소금' and r['unit']=='작은술']
        self.assertTrue(sesame)
        self.assertGreater(len(sesame[0]['reviewedOfficialEquivalence']['distinctGramsPerUnit']),1)
        self.assertEqual(6,sum(r['result']=='EXPLICIT_SAME_FORM_RDA_REFERENCE_PORTION' for r in rows))
    def test_05_bulk_index_serving_basis_and_dates(self):
        self.assertEqual(3672,len(load(OUT/'public-raw-nutrients.json')))
        self.assertEqual(3648,len(bulk_index())) # 24 blank energy rows are not zero-filled.
        n=self.legacy['MFDS-LEGACY-545'];self.assertEqual(2017,n['dataYear']);self.assertEqual(53,n['energyKcal'])
        for n in self.legacy.values():self.assertAlmostEqual(n['energyKcal'],n['originalServingKcal']/n['originalServingGrams']*100)
    def test_06_alternate_recipe_is_independent(self):
        alternatives=load(OUT/'alternate-official-reference-review.json')
        self.assertEqual(2,len(alternatives));self.assertTrue(all(not r['published'] for r in alternatives))
        self.assertEqual([300,120,None],[i['grams'] for i in alternatives[0]['inputs']])
        self.assertEqual(516,len(load(OUT/'recipe-final-audit.json')['audit']))
    def test_07_kcal_math_and_confidence(self):
        self.assertEqual(214,len(self.new))
        for d in self.new:
            self.assertIn(d['matchingConfidence'],('A','B','C'))
            self.assertTrue(math.isfinite(d['estimatedKcal']))
            self.assertAlmostEqual(d['amountGrams']*d['nutritionProvenance']['energyKcal']/100,d['estimatedKcal'])
    def test_08_provenance_all_residuals_once(self):
        a=load(OUT/'residual-attempts.json');self.assertEqual(1961,len(a));self.assertEqual(1961,len({key(d) for d in a}))
        for d in self.new:
            n=d['nutritionProvenance'];self.assertTrue(n['sourceUrl']);self.assertTrue(n.get('sourceSha256') or n.get('sha256'))
            self.assertEqual(d['sourceUrl'],d['amountProvenance']['sourceUrl'])
        for c in load(OUT/'source-captures.json'):
            if c.get('rawFile'):self.assertEqual(c['sha256'],hashlib.sha256((ROOT/c['rawFile']).read_bytes()).hexdigest())
        for c in load(OUT/'bulk-transform-provenance.json'):
            self.assertEqual(c['derivedSha256'],hashlib.sha256((OUT/c['file']).read_bytes()).hexdigest())
    def test_09_no_fake_ink_jam_or_kimbap_amount(self):
        gaps=load(OUT/'known-source-gaps.json')
        self.assertEqual([],gaps['webfootOctopusInk']['inkRows'])
        self.assertFalse(gaps['originalJam']['originalAmountOverridden'])
        rows=load(OUT/'kimbap-new-attempts.json');self.assertEqual(18,len(rows))
        self.assertFalse(any(r['linked'] for r in rows))
    def test_10_official_food_assets_and_complete_counts(self):
        expected={'food_items.csv':'ccf6a9b2322ad5e75fe29f13ffb7c4bf719c2cca5cd3b459f104d659231261b4',
            'product_items.csv':'3f659d78f279b65115e17d8194bd686560bca018dea701aaa1a9585261bbe18e',
            'franchise_official_items.csv':'4a44591f8d8c6ce8276bee5449e90ced1ca513555c2920718ba9722e5ada06e3'}
        for name,digest in expected.items():self.assertEqual(digest,hashlib.sha256((ROOT/'app/src/main/assets/fooddata'/name).read_bytes()).hexdigest())
        audit=load(OUT/'recipe-final-audit.json')['audit'];self.assertEqual(41,sum(r['complete'] for r in audit))
        for r in audit:self.assertEqual(r['complete'],all(d['status'] in ('LINKED',EXCLUDED) for d in r['ingredients']))

if __name__=='__main__':unittest.main(verbosity=2)
