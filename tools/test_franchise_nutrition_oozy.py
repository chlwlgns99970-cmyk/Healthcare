import copy
import gzip
import hashlib
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import franchise_nutrition as nutrition
from franchise_nutrition_oozy import parse, validate_replay, IMAGE_URL, IMAGE_SHA, TABLE

ROOT=Path(__file__).resolve().parents[1]


class OozyOfficialNutritionTest(unittest.TestCase):
    def setUp(self):
        self.directory=TABLE.parent
        self.image=gzip.decompress((self.directory/'official-table.jpg.gz').read_bytes())
        self.notice=gzip.decompress((self.directory/'official-notice.html.gz').read_bytes())
        self.patch=json.loads((self.directory/'linked-menus.json').read_bytes())['menus']

    def test_exact_official_capture_columns_serving_and_null_total_fat(self):
        self.assertEqual(IMAGE_SHA,hashlib.sha256(self.image).hexdigest())
        for menu in self.patch:
            fact=parse(menu,self.notice,lambda url:self.image,'2026-10-16T00:00:00Z')
            for key in nutrition.FIELDS+nutrition.OPTIONAL_FIELDS:
                self.assertEqual(menu['officialNutrition'].get(key),fact.get(key))
            self.assertIsNone(fact['fatGrams'])
            self.assertEqual((1,'인분'),(fact['servingAmount'],fact['servingUnit']))
            self.assertEqual('',menu['externalId'])
            validate_replay(menu,menu['officialNutrition'])

    def test_names_sizes_brand_and_unsupported_quantity_are_never_guessed(self):
        menu=self.patch[0]
        for changed in [dict(menu,brand='다른 브랜드'),dict(menu,name='이탈리안티라미수케이크'),
                        dict(menu,name='글레이즈도넛'),dict(menu,name='프렌치치즈케이크 (라지)'),
                        dict(menu,externalId='invented'),dict(menu,sourceUrl='https://www.oozycoffee.com/coffee')]:
            with self.assertRaises(AssertionError):parse(changed,self.notice,lambda url:self.image)

    def test_changed_image_or_notice_never_reuses_numeric_values(self):
        with self.assertRaises(AssertionError):parse(self.patch[0],self.notice,lambda url:self.image+b'changed')
        with self.assertRaises(AssertionError):parse(self.patch[0],self.notice.replace(IMAGE_URL.encode(),b'https://other.test/new.jpg'),lambda url:self.image)
        bad=copy.deepcopy(self.patch[0]['officialNutrition']);bad['fatGrams']=bad['saturatedFatGrams']
        with self.assertRaises(AssertionError):validate_replay(self.patch[0],bad)

    def test_new_replay_is_pinned_idempotent_and_survives_network_failure(self):
        menus=json.loads((ROOT/'app/src/main/assets/franchise/catalog.json').read_bytes())['menus']
        registry=json.loads(nutrition.REGISTRY.read_bytes())
        rows=[m for m in menus if m['brand']=='우지커피'];before=copy.deepcopy(rows)
        calls=[]
        def failed(url):calls.append(url);raise TimeoutError('offline test')
        retained,audit=nutrition.refresh(rows,registry=registry,fetcher=failed)
        self.assertEqual(before,rows)
        self.assertEqual(1,len(calls)) # Shared failed notice, no per-product retries.
        for old,new in zip(before,retained):
            if old.get('officialNutrition'):
                self.assertEqual(old['officialNutrition'],new['officialNutrition'])
        again,_=nutrition.load_reviewed_nutrition(retained,registry)
        self.assertEqual(retained,again)


if __name__=='__main__':unittest.main()
