"""One focused source-data test; no recommendation-policy or UI changes."""
import hashlib
import unittest
from extract_food_identity_fields import ROOT, OUT, SOURCE_URL, extract, rows

class RecommendationPortionEvidenceTest(unittest.TestCase):
    def test_two_exact_source_food_weights_do_not_become_nutrition_or_household_basis(self):
        expected={'D305-239000000-0001':('어탕','150g','150','2014-09-30'),
                  'D110-472000000-0001':('멸치볶음','50g','50','2018-12-31')}
        source=ROOT/'data-source/kfind/kfind-food-db-2026-08-28.xlsx'
        official,_=extract(source,expected)
        official={r['식품코드']:r for r in official}
        raw={r['식품코드']:r for r in rows(OUT/'raw-identity-fields.csv')if r['식품코드']in expected}
        foods={r['sourceFoodCode']:r for r in rows(ROOT/'app/src/main/assets/fooddata/food_items.csv')if r['sourceFoodCode']in expected}
        proof=rows(OUT/'followup-recommendation-portion-evidence.csv')
        self.assertEqual(2,len(proof));self.assertEqual(set(expected),{r['sourceFoodCode']for r in proof})
        for r in proof:
            code=r['sourceFoodCode'];name,weight,amount,date=expected[code];src=official[code];food=foods[code]
            self.assertEqual((name,weight,date),(src['식품명'],src['식품중량'],src['데이터생성일자']))
            self.assertEqual(weight,raw[code]['식품중량'])
            self.assertEqual('100g',src['영양성분함량기준량'])
            self.assertEqual(('100','g'),(food['referenceAmount'],food['unit']))
            self.assertEqual(food['id'],r['foodItemId']);self.assertEqual(name,r['name'])
            self.assertEqual((amount,'g'),(r['recommendationReferenceAmount'],r['recommendationReferenceUnit']))
            self.assertEqual(('100','g'),(r['nutritionReferenceAmount'],r['nutritionReferenceUnit']))
            self.assertEqual(('', ''),(src['1인(회)분량 참고량'],src['1회 섭취참고량']))
            self.assertEqual(('', ''),(r['rawPersonServingReference'],r['rawIntakeReference']))
            self.assertEqual(date,r['sourceDate']);self.assertEqual(SOURCE_URL,r['sourceUrl'])
            self.assertEqual(SOURCE_URL,r['recommendationSourceReference'])
            self.assertIn(code,r['identityEvidence']);self.assertIn('COLUMN:식품중량',r['identityEvidence'])
            self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest().upper(),r['sourceSha256'])
            self.assertEqual('EXACT_OFFICIAL_FOOD_WEIGHT_REFERENCE_NOT_HOUSEHOLD_SERVING',r['evidenceKind'])
            self.assertNotIn(r['recommendationReferenceUnit'],['인분','그릇','접시'])

if __name__=='__main__':unittest.main()
