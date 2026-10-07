"""Recompute the residual queue from shipped assets; never publish guesses."""
import csv, hashlib, json, re, collections
from pathlib import Path
from finish_recipe_reference_mapping import original_keys, keys
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-residual-91'
AS = ROOT / 'app/src/main/assets/fooddata'
def load(p): return json.loads(p.read_text(encoding='utf-8'))
def rows(p): return list(csv.DictReader(p.open(encoding='utf-8-sig', newline='')))
def save(n, v): (OUT/n).write_text(json.dumps(v, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def run():
 OUT.mkdir(exist_ok=True)
 states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')
 prior={r['recipeId']:r for r in load(ROOT/'data-source/recipe-residual-93/remaining-91-foods.json')}
 foods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
 food_ids={r['id'] for r in foods}; groups=collections.defaultdict(list)
 for n in ('recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv'):
  for r in rows(AS/n): groups[(r['foodId'],r['recipeId'])].append(r)
 complete={(f,k) for (f,k),rs in groups.items() if f in food_ids and all(r['recipeComplete']=='true' for r in rs)}
 decisions=load(ROOT/'data-source/recipe-final-residual/ingredient-decisions.json')
 refs=load(ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json')
 food_index=collections.defaultdict(set)
 for r in foods:
  for name in (r['name'],*r['aliases'].split('|')):
   if name:
    for k in keys(name): food_index[k].add(r['id'])
 queue=[]; counts=collections.Counter()
 for s in states:
  present=any((f,k) in complete for f in s['targetFoodIds'] for k in (s['recipeId'],s.get('referenceId')))
  assert present==s['appCompleteAvailable'], s['recipeId']
  counts['appComplete' if present else 'remaining']+=1
  if present: continue
  old=prior[s['recipeId']]; wanted=original_keys(s['name'])
  matches=sorted(set().union(*(food_index[k] for k in wanted)))
  inputs=[r for r in decisions if r['recipeId']==s['recipeId']]
  relevant=[dict(recipeId=r['recipeId'],name=r['name'],complete=r['complete'],sourceUrl=r['sourceUrl'],sourceInstitution=r['sourceInstitution'],inputs=r['inputs']) for r in refs if wanted & keys(r['name'])]
  variants=list(dict.fromkeys([s['name'],*old.get('searchVariants',[]),re.sub(r'\([^)]*\)','',s['name']).strip(),s['name'].replace('주꾸미','쭈꾸미').replace('스프','수프').replace('쥬스','주스')]))
  queue.append(dict(recipeId=s['recipeId'],name=s['name'],baselineState=s['state'],classification='FOOD_NUTRITION_MISSING' if s['originalComplete'] else s['state'],foodIds=matches,selectedFoodIds=s['targetFoodIds'],originalComplete=s['originalComplete'],connectedIngredients=[r for r in inputs if r['status']=='LINKED'],missingIngredients=[r for r in inputs if r['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')],exactReferenceCandidates=relevant,searchVariants=variants,priorEvidence=old,newSearchReceipts=[],newSourceReviews=[],appComplete=present))
 assert len(states)==516 and counts['appComplete']==425 and len(queue)==91
 save('queue-91.json',queue)
 save('baseline-counts.json',dict(total=len(states),foodCount=len(foods),**counts,classifications=dict(collections.Counter(q['classification'] for q in queue))))
 protected=[]
 for p in sorted((ROOT/'app/src/main').rglob('*')):
  if p.is_file():
   protected.append(dict(path=p.relative_to(ROOT).as_posix(),sha256=hashlib.sha256(p.read_bytes()).hexdigest(),size=p.stat().st_size))
 save('protected-baseline.json',protected)
 for n in ('food_items.csv','product_items.csv','franchise_official_items.csv','recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv','food_metadata.csv','food_data_manifest.properties'):
  p=OUT/('baseline-'+n)
  if not p.exists(): p.write_bytes((AS/n).read_bytes())
 print(json.dumps(load(OUT/'baseline-counts.json'),ensure_ascii=False))
 print('Exact reference candidates:',[(q['name'],[(r['name'],r['complete']) for r in q['exactReferenceCandidates']]) for q in queue if q['exactReferenceCandidates']])
if __name__=='__main__':run()
