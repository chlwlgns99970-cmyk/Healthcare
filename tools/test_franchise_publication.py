"""Publication preparation uses verified snapshots, never a live write."""
import copy, hashlib, json, tempfile, unittest
from pathlib import Path
import franchise_daily_sync as daily
import prepare_franchise_publication as prep

class PublicationPreparationTest(unittest.TestCase):
    def setUp(self):
        self.brands=json.loads(daily.CONFIG.read_text(encoding='utf-8'))
        brand=next(b for b in self.brands if b['name']=='설빙')
        self.row=daily.candidates(brand,'<a href="menu_view.php?menu=991122">격리 게시 검사 메뉴</a>','2026-10-17T00:00:00Z')[0]
        self.other=daily.candidates(brand,'<a href="menu_view.php?menu=991123">다른 실제 기록 검사</a>','2026-10-17T00:00:00Z')[0]
        self.document=lambda rows:dict(schemaVersion=1,menus=rows)
        self.root=daily.ROOT/'app/build/franchise-publication-tests'
        self.root.mkdir(parents=True,exist_ok=True)

    def snapshot(self,folder,rows):
        folder.mkdir(parents=True,exist_ok=True)
        data=json.dumps(self.document(rows),ensure_ascii=False).encode();digest=hashlib.sha256(data).hexdigest()
        name='catalog-'+digest+'.json'
        (folder/name).write_bytes(data)
        (folder/'latest.json').write_text(json.dumps(dict(schemaVersion=1,menuCount=len(rows),file=name,sha256=digest,version=digest)))
        return folder/name,folder/'latest.json'

    def nutrition(self,row,**values):
        row=copy.deepcopy(row)
        row['officialNutrition']=dict(officialProductId=row['externalId'],sourceUrl=row['sourceUrl'],
            sourceSha256='a'*64,checkedAt='2026-10-17T00:00:00Z',servingAmount=100,servingUnit='g',**values)
        row['nutritionStatus']='NUTRITION_PARTIAL'
        return row

    def test_metadata_difference_is_not_a_new_menu_or_numeric_conflict(self):
        local=copy.deepcopy(self.row);local['checkedAt']='2026-10-17T01:00:00Z'
        result=prep.compare_catalogs(self.document([local]),self.document([self.row]))
        self.assertEqual(1,result['differentCommonCount']);self.assertEqual(0,result['localOnlyCount'])
        self.assertEqual(0,result['effectiveNutritionDifferentCount']);self.assertFalse(result['knownValueConflicts'])

    def test_unknown_to_verified_zero_is_enrichment_not_absence(self):
        local=self.nutrition(self.row,proteinGrams=0)
        result=prep.compare_catalogs(self.document([local]),self.document([self.row]))
        self.assertEqual(1,result['localAdditionalVerifiedFields']['proteinGrams'])
        combined=prep.merge_verified(self.document([local]),self.document([self.row]),self.brands)
        self.assertEqual(0,combined['menus'][0]['officialNutrition']['proteinGrams'])

    def test_remote_only_ids_and_more_complete_remote_facts_are_retained(self):
        remote=self.nutrition(self.row,proteinGrams=3)
        merged=prep.merge_verified(self.document([self.row]),self.document([remote,self.other]),self.brands)
        self.assertEqual(2,len(merged['menus']))
        self.assertEqual(3,next(m for m in merged['menus'] if m['id']==self.row['id'])['officialNutrition']['proteinGrams'])

    def test_known_value_conflict_or_identity_change_is_never_auto_resolved(self):
        old=self.nutrition(self.row,proteinGrams=1);new=self.nutrition(self.row,proteinGrams=2)
        for left,right in [(new,old),(dict(self.row,name='다른 제품'),self.row)]:
            with self.assertRaises(ValueError):prep.merge_verified(self.document([left]),self.document([right]),self.brands)

    def test_complementary_snapshots_need_review_not_synthetic_provenance(self):
        left=self.nutrition(self.row,proteinGrams=1);right=self.nutrition(self.row,fatGrams=2)
        with self.assertRaises(ValueError):prep.merge_verified(self.document([left]),self.document([right]),self.brands)

    def test_prepare_is_isolated_repeatable_and_retains_rollback_bytes(self):
        with tempfile.TemporaryDirectory(dir=self.root) as folder:
            root=Path(folder);local,lm=self.snapshot(root/'local',[self.row,self.other]);self.snapshot(root/'remote',[self.row])
            before={p:p.read_bytes() for p in root.rglob('*.json')}
            first=prep.prepare(local,lm,root/'remote',root/'candidate',self.brands)
            self.assertEqual('READY_FOR_REVIEW_NOT_PUBLISHED',first['status'])
            self.assertEqual(2,first['menuCount'])
            after={p:p.read_bytes() for p in (root/'candidate').rglob('*.json')}
            self.assertEqual('UNCHANGED_PREPARED_CANDIDATE',prep.prepare(local,lm,root/'remote',root/'candidate',self.brands)['status'])
            self.assertTrue(all(p.read_bytes()==content for p,content in before.items()))
            self.assertTrue(all(p.read_bytes()==content for p,content in after.items()))
            manifest=json.loads((root/'candidate/latest.json').read_bytes())
            self.assertNotIn('publishedAt',manifest)
            self.assertEqual('PREPARED_NOT_PUBLISHED',manifest['publicationState'])
            self.assertEqual((root/'remote/latest.json').read_bytes(),(root/'candidate/rollback/latest.json').read_bytes())
            manifest['file']='unexpected.json';(root/'candidate/latest.json').write_text(json.dumps(manifest))
            with self.assertRaises(ValueError):prep.prepare(local,lm,root/'remote',root/'candidate',self.brands)

    def test_corruption_and_unsafe_output_do_not_modify_inputs(self):
        with tempfile.TemporaryDirectory(dir=self.root) as folder:
            root=Path(folder);local,lm=self.snapshot(root/'local',[self.row]);self.snapshot(root/'remote',[self.row])
            for out in (root/'local',root/'local/nested',root/'remote',root/'remote/nested'):
                with self.assertRaises(ValueError):prep.prepare(local,lm,root/'remote',out,self.brands)
            local.write_bytes(local.read_bytes()+b' ')
            with self.assertRaises(ValueError):prep.prepare(local,lm,root/'remote',root/'candidate',self.brands)
            self.assertFalse((root/'candidate').exists())

    def test_conflict_fails_before_any_candidate_is_written(self):
        with tempfile.TemporaryDirectory(dir=self.root) as folder:
            root=Path(folder);local,lm=self.snapshot(root/'local',[self.nutrition(self.row,proteinGrams=2)])
            self.snapshot(root/'remote',[self.nutrition(self.row,proteinGrams=1)])
            with self.assertRaises(ValueError):prep.prepare(local,lm,root/'remote',root/'candidate',self.brands)
            self.assertFalse((root/'candidate').exists())

    def test_android_incompatible_shapes_are_rejected(self):
        for document in (dict(schemaVersion=True,menus=[]),dict(schemaVersion=1,menus=[self.row]*10001),
            self.document([dict(self.row,officialNutrition=[])])):
            with self.assertRaises(ValueError):prep.validate_catalog(document,self.brands)

if __name__=='__main__':unittest.main(verbosity=2)
