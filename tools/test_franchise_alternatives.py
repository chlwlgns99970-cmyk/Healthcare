"""New official detail paths; current product identity never borrows stale facts."""
import copy,gzip,hashlib,json,unittest
from pathlib import Path
from franchise_daily_sync import CONFIG,SEED,candidates,merge,daily_nutrition
from franchise_nutrition import REGISTRY,refresh,load_reviewed_nutrition,FIELDS,OPTIONAL_FIELDS
from franchise_nutrition_youngman import parse,rows

ROOT=Path(__file__).resolve().parents[1]
DIR=ROOT/'data-source/franchise-nutrition-alternatives-20261017'

class AlternativeDetailTest(unittest.TestCase):
    def setUp(self):
        document=json.loads((DIR/'menu-identities.json').read_bytes())
        self.menus=document['menus'];self.sources=document['sourceCaptures']
        self.brands=json.loads(CONFIG.read_bytes());self.brand=next(b for b in self.brands if b['name']=='청년피자')
        self.registry=json.loads(REGISTRY.read_bytes())
    def raw(self,url):
        s=next(s for s in self.sources if s['url']==url)
        data=gzip.decompress((DIR/'evidence'/(s['sha256']+'.gz')).read_bytes())
        self.assertEqual(s['sha256'],hashlib.sha256(data).hexdigest());return data

    def test_five_exact_official_product_ids_and_two_sizes_do_not_use_image_ids(self):
        self.assertEqual(10,len(self.menus));self.assertEqual(5,len(self.sources))
        regenerated=[]
        for source in self.sources:
            regenerated+=candidates(dict(self.brand,sourceUrl=source['url'],adapter='youngman-detail'),
                self.raw(source['url']).decode('utf-8'),source['checkedAt'])
        self.assertEqual({m['id'] for m in self.menus},{m['id'] for m in regenerated})
        self.assertEqual({'35','36','39','41','46'},{m['externalId'].split(':')[1] for m in regenerated})
        self.assertEqual(10,len({m['id'] for m in regenerated}))

    def test_eight_matching_servings_only_have_actually_published_fields(self):
        verified=[m for m in self.menus if m.get('officialNutrition')]
        self.assertEqual(8,len(verified))
        for menu in verified:
            parsed=parse(menu,self.raw(menu['sourceUrl']))
            for key in FIELDS+OPTIONAL_FIELDS+('servingAmount','servingUnit','servingDescription'):
                self.assertEqual(parsed.get(key),menu['officialNutrition'].get(key))
            self.assertIsNone(parsed['carbohydrateGrams']);self.assertIsNone(parsed['fatGrams'])
        potato=next(m for m in verified if m['externalId']=='menu_detail_pizza.php:35:R')
        self.assertEqual((120,322,12),tuple(potato['officialNutrition'][k] for k in ('servingAmount','energyKcal','proteinGrams')))

    def test_current_sale_name_survives_old_product_nutrition_table_without_guessing(self):
        held=[m for m in self.menus if m['externalId'].split(':')[1]=='46']
        self.assertEqual(2,len(held))
        for menu in held:
            self.assertTrue(menu['name'].startswith('구름에그콘피자 ('))
            self.assertFalse(menu.get('officialNutrition'))
            self.assertEqual('NUTRITION_SOURCE_FOUND_UNMATCHED',menu['nutritionStatus'])
            self.assertTrue(all(menu.get(k) is None for k in FIELDS+OPTIONAL_FIELDS+('servingAmount','servingUnit')))
            raw=self.raw(menu['sourceUrl'])
            self.assertEqual(2,len(rows(dict(self.brand,sourceUrl=menu['sourceUrl']),raw)))
            with self.assertRaisesRegex(AssertionError,'Detail/table product mismatch'):parse(menu,raw)

    def test_exact_title_suffix_pair_cannot_match_another_product_or_variant(self):
        menu=next(m for m in self.menus if m['externalId']=='menu_detail_pizza.php:36:R')
        raw=self.raw(menu['sourceUrl'])
        self.assertGreater(parse(menu,raw)['proteinGrams'],0)
        for invalid in [dict(menu,externalId='menu_detail_pizza.php:35:R'),dict(menu,name='할라불고기 (L)'),
                        dict(menu,sourceUrl=menu['sourceUrl'].replace('seq=36','seq=35'))]:
            with self.assertRaises(AssertionError):parse(invalid,raw)
        changed=raw.replace('할라불고기 피자'.encode(),'할라불고기 새제품 피자'.encode())
        self.assertNotEqual(raw,changed)
        with self.assertRaises(AssertionError):parse(menu,changed)

    def test_repeated_daily_merge_and_replay_do_not_duplicate_or_fill_held_values(self):
        catalog=json.loads((ROOT/'app/src/main/assets/franchise/catalog.json').read_bytes())['menus']
        merged,audit=merge(dict(menus=catalog),[dict(menus=self.menus)],self.brands,json.loads(SEED.read_bytes()))
        self.assertEqual(catalog,merged);self.assertEqual(0,audit['publishedNewCount']);self.assertEqual(10,audit['duplicateCount'])
        once,_=load_reviewed_nutrition(merged,self.registry);twice,_=load_reviewed_nutrition(once,self.registry)
        self.assertEqual(once,twice)
        for menu in twice:
            if menu['id'] in {m['id'] for m in self.menus if '구름에그콘' in m['name']}:
                self.assertFalse(menu.get('officialNutrition'))

    def test_failed_refresh_preserves_last_verified_facts_and_all_menu_ids(self):
        before=copy.deepcopy(self.menus);calls=[]
        def offline(url):calls.append(url);raise TimeoutError('isolated network failure')
        retained,audit=refresh(before,registry=self.registry,fetcher=offline)
        self.assertEqual(10,audit['failed']);self.assertEqual(5,len(calls));self.assertEqual(5,len(set(calls)))
        self.assertEqual([m['id'] for m in self.menus],[m['id'] for m in retained])
        for old,new in zip(self.menus,retained):self.assertEqual(old.get('officialNutrition'),new.get('officialNutrition'))
        self.assertEqual(self.menus,before)

if __name__=='__main__':unittest.main()
