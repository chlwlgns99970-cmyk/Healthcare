"""Read-only whole catalog audit against the immutable pre-release data baseline."""
import collections,csv,hashlib,json,math,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];AS=ROOT/'app/src/main/assets/fooddata';OUT=ROOT/'app/build/final-release-audit'
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run():
 foods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)];ids={r['id'] for r in foods}
 md=rows(AS/'food_metadata.csv');by={r['foodItemId']:r for r in md}
 refs=rows(AS/'official_recipe_reference_estimates.csv');original=rows(AS/'recipe_ingredient_estimates.csv')
 checks={};checks['foodCount']=len(foods);checks['duplicateFoodIds']=len(foods)-len(ids)
 checks['nutritionOrphan']=len([r for r in foods if not r['energyKcal']]);checks['referenceOrphan']=len({r['foodId'] for r in refs+original}-ids)
 checks['missingFoodMetadata']=len(ids-set(by));checks['wrongIdentityMapping']=sum(by[r['id']]['sourceFoodCode']!=r['sourceFoodCode'] or by[r['id']]['normalizedName']!=r['normalizedName'] for r in foods)
 checks['invalidKcal']=sum(not math.isfinite(float(r['energyKcal'])) or float(r['energyKcal'])<0 for r in foods)
 checks['invalidBasis']=sum(not math.isfinite(float(r['referenceAmount'])) or float(r['referenceAmount'])<=0 or not r['unit'] for r in foods)
 checks['invalidMacros']=sum(not math.isfinite(float(r[k])) or float(r[k])<0 for r in foods for k in ('carbohydrateGrams','proteinGrams','fatGrams') if r[k])
 baseline=ROOT/'data-source/recipe-residual-90';checks['baselineAssetsChanged']=[p.name for p in baseline.glob('baseline-*') if (p.suffix=='.csv' or p.name=='baseline-food_data_manifest.properties') and sha(p)!=sha(AS/p.name.removeprefix('baseline-'))]
 groups=collections.defaultdict(list)
 for r in refs+original:groups[(r['foodId'],r['recipeId'])].append(r)
 complete={k for k,rs in groups.items() if k[0] in ids and all(r['recipeComplete']=='true' for r in rs)}
 states=json.loads((ROOT/'data-source/recipe-final-residual/recipe-final-states.json').read_text(encoding='utf-8'))
 checks['recipes']=len(states);checks['recipeComplete']=sum(any((fid,rid) in complete for fid in s['targetFoodIds'] for rid in (s['recipeId'],s.get('referenceId'))) for s in states);checks['recipeResidual']=len(states)-checks['recipeComplete']
 checks['metadataNamespaceExtras']=len(set(by)-ids) # official menu IDs must be checked by real runtime model, never assume Food orphans
 checks['qaDebugReleaseAssetsIdentical']={}
 for v in ('qa','debug','release'):
  with zipfile.ZipFile(ROOT/f'app/build/outputs/apk/{v}/app-{v}.apk') as z:
   checks['qaDebugReleaseAssetsIdentical'][v]=all(z.read('assets/fooddata/'+p.name)==p.read_bytes() for p in AS.iterdir() if p.is_file())
 assert checks['foodCount']==67357 and checks['recipes']==516 and checks['recipeComplete']==426 and checks['recipeResidual']==90
 assert all(checks[k]==0 for k in ('duplicateFoodIds','nutritionOrphan','referenceOrphan','missingFoodMetadata','wrongIdentityMapping','invalidKcal','invalidBasis','invalidMacros'))
 assert not checks['baselineAssetsChanged'] and all(checks['qaDebugReleaseAssetsIdentical'].values())
 checks['status']='PASS';checks['runtimeModelAndMetadataMenuNamespace']='Await real AllFoodDetailModelSamsungTest; not replaced by CSV simulation'
 (OUT/'data-integrity.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(checks,ensure_ascii=False))
if __name__=='__main__':run()
