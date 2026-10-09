import copy
import json
import unittest
from pathlib import Path

from franchise_nutrition import parse_starbucks, parse_hansot, parse_mcdonalds, parse_dunkin, parse_salady, parse_ediya, parse_pokeallday, dunkin_pages, refresh, validate_nutrition

FIX = Path(__file__).resolve().parents[1] / 'data-source/franchise-sync/fixtures'


class NutritionTests(unittest.TestCase):
    def test_daily_source_discovery_uses_official_nutrition_links_only(self):
        from discover_franchise_nutrition_sources import discover
        brand=dict(brandId='b',name='공식 브랜드',sourceUrl='https://official.example/',allowedHosts=['official.example'])
        markup=b'<a href="/nutrition">Nutrition</a><a href="https://blog.example/nutrition">Nutrition</a><a href="/promotion">\xec\x98\x81\xec\x96\x91</a>'
        rows=discover([brand],out=None,fetcher=lambda _:markup)
        self.assertEqual([dict(url='https://official.example/nutrition',label='Nutrition')],rows[0]['links'])

    def test_reviewed_snapshot_retains_capture_date_after_network_timeout(self):
        import franchise_nutrition as n
        from unittest.mock import patch
        captured=json.loads(n.REVIEWED.read_text(encoding='utf-8'))[0]
        menu={k:v for k,v in captured.items() if k!='officialNutrition'}
        sources=[dict(brandId=menu['brandId'],adapter='starbucks-product-jsonld',allowedHosts=['www.starbucks.co.kr'])]
        def fail(_):raise TimeoutError('temporary network timeout')
        rows,audit=refresh([menu],sources,fail)
        self.assertEqual(captured['officialNutrition'],rows[0]['officialNutrition'])
        self.assertEqual(1,audit['reviewedSnapshotsRetained']);self.assertEqual(1,audit['failed'])
        def mismatch(_):raise AssertionError('official product name changed')
        rows,audit=refresh([menu],sources,mismatch)
        self.assertNotIn('officialNutrition',rows[0]);self.assertEqual(0,audit['reviewedSnapshotsRetained'])

    def test_poke_finished_menu_not_same_name_ingredient(self):
        fact=parse_pokeallday(dict(brand='포케올데이',name='육회'),(FIX/'poke-nutrition.html').read_bytes())
        self.assertEqual(811.63,fact['energyKcal']);self.assertEqual(493,fact['servingAmount']);self.assertEqual(6,fact['nutritionGroup'])

    def test_poke_exact_hot_ice_variant(self):
        with self.assertRaises(AssertionError):parse_pokeallday(dict(brand='포케올데이',name='아메리카노 (Hot)'),(FIX/'poke-nutrition.html').read_bytes())
        fact=parse_pokeallday(dict(brand='포케올데이',name='카페라떼(ICE)'),(FIX/'poke-nutrition.html').read_bytes())
        self.assertEqual(120,fact['energyKcal']);self.assertIsNone(fact['servingUnit']);self.assertIn('16oz',fact['servingDescription'])

    def test_poke_different_topping_is_not_base_menu(self):
        with self.assertRaises(AssertionError):parse_pokeallday(dict(brand='포케올데이',name='들기름 메밀면 샐러드 + 육회'),(FIX/'poke-nutrition.html').read_bytes())

    def test_ediya_exact_product_weight_and_nullable_macros(self):
        fact=parse_ediya(dict(brand='이디야',name='잉글리쉬 머핀',externalId='58'),(FIX/'ediya-nutrition-food.html').read_bytes())
        self.assertEqual(278,fact['energyKcal']);self.assertEqual(140,fact['servingAmount']);self.assertEqual(13,fact['proteinGrams']);self.assertEqual(770,fact['sodiumMilligrams'])
        self.assertIsNone(fact['fatGrams']);self.assertIsNone(fact['carbohydrateGrams'])

    def test_ediya_cup_capacity_is_not_assumed_liquid_basis(self):
        fact=parse_ediya(dict(brand='이디야',name='(L) HOT 가나 시그니처 초콜릿',externalId='1319'),(FIX/'ediya-nutrition-drink.html').read_bytes())
        self.assertEqual(548,fact['energyKcal']);self.assertIsNone(fact['servingAmount']);self.assertIsNone(fact['servingUnit'])

    def test_ediya_wrong_variant_not_matched(self):
        with self.assertRaises(AssertionError):parse_ediya(dict(brand='이디야',name='(L) ICE 가나 시그니처 초콜릿',externalId='1319'),(FIX/'ediya-nutrition-drink.html').read_bytes())

    def test_dunkin_id_match_keeps_saturated_fat_unknown(self):
        rows=json.loads((FIX/'dunkin-nutrition-rows.json').read_text(encoding='utf-8'));row=next(r for r in rows if r['NUTRITION_KCAL'] not in ('-',None))
        fact=parse_dunkin(dict(brand='던킨',name=row['TITLE'],externalId='products:'+str(row['id'])),(FIX/'dunkin-nutrition-rows.json').read_bytes())
        self.assertIsNone(fact['fatGrams']);self.assertIsNone(fact['carbohydrateGrams']);self.assertEqual('g',fact['servingUnit'])

    def test_dunkin_renamed_product_not_auto_matched(self):
        with self.assertRaises(AssertionError):parse_dunkin(dict(brand='던킨',name='다른 이름',externalId='products:5563'),(FIX/'dunkin-nutrition-rows.json').read_bytes())

    def test_dunkin_incomplete_pagination_fails_closed(self):
        page={'props':{'productsNutrition':{'data':[],'meta':{'current_page':1,'path':'https://www.dunkindonuts.co.kr/nutrition','last_page':1,'total':3}}}}
        import html
        markup=('<div id="app" data-page="'+html.escape(json.dumps(page),quote=True)+'"></div>').encode()
        with self.assertRaises(AssertionError):dunkin_pages('https://www.dunkindonuts.co.kr/nutrition',lambda _:markup)

    def test_salady_pdf_exact_columns_basis_and_brand_scope(self):
        fact=parse_salady(dict(brand='샐러디',name='탄단지 샐러디'),(FIX/'salady-nutrition.pdf').read_bytes())
        self.assertEqual(225,fact['servingAmount']);self.assertEqual(322.9,fact['energyKcal']);self.assertEqual(34.3,fact['carbohydrateGrams']);self.assertEqual(13.1,fact['fatGrams'])

    def test_salady_hot_ambiguity_withholds_recording(self):
        with self.assertRaises(AssertionError):parse_salady(dict(brand='샐러디',name='아메리카노'),(FIX/'salady-nutrition.pdf').read_bytes())

    def test_found_source_without_parser_is_not_missing(self):
        rows,audit=refresh([self.menu()],registry=[dict(brandId='b',adapter=None,evidenceStatus='SOURCE_FOUND')])
        self.assertEqual('NUTRITION_SOURCE_FOUND_UNMATCHED',rows[0]['nutritionStatus'])

    def menu(self, brand='스타벅스', name='오트 콜드 브루', code='9200000003285', prefix='drink'):
        return dict(id='same-stable-id', brandId='b', brand=brand, name=name,
                    externalId=prefix+':'+code, sourceUrl='https://www.starbucks.co.kr/menu/'+prefix+'_view.do?product_cd='+code,
                    energyKcal=None, carbohydrateGrams=None, proteinGrams=None, fatGrams=None)

    def test_drink_exact_id_basis(self):
        result=parse_starbucks(self.menu(),(FIX/'starbucks-nutrition-drink.html').read_bytes())
        self.assertEqual(110,result['energyKcal']);self.assertEqual(355,result['servingAmount']);self.assertEqual('ml',result['servingUnit'])

    def test_food_real_carbs_and_fat_not_sugar_or_saturated(self):
        result=parse_starbucks(self.menu(name='스타벅스 치즈 쿠키',code='9300000006428',prefix='food'),(FIX/'starbucks-nutrition-food.html').read_bytes())
        self.assertEqual(70,result['energyKcal']);self.assertEqual(13,result['servingAmount']);self.assertEqual(8,result['carbohydrateGrams']);self.assertEqual(3.7,result['fatGrams'])

    def test_wrong_product_cannot_borrow_nutrition(self):
        with self.assertRaises(AssertionError):parse_starbucks(self.menu(code='wrong'),(FIX/'starbucks-nutrition-drink.html').read_bytes())

    def test_wrong_name_cannot_borrow_same_id(self):
        with self.assertRaises(AssertionError):parse_starbucks(self.menu(name='아이스 다른 메뉴'),(FIX/'starbucks-nutrition-drink.html').read_bytes())

    def test_hansot_calorie_without_basis_not_recordable(self):
        menu=dict(brand='한솥',name='한솥 돈부리소스',externalId='707',sourceUrl='https://www.hsd.co.kr/menu/menu_view/707')
        fact=parse_hansot(menu,(FIX/'hansot-nutrition.html').read_bytes())
        self.assertEqual(67,fact['energyKcal']);self.assertIsNone(fact['servingAmount'])

    def test_mc_exact_piece_variant(self):
        fact=parse_mcdonalds(dict(brand='맥도날드',name='맥너겟® 4조각'),(FIX/'mc-nutrition.json').read_bytes())
        self.assertEqual(163,fact['energyKcal']);self.assertEqual(64,fact['servingAmount']);self.assertEqual(10,fact['proteinGrams']);self.assertIsNone(fact['fatGrams'])

    def test_mc_wrong_variant_unmatched(self):
        with self.assertRaises(AssertionError):parse_mcdonalds(dict(brand='맥도날드',name='맥너겟® 5조각'),(FIX/'mc-nutrition.json').read_bytes())

    def test_mc_range_not_average_and_basis_not_invented(self):
        fact=parse_mcdonalds(dict(brand='맥도날드',name='빅맥® 라지 세트'),(FIX/'mc-nutrition.json').read_bytes())
        self.assertIsNone(fact['energyKcal']);self.assertIsNone(fact['servingAmount'])

    def test_missing_source_is_not_not_published(self):
        menu=self.menu();rows,audit=refresh([menu],registry=[])
        self.assertEqual('NUTRITION_SOURCE_MISSING',rows[0]['nutritionStatus']);self.assertEqual(0,audit['recordable']);self.assertIsNone(rows[0]['energyKcal'])

    def test_failure_retains_official_facts_and_id(self):
        menu=self.menu();menu['officialNutrition']=dict(energyKcal=100,servingAmount=355)
        sources=[dict(brandId='b',adapter='starbucks-product-jsonld',allowedHosts=['www.starbucks.co.kr'])]
        def fail(_):raise TimeoutError('temporary timeout')
        rows,audit=refresh([menu],sources,fail)
        self.assertEqual(menu['id'],rows[0]['id']);self.assertEqual(menu['officialNutrition'],rows[0]['officialNutrition']);self.assertEqual(1,audit['failed'])

    def test_legacy_values_are_not_overwritten(self):
        menu=self.menu();sources=[dict(brandId='b',adapter='starbucks-product-jsonld',allowedHosts=['www.starbucks.co.kr'])]
        rows,_=refresh([menu],sources,lambda _: (FIX/'starbucks-nutrition-drink.html').read_bytes())
        for k,v in menu.items():self.assertEqual(v,rows[0][k])
        self.assertEqual('NUTRITION_COMPLETE',rows[0]['nutritionStatus'])

    def test_invalid_negative_nan_and_bad_source_rejected(self):
        rows,_=refresh([self.menu()],[dict(brandId='b',adapter='starbucks-product-jsonld',allowedHosts=['www.starbucks.co.kr'])],lambda _: (FIX/'starbucks-nutrition-drink.html').read_bytes())
        fact=rows[0]['officialNutrition']
        for value in (-1,float('nan'),float('inf')):
            bad=copy.deepcopy(fact);bad['energyKcal']=value
            with self.assertRaises(AssertionError):validate_nutrition(bad,['www.starbucks.co.kr'])
        bad=copy.deepcopy(fact);bad['sourceUrl']='https://blog.example/menu'
        with self.assertRaises(AssertionError):validate_nutrition(bad,['www.starbucks.co.kr'])


if __name__=='__main__':unittest.main()
