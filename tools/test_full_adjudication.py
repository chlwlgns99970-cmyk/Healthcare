"""Targeted source, parser, reference and catalog safety checks for this request."""
import unittest,json,csv,hashlib,collections,math
from pathlib import Path
from recipe_ingredient_parser import parse
from reviewed_recipe_identities import RDA_ALIASES
import build_recipe_calorie_references as legacy
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication';A=ROOT/'app/src/main/assets/fooddata'
def load(name):return json.loads((OUT/name).read_text(encoding='utf-8'))
def rows(path):
    with path.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))

class FullAdjudicationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit=load('recipe-final-audit.json');cls.decisions=load('ingredient-decisions.json')
        cls.references=rows(A/'recipe_ingredient_estimates.csv')
        cls.nutrients={r['code']:r for r in load('rda-10.4-nutrients.json')}
        cls.nutrients.update({k:dict(name=v['description'],energyKcal=v['energyKcal']) for k,v in legacy.archive_nutrients().items()})
    def test_516_recipe_sources_and_all_inputs_are_final(self):
        self.assertEqual(516,len(self.audit['audit']))
        original={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in rows(p)}
        for r in self.audit['audit']:
            o=original[r['recipeId']]
            file=ROOT/'app/build/food-quality-qa/recipe-source'/(r['recipeId'].lower().replace('rda-diet-','nongsaro-diet-')+'.html')
            self.assertEqual(o['sourceSha256'],hashlib.sha256(file.read_bytes()).hexdigest())
            self.assertEqual(parse(', '.join(filter(None,[o['mainIngredientText'],o['additionalIngredientText']]))),[{k:d[k] for k in ('originalSpan','ingredient','quantity','unit','equivalents','quantityRange')} for d in r['ingredients']])
        for d in self.decisions:
            self.assertTrue(d['ingredient']);self.assertTrue(d['identityReason']);self.assertTrue(d['unitReason'])
            self.assertNotIn(d['identityStatus'],('UNREVIEWED','PENDING','TODO'));self.assertNotIn(d['unitStatus'],('UNREVIEWED','PENDING','TODO'))
    def test_identity_normalization_preserves_preparation(self):
        self.assertEqual(RDA_ALIASES['달걀'],RDA_ALIASES['계란'])
        self.assertEqual(RDA_ALIASES['대파'],RDA_ALIASES['다진대파'])
        for name in ('파','삶은감자','감자(껍질제거)','치즈','쌀','쇠고기(양지머리)','명란'):
            self.assertNotIn(name,RDA_ALIASES)
        roe=next(d for d in self.decisions if d['recipeId']=='RDA-91841' and d['ingredient']=='명란')
        self.assertEqual('K071000D590a',roe['nutrientId']);self.assertIsNone(roe['amountGrams'])
        self.assertEqual(1,len(parse('무 150g(1/5개)')))
        self.assertEqual(['갑오징어','물'],[r['ingredient'] for r in parse('갑오징어 200g(3/4마리), 물 1.6L(8컵)')])
        self.assertIsNone(parse('설탕 1~2큰술')[0]['quantity'])
    def test_every_published_nutrient_uses_verified_original(self):
        sha='271cc431f2991b3c0c049ec6e05fb59a040319e984ab71468184530de61dec50'
        self.assertEqual(sha,hashlib.sha256((OUT/'raw/b146dd6df98d23fa').read_bytes()).hexdigest())
        for r in self.references:
            code=r['nutrientFoodId'].split('reference-',1)[1];n=self.nutrients[code]
            self.assertEqual(n['name'],r['nutrientName']);self.assertEqual(n['energyKcal'],float(r['kcalPer100g']))
            self.assertGreater(float(r['amountGrams']),0);self.assertTrue(math.isfinite(float(r['amountGrams'])))
            if not code.isdigit():self.assertEqual(100,n['referenceAmount']);self.assertEqual('g',n['unit'])
    def test_unit_conversion_requires_explicit_consistent_evidence(self):
        self.assertIsNone(parse('당근 40')[0]['quantity'])
        self.assertEqual(2,parse('다진 마늘2(g)')[0]['quantity'])
        for d in self.decisions:
            if d['unitStatus']=='DIRECT_MASS':self.assertIn(d['unit'],('g','kg'))
            elif d['unitStatus']=='OFFICIAL_SOURCE_EQUIVALENCE':
                self.assertIn((d['ingredient'].replace(' ',''),d['unit']),{('고춧가루','작은술'),('물엿','큰술')})
                self.assertGreaterEqual(len({e['recipeId'] for e in d['unitCandidates']}),2)
                self.assertEqual(1,len({round(e['gramsPerUnit'],8) for e in d['unitCandidates']}))
                self.assertAlmostEqual(d['amountGrams'],d['quantity']*d['unitCandidates'][0]['gramsPerUnit'])
            elif d['unitStatus']=='UNIT_CONVERSION_UNVERIFIED':self.assertIsNone(d['amountGrams'])
        self.assertFalse(any(d['unit']=='ml' and d['amountGrams'] is not None for d in self.decisions))
    def test_complete_recipes_have_every_caloric_input(self):
        complete=[r for r in self.audit['audit'] if r['status']=='COMPLETE_LINKED'];self.assertGreaterEqual(len(complete),3)
        for r in complete:self.assertTrue(all(d['status'] in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM') for d in r['ingredients']))
        published=[r for r in self.references if r['recipeComplete']=='true'];self.assertGreaterEqual(len({r['recipeId'] for r in published}),3)
    def test_partial_recipes_retain_gaps_and_kimbap_identity(self):
        for r in self.audit['audit']:
            if r['status']=='PARTIAL_LINKED':
                self.assertTrue(any(d['status']=='LINKED' for d in r['ingredients']))
                self.assertTrue(any(d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM') for d in r['ingredients']))
        k=[r for r in self.references if r['foodId']=='kfind-d101-007000000-0001']
        self.assertTrue(all(r['recipeComplete']=='false' for r in k));self.assertEqual({'계란','김','오이','단무지','참기름'},{r['ingredientName'] for r in k}&{'계란','김','오이','단무지','참기름'})
        self.assertFalse(any(r['ingredientName'] in ('쌀','밥','쇠고기') for r in k))
    def test_unlinkable_recipes_never_generate_fake_estimates(self):
        for r in self.audit['audit']:
            if r['status']=='UNLINKABLE':
                self.assertFalse(any(d['status']=='LINKED' for d in r['ingredients']))
                self.assertFalse(any(x['recipeId']==r['recipeId'] for x in self.references))
        self.assertTrue(all(r['foodIdentityUnique'] for r in self.audit['audit'] if r['published']))
    def test_all_229_unknown_menus_have_individual_final_evidence(self):
        a=load('menu-final-audit.json');baseline=[r for r in rows(OUT/'menu-before.csv') if not r['menuCategory']]
        self.assertEqual({r['menuId'] for r in baseline},{r['menuId'] for r in a['reviews']});self.assertEqual(0,a['unreviewed'])
        for r in a['reviews']:
            self.assertTrue(r['reason'] and r['evidence'] and r['sourceUrl']);self.assertFalse(r['brandIndustryUsed'])
            self.assertIn(r['status'],('CATEGORY_ASSIGNED','CATEGORY_UNRESOLVABLE'))
            self.assertEqual(r['status']=='CATEGORY_ASSIGNED',bool(r['category']))
    def test_exact_menu_lookup_matches_final_decisions(self):
        lookup={r['menuId']:r for r in rows(ROOT/'data-source/recipe-menu-completion/reviewed-menu-categories.csv')}
        for r in load('menu-final-audit.json')['reviews']:
            if r['category']:self.assertEqual(r['category'],lookup[r['menuId']]['category'])
            else:self.assertNotIn(r['menuId'],lookup)
        captures={c['url']:c for c in load('source-captures.json')}
        for r in load('menu-final-audit.json')['reviews']:
            for s in r.get('additionalSources',[]):self.assertTrue(s['sha256']);self.assertTrue(s['url'])
            if r['sourceUrl'] in captures:
                c=captures[r['sourceUrl']];self.assertEqual(c['sha256'],hashlib.sha256((ROOT/c['rawFile']).read_bytes()).hexdigest())
    def test_whole_nutrition_basis_retains_exact_original_values(self):
        a=load('nutrition-basis-audit.json');self.assertEqual(67354,a['totalCatalogRows']);self.assertEqual(66761,a['kfindRowsCompared'])
        self.assertTrue(a['allCatalogRowsScanned']);self.assertFalse(a['parserMismatches'])
        self.assertEqual(a['suspiciousFound'],a['sourceConfirmed']+a['parserCorrected']+a['unresolved'])
        for name,sha in a['assetHashesBefore'].items():self.assertEqual(sha,hashlib.sha256((A/name).read_bytes()).hexdigest())
        potato=next(r for r in a['suspicious'] if r['foodId']=='kfind-d408-356000000-0001')
        self.assertEqual('SOURCE_CONFIRMED',potato['status']);self.assertEqual('100ml',potato['basis']);self.assertTrue(potato['basisEqual'])
    def test_nolboo_loader_preserves_scope_missing_nutrients_and_dates(self):
        audit=load('nolboo-order-catalog-audit.json');brand=rows(OUT/'nolboo-order-menu-snapshot.csv');store=rows(ROOT/'data-source/recipe-menu-completion/nolboo-public-menu-snapshot.csv')
        self.assertEqual(17,len(brand));self.assertEqual(7,len(store));self.assertEqual(47,sum(len(g['detail']['menus']) for g in audit['groups']))
        self.assertFalse(audit['currentSalesVerified']);self.assertEqual(0,audit['brandHomeBranches'])
        for r in brand:
            self.assertEqual('brand-wide',r['scope']);self.assertEqual('OFFICIAL_ORDER_BRAND_SALES_UNVERIFIED',r['saleState'])
            self.assertFalse(r['energyKcal'] or r['servingAmount'] or r['servingUnit'])
        for g in audit['groups']:
            c=g['source'];self.assertEqual(c['sha256'],hashlib.sha256((ROOT/c['rawFile']).read_bytes()).hexdigest())
            self.assertEqual('0027',g['detail']['category']['mainCode'])
        metadata={r['foodItemId']:r for r in rows(A/'food_metadata.csv')}
        for r in rows(OUT/'nolboo-order-metadata.csv'):self.assertEqual(r,metadata[r['foodItemId']]);self.assertEqual('UNKNOWN',r['allergenStatus'])
        self.assertEqual(17,len(load('nolboo-order-category-audit.json')))
        self.assertTrue(audit['allBranchMenuListsChecked']);self.assertEqual(47,audit['paginatedBranchCount'])

if __name__=='__main__':unittest.main(verbosity=2)
