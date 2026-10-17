"""Verify the actual reviewed batch fits Android while keeping nutrition proof."""
import json, unittest
from pathlib import Path
from franchise_daily_sync import published_menu,MAX_CATALOG_BYTES

class NutritionWireTest(unittest.TestCase):
    def test_reviewed_5142_menu_payload_keeps_values_and_bounded_size(self):
        root=Path(__file__).resolve().parents[1]
        full=json.loads((root/'app/src/androidTest/assets/nutrition-batch-2/catalog.json').read_bytes())
        patch=json.loads((root/'data-source/franchise-nutrition-batch-2-20261014/linked-menus.json').read_text(encoding='utf-8'))
        by_id={m['id']:m for m in full['menus']}
        for item in patch['menus']:
            self.assertEqual(item['name'],by_id[item['id']]['name'])
            by_id[item['id']]['officialNutrition']=item['officialNutrition']
        before=json.loads(json.dumps(full))
        wire=[published_menu(m) for m in sorted(full['menus'],key=lambda m:m['id'])]
        encoded=json.dumps(dict(schemaVersion=1,menus=wire),ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
        self.assertLessEqual(len(encoded),MAX_CATALOG_BYTES)
        self.assertEqual(full,before) # Serialization never alters reusable facts.
        self.assertEqual(len(wire),5142)
        for source,output in zip(sorted(full['menus'],key=lambda m:m['id']),wire):
            self.assertEqual({k:v for k,v in source.items() if k!='officialNutrition'}, {k:v for k,v in output.items() if k!='officialNutrition'})
            for key,value in source.get('officialNutrition',{}).items():
                if key in ('optionalFieldEvidence','retainedFieldEvidence','sourceType',
                           'listSourceURL','listSourceSha256','officialDetailFullName',
                           'listingDisplayName','groupCrosswalkEvidence'):
                    self.assertNotIn(key,output['officialNutrition'])
                    self.assertIn(key,source['officialNutrition'])
                elif value is not None:self.assertEqual(value,output['officialNutrition'][key])

if __name__=='__main__':unittest.main()
