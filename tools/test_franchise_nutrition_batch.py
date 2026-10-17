import copy
import hashlib
import json
import unittest
from pathlib import Path
from apply_franchise_nutrition_batch import apply_batch

ROOT=Path(__file__).resolve().parents[1]

class BatchNutritionTest(unittest.TestCase):
    def setUp(self):
        self.patch=json.loads((ROOT/'data-source/franchise-nutrition-batch-20261014/linked-menus.json').read_text(encoding='utf-8'))
        self.rows=json.loads((ROOT/'app/src/test/resources/franchise-nutrition-batch-20261014.json').read_text(encoding='utf-8'))
        # Use real captured rows; remove only the newly connected facts to model
        # the validated pre-enrichment baseline without a large catalog fixture.
        for row in self.rows:
            row.pop('officialNutrition',None);row['nutritionStatus']='NUTRITION_SOURCE_FOUND_UNMATCHED'
        self.raw=json.dumps(dict(schemaVersion=1,menus=self.rows),ensure_ascii=False).encode()
        self.patch['baseCatalogSha256']=hashlib.sha256(self.raw).hexdigest()
    def test_actual_links_preserve_ids_unknowns_and_published_zero(self):
        after=apply_batch(self.raw,self.patch)['menus']
        self.assertEqual([m['id'] for m in self.rows],[m['id'] for m in after])
        self.assertEqual(77,len(after))
        self.assertEqual(75,sum(m['officialNutrition']['proteinGrams'] is not None for m in after))
        self.assertEqual(76,sum(m['officialNutrition']['sodiumMilligrams'] is not None for m in after))
        self.assertTrue(all(m['officialNutrition']['carbohydrateGrams'] is None and m['officialNutrition']['fatGrams'] is None for m in after))
        melon=next(m for m in after if m['name']=='메론아이스캔디')
        self.assertEqual(0,melon['officialNutrition']['sodiumMilligrams'])
        self.assertEqual(apply_batch(self.raw,self.patch),apply_batch(self.raw,self.patch))
    def test_changed_baseline_identity_and_duplicate_patch_are_rejected(self):
        with self.assertRaises(AssertionError):apply_batch(self.raw+b' ',self.patch)
        patch=copy.deepcopy(self.patch);patch['menus'][0]['name']='다른 메뉴'
        with self.assertRaises(AssertionError):apply_batch(self.raw,patch)
        patch=copy.deepcopy(self.patch);patch['menus'].append(patch['menus'][0])
        with self.assertRaises(AssertionError):apply_batch(self.raw,patch)
    def test_known_value_conflict_blocks_instead_of_overwriting(self):
        self.rows[0]['officialNutrition']=dict(self.patch['menus'][0]['officialNutrition'],proteinGrams=999)
        raw=json.dumps(dict(schemaVersion=1,menus=self.rows),ensure_ascii=False).encode()
        self.patch['baseCatalogSha256']=hashlib.sha256(raw).hexdigest()
        with self.assertRaisesRegex(AssertionError,'conflict'):apply_batch(raw,self.patch)

if __name__=='__main__':unittest.main()
