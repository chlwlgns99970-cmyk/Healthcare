"""Focused checks for factual source parsing, exact identity and partial-evidence bounds."""
import unittest
from collections import Counter
from collect_public_recipe_evidence import OUT, ROOT, read_csv, plain
from build_recommendation_ingredient_evidence import classify, verdict

class RecommendationIngredientEvidenceTest(unittest.TestCase):
    def test_source_korean_section_labels_are_not_html(self):
        self.assertEqual('<만두소> 두부 150g, 숙주 100g', plain('<p>&lt;만두소&gt; 두부 150g, 숙주 100g</p>'))
        _, groups = classify('<만두소> 두부 150g, 숙주 100g')
        self.assertEqual({'LEGUME_SOY', 'VEGETABLE'}, groups)

    def test_milling_requires_actual_source_qualifier(self):
        self.assertEqual({'GRAIN_UNSPECIFIED'}, classify('쌀 200g')[1])
        self.assertEqual({'REFINED_GRAIN'}, classify('쌀(백미) 200g')[1])
        self.assertEqual({'WHOLE_GRAIN'}, classify('쌀(현미) 200g')[1])
        self.assertEqual({'MIXED_GRAIN'}, classify('보리 200g')[1])
        self.assertEqual(set(), classify('건강한 통곡물 샐러드')[1])

    def test_small_seasoning_does_not_supply_preferred_groups(self):
        _, groups = classify('두부 250g, 홍고추 10g, 다진 마늘 7g, 통깨 2g, 양파 10g')
        self.assertEqual({'LEGUME_SOY'}, groups)
        self.assertNotIn('NUT_SEED', classify('쌀 200g, 참깨 1큰술')[1])
        self.assertEqual({'NUT_SEED', 'GRAIN_UNSPECIFIED'}, classify('참깨 60g, 쌀 160g')[1])

    def test_meat_moderation_requires_real_preferred_ingredients(self):
        self.assertEqual({'RED_MEAT', 'LEGUME_SOY'}, classify('쇠고기 1g, 두부 200g')[1])
        self.assertEqual((False, 'VERIFIED_NO_PREFERRED_GROUP'), verdict({'RED_MEAT'}, 'SIMMERED', set()))
        self.assertEqual((True, 'ELIGIBLE'), verdict({'RED_MEAT', 'LEGUME_SOY', 'VEGETABLE'}, 'SIMMERED', set()))
        self.assertEqual((False, 'VERIFIED_DEEP_FRIED'), verdict({'LEGUME_SOY'}, 'DEEP_FRIED', set()))
        self.assertEqual((False, 'VERIFIED_PROCESSED_MEAT'), verdict({'VEGETABLE'}, 'PAN_COOKED', {'PROCESSED_MEAT'}))
        self.assertEqual((False, 'VERIFIED_SUGAR_OR_JUICE_MODERATION'), verdict({'FRUIT'}, 'UNHEATED', {'FRUIT_JUICE'}))

    def test_all292_stable_identities_have_a_reason_and_unchanged_unknowns(self):
        templates = read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
        research = read_csv(OUT/'recommendation-ingredient-research.csv')
        self.assertEqual(292, len(research))
        self.assertEqual({r['id'] for r in templates}, {r['stableTemplateId'] for r in research})
        self.assertTrue(all(r['reason'] and r['identityMatchReason'] for r in research))
        self.assertEqual({'UNKNOWN_REFERENCE_ONLY'}, {r['allergenStatus'] for r in research})
        self.assertEqual(Counter({'COMPLETE':36,'PARTIAL':1,'UNKNOWN':255}),
            Counter(r['ingredientCompleteness'] for r in read_csv(OUT/'recommendation-292-audit.csv')))
        self.assertEqual(36, sum('|INGREDIENTS_COMPLETE|' in r['tags'] for r in templates))

    def test_primary_provenance_never_uses_an_alternative_source_hash(self):
        recipes = {r['recipeId']:r for path in [OUT/'official-public-recipe-facts.csv',OUT/'official-followup-public-recipe-facts.csv']
            for r in read_csv(path)}
        for row in read_csv(OUT/'recommendation-ingredient-research.csv'):
            if row['researchStatus']=='SOURCED_PARTIAL_COMPOSITION':
                self.assertTrue(row['sourceReference'])
            if row['evidenceRecipeId'] in recipes:
                source = recipes[row['evidenceRecipeId']]
                self.assertEqual(source['sourceUrl'], row['sourceUrl'])
                self.assertEqual(source['sourceSha256'], row['sourceSha256'])
                self.assertEqual(source['mainIngredientText'], row['mainIngredientText'])
            elif row['sourceUrl']:
                self.assertEqual('', row['mainIngredientText'])
            if row['brand']:
                self.assertNotEqual('PUBLIC_REFERENCE_RECIPE', next((g['evidenceScope'] for g in
                    read_csv(OUT/'verified-food-groups.csv') if g['stableTemplateId']==row['stableTemplateId']), 'UNKNOWN'))

    def test_new_snacks_have_source_bean_filling_and_mung_bean_evidence(self):
        rows = {r['stableTemplateId']:r for r in read_csv(OUT/'verified-food-groups.csv')}
        for tid, rid in [('kfind-catalog-d309-415000000-0001','RDA-90877'),
                         ('kfind-catalog-d103-146000000-0001','RDA-90742')]:
            row = rows[tid]
            self.assertEqual(rid, row['evidenceRecipeId'])
            self.assertIn('LEGUME_SOY', row['foodGroups'].split('|'))
            self.assertEqual('true', row['slowStyleEligible'])
            self.assertEqual('REFERENCE_PARTIAL', row['ingredientStatus'])
            self.assertEqual('UNKNOWN', row['allergenStatus'])

if __name__=='__main__':unittest.main()
