"""Recorded official adapters, complete inventory, failures and no-change publishing."""
import copy, gzip, json, tempfile, unittest, urllib.error
from pathlib import Path
from unittest.mock import patch
from franchise_daily_sync import *

class Response:
    def __init__(self,data,url):self.data=data;self.url=url
    def __enter__(self):return self
    def __exit__(self,*args):pass
    def read(self,limit):return self.data[:limit]
    @property
    def headers(self):
        from email.message import Message
        m=Message();m['content-type']='text/html; charset=utf-8';return m

class AutoAuditTest(unittest.TestCase):
    def setUp(self):
        self.brands=json.loads(CONFIG.read_text(encoding='utf-8'))
        self.brand=next(b for b in self.brands if b['name']=='설빙')
        self.menu=candidates(self.brand,'<a href="menu_view.php?menu=991123">검증 신메뉴 HOT</a>','2026-10-08T00:00:00Z')[0]
    def test_all_recorded_source_adapters(self):
        fixtures=json.loads(gzip.decompress((ROOT/'data-source/franchise-sync/fixtures/recorded-adapters.json.gz').read_bytes()))
        self.assertGreaterEqual(len(fixtures),100)
        for fixture in fixtures:
            with self.subTest(brand=fixture['brand']['name'],url=fixture['brand']['sourceUrl']):
                actual=candidates(fixture['brand'],fixture['markup'],'2026-10-08T00:00:00Z')
                self.assertEqual(fixture['expected'],[dict(id=m['id'],name=m['name']) for m in actual])
                for menu in actual:
                    validate(menu,fixture['brand']);self.assertIsNone(menu['energyKcal']);self.assertIsNone(menu['fatGrams'])
    def test_registry_mapping_and_inventory_unknown_zero(self):
        self.assertEqual(102,len(self.brands))
        for brand in self.brands:
            for source in brand['sources']:
                self.assertTrue(official_url(source['url'],brand))
                self.assertIn(source['adapter'],('jsonld','isungdang','sulbing','reviewed-html','reviewed-delivery','bon-api','starbucks-json','tlj','hansot-json','mega-fragment','seventh-list','hjh-list','venti-list','pascucci-list','eatz-list','nene-list','cheogajip-list','bhc-json','bhc-categories','mc-categories','mc-index','mc-json','subway-list','hongik-text','ediya-list','ediya-fragment','kfc-json','paris-fragment','salady-side','hollys-list','jaws-set','banolim-native','dunkin-native','bbq-json','bbq-categories','burgerking-json','pizzahut-json','starbucks-navigation','emergency-list','dominos-list','poke-fixed','norang-list','baeksojeong-native'))
        path=ROOT/'data-source/franchise-auto-audit/brand-status.json'
        if path.exists():
            inventory=json.loads(path.read_text(encoding='utf-8'));self.assertEqual(0,inventory['unknown'])
            self.assertEqual({b['brandId'] for b in self.brands},{r['brandId'] for r in inventory['brands']})
            self.assertTrue(all(r['state'] in ('AUTO_READY','ADAPTER_REQUIRED','SOURCE_MISSING','SOURCE_BLOCKED','REVIEW_REQUIRED','BROKEN') for r in inventory['brands']))
    def test_alias_keeps_real_variants(self):
        self.assertEqual(normalize('커피 (HOT)'),normalize('커피 HOT'))
        for variant in ('ICE','Regular','Large','치즈','세트'):
            self.assertNotEqual(normalize('커피 HOT'),normalize('커피 '+variant))
    def test_explicit_unknown_new_and_repeat(self):
        menus,stats=merge({},[dict(menus=[self.menu])],self.brands,[])
        self.assertEqual('NEW',stats['decisions'][0]['state']);self.assertEqual(1,len(menus))
        for key in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams'):self.assertIsNone(menus[0][key])
        repeated,stats=merge(dict(menus=menus),[dict(menus=[self.menu])],self.brands,[])
        self.assertEqual(menus,repeated);self.assertEqual('EXISTING',stats['decisions'][0]['state'])
    def test_official_store_held_not_removed(self):
        brand=next(b for b in self.brands if b['name']=='이성당')
        self.assertEqual('REVIEW_ONLY',brand['publishPolicy'])
        self.assertEqual('REVIEW_REQUIRED',automation_state(brand,dict(status='SUCCESS',sources=[])))
    def test_timeout_500_malformed_empty_keep_manifest(self):
        source=dict(url=self.brand['sourceUrl'],adapter='sulbing')
        failures=[TimeoutError(),urllib.error.HTTPError(source['url'],500,'failure',{},None)]
        with tempfile.TemporaryDirectory() as tmp:
            run(tmp,'baseline',self.brands,[dict(brandId=self.brand['brandId'],status='SUCCESS',menus=[self.menu])])
            before=(Path(tmp)/'latest.json').read_bytes()
            for i,error in enumerate(failures):
                with patch('urllib.request.urlopen',side_effect=error): batch=fetch_source(self.brand,source)
                self.assertEqual('FETCH_FAILED',batch['status'])
                report=run(tmp,'error-'+str(i),self.brands,[batch]);self.assertEqual(before,(Path(tmp)/'latest.json').read_bytes())
                self.assertEqual('EXISTING_CATALOG_RETAINED',report['status'])
            for i,body in enumerate((b'',b'not HTML',b'{broken json')):
                with patch('urllib.request.urlopen',return_value=Response(body,source['url'])):batch=fetch_source(self.brand,source)
                self.assertEqual('FETCH_FAILED',batch['status']);run(tmp,'malformed-'+str(i),self.brands,[batch])
                self.assertEqual(before,(Path(tmp)/'latest.json').read_bytes())
    def test_unchanged_manifest_bytes_no_new_version(self):
        batch=[dict(brandId=self.brand['brandId'],status='SUCCESS',menus=[self.menu])]
        with tempfile.TemporaryDirectory() as tmp:
            run(tmp,'day1',self.brands,batch);before=(Path(tmp)/'latest.json').read_bytes()
            report=run(tmp,'day2',self.brands,batch)
            self.assertEqual(before,(Path(tmp)/'latest.json').read_bytes());self.assertEqual(0,report['publishedNewCount'])
            self.assertEqual('Asia/Seoul',report['timezone']);self.assertTrue(report['checksum'])
    def test_bon_brand_identity_not_inferred(self):
        brand=next(b for b in self.brands if b['name']=='본죽')
        source=brand['sources'][0];brand=dict(brand,adapter=source['adapter'],sourceUrl=source['url'])
        bad=json.dumps(dict(status='success',data=dict(brand=dict(brdCd='BF999',brdNm='타사'),menuList=[])))
        with self.assertRaises(AssertionError):candidates(brand,bad,'2026-10-08T00:00:00Z')

if __name__=='__main__':unittest.main(verbosity=2)
