"""Freeze current assets and assign dish-specific new evidence routes."""
import csv,hashlib,json,re,collections
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-90';AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def save(n,v):(OUT/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def strings(v):
 if isinstance(v,str):yield v
 elif isinstance(v,dict):
  for x in v.values():yield from strings(x)
 elif isinstance(v,list):
  for x in v:yield from strings(x)
def run():
 OUT.mkdir(exist_ok=True)
 if (OUT/'queue-90.json').exists(): raise RuntimeError('Immutable baseline already exists; do not recapture')
 states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json');old=load(ROOT/'data-source/recipe-residual-91/remaining-90-foods.json')
 food=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)];ids={r['id'] for r in food};groups=collections.defaultdict(list)
 for n in ('recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv'):
  for r in rows(AS/n):groups[(r['foodId'],r['recipeId'])].append(r)
 physical={(f,k) for (f,k),rs in groups.items() if f in ids and all(r['recipeComplete']=='true' for r in rs)}
 for s in states:assert s['appCompleteAvailable']==any((f,k) in physical for f in s['targetFoodIds'] for k in (s['recipeId'],s.get('referenceId')))
 assert len(states)==516 and sum(s['appCompleteAvailable'] for s in states)==426 and len(food)==67357 and len(old)==90
 # All previous query strings and URLs are deduplicated before future searches.
 knownqueries=set();knownurls=set()
 for p in (ROOT/'data-source').rglob('*.json'):
  if OUT in p.parents or not any(w in p.name for w in ('search','route','receipt','focused','source-capture','document-index')):continue
  try:d=load(p)
  except (ValueError,OSError):continue
  def queries(v):
   if isinstance(v,dict):
    if isinstance(v.get('q'),str):knownqueries.add(v['q'])
    for x in v.values():queries(x)
   elif isinstance(v,list):
    for x in v:queries(x)
  queries(d)
  for t in strings(d):knownurls.update(re.findall(r'https?://[^\s<>"\)]+',t))
 queue=[]
 for r in old:
  p=r['priorEvidence'];name=r['name'];gaps=r['missingInformation'];ingredients=[i.get('ingredient') for i in gaps if i.get('ingredient')]
  if r['classification']=='FOOD_NUTRITION_MISSING':
   strategy=dict(kind='DISH_NUTRITION_SOURCE_NOT_INGREDIENT_SUM',originalSupplement='동일 식약처 녹색식단의 영양/인쇄 첨부 및 다른 공식 영양 dataset의 개별 음식 행 확인',referenceReplacement='지자체 연구 원표/급식 레시피의 음식 단위 energy+basis와 전체 구성 동일성을 함께 확보',queries=[f'"{name}" 영양 연구보고서',f'"{name.replace("스프","수프")}" 급식 표준레시피 열량'])
  elif r['classification']=='UNRESOLVED':
   strategy=dict(kind='DEEP_DISH_VARIANT_DOCUMENT_SEARCH',originalSupplement=f"원문 {p['originalSource']['url']}에서 HTML외 첨부/별도 endpoint의 누락 규격·단위 확인",referenceReplacement='학교·지역 향토/보건소 HWP 변환본에서 주재료와 조리방식을 함께 비교',queries=[f'"{name.split("(")[0]}" 보건소 조리 재료',f'"{name.split("(")[0]}" 표준레시피 hwp'])
  else:
   gap=' '.join(ingredients[:2])
   strategy=dict(kind='DISH_CONTEXT_AND_INSTITUTION_ATTACHMENT',originalSupplement=f"원본 recipe {r['recipeId']}의 {gap} 규격/상태/계량을 다른 공식 형식과 조리문에서 확인",referenceReplacement='어린이/사회복지급식센터·교육청/지자체 첨부 정량 recipe; 동일 조리법의 독립 전체 배합',queries=[f'"{name}" 레시피 급식지원센터',f'"{name}" "{ingredients[0] if ingredients else "재료"}" hwp'])
  strategy['queries']=[q for q in strategy['queries'] if q not in knownqueries]
  assert strategy['queries'],r['recipeId']
  queue.append(dict(recipeId=r['recipeId'],name=name,foodIds=r['currentFoodIds'],currentStatus=r['classification'],originalRecipe=p['originalSource'],currentReference=r['exactReferenceCandidates'],missingElements=gaps,previousSources=sorted({u for t in strings(r) for u in re.findall(r'https?://[^\s<>"\)]+',t)}),previousCandidatesRejected=p.get('rankedReferenceAdjudications',[]),previousPrimaryReviews=p.get('newPrimaryCandidateReviews',[]),previousSearchReceipts=r['newSearchReceipts'],priorCurrentCandidateDecision=r['currentCandidateDecision'],nextUntriedSourceStrategy=strategy,originalComplete=r['originalComplete'],priorEvidenceFile='data-source/recipe-residual-91/remaining-90-foods.json',finalStatus='PENDING',newEvidence=[]))
 save('queue-90.json',queue);save('previous-search-dedup.json',dict(queries=sorted(knownqueries),urls=sorted(knownurls)))
 save('baseline-counts.json',dict(total=516,complete=426,remaining=90,foodCount=67357,classifications=dict(collections.Counter(r['currentStatus'] for r in queue))))
 save('protected-baseline.json',[dict(path=p.relative_to(ROOT).as_posix(),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted((ROOT/'app/src/main').rglob('*')) if p.is_file()])
 save('baseline-states.json',states)
 for n in ('food_items.csv','product_items.csv','franchise_official_items.csv','recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv','food_metadata.csv','food_data_manifest.properties'):(OUT/('baseline-'+n)).write_bytes((AS/n).read_bytes())
 print(json.dumps(dict(complete=426,remaining=len(queue),knownQueries=len(knownqueries),knownUrls=len(knownurls)),ensure_ascii=False))
if __name__=='__main__':run()
