import copy,gzip,hashlib,json
from pathlib import Path
import unittest
from franchise_nutrition_youngman import parse,details
from franchise_daily_sync import CONFIG,SEED,candidates,merge
from franchise_nutrition import refresh,REGISTRY,load_reviewed_nutrition,FIELDS,OPTIONAL_FIELDS

ROOT=Path(__file__).resolve().parents[1]
DIR=ROOT/'data-source/franchise-nutrition-remaining-six-20261016'

class YoungmanOfficialDetailTest(unittest.TestCase):
    def setUp(self):
        self.rows=json.loads((DIR/'linked-menus.json').read_bytes())['menus']
        self.registry=json.loads(REGISTRY.read_bytes())
    def payload(self,menu):
        raw=gzip.decompress((DIR/'evidence'/(menu['officialNutrition']['sourceSha256']+'.gz')).read_bytes())
        self.assertEqual(menu['officialNutrition']['sourceSha256'],hashlib.sha256(raw).hexdigest())
        return raw
    def test_actual_seven_details_have_twelve_exact_size_servings_and_no_carbs_or_total_fat(self):
        self.assertEqual(12,len(self.rows));self.assertEqual(7,len({r['sourceUrl'] for r in self.rows}))
        for row in self.rows:
            parsed=parse(row,self.payload(row));fact=row['officialNutrition']
            for key in FIELDS+OPTIONAL_FIELDS+('servingAmount','servingUnit','servingDescription'):
                self.assertEqual(fact.get(key),parsed.get(key))
            self.assertIsNone(parsed['carbohydrateGrams']);self.assertIsNone(parsed['fatGrams'])
            self.assertIn('1조각',parsed['servingDescription'])
        row=next(r for r in self.rows if r['name']=='옥수동 새우집 (R)')
        self.assertEqual((102,230,11),(row['officialNutrition']['servingAmount'],row['officialNutrition']['energyKcal'],row['officialNutrition']['proteinGrams']))
    def test_wrong_brand_id_size_name_or_source_is_rejected(self):
        row=self.rows[0];raw=self.payload(row)
        for changed in [dict(row,brand='다른 브랜드'),dict(row,name='다른 피자 (R)'),
                        dict(row,externalId='menu_detail_pizza.php:54:X'),dict(row,externalId='menu_detail_pizza.php:55:R'),
                        dict(row,sourceUrl='https://other.test/sub01/menu_detail_pizza.php?seq=54')]:
            with self.assertRaises(AssertionError):parse(changed,raw)
    def test_changed_columns_portions_and_duplicate_size_never_produce_guessed_values(self):
        row=self.rows[0];raw=self.payload(row)
        for changed in [raw.replace('포화지방'.encode(),'총지방'.encode()),raw.replace(b'<td>102</td>',b'<td>-</td>'),
                        raw.replace(b'<td>L</td>',b'<td>R</td>'),raw.replace('옥수동 새우집</th>'.encode(),'다른 피자</th>'.encode())]:
            with self.assertRaises(AssertionError):parse(row,changed)
    def test_detail_and_list_share_stable_ids_and_repeat_merge_cannot_duplicate(self):
        brands=json.loads(CONFIG.read_bytes());pizza=next(b for b in brands if b['name']=='청년피자')
        menus=[]
        for row in self.rows:
            brand=dict(pizza,sourceUrl=row['sourceUrl'],adapter='youngman-detail')
            menus.extend(candidates(brand,self.payload(row).decode('utf-8'),'2026-10-16T00:00:00Z'))
        unique={m['id']:m for m in menus};self.assertEqual(12,len(unique))
        self.assertEqual({r['id'] for r in self.rows},set(unique))
        first,_=merge({},[dict(menus=list(unique.values()))],brands,json.loads(SEED.read_bytes()))
        again,audit=merge(dict(menus=first),[dict(menus=list(unique.values()))],brands,[])
        self.assertEqual(first,again);self.assertEqual(12,audit['duplicateCount'])
    def test_repeated_replay_and_offline_refresh_preserve_existing_facts(self):
        all_menus=json.loads((ROOT/'app/src/main/assets/franchise/catalog.json').read_bytes())['menus']
        menus=[m for m in all_menus if m['brand']=='청년피자'];before=copy.deepcopy(menus)
        calls=[]
        def offline(url):calls.append(url);raise TimeoutError('isolated offline test')
        retained,audit=refresh(menus,registry=self.registry,fetcher=offline)
        self.assertEqual(before,menus);self.assertEqual(len(before),audit['failed'])
        source_count=len({m['sourceUrl'] for m in before})
        self.assertEqual(source_count,len(calls));self.assertEqual(source_count,len(set(calls)))
        for old,new in zip(before,retained):self.assertEqual(old.get('officialNutrition'),new.get('officialNutrition'))
        again,_=load_reviewed_nutrition(retained,self.registry);self.assertEqual(retained,again)
        # All current catalog IDs/verified facts remain present on every replay.
        full,_=load_reviewed_nutrition(all_menus,self.registry)
        self.assertEqual([m['id'] for m in all_menus],[m['id'] for m in full])

if __name__=='__main__':unittest.main()
