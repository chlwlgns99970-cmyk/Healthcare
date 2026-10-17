import copy
import json
import unittest
from pathlib import Path

from franchise_nutrition import parse_starbucks, parse_hansot, parse_mcdonalds, parse_dunkin, parse_salady, parse_ediya, parse_pokeallday, dunkin_pages, refresh, validate_nutrition

FIX = Path(__file__).resolve().parents[1] / 'data-source/franchise-sync/fixtures'


class NutritionTests(unittest.TestCase):
    def setUp(self):
        # Synthetic provider policies must not consume the installed real index.
        # Full indexed replay/publication is checked with the real 5,142 rows.
        from unittest.mock import patch
        replay = patch('franchise_nutrition.load_reviewed_nutrition',
                       side_effect=lambda menus, registry: (copy.deepcopy(menus), []))
        replay.start()
        self.addCleanup(replay.stop)

    def test_dunkin_inline_renderer_mapping_and_failure(self):
        import hashlib
        import html
        from franchise_nutrition import retain_verified_on_conflict
        url = 'https://www.dunkindonuts.co.kr/nutrition'
        asset = 'https://www.dunkindonuts.co.kr/build/assets/View-ce4a3a4a.js'
        # Exact bindings independently GET-verified on 2026-10-14, renderer SHA
        # 055ac6bd580a30e5945d5366d63da45c86dc001d82edd5ed2ed889df374956fb.
        renderer = ('[{accessorKey:"NUTRITION_SERVING",header:"1회 제공량(g)",cell:()=>0},'
                    '{accessorKey:"NUTRITION_SUGARS",header:"당류(g)",cell:()=>0},'
                    '{accessorKey:"NUTRITION_FAT",header:"포화지방(g)",cell:()=>0}]').encode()
        row = dict(id=1, TITLE='정확한 제품', NUTRITION_SERVING='100',
                   NUTRITION_KCAL='145', NUTRITION_SUGARS='3.5', NUTRITION_FAT='0')
        menu = dict(id='stable', brand='던킨', name=row['TITLE'], externalId='products:1')
        def capture(body=renderer, linked=True, changed=None):
            page = {'props': {'productsNutrition': {'data': [dict(row, **(changed or {}))],
                    'meta': dict(current_page=1, path=url, last_page=1, total=1)}}}
            markup = (('<link rel="modulepreload" href="'+asset+'">' if linked else '') +
                      '<div id="app" data-page="'+html.escape(json.dumps(page), quote=True)+'"></div>').encode()
            calls = []
            def get(target):
                calls.append(target)
                self.assertIn(target, (url, asset))
                if target == url:
                    return markup
                if isinstance(body, Exception):
                    raise body
                return body
            result = parse_dunkin(menu, dunkin_pages(url, get))
            self.assertEqual([url, asset] if linked else [url], calls)
            self.assertEqual(145, result['energyKcal'])
            self.assertIsNone(result['carbohydrateGrams'])
            self.assertIsNone(result['fatGrams'])
            return result
        fact = capture()
        self.assertEqual((3.5, 0), (fact['sugarsGrams'], fact['saturatedFatGrams']))
        self.assertEqual(asset, fact['optionalFieldEvidence']['sugarsGrams']['sourceUrl'])
        self.assertEqual(hashlib.sha256(renderer).hexdigest(), fact['optionalFieldEvidence']['sugarsGrams']['sourceSha256'])
        for body, linked in ((renderer, False), (b'', True), (TimeoutError(), True),
                             (b'x'*262145, True),
                             (renderer.replace('포화지방'.encode(), '지방'.encode()), True)):
            missing = capture(body, linked)
            self.assertIsNone(missing['sugarsGrams'])
            self.assertIsNone(missing['saturatedFatGrams'])
            merged, conflicts = retain_verified_on_conflict(dict(menu, officialNutrition=fact), missing)
            self.assertFalse(conflicts)
            self.assertEqual(0, merged['saturatedFatGrams'])
            self.assertEqual(fact['optionalFieldEvidence']['saturatedFatGrams'],
                             merged['retainedFieldEvidence']['saturatedFatGrams']['renderer'])
        for value in (None, '', '-', '-1', '3~5', '4%', True, 'NaN', 'Infinity', '100001'):
            result = capture(changed=dict(NUTRITION_SUGARS=value))
            self.assertIsNone(result['sugarsGrams'])
            self.assertEqual(0, result['saturatedFatGrams'])
        self.assertEqual(0, capture(changed=dict(NUTRITION_SUGARS='0'))['sugarsGrams'])
        for value in (None, '0', '-1', '10001', '100~140', True, 'Infinity'):
            result = capture(changed=dict(NUTRITION_SERVING=value))
            self.assertIsNone(result['sugarsGrams'])
            self.assertIsNone(result['saturatedFatGrams'])

    def test_dunkin_inline_unverified_columns_and_exact_identity(self):
        # Public GET 2026-10-14: embedded record, no published column labels.
        row = dict(id=5563, TITLE='히비스커스&패션후르츠 펀치',
                   NUTRITION_SERVING='401', NUTRITION_KCAL='145',
                   NUTRITION_SUGARS='31', NUTRITION_FAT='0',
                   NUTRITION_PROTEIN='0', NUTRITION_NATRIUM='4',
                   _sourceUrl='https://www.dunkindonuts.co.kr/nutrition',
                   _sourceSha256='6810c53392ed469f78373097bfa0d23cbcd49067d975e60a018e2b16c8a37e56')
        menu = dict(brand='던킨', name=row['TITLE'], externalId='products:5563')
        fact = parse_dunkin(menu, json.dumps([row]))
        for key in ('carbohydrateGrams', 'fatGrams', 'sugarsGrams', 'saturatedFatGrams'):
            self.assertIsNone(fact[key])
        self.assertEqual((145, 0, 4, 401, 'g'), tuple(fact[k] for k in
                         ('energyKcal', 'proteinGrams', 'sodiumMilligrams', 'servingAmount', 'servingUnit')))
        for changed in (dict(menu, externalId='productCats:5563'),
                        dict(menu, externalId='products:5562'),
                        dict(menu, name='히비스커스')):
            with self.assertRaises(AssertionError):
                parse_dunkin(changed, json.dumps([row]))
        with self.assertRaises(AssertionError):
            parse_dunkin(menu, json.dumps([row, row]))
        for basis in (None, '-', '0', '100~140', '1개-약20, 세트-143'):
            result = parse_dunkin(menu, json.dumps([dict(row, NUTRITION_SERVING=basis)]))
            self.assertIsNone(result['servingAmount'])
            self.assertIsNone(result['servingUnit'])

    def test_optional_inline_nullable_exact_numeric_validation(self):
        fact = dict(sourceUrl='https://www.dunkindonuts.co.kr/nutrition',
                    checkedAt='2026-10-14T00:00:00Z', sourceSha256='a'*64,
                    servingAmount=401, servingUnit='g')
        hosts = ['www.dunkindonuts.co.kr']
        validate_nutrition(fact, hosts)  # Missing optional fields are unknown.
        for key in ('sugarsGrams', 'saturatedFatGrams'):
            for value in (None, 0, 3.5, 100000):
                validate_nutrition(dict(fact, **{key: value}), hosts)
            for value in (-1, True, '0', '3.5', '3~5', '4%', float('nan'), float('inf'), 100001):
                with self.subTest(key=key, value=value), self.assertRaises(AssertionError):
                    validate_nutrition(dict(fact, **{key: value}), hosts)
        # Optional fields do not require the other optional nutrient or macros.
        validate_nutrition(dict(fact, sugarsGrams=0, saturatedFatGrams=None), hosts)
        validate_nutrition(dict(fact, sugarsGrams=None, saturatedFatGrams=3.5), hosts)

    def test_optional_inline_explicit_serving_basis(self):
        fact = dict(sourceUrl='https://www.dunkindonuts.co.kr/nutrition',
                    checkedAt='2026-10-14T00:00:00Z', sourceSha256='a'*64)
        hosts = ['www.dunkindonuts.co.kr']
        validate_nutrition(fact, hosts)
        for key in ('sugarsGrams', 'saturatedFatGrams'):
            for amount, unit in ((None, None), (None, 'g'), (0, 'g'), (-1, 'g'),
                                 (True, 'g'), ('401', 'g'), (float('inf'), 'g'),
                                 (10001, 'g'), (401, None), (401, 'cup')):
                with self.subTest(key=key, amount=amount, unit=unit), self.assertRaises(AssertionError):
                    validate_nutrition(dict(fact, servingAmount=amount, servingUnit=unit, **{key: 0}), hosts)
            validate_nutrition(dict(fact, servingAmount=10000, servingUnit='ml', **{key: 0}), hosts)

    def test_optional_inline_known_preservation_and_conflicts(self):
        from franchise_nutrition import retain_verified_on_conflict, FIELDS
        self.assertEqual(('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams', 'sodiumMilligrams'), FIELDS)
        old = dict(energyKcal=145, sugarsGrams=0, saturatedFatGrams=3.5,
                   servingAmount=401, servingUnit='g', sourceUrl='https://official/a',
                   checkedAt='2026-10-01T00:00:00Z', sourceSha256='a'*64)
        menu = dict(id='stable', officialNutrition=old)
        new = dict(old, sugarsGrams=None, sourceUrl='https://official/b')
        del new['saturatedFatGrams']
        merged, conflicts = retain_verified_on_conflict(menu, new)
        self.assertFalse(conflicts)
        for key in ('sugarsGrams', 'saturatedFatGrams'):
            self.assertEqual(old[key], merged[key])
            self.assertEqual(old['sourceUrl'], merged['retainedFieldEvidence'][key]['sourceUrl'])
        for change in (dict(sugarsGrams=1), dict(saturatedFatGrams=0),
                       dict(servingAmount=100), dict(servingUnit='ml'),
                       dict(servingAmount=None), dict(servingUnit=None)):
            retained, conflicts = retain_verified_on_conflict(menu, dict(old, **change))
            self.assertEqual(old, retained)
            self.assertTrue(conflicts)
        unknown = dict(old, sugarsGrams=None, saturatedFatGrams=None)
        filled, conflicts = retain_verified_on_conflict(dict(id='stable', officialNutrition=unknown), old)
        self.assertFalse(conflicts)
        self.assertEqual(0, filled['sugarsGrams'])
        # A candidate cannot add optional facts to an old unidentified basis.
        unknown['servingAmount'] = None
        retained, conflicts = retain_verified_on_conflict(dict(id='stable', officialNutrition=unknown), old)
        self.assertEqual(unknown, retained)
        self.assertTrue(conflicts)

    def test_product_id_resolves_nutrition_name_difference_without_assuming_basis(self):
        from franchise_nutrition import parse_mcdonalds_product
        menu=dict(brand='맥도날드',name='아메리카노 Medium',externalId='28')
        f=parse_mcdonalds_product(menu,(FIX/'mc-product-nutrition.json').read_bytes())
        self.assertEqual(12,f['energyKcal']);self.assertIsNone(f['servingAmount'])
        self.assertIsNone(f['fatGrams']);self.assertIsNone(f['carbohydrateGrams'])
        with self.assertRaises(AssertionError):parse_mcdonalds_product(dict(menu,name='아메리카노 Large'),(FIX/'mc-product-nutrition.json').read_bytes())
        with self.assertRaises(AssertionError):parse_mcdonalds_product(dict(menu,externalId='999999'),(FIX/'mc-product-nutrition.json').read_bytes())

    def test_verified_conflict_keeps_original_and_reports_sources(self):
        from franchise_nutrition import retain_verified_on_conflict
        old=dict(energyKcal=100, servingAmount=50, servingUnit='g', sourceUrl='https://official/a')
        new=dict(old, energyKcal=120, sourceUrl='https://official/b')
        fact, conflicts=retain_verified_on_conflict(dict(id='stable',officialNutrition=old),new)
        self.assertEqual(old,fact)
        self.assertEqual('stable',conflicts[0]['menuId'])
        self.assertEqual('RETAIN_VERIFIED_PENDING_REVIEW',conflicts[0]['decision'])

    def test_refresh_fills_null_without_erasing_known_zero(self):
        from franchise_nutrition import retain_verified_on_conflict
        old=dict(energyKcal=0, proteinGrams=None, servingAmount=100, servingUnit='ml',
                 sourceUrl='https://official/a',checkedAt='2026-10-01T00:00:00Z',sourceSha256='a'*64)
        new=dict(old, energyKcal=None, proteinGrams=2, sourceUrl='https://official/b')
        fact, conflicts=retain_verified_on_conflict(dict(id='stable',officialNutrition=old),new)
        self.assertEqual(0,fact['energyKcal']);self.assertEqual(2,fact['proteinGrams'])
        self.assertEqual(old['sourceUrl'],fact['retainedFieldEvidence']['energyKcal']['sourceUrl'])
        self.assertFalse(conflicts)

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
        with self.assertRaises(AssertionError):parse_pokeallday(dict(brand='포케올데이',name='아메리카노'),(FIX/'poke-nutrition.html').read_bytes())
        fact=parse_pokeallday(dict(brand='포케올데이',name='카페라떼(ICE)'),(FIX/'poke-nutrition.html').read_bytes())
        self.assertEqual(120,fact['energyKcal']);self.assertEqual('oz',fact['servingUnit']);self.assertEqual(16,fact['servingAmount'])

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


class ReviewedReplayInlineTests(unittest.TestCase):
    def exact_batches(self):
        n, menu, fact, _, registry = self.fixture()
        registry[0]['name'] = menu['brand']
        def row(index, enriched=False):
            identity = dict(menu, id='stable-'+str(index), externalId=str(index),
                            name='Exact product '+str(index), sourceUrl='https://official.example/product/'+str(index))
            identity.pop('brandId')
            nutrition = dict(fact, officialProductId=str(index), sourceUrl=identity['sourceUrl'])
            if enriched:
                nutrition.update(sugarsGrams=0, saturatedFatGrams=1, sourceSha256='d'*64,
                                 checkedAt='2026-10-02T00:00:00Z')
            return dict(identity, officialNutrition=nutrition)
        first = [dict(row(i), addedFields=['energyKcal', 'proteinGrams']) for i in range(77)]
        second = [row(i, True) for i in range(76)] + [row(i, True) for i in range(77, 843)]
        batches = [dict(schemaVersion=1, environment='DEVELOPMENT_ONLY_NOT_PUBLISHED',
                        baseCatalogSha256=sha*64, menus=rows) for sha, rows in (('b', first), ('c', second))]
        catalog = {r['id']: dict({k: v for k, v in r.items() if k not in ('officialNutrition', 'addedFields')},
                                brandId='b') for r in first + second}
        return n, list(catalog.values()), batches, registry

    def test_exact_77_842_incremental_replay_and_immutability(self):
        n, menus, batches, registry = self.exact_batches()
        self.assertEqual([77, 842], [len(b['menus']) for b in batches])
        self.assertEqual(76, len({r['id'] for r in batches[0]['menus']} & {r['id'] for r in batches[1]['menus']}))
        before = copy.deepcopy((menus, batches, registry))
        rows, audit = n.apply_reviewed_nutrition(menus, batches, registry)
        self.assertEqual(before, (menus, batches, registry))
        self.assertEqual(843, len(rows)); self.assertEqual(919, len(audit))
        fact = rows[0]['officialNutrition']
        self.assertEqual(100, fact['energyKcal']); self.assertEqual(0, fact['sugarsGrams'])
        self.assertIsNone(fact['fatGrams'])
        self.assertEqual('a'*64, fact['sourceSha256'])
        self.assertEqual('d'*64, fact['retainedFieldEvidence']['sugarsGrams']['sourceSha256'])
        self.assertNotIn('sugarsGrams', rows[76]['officialNutrition'])
        self.assertEqual(rows, n.apply_reviewed_nutrition(rows, batches, registry)[0])
        third = dict(baseCatalogSha256='e'*64, menus=[])
        self.assertEqual(rows, n.apply_reviewed_nutrition(menus, batches+[third], registry)[0])

    def test_cross_batch_conflicts_fail_before_apply(self):
        n, menu, fact, batches, registry = self.fixture()
        batches[1]['menus'] = [copy.deepcopy(batches[0]['menus'][0])]
        for key, value in (('brand', 'other'), ('brandId', 'other'), ('name', 'other'),
                           ('externalId', '43'), ('sourceUrl', menu['sourceUrl']+'?other')):
            bad = copy.deepcopy(batches); bad[1]['menus'][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], bad, registry)
        for key, value in (('energyKcal', 0), ('proteinGrams', 3), ('servingAmount', 200), ('servingUnit', 'g')):
            bad = copy.deepcopy(batches); bad[1]['menus'][0]['officialNutrition'][key] = value
            before = copy.deepcopy((menu, bad, registry))
            with self.subTest(key=key), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], bad, registry)
            self.assertEqual(before, (menu, bad, registry))
        # A third batch must also be checked against fields first acquired in the second.
        batches[1]['menus'][0]['officialNutrition']['sugarsGrams'] = 0
        third = copy.deepcopy(batches[1]); third['menus'][0]['officialNutrition']['sugarsGrams'] = 1
        with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], batches+[third], registry)

    def test_exact_format_within_batch_duplicates(self):
        n, menus, batches, registry = self.exact_batches()
        for index in (0, 1):
            bad = copy.deepcopy(batches)
            bad[index]['menus'].append(copy.deepcopy(bad[index]['menus'][0]))
            before = copy.deepcopy((menus, bad, registry))
            with self.subTest(batch=index), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition(menus, bad, registry)
            self.assertEqual(before, (menus, bad, registry))

    def test_exact_schema_and_brand_binding_guards(self):
        n, menu, _, batches, registry = self.fixture()
        registry[0]['name'] = menu['brand']
        row = batches[0]['menus'][0]; del row['brandId']
        self.assertEqual('b', n.apply_reviewed_nutrition([menu], batches, registry)[0][0]['brandId'])
        for policies in ([], [dict(registry[0], name='공 식')], [dict(registry[0], name='공식 ')],
                         registry+registry, registry+[dict(registry[0], brandId='other')],
                         registry+[dict(registry[0], name='other')], [dict(registry[0], brandId='')]):
            with self.subTest(policies=policies), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], batches, policies)
        for change in (dict(brandId='wrong'), dict(externalId=''), dict(externalId=None),
                       dict(addedFields=['invented']), dict(addedFields='energyKcal'),
                       dict(addedFields=['energyKcal', 'energyKcal']), dict(unrecognized=True)):
            bad = copy.deepcopy(batches); bad[0]['menus'][0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], bad, registry)
        exact = dict(batches[0], schemaVersion=1, environment='DEVELOPMENT_ONLY_NOT_PUBLISHED')
        for change in (dict(schemaVersion=True), dict(schemaVersion=2), dict(environment='PRODUCTION'), dict(extra=1)):
            with self.subTest(change=change), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], [dict(exact, **change), batches[1]], registry)
        for key in ('schemaVersion', 'environment'):
            bad = dict(exact); del bad[key]
            with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], [bad, batches[1]], registry)

    def test_optional_third_path_requires_both_hash_pinned_originals(self):
        import hashlib
        n, _, _, batches, _ = self.fixture()
        payloads = {path: json.dumps(batch).encode() for path, batch in
                    zip(n.REVIEWED_PATCH_PATHS, batches+[dict(baseCatalogSha256='e'*64, menus=[])])}
        manifest = dict(version=1, patches=[dict(path=p, sha256=hashlib.sha256(v).hexdigest())
                                           for p, v in payloads.items()])
        self.assertEqual(3, len(n.validate_reviewed_manifest(manifest, payloads)))
        for entries in (manifest['patches'][1:], manifest['patches'][:1]+manifest['patches'][2:],
                        manifest['patches']+[manifest['patches'][2]]):
            with self.assertRaises(ValueError): n.validate_reviewed_manifest(dict(version=1, patches=entries), payloads)
        changed = dict(payloads); changed[n.REVIEWED_PATCH_PATHS[2]] += b' '
        with self.assertRaises(ValueError): n.validate_reviewed_manifest(manifest, changed)
        for raw in (b'{"schemaVersion":1,"schemaVersion":1}', b'{"menus":[{"id":"a","id":"b"}]}'):
            with self.assertRaises(ValueError): n._reviewed_json(raw)

    def fixture(self):
        import franchise_nutrition as n
        menu = dict(id='stable', brandId='b', brand='공식', name='라떼 ICE Large',
                    externalId='42', sourceUrl='https://official.example/product/42')
        fact = dict(energyKcal=100, proteinGrams=2, carbohydrateGrams=None,
                    fatGrams=None, sodiumMilligrams=None, servingAmount=100, servingUnit='ml',
                    officialProductId='42', sourceUrl=menu['sourceUrl'],
                    checkedAt='2026-10-01T00:00:00Z', sourceSha256='a'*64)
        batches = [dict(baseCatalogSha256='b'*64, menus=[dict(menu, officialNutrition=fact)]),
                   dict(baseCatalogSha256='c'*64, menus=[])]
        registry = [dict(brandId='b', allowedHosts=['official.example'])]
        return n, menu, fact, batches, registry

    def test_compact_supplied_brand_id_with_original_nameless_registry(self):
        n, menu, fact, batches, registry = self.fixture()
        self.assertEqual([dict(brandId='b', allowedHosts=['official.example'])], registry)
        before = copy.deepcopy((menu, batches, registry))
        rows, _ = n.apply_reviewed_nutrition([menu], batches, registry)
        self.assertEqual(fact, rows[0]['officialNutrition'])
        self.assertEqual(before, (menu, batches, registry))
        for policies in ([], registry + registry,
                         [dict(registry[0], brandId='other')],
                         [dict(registry[0], name='other')],
                         registry + [dict(registry[0], name=menu['brand'], brandId='other')]):
            with self.subTest(policies=policies), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], batches, policies)
        missing = copy.deepcopy(batches)
        del missing[0]['menus'][0]['brandId']
        with self.assertRaises(ValueError):
            n.apply_reviewed_nutrition([menu], missing, registry)
        versioned = copy.deepcopy(batches)
        versioned[0].update(schemaVersion=1, environment='DEVELOPMENT_ONLY_NOT_PUBLISHED')
        with self.assertRaises(ValueError):
            n.apply_reviewed_nutrition([menu], versioned, registry)

    def test_manifest_hash_path_and_shape(self):
        import hashlib
        n, _, _, batches, _ = self.fixture()
        payloads = {p: json.dumps(b).encode() for p, b in zip(n.REVIEWED_PATCH_PATHS, batches)}
        manifest = dict(version=1, patches=[dict(path=p, sha256=hashlib.sha256(v).hexdigest())
                                           for p, v in payloads.items()])
        self.assertEqual(batches, n.validate_reviewed_manifest(manifest, payloads))
        bads = [None, {}, dict(manifest, version=True), dict(manifest, patches=[])]
        for path in ('../linked-menus.json', '/absolute', n.REVIEWED_PATCH_PATHS[0].replace('/', '\\'),
                     'data-source/other/linked-menus.json'):
            bad = copy.deepcopy(manifest); bad['patches'][0]['path'] = path; bads.append(bad)
        for sha in ('a'*63, 'A'*64, 'z'*64, None, '0'*64):
            bad = copy.deepcopy(manifest); bad['patches'][0]['sha256'] = sha; bads.append(bad)
        bad = copy.deepcopy(manifest); bad['patches'][1] = bad['patches'][0]; bads.append(bad)
        for bad in bads:
            with self.subTest(manifest=bad), self.assertRaises(ValueError):
                n.validate_reviewed_manifest(bad, payloads)
        duplicated = b'{"menus":[],"menus":[],"baseCatalogSha256":"'+b'a'*64+b'"}'
        changed = dict(payloads); changed[n.REVIEWED_PATCH_PATHS[0]] = duplicated
        bad = copy.deepcopy(manifest); bad['patches'][0]['sha256'] = hashlib.sha256(duplicated).hexdigest()
        with self.assertRaises(ValueError): n.validate_reviewed_manifest(bad, changed)

    def test_complete_validation_before_application(self):
        n, menu, _, batches, registry = self.fixture()
        original = copy.deepcopy(menu)
        second = copy.deepcopy(batches[0]['menus'][0]); second['id'] = 'second'
        second['officialNutrition']['proteinGrams'] = '2'
        batches[1]['menus'] = [second]
        with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], batches, registry)
        self.assertEqual(original, menu)
        batches[1]['menus'] = [copy.deepcopy(batches[0]['menus'][0])]
        self.assertEqual(1, len(n.apply_reviewed_nutrition([menu], batches, registry)[0]))
        batches[0]['menus'].append(copy.deepcopy(batches[0]['menus'][0]))
        with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], batches, registry)
        batches[0]['menus'].pop()
        for bad in (None, {}, dict(baseCatalogSha256='x', menus=[]),
                    dict(baseCatalogSha256='a'*64, menus={})):
            with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], [batches[0], bad], registry)

    def test_partial_immutable_provenance_and_idempotence(self):
        n, menu, fact, batches, registry = self.fixture()
        before = copy.deepcopy((menu, batches))
        rows, _ = n.apply_reviewed_nutrition([menu], batches, registry)
        self.assertEqual(before, (menu, batches))
        self.assertEqual(fact, rows[0]['officialNutrition'])
        self.assertEqual(fact, n.apply_reviewed_nutrition([dict(menu, officialNutrition=None)],
                                                       batches, registry)[0][0]['officialNutrition'])
        self.assertIsNone(rows[0]['officialNutrition']['fatGrams'])
        repeated, _ = n.apply_reviewed_nutrition(rows, batches, registry)
        self.assertEqual(rows, repeated)
        old = dict(fact, energyKcal=0, proteinGrams=None, checkedAt='2026-09-01T00:00:00Z', sourceSha256='d'*64)
        candidate = batches[0]['menus'][0]['officialNutrition']; candidate['energyKcal'] = None
        seeded, _ = n.apply_reviewed_nutrition([dict(menu, officialNutrition=old)], batches, registry)
        merged = seeded[0]['officialNutrition']
        self.assertEqual(0, merged['energyKcal']); self.assertEqual(2, merged['proteinGrams'])
        self.assertEqual(old['checkedAt'], merged['checkedAt'])
        self.assertEqual(old['sourceSha256'], merged['sourceSha256'])
        self.assertEqual(fact['checkedAt'], merged['retainedFieldEvidence']['proteinGrams']['checkedAt'])
        self.assertEqual(fact['sourceSha256'], merged['retainedFieldEvidence']['proteinGrams']['sourceSha256'])
        self.assertEqual(seeded, n.apply_reviewed_nutrition(seeded, batches, registry)[0])

    def test_conflicts_and_exact_identity_skips(self):
        n, menu, fact, batches, registry = self.fixture()
        for key, value in (('energyKcal', 0), ('servingUnit', 'g'), ('servingAmount', 200)):
            old = dict(fact, **{key: value})
            rows, audit = n.apply_reviewed_nutrition([dict(menu, officialNutrition=old)], batches, registry)
            self.assertEqual(old, rows[0]['officialNutrition'])
            self.assertEqual('RETAIN_VERIFIED_PENDING_REVIEW', audit[0]['decision'])
        for key, value in (('id', 'other'), ('brandId', 'other'), ('brand', '다른 브랜드'),
                           ('name', '라떼 ICE'), ('name', '라떼 HOT Large'), ('name', '라떼 ICE Small'),
                           ('externalId', '43'), ('sourceUrl', menu['sourceUrl']+'?variant=other')):
            changed = dict(menu, **{key: value})
            rows, audit = n.apply_reviewed_nutrition([changed], batches, registry)
            self.assertEqual([changed], rows)
            self.assertEqual('REVIEWED_IDENTITY_SKIP', audit[0]['decision'])

    def test_invalid_numeric_basis_url_and_provenance(self):
        n, menu, _, batches, registry = self.fixture()
        changes = [dict(officialProductId='43'), dict(sourceUrl='https://evil.example/42'),
                   dict(sourceUrl='https://user@official.example/42'), dict(servingUnit='cup'),
                   dict(servingAmount=0), dict(servingAmount=True), dict(servingAmount='100'),
                   dict(energyKcal=True), dict(energyKcal=-1), dict(energyKcal=float('nan')),
                   dict(energyKcal=float('inf')), dict(checkedAt='2026-10-01'),
                   dict(sourceSha256='bad'), dict(sugarsGrams=2, servingAmount=None, servingUnit=None),
                   dict(units='guessed'), dict(sourceType='BLOG'), dict(sourcePage=True),
                   dict(optionalFieldEvidence=[]), dict(retainedFieldEvidence=None),
                   dict(retainedFieldEvidence={'proteinGrams': dict(sourceUrl='https://evil.example')})]
        for change in changes:
            bad = copy.deepcopy(batches); bad[0]['menus'][0]['officialNutrition'].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], bad, registry)
        bad = copy.deepcopy(batches); bad[0]['menus'][0]['sourceUrl'] = 'https://evil.example/42'
        with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], bad, registry)

    def test_optional_evidence_original_timestamp_validation(self):
        n, menu, fact, batches, registry = self.fixture()
        evidence = dict(sourceUrl=fact['sourceUrl'], sourceSha256='d'*64,
                        checkedAt='2026-09-01T00:00:00Z')
        fact.update(sugarsGrams=0, optionalFieldEvidence={'sugarsGrams': evidence})
        original = copy.deepcopy(batches)
        rows, _ = n.apply_reviewed_nutrition([menu], batches, registry)
        self.assertEqual(evidence, rows[0]['officialNutrition']['optionalFieldEvidence']['sugarsGrams'])
        self.assertEqual(original, batches)
        for timestamp in ('invalid', '2026-09-01', None, 42):
            bad = copy.deepcopy(batches)
            bad[0]['menus'][0]['officialNutrition']['optionalFieldEvidence']['sugarsGrams']['checkedAt'] = timestamp
            with self.subTest(timestamp=timestamp), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], bad, registry)
        del evidence['checkedAt']
        rows, _ = n.apply_reviewed_nutrition([menu], batches, registry)
        self.assertNotIn('checkedAt', rows[0]['officialNutrition']['optionalFieldEvidence']['sugarsGrams'])

    def test_retained_nested_renderer_validation_before_application(self):
        n, menu, fact, batches, registry = self.fixture()
        renderer = dict(labels=dict(n.DUNKIN_LABELS), sourceUrl=fact['sourceUrl'], sourceSha256='d'*64)
        evidence = dict(sourceUrl=fact['sourceUrl'], checkedAt=fact['checkedAt'],
                        sourceSha256=fact['sourceSha256'], renderer=renderer)
        second = dict(menu, id='second', officialNutrition=dict(fact, sugarsGrams=0,
                      retainedFieldEvidence={'sugarsGrams': evidence}))
        batches[1]['menus'] = [second]
        menus = [menu, {k: v for k, v in second.items() if k != 'officialNutrition'}]
        before = copy.deepcopy((menus, batches))
        rows, _ = n.apply_reviewed_nutrition(menus, batches, registry)
        self.assertEqual(evidence, rows[1]['officialNutrition']['retainedFieldEvidence']['sugarsGrams'])
        self.assertEqual(rows, n.apply_reviewed_nutrition(rows, batches, registry)[0])
        for change in (dict(sourceUrl='https://evil.example/asset.js'),
                       dict(sourceUrl='https://user@official.example/asset.js'),
                       dict(sourceSha256='invalid'), dict(checkedAt='invalid'),
                       dict(labels={}), dict(renderer={})):
            bad = copy.deepcopy(batches)
            bad[1]['menus'][0]['officialNutrition']['retainedFieldEvidence']['sugarsGrams']['renderer'].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition(menus, bad, registry)
            self.assertEqual(before, (menus, batches))
        for renderer_value in (None, [], {}, dict(sourceUrl=fact['sourceUrl'], sourceSha256='d'*64)):
            bad = copy.deepcopy(batches)
            bad[1]['menus'][0]['officialNutrition']['retainedFieldEvidence']['sugarsGrams']['renderer'] = renderer_value
            with self.subTest(renderer=renderer_value), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition(menus, bad, registry)

    def test_missing_index_and_preseed_before_failed_refresh(self):
        from unittest.mock import Mock, patch
        n, menu, fact, batches, registry = self.fixture()
        missing = Mock(); missing.read_bytes.side_effect = FileNotFoundError()
        self.assertEqual(([menu], []), n.load_reviewed_nutrition([menu], registry, missing))
        malformed = Mock(); malformed.read_bytes.return_value = b'{}'
        with patch.object(n.Path, 'read_bytes', side_effect=AssertionError('must not read batches')):
            with self.assertRaises(ValueError): n.load_reviewed_nutrition([menu], registry, malformed)
        def fail(_): raise TimeoutError('inline failure')
        registry[0]['adapter'] = 'starbucks-product-jsonld'
        seeded, replay = n.apply_reviewed_nutrition([menu], batches, registry)
        with patch.object(n, 'load_reviewed_nutrition', return_value=(seeded, replay)), \
             patch.object(n.Path, 'exists', return_value=False):
            rows, audit = n.refresh([menu], registry, fail)
        self.assertEqual(fact, rows[0]['officialNutrition'])
        self.assertEqual(1, audit['failed']); self.assertEqual(replay, audit['reviewedReplay'])
        self.assertEqual(1, audit['newlyVerifiedMenus'])
        self.assertNotIn('officialNutrition', menu)
        get = Mock(side_effect=AssertionError('no live fetch before validation'))
        with patch.object(n, 'load_reviewed_nutrition', side_effect=ValueError('invalid manifest')):
            with self.assertRaises(ValueError): n.refresh([menu], registry, get)
        get.assert_not_called()



class TljCrosswalkRefreshInlineTests(unittest.TestCase):
    def fixture(self):
        import franchise_nutrition as n
        from test_franchise_nutrition_bakery import TljCrosswalkInlineTests as t
        menu = dict(t.menu, id='stable-tlj', brandId='tlj',
                    sourceUrl='https://www.tlj.co.kr/product/detail.asp?prod_num=5379')
        registry = [dict(brandId='tlj', name='뚜레쥬르', adapter='tlj-official-nutrition', allowedHosts=['www.tlj.co.kr'])]
        return n, t, menu, registry

    def test_reviewed_crosswalk_provenance_exact_allowlist(self):
        n, t, menu, registry = self.fixture()
        fact = n.parse_tlj(menu, t.payload, t.listing)
        fact.update(officialProductId='5379', sourceUrl=menu['sourceUrl'],
                    checkedAt='2026-10-01T00:00:00Z', sourceSha256='a'*64)
        batches = [dict(baseCatalogSha256='b'*64, menus=[dict(menu, officialNutrition=fact)]),
                   dict(baseCatalogSha256='c'*64, menus=[])]
        before = copy.deepcopy(batches)
        self.assertEqual(fact, n.apply_reviewed_nutrition([menu], batches, registry)[0][0]['officialNutrition'])
        self.assertEqual(before, batches)
        changes = [dict(listSourceURL='https://evil.example/product/result.asp'),
            dict(listSourceURL=n.TLJ_LIST_URL+'?x=1'), dict(listSourceSha256='bad'),
            dict(listSourceSha256=123), dict(officialDetailFullName=menu['name']),
            dict(officialDetailFullName=None), dict(listingDisplayName='wrong'),
            dict(matchedBy='prefix'), dict(provenance={}), dict(sourceUrl=menu['sourceUrl'].replace('5379', '5380'))]
        for change in changes:
            bad = copy.deepcopy(batches); bad[0]['menus'][0]['officialNutrition'].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], bad, registry)
        for key in n.TLJ_CROSSWALK_KEYS:
            bad = copy.deepcopy(batches); del bad[0]['menus'][0]['officialNutrition'][key]
            with self.subTest(missing=key), self.assertRaises(ValueError):
                n.apply_reviewed_nutrition([menu], bad, registry)

    def test_refresh_shared_listing_once_failure_and_no_need(self):
        from unittest.mock import Mock, patch
        n, t, menu, registry = self.fixture()
        menus = [menu, dict(menu, id='second')]
        def run(selected, failure=False):
            def fetch(url):
                if url == n.TLJ_LIST_URL:
                    if failure: raise TimeoutError('inline listing timeout')
                    return t.listing
                return ('<meta charset="utf-8">'+t.payload).encode('utf-8')
            get = Mock(side_effect=fetch)
            with patch.object(n, 'load_reviewed_nutrition', return_value=(copy.deepcopy(selected), [])), \
                 patch.object(n.Path, 'exists', return_value=False):
                rows, audit = n.refresh(selected, registry, get)
            return rows, audit, get
        rows, audit, get = run(menus)
        self.assertEqual(1, sum(call.args == (n.TLJ_LIST_URL,) for call in get.call_args_list))
        self.assertEqual(2, audit['matched'])
        self.assertEqual(['stable-tlj', 'second'], [row['id'] for row in rows])
        self.assertTrue(all(row['name'] == menu['name'] for row in rows))
        old = dict(rows[0]['officialNutrition'])
        failed, audit, get = run([dict(m, officialNutrition=old) for m in menus], True)
        self.assertEqual(old, failed[0]['officialNutrition'])
        self.assertEqual(2, audit['failed'])
        self.assertTrue(all(row['officialNutrition'] == old for row in failed))
        self.assertIn('inline listing timeout', audit['menus'][0]['listingFailureReason'])
        self.assertIn('title changed', audit['menus'][0]['reason'])
        self.assertEqual(1, sum(call.args == (n.TLJ_LIST_URL,) for call in get.call_args_list))
        exact = dict(menu, name=t.payload.split('<span class="name">')[1].split('</span>')[0])
        rows, audit, get = run([exact])
        self.assertEqual(1, audit['matched'])
        self.assertFalse(any(call.args == (n.TLJ_LIST_URL,) for call in get.call_args_list))
        rows, audit, get = run([])
        get.assert_not_called()
        # Canonical and duplicate-title failures cannot trigger listing fallback.
        for detail in (t.payload.replace('prod_num=5379', 'prod_num=5380'),
                       t.payload.replace('</span>', '</span><span class="name">duplicate</span>')):
            get = Mock(return_value=('<meta charset="utf-8">'+detail).encode('utf-8'))
            with patch.object(n, 'load_reviewed_nutrition', return_value=([menu], [])), \
                 patch.object(n.Path, 'exists', return_value=False):
                rows, audit = n.refresh([menu], registry, get)
            self.assertEqual(1, audit['failed'])
            self.assertFalse(any(call.args == (n.TLJ_LIST_URL,) for call in get.call_args_list))

class DunkinGroupInlineTests(unittest.TestCase):
    # Reduced inline contracts from supplied public evidence. These bytes are
    # synthetic fixtures, not the captured responses or their supplied hashes.
    checked = '2026-10-14T00:00:00Z'
    list_url = 'https://www.dunkindonuts.co.kr/menu?cat=6&page=2'
    cases = ((15, 4587, '던카치노 오리지널', 390, 300, 3, 130),
             (54, 5533, '카라멜 던카치노', 391, 280, 3, 162),
             (132, 5938, '자이언트 버킷 아메리카노', 1360, 35, 1, 2),
             (137, 5984, '에티오피아(Drip Coffee)', 290, 15, 0, 4))

    @staticmethod
    def page(component, props):
        import html
        return ('<div id="app" data-page="'+html.escape(json.dumps(dict(component=component, props=props)), quote=True)+'"></div>').encode()

    def fixture(self, case=None):
        gid, pid, title, amount, kcal, protein, sodium = case or self.cases[0]
        url = 'https://www.dunkindonuts.co.kr/menu/viewProductCat?cat=6&sub=%d&id=%d' % (44 if gid == 137 else 9, gid)
        menu = dict(id='stable-'+str(gid), brandId='dunkin', brand='던킨', name=title,
                    externalId='' if gid == 137 else 'productCats:'+str(gid),
                    sourceUrl=url if gid == 137 else self.list_url)
        child = dict(id=pid, TITLE=title, NUTRITION_SERVING=amount, NUTRITION_KCAL=kcal,
                     NUTRITION_PROTEIN=protein, NUTRITION_NATRIUM=sodium)
        children = [child]
        if gid == 15:
            children += [dict(id=5517, TITLE='디카페인 던카치노 오리지널'), dict(id=5639, TITLE='IB 던카치노 오리지널')]
        group = dict(id=gid, TITLE=title, PRODUCTS=children)
        nutrition = [dict(child, _sourceUrl='https://www.dunkindonuts.co.kr/nutrition', _sourceSha256='a'*64)]
        crosswalk = dict(sourceUrl=url, payload=self.page('Client/Menu/ViewGroup', dict(productCategory=dict(data=group))), checkedAt=self.checked)
        return menu, nutrition, group, crosswalk

    def test_unique_exact_child_and_original_products_path(self):
        import hashlib
        for case in self.cases:
            menu, nutrition, group, crosswalk = self.fixture(case)
            before = copy.deepcopy(menu)
            fact = parse_dunkin(menu, json.dumps(nutrition), crosswalk)
            self.assertEqual(before, menu)
            self.assertEqual('products:'+str(case[1]), fact['nutritionProductId'])
            self.assertEqual(case[3], fact['servingAmount'])
            self.assertEqual(hashlib.sha256(crosswalk['payload']).hexdigest(), fact['groupCrosswalkEvidence']['sourceSha256'])
            self.assertEqual(nutrition[0]['_sourceUrl'], fact['sourceUrl'])
            self.assertEqual('a'*64, fact['sourceSha256'])
            self.assertIsNone(fact['fatGrams']); self.assertIsNone(fact['carbohydrateGrams'])
            self.assertIsNone(fact['sugarsGrams']); self.assertIsNone(fact['saturatedFatGrams'])
            direct = parse_dunkin(dict(menu, externalId='products:'+str(case[1])), json.dumps(nutrition))
            self.assertNotIn('groupCrosswalkEvidence', direct)
            self.assertEqual(case[4], direct['energyKcal'])
            with self.assertRaises(AssertionError): parse_dunkin(menu, json.dumps(nutrition))

    def test_group_child_namespace_title_basis_nutrition_rejections(self):
        menu, nutrition, group, crosswalk = self.fixture()
        mutations = []
        for key, value in (('id', 54), ('TITLE', 'wrong')):
            mutations.append(dict(group, **{key: value}))
        for key, value in (('id', 5517), ('TITLE', '디카페인 던카치노 오리지널'),
                           ('TITLE', 'IB 던카치노 오리지널'), ('TITLE', menu['name']+' (Large)'),
                           ('NUTRITION_SERVING', 391), ('NUTRITION_KCAL', 301),
                           ('NUTRITION_PROTEIN', None), ('NUTRITION_NATRIUM', 131)):
            mutations.append(dict(group, PRODUCTS=[dict(group['PRODUCTS'][0], **{key:value})]))
        mutations += [dict(group, PRODUCTS=[], RECOMMENDED_PRODUCTS=group['PRODUCTS']),
                      dict(group, PRODUCTS=[group['PRODUCTS'][0], group['PRODUCTS'][0]]),
                      dict(group, dd_product_cat1_id=7), dict(group, dd_product_cat2_id=44)]
        for changed in mutations:
            cw = dict(crosswalk, payload=self.page('Client/Menu/ViewGroup', dict(productCategory=dict(data=changed))))
            with self.subTest(group=changed), self.assertRaises(AssertionError): parse_dunkin(menu, json.dumps(nutrition), cw)
        for changed in (dict(menu, externalId='other:15'), dict(menu, externalId='productCats:54'),
                        dict(menu, name=menu['name']+' (Small)')):
            with self.assertRaises(AssertionError): parse_dunkin(changed, json.dumps(nutrition), crosswalk)
        for changed in (dict(nutrition[0], id=5517), dict(nutrition[0], TITLE='wrong'),
                        dict(nutrition[0], NUTRITION_SERVING=391)):
            with self.assertRaises(AssertionError): parse_dunkin(menu, json.dumps([changed]), crosswalk)
        with self.assertRaises(AssertionError): parse_dunkin(menu, json.dumps(nutrition*2), crosswalk)
        with self.assertRaises(AssertionError):
            parse_dunkin(menu, json.dumps(nutrition), dict(crosswalk, payload=self.page('Client/Menu/List', dict(productCategory=dict(data=group)))))

    def test_reviewed_proof_empty_id_and_authoritative_brand_binding(self):
        import franchise_nutrition as n
        for case in (self.cases[0], self.cases[3]):
            menu, nutrition, _, crosswalk = self.fixture(case)
            fact = parse_dunkin(menu, json.dumps(nutrition), crosswalk)
            fact.update(officialProductId=menu['externalId'], checkedAt=self.checked)
            registry = [dict(brand='던킨', brandId='dunkin', adapter='dunkin-nutrition-pages', allowedHosts=[n.DUNKIN_HOST])]
            batches = [dict(baseCatalogSha256='b'*64, menus=[dict(menu, officialNutrition=fact, addedFields=['energyKcal', 'servingAmount'])]),
                       dict(baseCatalogSha256='c'*64, menus=[])]
            result, _ = n.apply_reviewed_nutrition([menu], batches, registry)
            self.assertEqual(fact, result[0]['officialNutrition'])
            self.assertEqual(menu['externalId'], result[0]['externalId'])
            for policy in (dict(registry[0], name='wrong'), dict(registry[0], brand='other')):
                with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], batches, [policy])
            mutations = [dict(groupId=True), dict(groupId=54), dict(productId='4587'), dict(productId=5517),
                dict(fullTitle='wrong'), dict(sourceSha256='bad'), dict(sourceSha256=123),
                dict(checkedAt='2026-10-14'), dict(checkedAt=123), dict(basis=dict(servingAmount=1, servingUnit='g')),
                dict(basis=dict(servingAmount=True, servingUnit='g')), dict(extra=1)]
            for url in ('http://www.dunkindonuts.co.kr/menu/viewProductCat?cat=6&sub=9&id=15',
                        crosswalk['sourceUrl'].replace(n.DUNKIN_HOST, 'foreign.example'),
                        crosswalk['sourceUrl']+'&id=15', crosswalk['sourceUrl']+'&x=1',
                        crosswalk['sourceUrl']+'#fragment', crosswalk['sourceUrl'].replace('/viewProductCat', '/other')):
                mutations.append(dict(sourceUrl=url))
            if not menu['externalId']:
                mutations.append(dict(sourceUrl=crosswalk['sourceUrl'].replace('sub=44', 'sub=9')))
            for change in mutations:
                bad = copy.deepcopy(batches)
                bad[0]['menus'][0]['officialNutrition']['groupCrosswalkEvidence'].update(change)
                with self.subTest(change=change), self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], bad, registry)
            for key in fact['groupCrosswalkEvidence']:
                bad = copy.deepcopy(batches); del bad[0]['menus'][0]['officialNutrition']['groupCrosswalkEvidence'][key]
                with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], bad, registry)
            bad = copy.deepcopy(batches); del bad[0]['menus'][0]['officialNutrition']['groupCrosswalkEvidence']
            with self.assertRaises(ValueError): n.apply_reviewed_nutrition([menu], bad, registry)
            for missing in (('nutritionProductId',), ('nutritionProductId', 'groupCrosswalkEvidence')):
                bad = copy.deepcopy(batches)
                for key in missing:
                    del bad[0]['menus'][0]['officialNutrition'][key]
                with self.subTest(missing=missing), self.assertRaises(ValueError):
                    n.apply_reviewed_nutrition([menu], bad, registry)

    def test_reviewed_and_failed_refresh_audits_preserve_group_proof(self):
        from unittest.mock import Mock, patch
        import franchise_nutrition as n
        for case in (self.cases[0], self.cases[3]):
            menu, nutrition, _, crosswalk = self.fixture(case)
            fact = parse_dunkin(menu, json.dumps(nutrition), crosswalk)
            fact.update(officialProductId=menu['externalId'], checkedAt=self.checked)
            registry = [dict(brand='던킨', brandId='dunkin', adapter='dunkin-nutrition-pages',
                url=nutrition[0]['_sourceUrl'], allowedHosts=[n.DUNKIN_HOST])]
            batches = [dict(baseCatalogSha256='b'*64, menus=[dict(menu, officialNutrition=fact)]),
                       dict(baseCatalogSha256='c'*64, menus=[])]
            replayed, replay_audit = n.apply_reviewed_nutrition([menu], batches, registry)
            get = Mock(side_effect=TimeoutError('inline nutrition timeout'))
            with patch.object(n, 'load_reviewed_nutrition', return_value=(replayed, replay_audit)), \
                 patch.object(n.Path, 'exists', return_value=False):
                rows, audit = n.refresh([menu], registry, get)
            self.assertEqual(fact, rows[0]['officialNutrition'])
            self.assertEqual('FAILED_RETAINED', audit['menus'][0]['result'])
            self.assertEqual('REVIEWED_REPLAY', audit['reviewedReplay'][0]['decision'])
            for evidence in (audit['menus'][0], audit['reviewedReplay'][0]):
                self.assertEqual(fact['nutritionProductId'], evidence['nutritionProductId'])
                self.assertEqual(fact['groupCrosswalkEvidence'], evidence['groupCrosswalkEvidence'])
                self.assertIsNot(rows[0]['officialNutrition']['groupCrosswalkEvidence'],
                                 evidence['groupCrosswalkEvidence'])

    def test_unsupplied_empty_id_children_remain_unknown_and_identity_stable(self):
        for gid, title in ((136, '케냐(Drip Coffee)'), (138, '아이스 에티오피아(Drip Coffee)'),
                           (139, '아이스 케냐(Drip Coffee)')):
            url = 'https://www.dunkindonuts.co.kr/menu/viewProductCat?cat=6&sub=44&id='+str(gid)
            menu = dict(id='stable-'+str(gid), brand='던킨', name=title, externalId='', sourceUrl=url)
            before = copy.deepcopy(menu)
            group = dict(id=gid, TITLE=title, PRODUCTS=[])
            crosswalk = dict(sourceUrl=url, payload=self.page('Client/Menu/ViewGroup', dict(productCategory=dict(data=group))), checkedAt=self.checked)
            with self.assertRaises(AssertionError): parse_dunkin(menu, '[]', crosswalk)
            self.assertEqual(before, menu)

    def test_refresh_reuses_list_and_group_success_and_failure(self):
        from unittest.mock import Mock, patch
        import franchise_nutrition as n
        fixtures = [self.fixture(case) for case in self.cases]
        menus = [f[0] for f in fixtures] + [dict(fixtures[0][0], id='duplicate-menu')]
        groups = {f[3]['sourceUrl']: f[3]['payload'] for f in fixtures}
        listing = self.page('Client/Menu/List', dict(productCats=dict(data=[
            dict(id=c[0], TITLE=c[2], dd_product_cat1_id=6, dd_product_cat2_id=9) for c in self.cases[:3]])))
        nutrition_url = 'https://www.dunkindonuts.co.kr/nutrition'
        page = self.page('Client/Menu/View', dict(productsNutrition=dict(data=[f[1][0] for f in fixtures],
            meta=dict(current_page=1, path=nutrition_url, last_page=1, total=4))))
        registry = [dict(brand='던킨', brandId='dunkin', adapter='dunkin-nutrition-pages', url=nutrition_url, allowedHosts=[n.DUNKIN_HOST])]
        for failure in (False, True):
            def fetch(url):
                if url == nutrition_url: return page
                if url == self.list_url: return listing
                if failure: raise TimeoutError('inline group failure')
                return groups[url]
            get = Mock(side_effect=fetch)
            selected = copy.deepcopy(menus)
            if failure:
                for m in selected:
                    m['officialNutrition'] = dict(energyKcal=99, sourceUrl=nutrition_url)
            with patch.object(n, 'load_reviewed_nutrition', return_value=(selected, [])), patch.object(n.Path, 'exists', return_value=False):
                rows, audit = n.refresh(selected, registry, get)
            self.assertEqual(1, sum(c.args == (self.list_url,) for c in get.call_args_list))
            self.assertEqual(1, sum(c.args == (nutrition_url,) for c in get.call_args_list))
            for url in groups: self.assertEqual(1, sum(c.args == (url,) for c in get.call_args_list))
            self.assertEqual(5, audit['failed'] if failure else audit['matched'])
            if failure:
                self.assertTrue(all(r['officialNutrition']['energyKcal'] == 99 for r in rows))
            else:
                self.assertTrue(all('groupCrosswalkEvidence' in a for a in audit['menus']))
                self.assertTrue(all(r['officialNutrition']['officialProductId'] == r['externalId'] for r in rows))
                self.assertEqual([m['sourceUrl'] for m in menus], [m['sourceUrl'] for m in rows])


if __name__=='__main__':unittest.main()
