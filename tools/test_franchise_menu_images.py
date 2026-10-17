import copy
import gzip
import hashlib
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from franchise_menu_images import INDEX, ImageReviewRequired, enrich, inventory, policy, rows, NoRedirect
from franchise_daily_sync import CONFIG, candidates, merge, fetch, automation_state
from franchise_source_diagnostics import public_response_diagnostics

ROOT = Path(__file__).resolve().parents[1]


class OfficialImageMenuTest(unittest.TestCase):
    def setUp(self):
        self.sources = json.loads(INDEX.read_bytes())['sources']
        self.brands = json.loads(CONFIG.read_bytes())

    def markup(self, source):
        if source['brand'] == '청년다방':
            return '<html><main id="menubook-popup">' + ''.join('<div class="swiper-slide"><img src="'+i['url']+'"></div>' for i in source['items']) + '</main></html>'
        if source['brand'] == '캠토토스트':
            return '<html><main>' + ''.join('<div class="_img_box"><img id="'+i['id']+'" src="'+i['url']+'" alt=""></div>' for i in source['items']) + '</main></html>'
        return '<html><main>' + ''.join('<div class="_item item_gallary"><div id="'+i['id']+'" data-bg="url('+i['url']+')"></div></div>' for i in source['items']) + '</main></html>'

    def brand(self, source):
        return dict(next(b for b in self.brands if b['name']==source['brand']),sourceUrl=source['sourceUrl'],adapter='reviewed-image-labels')

    def captured(self, source):
        return {i['url']:gzip.decompress((INDEX.parent/i['evidence']).read_bytes()) for i in source['items']}

    def test_all_reviewed_sources_have_exact_captured_image_proof_and_names(self):
        for source in self.sources:
            with self.subTest(source=source['sourceUrl']):
                data=self.captured(source);before=copy.deepcopy(source);brand=self.brand(source)
                for i in source['items']:self.assertEqual(i['sha256'],hashlib.sha256(data[i['url']]).hexdigest())
                enriched=enrich(brand,self.markup(source),data.__getitem__)
                result=rows(brand,enriched)
                self.assertEqual(sum(len(i['names']) for i in source['items']),len(result))
                menus=candidates(brand,enriched,'2026-10-16T00:00:00Z')
                self.assertEqual(len(result),len(menus))
                self.assertTrue(all(m['externalId']=='' and m['energyKcal'] is None and m['fatGrams'] is None for m in menus))
                first,_=merge({},[dict(menus=menus)],self.brands,[])
                again,count=merge(dict(menus=first),[dict(menus=menus)],self.brands,[])
                self.assertEqual(first,again);self.assertEqual(len(menus),count['duplicateCount'])
                self.assertEqual(source,before)

    def test_changed_image_or_inventory_never_reuses_old_name(self):
        source=self.sources[0];brand=self.brand(source);markup=self.markup(source);data=self.captured(source)
        with self.assertRaisesRegex(ImageReviewRequired,'IMAGE_CHANGED'):enrich(brand,markup,lambda url:data[url]+b'changed')
        for changed in [markup.replace(source['items'][0]['id'],'img_w1234'),markup.replace(source['items'][0]['url'],source['items'][0]['url']+'?new=1'),markup.replace('</main>','<div class="_img_box"><img id="img_w1234" src="'+source['items'][0]['url']+'"></div></main>')]:
            with self.assertRaises(ImageReviewRequired):enrich(brand,changed,data.__getitem__)

    def test_other_brand_host_and_unverified_hashes_are_blocked(self):
        source=self.sources[0];brand=self.brand(source);markup=self.markup(source)
        with self.assertRaises(ImageReviewRequired):inventory(dict(brand,name='다른 브랜드'),markup)
        with self.assertRaises(ImageReviewRequired):inventory(brand,markup.replace('https://cdn.imweb.me/','https://unapproved.test/'))
        with self.assertRaisesRegex(ImageReviewRequired,'HASHES_REQUIRED'):rows(brand,markup)
        self.assertIsNone(NoRedirect().redirect_request(None,None,302,'',{},'https://other.test'))

    def test_changed_source_needs_review_and_no_automatic_repeat(self):
        brand=next(b for b in self.brands if b['name']=='우지커피')
        brand=dict(brand,sources=brand['sources'][:1])
        failed=dict(brandId=brand['brandId'],status='REVIEW_REQUIRED',menus=[],checkedAt='2026-10-16T00:00:00Z',error='ImageReviewRequired',errorReason='OFFICIAL_IMAGE_CHANGED_REVIEW_REQUIRED')
        with patch('franchise_daily_sync.fetch_source',return_value=failed) as source:
            result=fetch(brand)
        self.assertEqual(1,source.call_count);self.assertEqual('REVIEW_REQUIRED',result['status']);self.assertEqual([],result['menus'])

    def test_menu_board_changes_or_images_outside_the_official_popup_require_review(self):
        source=next(s for s in self.sources if s['brand']=='청년다방');brand=self.brand(source)
        markup=self.markup(source);data=self.captured(source)
        for changed in [markup.replace('menubook-popup','another-popup'),
                        markup.replace('youngdabang.com/','cdn.imweb.me/'),
                        markup.replace('menu_table1.jpg','menu_table4.jpg'),
                        markup.replace('</main>','<div class="swiper-slide"><img src="/img/page/menu/01/menu_table4.jpg"></div></main>')]:
            with self.assertRaises(ImageReviewRequired):enrich(brand,changed,data.__getitem__)

    def test_http_200_traffic_quota_is_not_a_missing_menu_or_a_retry(self):
        markup='<html><body>This site is currently unavailable. Site traffic resets at midnight.</body></html>'
        self.assertTrue(public_response_diagnostics(markup,len(markup))['officialTrafficLimit'])
        self.assertFalse(public_response_diagnostics('<html><body>This site is currently unavailable</body></html>',1)['officialTrafficLimit'])
        brand=next(b for b in self.brands if b['name']=='탕화쿵푸')
        held=dict(brandId=brand['brandId'],status='REVIEW_REQUIRED',menus=[],checkedAt='2026-10-16T00:00:00Z',accessBlocked=True,httpStatus=200)
        with patch('franchise_daily_sync.fetch_source',return_value=held) as source:
            result=fetch(brand)
        self.assertEqual(1,source.call_count)
        self.assertEqual('SOURCE_BLOCKED',automation_state(brand,result))
        self.assertEqual([],result['menus'])


if __name__=='__main__':unittest.main()
