"""Verify normal browser boundaries against a local public HTML fixture."""
import json, threading, unittest
from pathlib import Path
from franchise_daily_sync import candidates,fetch
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from franchise_browser import render,brand_browser_session,denied,PublicAccessDenied

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        if self.path=='/denied':self.send_response(403);self.end_headers();return
        self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.end_headers()
        if self.path=='/redirect':content='<script>document.cookie="public=ok";location.href="/menu"</script>'
        elif self.path=='/challenge':content='<body>Verify you are human CAPTCHA</body>'
        elif self.path=='/loadmore':content='<body><button>더 보기</button></body>'
        else:content='<body><ul id="menu"></ul><script>setTimeout(()=>document.querySelector("#menu").innerHTML="<li>새 메뉴</li>",200)</script></body>'
        self.wfile.write(content.encode())

class BrowserTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=cls.server.serve_forever,daemon=True).start()
        cls.base='http://127.0.0.1:'+str(cls.server.server_port)
        cls.brand={'allowedHosts':['127.0.0.1']}
    @classmethod
    def tearDownClass(cls):cls.server.shutdown();cls.server.server_close()
    def get(self,path):
        with brand_browser_session():return render(self.brand,dict(url=self.base+path))
    def test_js_and_normal_cookie_redirect(self):
        markup,evidence=self.get('/redirect');self.assertIn('새 메뉴',markup)
        self.assertEqual(self.base+'/menu',evidence['finalUrl']);self.assertTrue(evidence['tlsVerification']);self.assertTrue(evidence['scrollStable'])
    def test_access_denial_not_bypassed(self):
        with self.assertRaisesRegex(ValueError,'ACCESS_DENIED'):self.get('/denied')
    def test_challenge_not_solved(self):
        with self.assertRaisesRegex(ValueError,'ACCESS_CHALLENGE'):self.get('/challenge')
    def test_unreviewed_loadmore_withheld(self):
        with self.assertRaisesRegex(AssertionError,'load-more'):self.get('/loadmore')
    def test_unapproved_navigation(self):
        with brand_browser_session():
            with self.assertRaises(AssertionError):render(self.brand,dict(url='https://example.com/menu'))
    def test_hecbob_whole_category_variant_identity(self):
        root=Path(__file__).resolve().parents[1]
        brand=next(b for b in json.loads((root/'data-source/franchise-sync/brands.json').read_text(encoding='utf-8')) if b['name']=='핵밥')
        brand.update(adapter='hecbob-rendered',sourceUrl=brand['sources'][0]['url'])
        markup=(root/'data-source/franchise-sync/fixtures/hecbob-rendered.html').read_text(encoding='utf-8')
        first=candidates(brand,markup,'2026-10-08T00:00:00Z');second=candidates(brand,markup,'2026-10-09T00:00:00Z')
        self.assertEqual(58,len(first));self.assertEqual(6,len({m['category'] for m in first}));self.assertEqual({m['id'] for m in first},{m['id'] for m in second})
        self.assertTrue(all(m['energyKcal'] is None for m in first))
        with self.assertRaises(AssertionError):candidates(brand,markup.replace('id="menu_bowl"','id="missing"'),'2026-10-08T00:00:00Z')
    def test_brand_failure_isolated(self):
        with patch('franchise_daily_sync._fetch',side_effect=ValueError('changed schema')):
            result=fetch(dict(brandId='isolated',sources=[]))
        self.assertEqual('FETCH_FAILED',result['status']);self.assertEqual([],result['menus'])
    def test_browser_http_status_recorded_without_retry(self):
        from franchise_daily_sync import fetch_source
        brand=dict(brandId='test',name='Test',allowedHosts=['127.0.0.1'])
        with patch('franchise_browser.render',side_effect=PublicAccessDenied(403)):
            result=fetch_source(brand,dict(url=self.base+'/menu',adapter='jsonld',transport='browser'))
        self.assertEqual(403,result['httpStatus']);self.assertEqual([],result['menus'])

if __name__=='__main__':unittest.main(verbosity=2)
