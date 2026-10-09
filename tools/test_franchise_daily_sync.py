import copy, json, tempfile, unittest
from pathlib import Path
from franchise_daily_sync import *

class DailySyncTest(unittest.TestCase):
    def setUp(self):
        self.brands=json.loads(CONFIG.read_text(encoding='utf-8'))
        self.brand=next(b for b in self.brands if b['name']=='설빙')
        self.menu=candidates(self.brand,'<a href="menu_view.php?menu=999991">검증 신메뉴 ICE 라지</a>','2026-10-08T00:00:00Z')[0]
    def test_full_queue(self):
        self.assertEqual(102,len(self.brands));self.assertEqual(102,len({b['brandId'] for b in self.brands}))
        self.assertTrue(all(official_url(b['sourceUrl'],b) for b in self.brands))
    def test_recorded_adapters(self):
        for name,file,count in [('설빙','sulbing-menu.html',31),('이성당','isungdang-list.html',18)]:
            brand=next(b for b in self.brands if b['name']==name)
            menus=candidates(brand,(ROOT/'data-source/franchise-sync/fixtures'/file).read_text(encoding='utf-8'),'2026-10-08T00:00:00Z')
            self.assertEqual(count,len(menus));self.assertTrue(all(m['name'] not in ['이전 다음','이전','다음'] for m in menus))
            self.assertTrue(all('에코백' not in m['name'] and m['name'] != '이성당 추천세트' for m in menus))
    def test_jsonld_only_menu_items(self):
        html='<script type="application/ld+json">'+json.dumps([{'@type':'MenuItem','name':'확인 메뉴','identifier':'A'},{'@type':'Product','name':'비음식 광고'}])+'</script>'
        self.assertEqual(1,len(candidates(self.brand,html,'2026-10-08T00:00:00Z')))
        self.assertFalse(candidates(self.brand,'<h2>그럴듯한 광고 메뉴</h2>','2026-10-08T00:00:00Z'))
    def test_official_source(self):
        bad=copy.deepcopy(self.menu);bad['sourceUrl']='https://example.net/menu'
        with self.assertRaises(AssertionError): validate(bad,self.brand)
    def test_unknown_not_zero(self):
        self.assertIsNone(self.menu['energyKcal']);self.assertIsNone(self.menu['proteinGrams'])
    def test_actual_source_types_and_legacy_compatibility(self):
        self.assertEqual('HTML',self.menu['sourceType'])
        for source,expected in [(dict(adapter='bon-api'),'API'),(dict(adapter='kfc-json'),'JSON'),(dict(adapter='hecbob-rendered',transport='browser'),'BROWSER')]:
            self.assertEqual(expected,source_type(source))
        old=dict(self.menu);old.pop('sourceType');validate(old,self.brand)
        bad=dict(self.menu,sourceType='GUESS')
        with self.assertRaises(AssertionError):validate(bad,self.brand)
    def test_legacy_provenance_is_additive_and_never_guessed(self):
        evidence=json.loads(LEGACY_PROVENANCE.read_text(encoding='utf-8'))[0]
        brand=next(b for b in self.brands if b['brandId']==evidence['brandId'])
        old=dict(brandId=brand['brandId'],brand=brand['name'],name='보존 확인',normalizedName=normalize('보존 확인'),externalId='legacy-check',category='',sourceUrl=evidence['sourceUrl'],checkedAt=evidence['checkedAt'],energyKcal=None)
        old['id']=stable_id(old['brandId'],old['externalId'],old['name'],'')
        before=copy.deepcopy(old)
        menus,stats=merge(dict(menus=[old]),[],self.brands,[])
        self.assertEqual(before,old)
        self.assertTrue(all(menus[0][k]==v for k,v in before.items()))
        self.assertEqual(evidence['sourceType'],menus[0]['sourceType'])
        old['checkedAt']='2020-01-01T00:00:00Z'
        self.assertNotIn('sourceType',merge(dict(menus=[old]),[],self.brands,[])[0][0])
    def test_nutrition_basis_and_invalid(self):
        known=copy.deepcopy(self.menu);known.update(energyKcal=0,carbohydrateGrams=0,servingAmount=100,servingUnit='g',nutritionSourceUrl=known['sourceUrl'])
        validate(known,self.brand)
        for bad in (-1,float('nan'),float('inf'),100001):
            known['energyKcal']=bad
            with self.assertRaises(AssertionError): validate(known,self.brand)
        known['energyKcal']=100;known['servingAmount']=None
        with self.assertRaises(AssertionError): validate(known,self.brand)
    def test_stable_external_id(self):
        self.assertEqual(self.menu['id'],stable_id(self.brand['brandId'],'999991','renamed','other'))
    def test_size_temperature_kept(self):
        self.assertNotEqual(stable_id('brand','', '커피 ICE 라지',''),stable_id('brand','','커피 HOT 레귤러',''))
        self.assertEqual(normalize('  ICE   라지 '),normalize('ICE 라지'))
    def test_duplicate_runs_and_static(self):
        batch=[dict(status='SUCCESS',menus=[self.menu,self.menu])]
        merged,stats=merge({},batch,self.brands,[]);self.assertEqual(1,len(merged));self.assertEqual(1,stats['duplicateCount'])
        again,stats=merge(dict(menus=merged),batch,self.brands,[]);self.assertEqual(merged,again);self.assertEqual(2,stats['duplicateCount'])
        merged,stats=merge({},batch,self.brands,[dict(brandId=self.menu['brandId'],name=self.menu['name'])]);self.assertFalse(merged)
    def test_publish_manifest_atomic_and_same_day(self):
        with tempfile.TemporaryDirectory() as tmp:
            report=run(tmp,'fixture-day',self.brands,[dict(brandId=self.brand['brandId'],status='SUCCESS',menus=[self.menu])])
            self.assertEqual(1,report['publishedNewCount'])
            manifest=json.loads((Path(tmp)/'latest.json').read_text());data=(Path(tmp)/manifest['file']).read_bytes()
            self.assertEqual(manifest['sha256'],hashlib.sha256(data).hexdigest())
            self.assertEqual('ALREADY_COMPLETED',run(tmp,'fixture-day',self.brands,[])['status'])
    def test_failure_keeps_existing_catalog(self):
        with tempfile.TemporaryDirectory() as tmp:
            run(tmp,'one',self.brands,[dict(status='SUCCESS',menus=[self.menu])])
            before=(Path(tmp)/'latest.json').read_bytes()
            report=run(tmp,'two',self.brands,[dict(status='FETCH_FAILED',menus=[])])
            self.assertEqual(before,(Path(tmp)/'latest.json').read_bytes());self.assertEqual('EXISTING_CATALOG_RETAINED',report['status'])
            self.assertEqual('ALREADY_COMPLETED',run(tmp,'two',self.brands,[])['status'])
    def test_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp)/'.sync-lock').touch();self.assertEqual('RUN_LOCKED',run(tmp,'one',self.brands,[])['status'])
    def test_invalid_candidate_not_published(self):
        bad=copy.deepcopy(self.menu);bad['energyKcal']=-2
        merged,stats=merge({},[dict(menus=[bad])],self.brands,[])
        self.assertFalse(merged);self.assertEqual(1,stats['invalidCount'])

if __name__=='__main__': unittest.main(verbosity=2)
