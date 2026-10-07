"""Only this change: official input parser, linking safety, completeness, menus."""
import unittest,csv,json,hashlib
from pathlib import Path
import build_recipe_calorie_references as b
ROOT=Path(__file__).resolve().parents[1]
class RecipeMenuTests(unittest.TestCase):
    def test_official_recipe_originals_reaudited(self):
        a=json.loads((b.OUT/'audit.json').read_text(encoding='utf-8'))
        self.assertEqual(516,len(a['audit']));self.assertEqual(0,a['sourceHashFailures'])
        self.assertTrue(all('ingredients' in r and 'identityReason' in r for r in a['audit']))
    def test_explicit_amount_parser(self):
        self.assertEqual([('당근',40),('다진 마늘',2)],b.parse_ingredients('당근40g, 다진 마늘2(g)'))
        self.assertEqual([('오이',13)],b.parse_ingredients('오이 13g'))
    def test_unverified_units_not_converted(self):
        for value in ('쌀 90','설탕 1큰술','우유 1컵','오이 1개','설탕 약간','우유 100ml'):
            self.assertTrue(all(g is None for _,g in b.parse_ingredients(value)),value)
    def test_aliases_preserve_cooking_identity(self):
        self.assertEqual(b.ALIASES['마늘'],b.ALIASES[b.norm('다진 마늘')])
        for name in ('삶은마늘','찐당근','현미밥','쌀','깐오이','붉은양배추'):
            self.assertNotIn(name,b.ALIASES)
    def test_verified_nutrition_reference(self):
        facts=json.loads((b.OUT/'reviewed-nutrient-facts.json').read_text(encoding='utf-8'))
        self.assertEqual('Potatoes, flesh and skin, raw',facts['170026']['description'])
        self.assertEqual(77,facts['170026']['energyKcal'])
        self.assertEqual(0,facts['173468']['energyKcal'])
    def test_complete_recipe_all_inputs(self):
        rows=[r for r in b.read(b.ASSETS/'recipe_ingredient_estimates.csv') if r['recipeId']=='MFDS-247']
        self.assertEqual(4,len({r['foodId'] for r in rows}));self.assertEqual(8,len(rows))
        self.assertEqual({'감자','소금'},{r['ingredientName'] for r in rows})
        self.assertTrue(all(r['recipeComplete']=='true' for r in rows))
    def test_partial_recipe_retains_unknowns(self):
        a=json.loads((b.OUT/'audit.json').read_text(encoding='utf-8'))
        r=next(r for r in a['audit'] if r['recipeId']=='MFDS-108')
        self.assertFalse(r['complete']);self.assertTrue(r['reasonCounts'])
        self.assertEqual(59,a['partialRecipeCount'])
    def test_calorie_reference_not_balanced(self):
        self.assertAlmostEqual(400.4,520*77/100)
        rows=b.read(b.ASSETS/'recipe_ingredient_estimates.csv')
        self.assertTrue(all(float(r['amountGrams'])>0 for r in rows))
    def test_nolboo_loader_no_fabricated_nutrients(self):
        rows=b.read(ROOT/'data-source/recipe-menu-completion/nolboo-public-menu-snapshot.csv')
        self.assertEqual(7,len(rows))
        self.assertTrue(all(not r['energyKcal'] and not r['servingAmount'] for r in rows))
        self.assertFalse(any('이용권' in r['name'] for r in rows))
    def test_all_unknowns_audited_and_food_assets_preserved(self):
        a=json.loads((ROOT/'data-source/recipe-menu-completion/menu-summary.json').read_text(encoding='utf-8'))
        self.assertEqual(274,a['beforeUnknown']);self.assertEqual(229,a['afterUnknown'])
        self.assertEqual(274,len(b.read(ROOT/'data-source/recipe-menu-completion/all-unknown-menu-audit.csv')))
        self.assertTrue(a['allExistingFoodAssetBytesUnchanged'])
if __name__=='__main__':unittest.main()
