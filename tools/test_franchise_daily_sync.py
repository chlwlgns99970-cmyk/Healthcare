import copy, json, tempfile, unittest
from unittest.mock import patch
from contextlib import ExitStack
import franchise_daily_sync as daily
import franchise_nutrition as nutrition
from pathlib import Path
from franchise_daily_sync import *

class DailySyncTest(unittest.TestCase):
    def setUp(self):
        self.brands=json.loads(CONFIG.read_text(encoding='utf-8'))
        self.brand=next(b for b in self.brands if b['name']=='설빙')
        self.menu=candidates(self.brand,'<a href="menu_view.php?menu=999991">검증 신메뉴 ICE 라지</a>','2026-10-08T00:00:00Z')[0]
    def test_full_queue(self):
        self.assertEqual(102,len(self.brands));self.assertEqual(102,len({b['brandId'] for b in self.brands}))
        self.assertTrue(all(official_url(b['sourceUrl'],b) for b in self.brands))
    def test_recorded_adapters(self):
        for name,file,count in [('설빙','sulbing-menu.html',31),('이성당','isungdang-list.html',18)]:
            brand=next(b for b in self.brands if b['name']==name)
            menus=candidates(brand,(ROOT/'data-source/franchise-sync/fixtures'/file).read_text(encoding='utf-8'),'2026-10-08T00:00:00Z')
            self.assertEqual(count,len(menus));self.assertTrue(all(m['name'] not in ['이전 다음','이전','다음'] for m in menus))
            self.assertTrue(all('에코백' not in m['name'] and m['name'] != '이성당 추천세트' for m in menus))
    def test_jsonld_only_menu_items(self):
        html='<script type="application/ld+json">'+json.dumps([{'@type':'MenuItem','name':'확인 메뉴','identifier':'A'},{'@type':'Product','name':'비음식 광고'}])+'</script>'
        self.assertEqual(1,len(candidates(self.brand,html,'2026-10-08T00:00:00Z')))
        self.assertFalse(candidates(self.brand,'<h2>그럴듯한 광고 메뉴</h2>','2026-10-08T00:00:00Z'))
    def test_official_source(self):
        bad=copy.deepcopy(self.menu);bad['sourceUrl']='https://example.net/menu'
        with self.assertRaises(AssertionError): validate(bad,self.brand)
    def test_unknown_not_zero(self):
        self.assertIsNone(self.menu['energyKcal']);self.assertIsNone(self.menu['proteinGrams'])
    def test_actual_source_types_and_legacy_compatibility(self):
        self.assertEqual('HTML',self.menu['sourceType'])
        for source,expected in [(dict(adapter='bon-api'),'API'),(dict(adapter='kfc-json'),'JSON'),(dict(adapter='hecbob-rendered',transport='browser'),'BROWSER')]:
            self.assertEqual(expected,source_type(source))
        old=dict(self.menu);old.pop('sourceType');validate(old,self.brand)
        bad=dict(self.menu,sourceType='GUESS')
        with self.assertRaises(AssertionError):validate(bad,self.brand)
    def test_legacy_provenance_is_additive_and_never_guessed(self):
        evidence=json.loads(LEGACY_PROVENANCE.read_text(encoding='utf-8'))[0]
        brand=next(b for b in self.brands if b['brandId']==evidence['brandId'])
        old=dict(brandId=brand['brandId'],brand=brand['name'],name='보존 확인',normalizedName=normalize('보존 확인'),externalId='legacy-check',category='',sourceUrl=evidence['sourceUrl'],checkedAt=evidence['checkedAt'],energyKcal=None)
        old['id']=stable_id(old['brandId'],old['externalId'],old['name'],'')
        before=copy.deepcopy(old)
        menus,stats=merge(dict(menus=[old]),[],self.brands,[])
        self.assertEqual(before,old)
        self.assertTrue(all(menus[0][k]==v for k,v in before.items()))
        self.assertEqual(evidence['sourceType'],menus[0]['sourceType'])
        old['checkedAt']='2020-01-01T00:00:00Z'
        self.assertNotIn('sourceType',merge(dict(menus=[old]),[],self.brands,[])[0][0])
    def test_nutrition_basis_and_invalid(self):
        known=copy.deepcopy(self.menu);known.update(energyKcal=0,carbohydrateGrams=0,servingAmount=100,servingUnit='g',nutritionSourceUrl=known['sourceUrl'])
        validate(known,self.brand)
        for bad in (-1,float('nan'),float('inf'),100001):
            known['energyKcal']=bad
            with self.assertRaises(AssertionError): validate(known,self.brand)
        known['energyKcal']=100;known['servingAmount']=None
        with self.assertRaises(AssertionError): validate(known,self.brand)
    def test_stable_external_id(self):
        self.assertEqual(self.menu['id'],stable_id(self.brand['brandId'],'999991','renamed','other'))
    def test_size_temperature_kept(self):
        self.assertNotEqual(stable_id('brand','', '커피 ICE 라지',''),stable_id('brand','','커피 HOT 레귤러',''))
        self.assertEqual(normalize('  ICE   라지 '),normalize('ICE 라지'))
    def test_duplicate_runs_and_static(self):
        batch=[dict(status='SUCCESS',menus=[self.menu,self.menu])]
        merged,stats=merge({},batch,self.brands,[]);self.assertEqual(1,len(merged));self.assertEqual(1,stats['duplicateCount'])
        again,stats=merge(dict(menus=merged),batch,self.brands,[]);self.assertEqual(merged,again);self.assertEqual(2,stats['duplicateCount'])
        merged,stats=merge({},batch,self.brands,[dict(brandId=self.menu['brandId'],name=self.menu['name'])]);self.assertFalse(merged)
    def test_publish_manifest_atomic_and_same_day(self):
        with tempfile.TemporaryDirectory() as tmp:
            report=run(tmp,'fixture-day',self.brands,[dict(brandId=self.brand['brandId'],status='SUCCESS',menus=[self.menu])])
            self.assertEqual(1,report['publishedNewCount'])
            manifest=json.loads((Path(tmp)/'latest.json').read_text());data=(Path(tmp)/manifest['file']).read_bytes()
            self.assertEqual(manifest['sha256'],hashlib.sha256(data).hexdigest())
            self.assertEqual('ALREADY_COMPLETED',run(tmp,'fixture-day',self.brands,[])['status'])
    def test_failure_keeps_existing_catalog(self):
        with tempfile.TemporaryDirectory() as tmp:
            run(tmp,'one',self.brands,[dict(status='SUCCESS',menus=[self.menu])])
            before=(Path(tmp)/'latest.json').read_bytes()
            report=run(tmp,'two',self.brands,[dict(status='FETCH_FAILED',menus=[])])
            self.assertEqual(before,(Path(tmp)/'latest.json').read_bytes());self.assertEqual('EXISTING_CATALOG_RETAINED',report['status'])
            self.assertEqual('ALREADY_COMPLETED',run(tmp,'two',self.brands,[])['status'])
    def test_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp)/'.sync-lock').touch();self.assertEqual('RUN_LOCKED',run(tmp,'one',self.brands,[])['status'])
    def test_invalid_candidate_not_published(self):
        bad=copy.deepcopy(self.menu);bad['energyKcal']=-2
        merged,stats=merge({},[dict(menus=[bad])],self.brands,[])
        self.assertFalse(merged);self.assertEqual(1,stats['invalidCount'])

class DailyNutritionInlineTests(unittest.TestCase):
    """Inline contracts only: all publication and project-input IO is mocked."""
    def setUp(self):
        self.brands = [dict(brandId=key, name=key, allowedHosts=['official.example'],
            adapter='jsonld', sourceUrl='https://official.example/menu') for key in ('a', 'b')]
        self.registry = [dict(brandId=b['brandId'], name=b['name'], allowedHosts=b['allowedHosts']) for b in self.brands]
        self.menus = [candidates(b, '<script type="application/ld+json">' + json.dumps(
            dict(**{'@type':'MenuItem'}, name='Menu '+b['name'], identifier=b['brandId'])) + '</script>',
            '2026-10-14T00:00:00Z')[0] for b in self.brands]
        self.fact = dict(energyKcal=145, carbohydrateGrams=None, proteinGrams=0, fatGrams=None,
            sodiumMilligrams=175, sugarsGrams=18, saturatedFatGrams=None,
            servingAmount=200, servingUnit='g', officialProductId='a',
            sourceUrl='https://official.example/nutrition', sourceSha256='a'*64,
            checkedAt='2026-10-14T00:00:00Z', sourceType='HTML',
            optionalFieldEvidence={'sugarsGrams':dict(sourceUrl='https://official.example/renderer',
                sourceSha256='b'*64, checkedAt='2026-10-14T00:00:00Z')},
            retainedFieldEvidence={'energyKcal':dict(sourceUrl='https://official.example/older',
                sourceSha256='c'*64, checkedAt='2026-10-13T00:00:00Z')})
        row = {key:self.menus[0][key] for key in ('id','brandId','brand','name','externalId','sourceUrl')}
        row['officialNutrition'] = copy.deepcopy(self.fact)
        self.patches = [dict(baseCatalogSha256='d'*64, menus=[row]),
                        dict(baseCatalogSha256='e'*64, menus=[])]
        self.io = {str(SEED):b'[]', str(nutrition.REGISTRY):json.dumps(self.registry).encode()}
        entries = []
        for path, batch in zip(nutrition.REVIEWED_PATCH_PATHS, self.patches):
            payload = json.dumps(batch).encode()
            self.io[str(ROOT/path)] = payload
            entries.append(dict(path=path, sha256=hashlib.sha256(payload).hexdigest()))
        self.io[str(nutrition.REVIEWED_INDEX)] = json.dumps(dict(version=1,patches=entries)).encode()
        self.out = Path('mock-publication')
        self.batch = [dict(brandId=b['brandId'],status='SUCCESS',menus=[m],sources=[])
                      for b,m in zip(self.brands,self.menus)]

    def mocked_io(self):
        stack = ExitStack()
        def read(path):
            if str(path) not in self.io:
                raise FileNotFoundError(str(path))
            return self.io[str(path)]
        stack.enter_context(patch.object(Path,'read_bytes',read))
        stack.enter_context(patch.object(Path,'read_text',lambda p,*a,**k:read(p).decode()))
        stack.enter_context(patch.object(Path,'exists',lambda p:str(p) in self.io))
        stack.enter_context(patch.object(Path,'mkdir'))
        stack.enter_context(patch.object(Path,'unlink'))
        stack.enter_context(patch.object(daily.os,'open',return_value=123))
        stack.enter_context(patch.object(daily.os,'close'))
        stack.enter_context(patch.object(daily,'atomic_write',lambda p,data:self.io.__setitem__(str(p),data)))
        return stack

    def catalog(self):
        manifest = json.loads(self.io[str(self.out/'latest.json')])
        return json.loads(self.io[str(self.out/manifest['file'])])

    def test_exact_wire_split_and_immutable_facts(self):
        menu = dict(self.menus[0],officialNutrition=copy.deepcopy(self.fact))
        before = copy.deepcopy(menu)
        wire = published_menu(menu)
        expected = {k:v for k,v in self.fact.items() if v is not None and
                    k not in ('optionalFieldEvidence','retainedFieldEvidence','sourceType',
                              'listSourceURL','listSourceSha256','officialDetailFullName',
                              'listingDisplayName','groupCrosswalkEvidence')}
        self.assertEqual(expected,wire['officialNutrition'])
        self.assertEqual(before,menu)
        self.assertEqual(0,wire['officialNutrition']['proteinGrams'])
        self.assertIsNone(wire['officialNutrition'].get('carbohydrateGrams'))
        self.assertEqual(menu['id'],wire['id'])
        self.assertEqual(MAX_CATALOG_BYTES,8_000_000)

    def test_supplied_batches_replay_audit_and_repeat(self):
        before = copy.deepcopy(self.patches)
        with self.mocked_io(), patch.object(nutrition,'refresh') as live, patch.object(daily,'fetch') as fetch:
            first = run(self.out,'one',self.brands,copy.deepcopy(self.batch))
            self.assertEqual(1,first['nutritionAudit']['reviewedReplayAppliedMenus'])
            self.assertEqual(1,first['nutritionAudit']['newlyVerifiedMenus'])
            self.assertEqual(0,first['nutritionAudit']['conflictCount'])
            self.assertEqual(1,first['newWithNutrition'])
            audit = json.loads(self.io[str(self.out/'latest-franchise-nutrition-audit.json')])
            self.assertEqual(self.fact,audit['menus'][0]['officialNutrition'])
            for key in ('optionalFieldEvidence','retainedFieldEvidence'):
                self.assertEqual(self.fact[key],audit['menus'][0][key])
                self.assertNotIn(key,self.catalog()['menus'][0]['officialNutrition'])
            manifest = self.io[str(self.out/'latest.json')]
            catalog = copy.deepcopy(self.catalog())
            again = run(self.out,'two',self.brands,copy.deepcopy(self.batch))
            self.assertEqual(0,again['nutritionAudit']['reviewedReplayAppliedMenus'])
            self.assertEqual(0,again['nutritionAudit']['newlyVerifiedMenus'])
            self.assertEqual(0,again['nutritionAudit']['nutritionChangedMenus'])
            self.assertEqual(manifest,self.io[str(self.out/'latest.json')])
            self.assertEqual(catalog,self.catalog())
            repeated_audit=json.loads(self.io[str(self.out/'latest-franchise-nutrition-audit.json')])
            for key in ('optionalFieldEvidence','retainedFieldEvidence'):
                self.assertEqual(self.fact[key],repeated_audit['menus'][0]['officialNutrition'][key])
            live.assert_not_called();fetch.assert_not_called()
        self.assertEqual(before,self.patches)

    def test_live_target_scoping_restores_full_range_and_proof(self):
        calls = []
        self.registry[1]['adapter']='inline-target'
        self.io[str(nutrition.REGISTRY)]=json.dumps(self.registry).encode()
        def parser(menu, payload):
            calls.append((copy.deepcopy(menu),payload))
            self.assertEqual('b',menu['brandId'])
            return dict(energyKcal=200, proteinGrams=0, carbohydrateGrams=None,
                        fatGrams=None, servingAmount=100, servingUnit='g')
        class Response:
            status=200
            url=self.menus[1]['sourceUrl']
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def read(self,*args):return b'inline official nutrition'
        with self.mocked_io(), patch.object(daily,'fetch',return_value=self.batch[1]), \
                patch.dict(nutrition.PARSERS,{'inline-target':parser}), \
                patch.object(nutrition.urllib.request,'urlopen',return_value=Response()) as network, \
                patch.object(nutrition,'load_reviewed_nutrition',wraps=nutrition.load_reviewed_nutrition) as replay:
            # The non-target range is present in the prior catalog.
            # Its valid replay row must be checked against the full registry,
            # even though only b participates in real live refresh.
            payload=json.dumps(dict(menus=[self.menus[0]])).encode()
            self.io[str(self.out/'prior.json')]=payload
            self.io[str(self.out/'latest.json')]=json.dumps(dict(file='prior.json',sha256=hashlib.sha256(payload).hexdigest())).encode()
            before=copy.deepcopy(self.menus)
            report=run(self.out,'target',self.brands,target_brand_ids={'b'})
            self.assertEqual(['b'],[m['brandId'] for m,payload in calls])
            network.assert_called_once()
            self.assertEqual(self.menus[1]['sourceUrl'],network.call_args.args[0].full_url)
            self.assertEqual(self.registry,replay.call_args.args[1])
            self.assertEqual(['b'],[m['brandId'] for m in replay.call_args.args[0]])
            self.assertEqual({m['id'] for m in self.menus},{m['id'] for m in self.catalog()['menus']})
            audit=json.loads(self.io[str(self.out/'latest-franchise-nutrition-audit.json')])
            a=next(m for m in audit['menus'] if m['brandId']=='a')
            self.assertEqual(self.fact,a['officialNutrition'])
            self.assertEqual(1,report['nutritionAudit']['matched'])
            self.assertEqual(0,report['nutritionAudit']['failed'])
            self.assertEqual(0,report['nutritionAudit']['conflictCount'])
            self.assertEqual(1,report['nutritionAudit']['total'])
            self.assertEqual(2,report['nutritionAudit']['newlyVerifiedMenus'])
            self.assertEqual(before,self.menus)
            again=run(self.out,'target-repeat',self.brands,target_brand_ids={'b'})
            self.assertEqual(0,again['nutritionAudit']['newlyVerifiedMenus'])
            self.assertEqual(0,again['nutritionAudit']['reviewedReplayAppliedMenus'])
            self.assertEqual(0,again['nutritionAudit']['nutritionChangedMenus'])
            self.assertEqual(1,again['nutritionAudit']['matched'])

    def test_default_live_repeat_counts_and_full_refresh(self):
        def refresh(menus):
            return copy.deepcopy(menus),dict(matched=1,failed=1,conflicts=[],menus=[])
        with self.mocked_io(), patch.object(daily,'fetch',side_effect=lambda b:copy.deepcopy(self.batch[0 if b['brandId']=='a' else 1])), patch.object(nutrition,'refresh',side_effect=refresh) as live:
            first=run(self.out,'live-one',self.brands)
            again=run(self.out,'live-two',self.brands)
            self.assertEqual(2,len(live.call_args.args[0]));self.assertEqual({},live.call_args.kwargs)
            self.assertEqual(1,first['nutritionAudit']['newlyVerifiedMenus'])
            self.assertEqual(0,again['nutritionAudit']['newlyVerifiedMenus'])
            self.assertEqual(0,again['nutritionAudit']['reviewedReplayAppliedMenus'])
            self.assertEqual(1,again['nutritionAudit']['matched'])
            self.assertEqual(1,again['nutritionAudit']['failed'])

    def test_invalid_index_and_catalog_conflict_preserve_all_bytes(self):
        with self.mocked_io():
            run(self.out,'initial',self.brands,copy.deepcopy(self.batch))
            prior=copy.deepcopy(self.io)
            self.io[str(nutrition.REVIEWED_INDEX)]=b'{"version":99}'
            expected=copy.deepcopy(self.io)
            with patch.object(nutrition,'refresh') as live:
                with self.assertRaises(ValueError):run(self.out,'bad-index',self.brands,copy.deepcopy(self.batch))
                self.assertEqual(expected,self.io);live.assert_not_called()
            self.io=prior
            # Valid pinned replay conflicts with the already published known kcal.
            changed=copy.deepcopy(self.patches[0]);changed['menus'][0]['officialNutrition']['energyKcal']=146
            path=nutrition.REVIEWED_PATCH_PATHS[0];payload=json.dumps(changed).encode()
            self.io[str(ROOT/path)]=payload
            index=json.loads(self.io[str(nutrition.REVIEWED_INDEX)])
            index['patches'][0]['sha256']=hashlib.sha256(payload).hexdigest()
            self.io[str(nutrition.REVIEWED_INDEX)]=json.dumps(index).encode()
            expected=copy.deepcopy(self.io)
            with self.assertRaises(ValueError):run(self.out,'conflict',self.brands,copy.deepcopy(self.batch))
            self.assertEqual(expected,self.io)

    def test_known_values_retained_and_null_only_enrichment(self):
        old=copy.deepcopy(self.menus)
        old[0]['officialNutrition']=copy.deepcopy(self.fact)
        old[0]['officialNutrition']['sugarsGrams']=None
        old[0]['officialNutrition'].pop('optionalFieldEvidence')
        before=copy.deepcopy(old)
        with self.mocked_io():
            result,audit=daily_nutrition(old,False)
        self.assertEqual(before,old)
        self.assertEqual(145,result[0]['officialNutrition']['energyKcal'])
        self.assertEqual(0,result[0]['officialNutrition']['proteinGrams'])
        self.assertEqual(18,result[0]['officialNutrition']['sugarsGrams'])
        self.assertIsNone(result[0]['officialNutrition']['carbohydrateGrams'])
        self.assertEqual(self.fact['checkedAt'],result[0]['officialNutrition']['checkedAt'])
        self.assertEqual(1,audit['reviewedReplayAppliedMenus'])

    def test_live_conflict_counts_and_failed_fetch_keep_known_facts(self):
        conflict=dict(menuId=self.menus[0]['id'],differences=[dict(field='energyKcal',oldValue=145,newValue=146)],
                      decision='RETAIN_VERIFIED_PENDING_REVIEW')
        def refresh(menus):
            return copy.deepcopy(menus),dict(matched=0,failed=1,conflicts=[conflict],
                menus=[dict(id=menus[0]['id'],result='CONFLICT_RETAINED',conflicts=[conflict]),
                       dict(id=menus[1]['id'],result='FAILED_RETAINED')])
        before=copy.deepcopy(self.menus)
        with self.mocked_io(), patch.object(nutrition,'refresh',side_effect=refresh):
            result,audit=daily_nutrition(self.menus,True)
            self.assertEqual(1,audit['conflictCount'])
            self.assertEqual([conflict],audit['conflicts'])
            self.assertEqual(0,audit['matched']);self.assertEqual(1,audit['failed'])
            self.assertEqual(1,audit['newlyVerifiedMenus'])
            self.assertEqual(145,result[0]['officialNutrition']['energyKcal'])
            self.assertNotIn('officialNutrition',result[1])
        self.assertEqual(before,self.menus)

if __name__=='__main__': unittest.main(verbosity=2)
