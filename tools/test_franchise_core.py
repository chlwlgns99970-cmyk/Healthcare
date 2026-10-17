"""Confirmed source changes, permanent replay and the actual app snapshot."""
import copy
import hashlib
import gzip
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from franchise_daily_sync import CONFIG, MAX_CATALOG_BYTES, candidates
from franchise_nutrition import load_reviewed_nutrition, REGISTRY, FIELDS, OPTIONAL_FIELDS, resolve_ediya_page, refresh
from franchise_nutrition_bakery import parse_paris, parse_tlj
from franchise_daily_sync import daily_nutrition

ROOT = Path(__file__).resolve().parents[1]


class CoreFranchiseTest(unittest.TestCase):
    def setUp(self):
        self.brands = json.loads(CONFIG.read_text(encoding='utf-8'))

    def test_kfc_recommendation_accepts_official_side_but_not_arbitrary_category(self):
        brand = dict(next(b for b in self.brands if b['name']=='KFC'), adapter='kfc-json',
            sourceKey='RCMD', sourceUrl='https://www.kfckorea.com/kfc/interface/selectDeliveryList?subGroupCd=RCMD')
        row = dict(menuCd='official-side', menuNm='공식 사이드', subGroupCd='SIDE', subGroupNm='사이드')
        def body():
            return json.dumps(dict(resultState='00',resultCode='Y',kfcs=dict(resultCd='0000',data=dict(list=[row],totCnt=1))))
        self.assertEqual(1,len(candidates(brand,body(),'2026-10-16T00:00:00Z')))
        row['subGroupCd']='UNKNOWN'
        with self.assertRaises(AssertionError):candidates(brand,body(),'2026-10-16T00:00:00Z')
        row['subGroupCd']='SIDE';brand['sourceKey']='CHKN'
        with self.assertRaises(AssertionError):candidates(brand,body(),'2026-10-16T00:00:00Z')

    def test_priced_food_bundle_is_preserved_but_merchandise_is_not_a_food(self):
        brand = dict(next(b for b in self.brands if b['name']=='반올림피자'), adapter='banolim-native',
            sourceKey='공식 세트', sourceUrl='https://order.banolimpizza.com/menu/list?categoryId=17')
        names=['명일방주 로도스 세트(고구마피자+굿즈)','명일방주 굿즈','키링','기프티콘']
        rows=[dict(categoryId=17)]+[dict(id=641+i,name=n,basePrice=20000,soldOut=False,optionGroups=[]) for i,n in enumerate(names)]
        body='<script id="recorded-next-menu-data" type="application/json">'+json.dumps(rows)+'</script>'
        menus=candidates(brand,body,'2026-10-16T00:00:00Z')
        self.assertEqual([names[0]],[m['name'] for m in menus])
        self.assertIsNone(menus[0]['energyKcal'])

    def test_bundled_data_preserves_pinned_additions_on_repeated_daily_replay(self):
        assets=ROOT/'app/src/main/assets/franchise'
        payload=(assets/'catalog.json').read_bytes();manifest=json.loads((assets/'catalog-manifest.json').read_bytes())
        self.assertEqual(hashlib.sha256(payload).hexdigest(),manifest['sha256'])
        self.assertLessEqual(len(payload),MAX_CATALOG_BYTES)
        menus=json.loads(payload)['menus'];self.assertEqual(manifest['menuCount'],len(menus))
        self.assertEqual(len(menus),len({m['id'] for m in menus}))
        registry=json.loads(REGISTRY.read_text(encoding='utf-8'))
        patch=json.loads((ROOT/'data-source/franchise-nutrition-core-20261016/linked-menus.json').read_bytes())
        depleted=copy.deepcopy(menus);by_id={m['id']:m for m in depleted}
        for row in patch['menus']:
            for field in row['addedFields']:by_id[row['id']]['officialNutrition'].pop(field,None)
        restored,_=load_reviewed_nutrition(depleted,registry)
        again,_=load_reviewed_nutrition(restored,registry)
        self.assertEqual(restored,again)
        by_id={m['id']:m for m in restored}
        for row in patch['menus']:
            for field in row['addedFields']:
                self.assertEqual(row['officialNutrition'][field],by_id[row['id']]['officialNutrition'][field])
        original={m['id']:m for m in menus}
        for m in restored:
            for field in FIELDS+OPTIONAL_FIELDS:
                known=original[m['id']].get('officialNutrition',{}).get(field)
                if known is not None:self.assertEqual(known,m.get('officialNutrition',{}).get(field))

    def test_added_values_reparse_from_captured_official_identity_and_serving(self):
        directory=ROOT/'data-source/franchise-nutrition-core-20261016'
        rows=json.loads((directory/'linked-menus.json').read_bytes())['menus']
        for row in rows:
            with self.subTest(menu=row['name']):
                fact=row['officialNutrition']
                raw=gzip.decompress((directory/'evidence'/(fact['sourceSha256']+'.gz')).read_bytes())
                self.assertEqual(fact['sourceSha256'],hashlib.sha256(raw).hexdigest())
                if row['brand']=='뚜레쥬르':
                    listing=gzip.decompress((directory/'evidence'/(fact['listSourceSha256']+'.gz')).read_bytes()) if 'listSourceSha256' in fact else None
                    parsed=parse_tlj(row,raw,listing)
                else:parsed=parse_paris(row,raw)
                for field in row['addedFields']+['servingAmount','servingUnit']:
                    self.assertEqual(fact[field],parsed[field])
                self.assertIsNone(parsed['carbohydrateGrams'])
                self.assertIsNone(parsed['fatGrams'])

    def test_actual_daily_refresh_keeps_facts_when_official_sources_fail(self):
        menus=json.loads((ROOT/'app/src/main/assets/franchise/catalog.json').read_bytes())['menus']
        before=copy.deepcopy(menus)
        target={b['brandId'] for b in self.brands if b['name'] in ('뚜레쥬르','파리바게뜨')}
        with patch('franchise_nutrition.fetch_official_nutrition',side_effect=TimeoutError('offline test')):
            retained,audit=daily_nutrition(menus,True,target)
        self.assertEqual(before,menus)
        self.assertEqual([m['id'] for m in before],[m['id'] for m in retained])
        for old,new in zip(before,retained):
            for field in FIELDS+OPTIONAL_FIELDS+('servingAmount','servingUnit'):
                known=(old.get('officialNutrition') or {}).get(field)
                if known is not None:self.assertEqual(known,(new.get('officialNutrition') or {}).get(field))
        self.assertGreater(audit['failed'],0)

    def test_ediya_page_shift_uses_exact_id_and_full_name_not_position(self):
        body=(ROOT/'data-source/franchise-sync/fixtures/ediya-nutrition-food.html').read_bytes()
        menu=dict(brand='이디야',name='잉글리쉬 머핀',externalId='58')
        url='https://www.ediya.com/inc/ajax_brand.php?gubun=menu_more&page=2'
        page,raw,fact=resolve_ediya_page(menu,{'https://www.ediya.com/contents/bakery.html':b'<li>different page</li>',url:body},['www.ediya.com'])
        self.assertEqual(url,page);self.assertEqual(body,raw);self.assertEqual(140,fact['servingAmount'])
        for bad in [dict(menu,externalId='59'),dict(menu,name='비슷한 머핀')]:
            with self.assertRaises(AssertionError):resolve_ediya_page(bad,{url:body},['www.ediya.com'])

    def test_ediya_unapproved_pages_and_inconsistent_facts_are_withheld(self):
        body=(ROOT/'data-source/franchise-sync/fixtures/ediya-nutrition-food.html').read_bytes()
        menu=dict(brand='이디야',name='잉글리쉬 머핀',externalId='58')
        for url in ['https://other.test/contents/bakery.html','http://www.ediya.com/contents/bakery.html',
                    'https://www.ediya.com/private','https://www.ediya.com/inc/ajax_brand.php?gubun=other']:
            with self.assertRaises(AssertionError):resolve_ediya_page(menu,{url:body},['www.ediya.com'])
        changed=body.replace(b'278',b'279');self.assertNotEqual(body,changed)
        with self.assertRaises(AssertionError):resolve_ediya_page(menu,{
            'https://www.ediya.com/contents/bakery.html':body,
            'https://www.ediya.com/inc/ajax_brand.php?gubun=menu_more&page=2':changed},['www.ediya.com'])

    def test_normal_refresh_uses_changed_official_page_without_changing_menu_identity(self):
        brand=next(b for b in self.brands if b['name']=='이디야')
        first='https://www.ediya.com/contents/bakery.html'
        second='https://www.ediya.com/inc/ajax_brand.php?gubun=menu_more&page=2'
        def menu(name,external,url):
            return candidates(dict(brand,adapter='jsonld',sourceUrl=url),
                '<script type="application/ld+json">'+json.dumps({'@type':'MenuItem','name':name,'identifier':external})+'</script>',
                '2026-10-16T00:00:00Z')[0]
        menus=[menu('잉글리쉬 머핀','58',first),menu('다른 제품','99999999',second)]
        body=(ROOT/'data-source/franchise-sync/fixtures/ediya-nutrition-food.html').read_bytes()
        pages={first:b'<li>old page moved</li>',second:body}
        result,audit=refresh(menus,registry=json.loads(REGISTRY.read_bytes()),fetcher=pages.__getitem__)
        self.assertEqual([m['id'] for m in menus],[m['id'] for m in result])
        self.assertEqual(first,result[0]['sourceUrl'])
        self.assertEqual(second,result[0]['officialNutrition']['sourceUrl'])
        self.assertEqual(278,result[0]['officialNutrition']['energyKcal'])
        self.assertEqual('MATCHED',audit['menus'][0]['result'])
        self.assertEqual('FAILED_RETAINED',audit['menus'][1]['result'])


if __name__=='__main__':unittest.main()
