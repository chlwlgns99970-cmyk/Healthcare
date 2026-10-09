import json
import unittest
from pathlib import Path
from franchise_nutrition_cafes import parse_mega,parse_hollys,parse_dalkomm,parse_paik,parse_gongcha,parse_pascucci
FIX=Path(__file__).resolve().parents[1]/'data-source/franchise-sync/fixtures'

class CafeNutritionTests(unittest.TestCase):
    def test_official_fixtures_keep_independent_nulls_and_basis(self):
        for key,parser in [('mega',parse_mega),('hollys',parse_hollys),('dalkomm',parse_dalkomm),('paik',parse_paik),('gongcha',parse_gongcha),('pascucci',parse_pascucci)]:
            with self.subTest(brand=key):
                m=json.loads((FIX/(key+'-nutrition-menu.json')).read_text(encoding='utf-8'))
                f=parser(m,(FIX/(key+'-nutrition.html')).read_bytes())
                for field in ('energyKcal','proteinGrams','fatGrams','carbohydrateGrams','sodiumMilligrams','servingAmount','servingUnit'):
                    self.assertEqual(m['officialNutrition'][field],f[field])
                with self.assertRaises(AssertionError):parser(dict(m,name=m['name']+' 다른맛'),(FIX/(key+'-nutrition.html')).read_bytes())

    def test_cup_capacity_is_not_serving_for_paik(self):
        m=json.loads((FIX/'paik-nutrition-menu.json').read_text(encoding='utf-8'))
        f=parse_paik(m,(FIX/'paik-nutrition.html').read_bytes())
        self.assertIsNone(f['servingAmount']);self.assertIsNone(f['fatGrams']);self.assertIsNone(f['carbohydrateGrams'])

if __name__=='__main__':unittest.main()
