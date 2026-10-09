import json
import unittest
from pathlib import Path
from franchise_nutrition_meals import parse_burgerking,parse_isaac_not_published,parse_slowcali,slow_finished_rows,parse_kyochon

FIX=Path(__file__).resolve().parents[1]/'data-source/franchise-sync/fixtures'

class MealsNutritionTests(unittest.TestCase):
    def test_kyochon_uses_table_basis_not_raw_chicken_weight(self):
        menu=dict(brand='교촌치킨',name='반반윙콤비[레드+마라레드]',externalId='id:41581',
                  sourceUrl='https://www.kyochon.com/menu/view.asp?id=41581&cg=2')
        raw=(FIX/'kyochon-nutrition.html').read_bytes()
        fact=parse_kyochon(menu,raw)
        self.assertEqual(100,fact['servingAmount']);self.assertEqual(336,fact['energyKcal'])
        self.assertEqual(24,fact['proteinGrams']);self.assertEqual(440,fact['sodiumMilligrams'])
        self.assertIsNone(fact['carbohydrateGrams']);self.assertIsNone(fact['fatGrams'])
        with self.assertRaises(AssertionError):parse_kyochon(dict(menu,name='반반윙콤비[레드+허니]'),raw)
        with self.assertRaises(AssertionError):parse_kyochon(dict(menu,externalId='id:1'),raw)
        with self.assertRaises(AssertionError):parse_kyochon(menu,raw.replace(b'100g',b'100'))
    def test_burgerking_g_or_ml_is_not_guessed(self):
        f=parse_burgerking(dict(brand='버거킹',name='몬스터와퍼'),(FIX/'burgerking-nutrition.json').read_bytes())
        self.assertEqual(1094,f['energyKcal']);self.assertEqual(52,f['proteinGrams'])
        self.assertIsNone(f['servingAmount']);self.assertIsNone(f['fatGrams']);self.assertIsNone(f['carbohydrateGrams'])

    def test_burgerking_duplicate_name_is_withheld(self):
        with self.assertRaises(AssertionError):parse_burgerking(dict(brand='버거킹',name='해쉬브라운'),(FIX/'burgerking-nutrition.json').read_bytes())

    def test_slowcali_explicit_base_has_real_macros(self):
        f=parse_slowcali(dict(brand='슬로우캘리',name='연어 포케 (현미밥&샐러드)'),(FIX/'slowcali-nutrition.html').read_bytes())
        self.assertEqual(351,f['servingAmount']);self.assertEqual(627.8,f['energyKcal'])
        self.assertEqual(61.7,f['carbohydrateGrams']);self.assertEqual(9.9,f['fatGrams'])

    def test_slowcali_generic_or_ingredient_is_not_finished_variant(self):
        for name in ['연어 포케','연어 30g']:
            with self.assertRaises(AssertionError):parse_slowcali(dict(brand='슬로우캘리',name=name),(FIX/'slowcali-nutrition.html').read_bytes())
        self.assertEqual(45,len(slow_finished_rows((FIX/'slowcali-nutrition.html').read_bytes())))

    def test_not_published_requires_official_explicit_statement(self):
        self.assertTrue(parse_isaac_not_published(dict(brand='이삭토스트'),(FIX/'isaac-nutrition-faq.html').read_bytes())['notPublishedConfirmed'])
        with self.assertRaises(AssertionError):parse_isaac_not_published(dict(brand='이삭토스트'),b'<html>No nutrition table found</html>')

if __name__=='__main__':unittest.main()
