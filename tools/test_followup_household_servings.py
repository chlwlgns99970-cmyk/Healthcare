"""Focused new tests for exact household-source joins; no old suites are rerun."""
import copy
import json
import unittest
from pathlib import Path
from audit_followup_household_servings import (
    CACHE, DATA, ROOT, fdc_evidence, maeil_evidence, read, verified_cache,
)

class FollowupHouseholdSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.foods = sum([read(ROOT/'app/src/main/assets/fooddata'/name) for name in
                        ('food_items.csv','product_items.csv','franchise_official_items.csv')],[])
        cls.raw = read(DATA/'raw-identity-fields.csv')
        cls.review = json.loads((DATA/'followup-household-reviewed-sources.json').read_text(encoding='utf-8'))
        cls.manifest = verified_cache()

    def test_all_thirteen_fdc_servings_join_exact_codes_and_keep_sizes(self):
        rows, facts = fdc_evidence(self.foods,self.review,self.manifest)
        self.assertEqual(13,len(rows))
        self.assertEqual(13,len({row['foodItemId'] for row in rows}))
        for row,fact in zip(rows,facts):
            self.assertEqual(row['sourceFoodCode'],'FDC-'+fact['originalPortion']['fdc_id'])
            self.assertIn(fact['originalPortion']['modifier'],row['servingSourceSize'])
            self.assertEqual('g',row['basisUnit'])
            self.assertEqual('VERIFIED_CONVERSION',row['servingEvidenceKind'])

    def test_ten_grapes_measure_is_divided_by_explicit_original_amount(self):
        rows,_ = fdc_evidence(self.foods,self.review,self.manifest)
        grapes=next(row for row in rows if row['sourceFoodCode']=='FDC-174683')
        self.assertEqual('4.9',grapes['basisAmountPerUnit'])
        self.assertEqual('10 grapes = 49g',grapes['fullUnitPhrase'])

    def test_wrong_fdc_portion_identity_is_rejected(self):
        review=copy.deepcopy(self.review)
        review['fdcSelections'][0]['portionId']='92500'
        with self.assertRaisesRegex(ValueError,'identity/modifier'):
            fdc_evidence(self.foods,review,self.manifest)

    def test_two_report_numbers_prove_each_factory_and_pack(self):
        servings,labels=maeil_evidence(self.foods,self.raw,self.review,self.manifest)
        self.assertEqual(2,len(servings))
        self.assertEqual({'19810227007211','20000441043301'}, {label['productReportNumber'] for label in labels})
        self.assertTrue(all(row['householdUnit']=='팩' and row['basisAmountPerUnit']=='190' and row['basisUnit']=='ml' for row in servings))

    def test_composite_raw_ingredients_and_cross_contact_are_preserved_separately(self):
        _,labels=maeil_evidence(self.foods,self.raw,self.review,self.manifest)
        for label in labels:
            self.assertEqual('COMPLETE_DECLARATION',label['ingredientStatus'])
            self.assertEqual(label['ingredientText'],label['completeIngredientText'])
            self.assertIn('외국산(미국, 캐나다, 러시아 등)',label['ingredientText'])
            self.assertEqual('대두',label['allergens'])
            self.assertNotIn('밀',label['allergens'])
            self.assertIn('밀',label['mayContainAllergens'])
            self.assertIn('토마토, 복숭아',label['crossContactText'])
            self.assertEqual('LEGUME_SOY',label['foodGroups'])
            self.assertEqual('EXACT_FULL_PRODUCT_LABEL',label['foodGroupEvidenceScope'])

    def test_another_product_size_is_rejected_instead_of_borrowing_pack(self):
        raw=copy.deepcopy(self.raw)
        next(row for row in raw if row['품목제조보고번호']=='20000441043301')['식품중량']='950ml'
        with self.assertRaisesRegex(ValueError,'total mismatch'):
            maeil_evidence(self.foods,raw,self.review,self.manifest)

    def test_source_hash_change_cannot_keep_reviewed_full_declaration(self):
        manifest=copy.deepcopy(self.manifest)
        manifest['maeil-99-9-declaration.jpg']['sha256']='different'
        with self.assertRaisesRegex(ValueError,'reviewed ingredient label'):
            maeil_evidence(self.foods,self.raw,self.review,manifest)

    def test_generated_evidence_never_promotes_an_unresolved_generic_kimbap(self):
        fdc,_=fdc_evidence(self.foods,self.review,self.manifest)
        maeil,_=maeil_evidence(self.foods,self.raw,self.review,self.manifest)
        self.assertFalse(any(row['sourceFoodCode'].startswith(('D401-007','D501-007')) for row in fdc+maeil))
        self.assertTrue(all(row['sourceFoodCode'] for row in fdc+maeil))

if __name__=='__main__':
    unittest.main()
