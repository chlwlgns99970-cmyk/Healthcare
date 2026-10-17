"""Exact official product evidence and targeted daily-update preservation."""
import copy,gzip,hashlib,json,re,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from franchise_daily_sync import CONFIG,SEED,candidates,merge,fetch,fetch_source,published_menu,run
from franchise_nutrition import REGISTRY,load_reviewed_nutrition,refresh
from franchise_nutrition_youngman import parse,details

ROOT=Path(__file__).resolve().parents[1]
DIR=ROOT/'data-source/franchise-nutrition-five-20261017'

class FiveBrandTest(unittest.TestCase):
    def setUp(self):
        self.brands=json.loads(CONFIG.read_bytes())
        self.pizza=next(b for b in self.brands if b['name']=='청년피자')
        self.rows=json.loads((DIR/'linked-menus.json').read_bytes())['menus']
        self.sources=json.loads((DIR/'source-audit.json').read_bytes())
        self.registry=json.loads(REGISTRY.read_bytes())

    def raw(self,sha):
        raw=gzip.decompress((DIR/'evidence'/(sha+'.gz')).read_bytes())
        self.assertEqual(sha,hashlib.sha256(raw).hexdigest())
        return raw

    def test_ten_new_exact_id_size_rows_have_official_protein_but_no_invented_macros(self):
        self.assertEqual(10,len(self.rows));self.assertEqual(5,len({r['sourceUrl'] for r in self.rows}))
        for row in self.rows:
            fact=parse(row,self.raw(row['officialNutrition']['sourceSha256']))
            for key,value in fact.items():self.assertEqual(value,row['officialNutrition'].get(key))
            self.assertIsNone(fact['carbohydrateGrams']);self.assertIsNone(fact['fatGrams'])

    def test_reviewed_suffix_pairs_are_product_bound_not_fuzzy_matches(self):
        row=next(r for r in self.rows if r['externalId']=='menu_detail_pizza.php:37:R')
        raw=self.raw(row['officialNutrition']['sourceSha256'])
        for bad in [raw.replace('리얼치즈 피자'.encode(),'리얼치즈 피자 신제품'.encode()),
                    raw.replace('리얼치즈 피자'.encode(),'슈퍼슈프림 피자'.encode())]:
            self.assertNotEqual(raw,bad)
            with self.assertRaises(AssertionError):parse(row,bad)
        with self.assertRaises(AssertionError):parse(dict(row,sourceUrl=row['sourceUrl'].replace('seq=37','seq=40')),raw)

    def test_premium_details_preserve_path_scoped_ids_and_static_duplicates(self):
        menus=[]
        for source in self.sources['pizzaDetails']:
            brand=dict(self.pizza,sourceUrl=source['url'],adapter='youngman-detail')
            menus+=candidates(brand,self.raw(source['sha256']).decode('utf-8'),source['checkedAt'])
        self.assertEqual(14,len(menus))
        baseline=dict(menus=[])
        first,audit=merge(baseline,[dict(menus=menus)],self.brands,json.loads(SEED.read_bytes()))
        self.assertEqual(10,len(first));self.assertEqual(4,audit['duplicateCount'])
        again,audit=merge(dict(menus=first),[dict(menus=menus)],self.brands,json.loads(SEED.read_bytes()))
        self.assertEqual(first,again);self.assertEqual(0,audit['publishedNewCount'])
        for m in menus:
            if 'pizza2.php' in m['sourceUrl']:self.assertTrue(m['externalId'].startswith('menu_detail_pizza2.php:'))

    def test_daily_replay_fills_new_facts_once_and_preserves_conflicting_verified_values(self):
        menus=json.loads((ROOT/'app/src/main/assets/franchise/catalog.json').read_bytes())['menus']
        first,_=load_reviewed_nutrition(menus,self.registry)
        again,_=load_reviewed_nutrition(first,self.registry)
        self.assertEqual(first,again)
        one=copy.deepcopy(next(m for m in menus if m['id']==self.rows[0]['id']))
        one['officialNutrition']['proteinGrams']+=1
        retained,audit=load_reviewed_nutrition([one],self.registry)
        self.assertEqual(one,retained[0]);self.assertTrue(any('differences' in r for r in audit))

    def test_incomplete_http_200_never_releases_partial_general_pizza_catalog(self):
        source=self.sources['youngmanGeneral']
        raw=self.raw(source['sha256'])
        class Response:
            url=source['url']
            headers=__import__('email.message',fromlist=['Message']).Message()
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def read(self,*args):return raw
        with patch('urllib.request.urlopen',return_value=Response()):
            result=fetch_source(self.pizza,dict(url=source['url'],adapter='youngman-native'))
        self.assertNotEqual('SUCCESS',result['status']);self.assertEqual([],result['menus'])
        self.assertIn('HTTP 200',result['errorReason']);self.assertFalse(result['sourceDiagnostics']['hasBody'])

    def test_ingredient_photos_and_subbrand_promotions_do_not_become_finished_menus(self):
        for source in self.sources['unidentifiedSources']:
            brand=next(b for b in self.brands if b['name']==source['brand'])
            raw=self.raw(source['sha256'])
            declared=re.search(br'charset\s*=\s*["\']?([a-zA-Z0-9_-]+)',raw[:4096])
            encoding=declared.group(1).decode('ascii') if declared else 'utf-8'
            self.assertEqual([],candidates(dict(brand,sourceUrl=source['url'],adapter='jsonld'),
                raw.decode(encoding),'2026-10-17T00:00:00Z'))

    def test_actual_six_tab_replay_keeps_failure_evidence_and_withholds_incomplete_brand(self):
        from urllib.parse import urlparse
        captures={urlparse(s['url']).path:s for s in self.sources['pizzaTabs']}
        outer=self
        def response(request,**kwargs):
            uri=urlparse(request.full_url);source=captures[uri.path]
            class Response:
                url=request.full_url
                headers=__import__('email.message',fromlist=['Message']).Message()
                def __enter__(self):return self
                def __exit__(self,*args):pass
                def read(self,*args):return outer.raw(source['sha256'])
            return Response()
        with patch('urllib.request.urlopen',side_effect=response):result=fetch(self.pizza)
        self.assertEqual('FETCH_FAILED',result['status'])
        self.assertGreaterEqual(len(result['sources']),6)
        self.assertIn('INCOMPLETE_SOURCE_COVERAGE_WITHHELD',result['warnings'])
        self.assertEqual([],result['menus'])
        self.assertTrue(any('HTTP 200' in s.get('errorReason','') for s in result['sources']))

    def test_timeout_has_one_bounded_retry_and_never_removes_last_good_catalog(self):
        brand=next(b for b in self.brands if b['name']=='놀부부대찌개')
        with patch('urllib.request.urlopen',side_effect=TimeoutError('timed out')) as get,patch('time.sleep'):
            batch=fetch(brand)
        self.assertEqual(2,get.call_count);self.assertEqual('FETCH_FAILED',batch['status'])
        self.assertEqual([],batch['menus'])
        catalog=(ROOT/'app/src/main/assets/franchise/catalog.json').read_bytes()
        digest=hashlib.sha256(catalog).hexdigest()
        with tempfile.TemporaryDirectory(dir=ROOT/'app/build') as tmp:
            out=Path(tmp);(out/'catalog.json').write_bytes(catalog)
            manifest=dict(schemaVersion=1,file='catalog.json',sha256=digest,version=digest)
            payload=json.dumps(manifest).encode();(out/'latest.json').write_bytes(payload)
            result=run(out,'isolated-failed-source-preservation',self.brands,batches=[batch],target_brand_ids={brand['brandId']})
            self.assertEqual('EXISTING_CATALOG_RETAINED',result['status'])
            self.assertEqual(payload,(out/'latest.json').read_bytes());self.assertEqual(catalog,(out/'catalog.json').read_bytes())

if __name__=='__main__':unittest.main()
