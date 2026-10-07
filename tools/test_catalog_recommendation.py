"""Only the new official composition and exact measured-choice review."""
import hashlib
import json
import unittest
from collect_catalog_recommendation_sources import OUT, RAW, SOURCES
from collect_public_recipe_evidence import ROOT, read_csv
from build_recommendation_ingredient_evidence import classify, verdict
from build_catalog_recommendation_portions import DECISIONS

class CatalogRecommendationTest(unittest.TestCase):
    def test_nine_requests_are_finite_public_hashed_and_offline_replayable(self):
        report=json.loads((OUT/'source-request-audit.json').read_text(encoding='utf-8'))
        self.assertEqual(9,report['finitePublishedRecords'])
        self.assertEqual(set(SOURCES),{r['cacheFile'] for r in report['requests']})
        self.assertFalse(report['apiKeyUsed']);self.assertFalse(report['authenticationUsed'])
        for row in report['requests']:
            raw=(RAW/row['cacheFile']).read_bytes()
            self.assertEqual(row['bytes'],len(raw))
            self.assertEqual(row['sha256'],hashlib.sha256(raw).hexdigest())

    def test_parenthesized_mass_is_explicit_but_bare_number_remains_unknown(self):
        parsed,groups=classify('두부 94.7(g), 배추김치 31.5(g), 돼지고기 12.8(g)')
        self.assertEqual([94.7,31.5,12.8],[i['grams'] for i in parsed])
        self.assertFalse(any(i['quantityUnitUnverified'] for i in parsed))
        self.assertEqual({'LEGUME_SOY','VEGETABLE','RED_MEAT'},groups)
        unitless,_=classify('두부 94.7, 배추김치 31.5')
        self.assertTrue(all(i['grams'] is None and i['quantityUnitUnverified'] for i in unitless))
        self.assertEqual((False,'SOURCE_MAJOR_QUANTITY_UNIT_UNVERIFIED'),verdict(groups,'UNKNOWN',{'REFERENCE_MAJOR_QUANTITY_UNIT_UNVERIFIED'}))

    def test_exact_official_wrap_never_inherits_neighbor_product_beans_or_frying(self):
        row=next(r for r in read_csv(OUT/'reviewed-group-evidence.csv') if r['brand'])
        self.assertEqual('뚜레쥬르',row['brand'])
        self.assertEqual('또띠아_베지텐더밸런스랩',row['originalFoodName'])
        self.assertEqual({'식물성 텐더','채소'},set(row['ingredients'].split('|')))
        self.assertEqual('VEGETABLE',row['foodGroups'])
        self.assertEqual('UNKNOWN',row['allergenStatus'])
        self.assertNotIn('LEGUME_SOY',row['foodGroups']);self.assertNotIn('DEEP_FRIED',row['cookingStyle'])
        self.assertIn('담백아삭 베지랩',row['notes'])

    def test_all_292_identities_and_existing_weights_have_actual_breakfast_snack_growth(self):
        report=json.loads((OUT/'summary.json').read_text(encoding='utf-8'))
        self.assertEqual((163,115),(report['before']['sourcedGroups'],report['before']['styleEligible']))
        self.assertEqual((168,120),(report['after']['sourcedGroups'],report['after']['styleEligible']))
        self.assertEqual({'BREAKFAST':48,'LUNCH':105,'DINNER':106,'SNACK':8},report['after']['perMeal'])
        self.assertEqual([],report['removedStyleTemplateIds'])
        self.assertEqual(0,report['policyWeightsChanged'])
        self.assertEqual(0,report['nutritionTransferred']);self.assertEqual(0,report['servingTransferredFromRecipes'])
        research=read_csv(ROOT/'data-source/recommendation/recommendation-ingredient-research.csv')
        self.assertEqual(292,len(research));self.assertEqual(292,len({r['stableTemplateId'] for r in research}))
        self.assertEqual(124,sum(not r['ingredients'] for r in research))
        self.assertTrue(all(r['allergenStatus']=='UNKNOWN_REFERENCE_ONLY' for r in research))

    def test_ten_source_portions_have_eight_measured_choices_and_two_preserved_bowl_choices(self):
        review=read_csv(OUT/'portion-rejudgement.csv');evidence=read_csv(OUT/'portion-evidence.csv')
        self.assertEqual(set(DECISIONS),{r['sourceFoodCode'] for r in review})
        self.assertEqual(8,len(evidence))
        self.assertTrue(all(r['recommendationReferenceUnit']=='g' for r in evidence))
        for row in review:
            self.assertEqual('False',row['healthMaximum']);self.assertEqual('False',row['blanketCapApplied'])
            if row['decision']=='RETAIN_DISCRETE_HOUSEHOLD_CHOICES':
                self.assertEqual('105|210|315',row['selectedCandidateGrams'])
                self.assertEqual('공기',row['householdUnitPreserved'])
                self.assertNotIn(row['sourceFoodCode'],{r['sourceFoodCode'] for r in evidence})
        old=read_csv(ROOT/'data-source/food-quality/followup-recommendation-portion-evidence.csv')
        self.assertEqual({'어탕':'150','멸치볶음':'50'},{r['name']:r['recommendationReferenceAmount'] for r in old})

if __name__=='__main__':unittest.main()
