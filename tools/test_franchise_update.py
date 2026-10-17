"""Offline contracts for scoped updates; these do not claim live collection."""
import copy, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
import franchise_daily_sync as daily

class FranchiseUpdateTest(unittest.TestCase):
    def setUp(self):
        self.brands=json.loads(daily.CONFIG.read_text(encoding='utf-8'))
        self.brand=next(b for b in self.brands if b['name']=='설빙')
        self.menu=daily.candidates(self.brand,'<a href="menu_view.php?menu=993312">검증 제품 ICE 라지</a>',
            '2026-10-17T00:00:00Z')[0]

    def merge(self, incoming, status='SUCCESS'):
        return daily.merge(dict(menus=[self.menu]),[dict(brandId=self.brand['brandId'],status=status,menus=incoming)],self.brands,[])

    def test_97_targets_never_remove_five_existing_brands(self):
        selected=daily.update_targets(self.brands,True)
        self.assertEqual(97,len(selected))
        self.assertFalse(selected & {b['brandId'] for b in self.brands if b['name'] in daily.DEFERRED_BRANDS})
        with self.assertRaises(AssertionError):daily.update_targets(self.brands,True,{'청년피자'})
        unchanged,_=daily.merge(dict(menus=[self.menu]),[],self.brands,[])
        self.assertEqual([self.menu],unchanged)

    def test_same_id_rename_and_size_change_are_review_not_silent_existing(self):
        for changed in (dict(name='규격 변경 제품'),dict(category='다른 규격'),dict(sourceUrl=self.menu['sourceUrl']+'&size=L')):
            with self.subTest(changed=changed):
                menus,audit=self.merge([dict(self.menu,**changed)])
                self.assertEqual([self.menu],menus)
                self.assertEqual(1,audit['changeReviewCount'])
                self.assertEqual('EXISTING_PRODUCT_CHANGED',audit['decisions'][0]['reason'])

    def test_image_change_requires_review(self):
        self.menu['imageUrl']='https://sulbing.com/images/previous.jpg'
        menus,audit=self.merge([dict(self.menu,imageUrl='https://sulbing.com/images/new.jpg')])
        self.assertEqual([self.menu],menus)
        self.assertEqual(['imageUrl'],audit['decisions'][0]['changedFields'])

    def test_disappearance_is_not_deletion_or_confirmed_end_of_sale(self):
        menus,audit=self.merge([])
        self.assertEqual([self.menu],menus)
        self.assertEqual(1,audit['availabilityReviewCount'])
        self.assertEqual('NOT_OBSERVED_EXISTING_RETAINED',audit['decisions'][0]['reason'])
        self.assertNotIn('saleState',menus[0])

    def test_failed_or_partial_source_cannot_add_or_remove(self):
        new=dict(self.menu,externalId='993313',name='독립 신규 제품')
        new['id']=daily.stable_id(new['brandId'],new['externalId'],new['name'],new['category'])
        menus,audit=self.merge([new],'FETCH_FAILED')
        self.assertEqual([self.menu],menus)
        self.assertEqual(0,audit['publishedNewCount'])
        self.assertEqual(0,audit['availabilityReviewCount'])
        good=dict(brandId=self.brand['brandId'],status='SUCCESS',menus=[new])
        bad=dict(good,sources=[dict(status='FETCH_FAILED')])
        merged,audit=daily.merge(dict(menus=[self.menu]),[bad,good],self.brands,[])
        self.assertEqual(2,len(merged));self.assertEqual(1,audit['publishedNewCount'])
        self.assertEqual(merged,daily.merge(dict(menus=merged),[good],self.brands,[])[0])

    def test_validated_baseline_is_explicit_and_checksum_failure_is_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'catalog.json'
            payload=json.dumps(dict(schemaVersion=1,menus=[self.menu])).encode()
            path.write_bytes(payload)
            manifest=path.with_name('catalog-manifest.json')
            manifest.write_text(json.dumps(dict(schemaVersion=1,menuCount=1,sha256=daily.hashlib.sha256(payload).hexdigest())))
            result,audit=daily.seed_verified_catalog({},path,self.brands)
            self.assertEqual([self.menu],result['menus'])
            self.assertEqual(1,audit['publishedNewCount'])
            path.write_bytes(payload+b' ')
            with self.assertRaises(AssertionError):daily.seed_verified_catalog({},path,self.brands)

    def test_old_published_plus_actual_app_baseline_replays_without_loss(self):
        baseline=daily.ROOT/'app/src/main/assets/franchise/catalog.json'
        menus=json.loads(baseline.read_bytes())['menus']
        old=copy.deepcopy(menus[:100])
        combined,audit=daily.seed_verified_catalog(dict(menus=old),baseline,self.brands)
        enriched,_=daily.daily_nutrition(combined['menus'],False)
        by_id={m['id']:m for m in enriched}
        self.assertEqual(6197,len(by_id))
        self.assertEqual(6097,audit['publishedNewCount'])
        for before in menus:
            after=by_id[before['id']]
            for key in ('brand','name','externalId','sourceUrl'):
                self.assertEqual(before[key],after[key])
            for key in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams','sodiumMilligrams',
                        'sugarsGrams','saturatedFatGrams','servingAmount','servingUnit'):
                value=before.get('officialNutrition',{}).get(key)
                if value is not None:self.assertEqual(value,after.get('officialNutrition',{}).get(key))

    def test_no_network_for_supplied_batches_and_local_artifact_status(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(daily,'fetch',side_effect=AssertionError('unexpected network')):
            batch=dict(brandId=self.brand['brandId'],status='SUCCESS',menus=[self.menu])
            first=daily.run(folder,'one',self.brands,[batch])
            second=daily.run(folder,'two',self.brands,[batch])
            self.assertTrue(first['catalogWritten']);self.assertFalse(second['catalogWritten'])
            self.assertEqual('LOCAL_ARTIFACT_ONLY',second['publicationStage'])

if __name__=='__main__':unittest.main(verbosity=2)
