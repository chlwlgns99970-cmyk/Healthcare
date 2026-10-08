"""Safety contracts for newly reviewed sources and bounded full-page traversal."""
import copy, gzip, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
from franchise_daily_sync import CONFIG, ROOT, candidates, fetch, fetch_source, merge, normalize, source_identity,run
from franchise_pagination import additional_pages

class CompletionTest(unittest.TestCase):
    def setUp(self):
        self.brands=json.loads(CONFIG.read_text(encoding='utf-8'))
        self.brand=next(b for b in self.brands if b['name']=='설빙')
        self.menu=candidates(self.brand,'<a href="menu_view.php?menu=991123">검증 신메뉴</a>','2026-10-08T00:00:00Z')[0]
    def batch(self,menus,status='SUCCESS'):
        return dict(brandId=self.brand['brandId'],checkedAt='2026-10-08T00:00:00Z',status=status,menus=menus)
    def test_transient_retry_bounded_and_403_not_retried(self):
        brand=dict(self.brand,sources=self.brand['sources'][:1])
        failed=dict(self.batch([],'FETCH_FAILED'),error='TimeoutError',httpStatus=None)
        with patch('franchise_daily_sync.fetch_source',side_effect=[failed,self.batch([self.menu])]) as transport,patch('franchise_daily_sync.time.sleep'):
            result=fetch(brand)
        self.assertEqual(2,transport.call_count);self.assertEqual('SUCCESS',result['status'])
        forbidden=dict(failed,error='HTTPError',httpStatus=403)
        with patch('franchise_daily_sync.fetch_source',return_value=forbidden) as transport:fetch(brand)
        self.assertEqual(1,transport.call_count)
    def test_audit_history_retains_last_success_after_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            first=run(directory,'success',self.brands,[self.batch([self.menu])])
            failure=self.batch([],'FETCH_FAILED');failure['checkedAt']='2026-10-09T00:00:00Z'
            second=run(directory,'failed',self.brands,[failure])
            state=json.loads((Path(directory)/'latest-franchise-brand-status.json').read_text(encoding='utf-8'))[0]
            self.assertEqual('FAILED',state['lastResult']);self.assertEqual('2026-10-08T00:00:00Z',state['lastSuccessAt'])
            self.assertEqual(2,len(list((Path(directory)/'sync-history').glob('*.json'))));self.assertEqual(first['checksum'],second['checksum'])
    def test_spike_withholds_all_new_candidates(self):
        brand=dict(self.brand,expectedMenuCount=20)
        source=self.batch([dict(self.menu,id=str(i)) for i in range(100)])
        with patch('franchise_daily_sync.fetch_source',return_value=source):result=fetch(brand)
        self.assertEqual([],result['menus']);self.assertEqual(100,result['observedMenuCount'])
        self.assertIn('MENU_COUNT_SPIKE_REVIEW',result['warnings'])
    def test_drop_warns_and_keeps_every_existing_menu(self):
        brand=dict(self.brand,expectedMenuCount=20)
        with patch('franchise_daily_sync.fetch_source',return_value=self.batch([self.menu])):result=fetch(brand)
        self.assertIn('MENU_COUNT_DROP_EXISTING_RETAINED',result['warnings'])
        existing=dict(menus=[self.menu]);menus,_=merge(existing,[result],self.brands,[])
        self.assertEqual(existing['menus'],menus)
    def test_failed_category_cannot_publish_partial_collection(self):
        brand=dict(self.brand,sources=[dict(url=self.brand['sourceUrl'],adapter='sulbing'),dict(url=self.brand['sourceUrl']+'?page=2',adapter='sulbing')])
        with patch('franchise_daily_sync.fetch_source',side_effect=[self.batch([self.menu]),self.batch([],'FETCH_FAILED')]):result=fetch(brand)
        self.assertEqual([],result['menus']);self.assertIn('INCOMPLETE_SOURCE_COVERAGE_WITHHELD',result['warnings'])
    def test_mega_traverses_last_official_page_and_bounds_limit(self):
        brand=next(b for b in self.brands if b['name']=='메가MGC커피');source=brand['sources'][0]
        pages=additional_pages(brand,source,'<ul id="board_page"><a data-page="8">8</a></ul>')
        self.assertEqual(7,len(pages));self.assertIn('page=8',pages[-1]['url'])
        with self.assertRaises(AssertionError):additional_pages(brand,source,'<ul id="board_page"><a data-page="9999">9999</a></ul>')
    def test_pagination_never_follows_another_site(self):
        brand=next(b for b in self.brands if b['name']=='홍종흔베이커리');source=dict(brand['sources'][0],pagination='hjh')
        with self.assertRaises(AssertionError):additional_pages(brand,source,'<div class="pagination"><a href="https://unofficial.example/?page=2">2</a></div>')
    def test_bhc_options_keep_distinct_identities(self):
        brand=next(b for b in self.brands if b['name']=='BHC')
        brand=dict(brand,adapter='bhc-json',sourceUrl='https://www.bhc.co.kr/api/v1/web/categories/1/products')
        body=json.dumps(dict(status='success',body=[dict(productCd='120000',productNm='커링클',options=[dict(optionNm='순살'),dict(optionNm='한마리')])]))
        menus=candidates(brand,body,'2026-10-08T00:00:00Z')
        self.assertEqual(['커링클 (순살)','커링클 (한마리)'],[m['name'] for m in menus])
        self.assertNotEqual(menus[0]['id'],menus[1]['id'])
        self.assertTrue(all(m['energyKcal'] is None for m in menus))
    def test_mcdonalds_page_count_mismatch_fails_closed(self):
        brand=next(b for b in self.brands if b['name']=='맥도날드')
        source=dict(url='https://www.mcdonalds.co.kr/api/v1/kor/product/product/list?page=1&view_rows=6&mainCategory=1&subCategory=16&searchWord=',pagination='mc-pages')
        body=json.dumps(dict(resultCode=100,isOk='ok',resultObject=dict(totalCount=23,list=[{}])))
        with self.assertRaises(AssertionError):additional_pages(brand,source,body)
    def test_ediya_load_more_stops_only_at_official_terminal_marker(self):
        brand=next(b for b in self.brands if b['name']=='이디야')
        source=dict(url='https://www.ediya.com/inc/ajax_brand.php?gubun=menu_more&product_cate=7&chked_val=&skeyword=&page=2',pagination='ediya-next',adapter='ediya-fragment')
        self.assertEqual([],additional_pages(brand,source,'none'))
        self.assertIn('page=3',additional_pages(brand,source,'<li>menu</li>')[0]['url'])
    def test_hongik_prices_and_option_notes_are_not_menus(self):
        brand=next(b for b in self.brands if b['name']=='홍익돈까스')
        brand=dict(brand,sourceUrl='https://www.hongikdonkatsu.com/menu_2021_takeout',adapter='hongik-text')
        markup='<html><p><span text-style-option="fontStyleBold">까르보나라 14,000원</span></p><p style="font-size: 20px"><span text-style-option="color">&lt;등심돈까스 + 생선까스&gt;</span></p></html>'
        self.assertEqual(['까르보나라'],[m['name'] for m in candidates(brand,markup,'2026-10-08T00:00:00Z')])
    def test_paris_page_total_must_match_page_size(self):
        brand=next(b for b in self.brands if b['name']=='파리바게뜨');source=brand['sources'][0]
        with self.assertRaises(AssertionError):additional_pages(brand,source,'<ul data-total-count="24"><li>one</li></ul>')
    def test_bhc_category_schema_never_silently_drops_invalid_category(self):
        brand=next(b for b in self.brands if b['name']=='BHC');source=brand['sources'][0]
        body=json.dumps(dict(status='success',body=[dict(cateIdx=1,cateNm='치킨'),dict(cateIdx='invalid',cateNm='사이드')]))
        with self.assertRaises(AssertionError):additional_pages(brand,source,body)
    def test_banolim_rejects_repeated_first_tab_response(self):
        brand=next(b for b in self.brands if b['name']=='반올림피자')
        brand=dict(brand,sourceUrl='https://order.banolimpizza.com/menu/list?categoryId=3',adapter='banolim-native')
        records=[dict(categoryId=1),dict(id=585,name='공식메뉴',basePrice=10000,soldOut=False,optionGroups=[])]
        markup='<html><script type="application/json" id="recorded-next-menu-data">'+json.dumps(records)+'</script></html>'
        with self.assertRaises(AssertionError):candidates(brand,markup,'2026-10-08T00:00:00Z')
    def test_bbq_category_index_follows_all_official_categories(self):
        brand=next(b for b in self.brands if b['name']=='BBQ');source=brand['sources'][0]
        body=json.dumps([dict(id=i,categoryName='메뉴 '+str(i)) for i in range(1,9)])
        pages=additional_pages(brand,source,body);self.assertEqual(8,len(pages))
        self.assertTrue(all(p['adapter']=='bbq-json' for p in pages))
    def test_recorded_completion_sources_keep_menu_identities(self):
        path=ROOT/'data-source/franchise-sync/fixtures/recorded-completion.json.gz'
        fixtures=json.loads(gzip.decompress(path.read_bytes()))
        self.assertGreaterEqual(len(fixtures),200)
        for fixture in fixtures:
            with self.subTest(brand=fixture['brand']['name'],url=fixture['brand']['sourceUrl']):
                menus=candidates(fixture['brand'],fixture['markup'],'2026-10-08T00:00:00Z')
                self.assertEqual(fixture['expected'],[dict(id=m['id'],name=m['name']) for m in menus])
                self.assertFalse(any(m['energyKcal'] is not None for m in menus))
    def test_form_category_requests_are_not_collapsed_by_url(self):
        sources=[dict(url='https://www.kfckorea.com/kfc/interface/selectDeliveryList',form=dict(product_ordertype='D',delivery_subGroupCd=k)) for k in ('CHKN','BEGR')]
        self.assertNotEqual(source_identity(sources[0]),source_identity(sources[1]))
    def test_encoded_category_space_is_preserved(self):
        brand=next(b for b in self.brands if b['name']=='미스터피자')
        source=brand['sources'][0]
        seen=[]
        def observe(request,**kwargs):
            seen.append(request.full_url);raise TimeoutError()
        with patch('urllib.request.urlopen',side_effect=observe):fetch_source(brand,source)
        self.assertIn('+',seen[0]);self.assertNotIn('%2B',seen[0])
    def test_same_name_different_official_id_is_held_for_review(self):
        second=candidates(self.brand,'<a href="menu_view.php?menu=991124">검증 신메뉴</a>','2026-10-08T00:00:00Z')[0]
        menus,stats=merge(dict(menus=[self.menu]),[dict(menus=[second])],self.brands,[])
        self.assertEqual([self.menu],menus);self.assertEqual(1,stats['identityReviewCount'])
        self.assertEqual('REVIEW_REQUIRED',stats['decisions'][0]['state'])
    def test_best_menu_reference_missing_from_full_tabs_withholds_brand(self):
        brand=next(b for b in self.brands if b['name']=='노랑통닭')
        source=dict(self.batch([]),coverageReferenceIds=['999999'])
        with patch('franchise_daily_sync.fetch_source',return_value=source):result=fetch(brand)
        self.assertEqual([],result['menus']);self.assertIn('INCOMPLETE_SOURCE_COVERAGE_WITHHELD',result['warnings'])
    def test_default_tls_transport_keeps_certificate_and_host_validation(self):
        source=(ROOT/'tools/FranchiseOfficialTlsFetch.java').read_text()
        self.assertNotIn('setHostnameVerifier',source);self.assertNotIn('TrustManager',source)
        self.assertNotIn('setSSLSocketFactory',source);self.assertNotIn('Security.setProperty',source)
        self.assertIn('uri.getUserInfo() != null',source)
    def test_empty_unknown_markup_never_authorizes_success(self):
        from franchise_pagination import confirmed_empty_source
        self.assertFalse(confirmed_empty_source(self.brand,dict(adapter='sulbing',url=self.brand['sourceUrl']),'<html></html>'))
    def test_goobne_follows_official_get_category_form(self):
        brand=next(b for b in self.brands if b['name']=='굽네치킨')
        markup='<form name="menuListForm" method="get"></form>'+''.join('<a onclick="menu_list(\'%s\',\'\')">tab</a>'%i for i in (1,3,12,16,18))
        pages=additional_pages(brand,brand['sources'][0],markup)
        self.assertEqual(5,len(pages));self.assertTrue(all('classId=' in p['url'] for p in pages))
    def test_static_object_data_rejects_javascript_execution(self):
        from franchise_literal_data import parse_object_literal
        self.assertEqual({'new':{'items':[{'name':'공식메뉴'}]}},parse_object_literal("{new:{items:[{name:'공식메뉴'}]}}"))
        for source in ("{name:fetch('https://example.com')}","{name:(function(){return 'x'})()}","{name:'x',name:'y'}"):
            with self.assertRaises(AssertionError):parse_object_literal(source)
    def test_baeksojeong_missing_menu_tab_fails_closed(self):
        brand=next(b for b in self.brands if b['name']=='백소정')
        brand=dict(brand,adapter='baeksojeong-native')
        markup='<html><script id="recorded-baeksojeong-data" type="application/json">{"new":{"items":[{"name":"메뉴"}]}}</script></html>'
        with self.assertRaises(AssertionError):candidates(brand,markup,'2026-10-08T00:00:00Z')
    def test_youngman_real_size_and_product_ids_stay_distinct(self):
        brand=dict(next(b for b in self.brands if b['name']=='청년피자'),adapter='youngman-native',sourceUrl='https://youngmanpizza.co.kr/sub01/menu2.php')
        markup='<html><ul class="menu_list"><li><a href="menu_detail_pizza2.php?seq=20"><div class="menu_name">공식 피자</div><ul class="menu_price"><li><div class="size">R</div><div class="price">24000</div></li><li><div class="size">L</div><div class="price">29000</div></li></ul></a></li></ul></html>'
        menus=candidates(brand,markup,'2026-10-08T00:00:00Z')
        self.assertEqual(['공식 피자 (R)','공식 피자 (L)'],[m['name'] for m in menus]);self.assertEqual(2,len({m['id'] for m in menus}))
        self.assertTrue(all(m['energyKcal'] is None for m in menus))
        with self.assertRaises(AssertionError):candidates(dict(brand,sourceUrl='https://youngmanpizza.co.kr/sub01/menu5.php'),markup,'2026-10-08T00:00:00Z')
    def test_youngman_pagination_bounds_and_host(self):
        brand=next(b for b in self.brands if b['name']=='청년피자');source=brand['sources'][1]
        pages=additional_pages(brand,source,'<a href="menu2.php?page_num=3">3</a><a href="menu5.php">토핑</a>')
        self.assertEqual(1,len(pages));self.assertIn('page_num=3',pages[0]['url'])
        with self.assertRaises(AssertionError):additional_pages(brand,source,'<a href="menu2.php?page_num=999">999</a>')
    def test_diagnostics_never_retains_scripts_cookie_or_form_values(self):
        from franchise_source_diagnostics import public_response_diagnostics
        markup='<html><body><script>document.cookie="private-cookie";location.href="/?token=private-token";</script><input value="private-value"></body></html>'
        result=public_response_diagnostics(markup,100)
        self.assertTrue(result['inlineSignals']['cookieWrite']);self.assertTrue(result['inlineSignals']['locationNavigation'])
        self.assertNotIn('private',json.dumps(result));self.assertEqual(0,result['visibleTextLength'])

if __name__=='__main__':unittest.main(verbosity=2)
