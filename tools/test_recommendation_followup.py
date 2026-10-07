"""Focused validation of this followup only; no Android full-suite execution."""
import hashlib
import json
import unittest
from collect_public_recipe_evidence import ROOT, OUT, CACHE, read_csv
from build_recommendation_ingredient_evidence import classify, composition_with_explicit_filling, recipe_rank, verdict

class RecommendationFollowupTest(unittest.TestCase):
    def test_public_source_requests_are_finite_cached_hashed_and_keyless(self):
        report=json.loads((OUT/'followup-public-diet-source-audit.json').read_text(encoding='utf-8'))
        self.assertEqual(30,report['indexPages'])
        self.assertEqual(268,report['indexedDiets'])
        self.assertFalse(report['apiKeyUsed'])
        self.assertFalse(report['authenticatedApiUsed'])
        facts=read_csv(OUT/'official-followup-public-recipe-facts.csv')
        self.assertEqual(102,len(facts))
        requests={r['cacheKey']:r for r in report['requests']}
        for r in requests.values():
            raw=(CACHE/(r['cacheKey']+'.html')).read_bytes()
            self.assertEqual(r['sha256'],hashlib.sha256(raw).hexdigest())
            self.assertEqual(r['bytes'],len(raw))
        self.assertEqual(102,len({r['recipeId']for r in facts}))
        self.assertTrue(all(r['mainIngredientText']and r['sourceUrl'].startswith('https://api.nongsaro.go.kr/sample/')for r in facts))

    def test_explicit_filling_is_restored_but_garnish_never_supplies_groups(self):
        source={r['recipeId']:r for r in read_csv(OUT/'official-public-recipe-facts.csv')}
        wind=source['RDA-90925']
        composition=composition_with_explicit_filling(wind)
        self.assertIn('거피팥 420g(2컵)',composition)
        self.assertEqual({'LEGUME_SOY','GRAIN_UNSPECIFIED'},classify(composition)[1])
        self.assertEqual(source['RDA-90306']['mainIngredientText'],composition_with_explicit_filling(source['RDA-90306']))
        fake=dict(mainIngredientText='쌀 300g',additionalIngredientText='<소> 두부 200g <고명> 브로콜리 200g, 잣 100g')
        parsed=composition_with_explicit_filling(fake)
        self.assertNotIn('브로콜리',parsed)
        self.assertEqual({'GRAIN_UNSPECIFIED','LEGUME_SOY'},classify(parsed)[1])

    def test_unitless_recipe_number_never_becomes_grams_or_style_eligibility(self):
        items,groups=classify('두부 25.3, 홍고추 0.5, 콩기름 1.4')
        self.assertTrue(items[0]['quantityUnitUnverified'])
        self.assertIsNone(items[0]['grams'])
        self.assertEqual((False,'SOURCE_MAJOR_QUANTITY_UNIT_UNVERIFIED'),
            verdict(groups,'UNKNOWN',{'REFERENCE_MAJOR_QUANTITY_UNIT_UNVERIFIED'}))
        unitless=dict(recipeId='RDA-DIET-X',name='떡만두국',mainIngredientText='두부 25.3',additionalIngredientText='')
        known=dict(recipeId='RDA-90742',name='떡만둣국',mainIngredientText='두부 100g',additionalIngredientText='')
        self.assertLess(recipe_rank(known,'떡만두국'),recipe_rank(unitless,'떡만두국'))

    def test_new_official_snack_and_optional_public_ingredients_are_bounded(self):
        rows={r['stableTemplateId']:r for r in read_csv(OUT/'verified-food-groups.csv')}
        greek=rows['kfind-catalog-d202-096000000-0010']
        self.assertEqual('BRAND_OFFICIAL_MAJOR_INGREDIENTS',greek['evidenceScope'])
        self.assertEqual({'DAIRY','POULTRY','VEGETABLE'},set(greek['foodGroups'].split('|')))
        self.assertEqual('true',greek['slowStyleEligible'])
        self.assertEqual('UNKNOWN',greek['allergenStatus'])
        wind=rows['kfind-catalog-d302-054000000-0001']
        self.assertIn('거피팥',wind['ingredients'].split('|'))
        self.assertEqual('true',wind['slowStyleEligible'])
        rolled=rows['kfind-catalog-d109-416000000-0001']
        self.assertEqual('EGG',rolled['foodGroups'])
        self.assertEqual('달걀',rolled['ingredients'])
        self.assertEqual('false',rows['kfind-catalog-d312-541000000-0001']['slowStyleEligible'])

    def test_292_stable_templates_and_strict_existing_policy_have_actual_growth(self):
        before=json.loads((OUT/'followup-baseline.json').read_text(encoding='utf-8'))
        after=json.loads((OUT/'followup-ingredient-evidence-summary.json').read_text(encoding='utf-8'))
        self.assertEqual(292,len(read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')))
        self.assertEqual(before['templateIdentitySha256'],hashlib.sha256((ROOT/'app/src/main/assets/fooddata/meal_templates.csv').read_bytes()).hexdigest())
        self.assertEqual((151,145,106,5),(before['linkedIngredients'],before['sourcedGroups'],before['styleEligible'],before['perMeal']['SNACK']))
        self.assertEqual((163,115,7),(after['after']['sourcedGroups'],after['after']['styleEligible'],after['after']['perMeal']['SNACK']))
        self.assertEqual([],after['removedStyleTemplateIds'])
        self.assertEqual(0,after['nutritionTransferred'])
        self.assertEqual(0,after['servingTransferred'])
        self.assertEqual(0,after['newHealthPolicyWeights'])

if __name__=='__main__':unittest.main()
