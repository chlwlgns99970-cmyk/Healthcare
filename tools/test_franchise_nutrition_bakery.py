import json
import unittest
from pathlib import Path
from franchise_nutrition_bakery import parse_paris, parse_tlj

FIXTURES=Path(__file__).resolve().parents[1]/'data-source/franchise-sync/fixtures'
def fixture(label):
    return json.loads((FIXTURES/('nutrition-'+label+'-menu.json')).read_text(encoding='utf8')),(FIXTURES/('nutrition-'+label+'-detail.html')).read_bytes()

class BakeryNutritionTest(unittest.TestCase):
    def test_paris_published_total_basis(self):
        m,p=fixture('paris'); f=parse_paris(m,p)
        self.assertEqual((f['energyKcal'],f['servingAmount'],f['proteinGrams'],f['sodiumMilligrams']),(875,240,18,820))
        self.assertIsNone(f['fatGrams']);self.assertIsNone(f['carbohydrateGrams'])
    def test_tlj_published_basis_not_hardcoded_summary(self):
        m,p=fixture('tlj'); f=parse_tlj(m,p)
        self.assertEqual((f['energyKcal'],f['servingAmount'],f['proteinGrams'],f['sodiumMilligrams']),(190,70,5,160))
        self.assertIsNone(f['fatGrams']);self.assertIsNone(f['carbohydrateGrams'])
    def test_per100_not_whole_package_weight(self):
        m,p=fixture('paris-per100');f=parse_paris(m,p)
        self.assertEqual((f['energyKcal'],f['servingAmount'],f['proteinGrams']),(335,100,11))
    def test_serving_calories_not_whole_cake_mass(self):
        m,p=fixture('paris-serving');f=parse_paris(m,p)
        self.assertEqual(f['energyKcal'],370);self.assertEqual(1,f['servingAmount']);self.assertEqual('인분',f['servingUnit'])
    def test_less_than_is_not_an_exact_protein_value(self):
        m,p=fixture('paris');p=p.replace('단백질(g): 18'.encode(),'단백질(g): 1g 미만'.encode())
        self.assertIsNone(parse_paris(m,p)['proteinGrams'])
    def test_impossible_published_kcal_unit_is_withheld(self):
        m,p=fixture('paris-per100');p=p.replace(b': 335',b': 1995')
        with self.assertRaisesRegex(AssertionError,'physical upper bound'):parse_paris(m,p)
    def test_product_identity_rejected(self):
        for label,parser in [('paris',parse_paris),('tlj',parse_tlj)]:
            m,p=fixture(label);m['externalId']='wrong'
            with self.assertRaises(AssertionError):parser(m,p)
    def test_variant_or_truncated_title_rejected(self):
        for label,parser in [('paris',parse_paris),('tlj',parse_tlj)]:
            m,p=fixture(label);m['name']=m['name'][:3]+'...'
            with self.assertRaises(AssertionError):parser(m,p)
    def test_missing_weight_not_zero_or_assumed(self):
        m,p=fixture('tlj');p=p.replace(b'70',b'')
        f=parse_tlj(m,p);self.assertIsNone(f['servingAmount'])
    def test_explicit_zero_kcal_preserved(self):
        m,p=fixture('paris');p=p.replace(b': 875',b': 0')
        self.assertEqual(parse_paris(m,p)['energyKcal'],0)
if __name__=='__main__': unittest.main()
