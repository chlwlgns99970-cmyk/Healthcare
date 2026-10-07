"""Residual-only evidence ledger and independent asset/protection checks."""
import collections, csv, hashlib, json, math, re, zipfile
from pathlib import Path
from urllib.parse import urlparse
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-residual-91'
AS=ROOT/'app/src/main/assets/fooddata'
def load(p): return json.loads(p.read_text(encoding='utf-8'))
def rows(p): return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def save(n,v): (OUT/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def run():
 q=load(OUT/'queue-91.json'); states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')
 byid={s['recipeId']:s for s in states}; new=load(OUT/'new-app-complete-foods.json'); docs=load(OUT/'new-document-index.json')
 receipts={}
 for p in sorted(OUT.glob('route-*.json')):
  for f in load(p)['foods']: receipts[f['recipeId']]=p.relative_to(ROOT).as_posix()
 reviews={
  'MFDS-139': '아워홈 겉절이보리비빔밥은 부추 강된장 밥과 주재료가 다름. 완성 Food-level 영양도 확보되지 않아 이름을 대체하지 않음.',
  'MFDS-110': '메뉴젠 비빔국수 D031045는 새싹 배합을 확인할 수 없어 새싹 qualifier를 삭제한 매칭을 하지 않음. 영양사협회 두부새싹비빔밥은 국수가 아님.',
  'MFDS-154': '농식품정보누리 단호박경단은 찹쌀가루 300g이 있지만 단호박 1/2개·천일염/설탕 약간·깨 컵의 동일 상태 중량 환산이 미확정. 원본 카스테라 배합과 섞지 않음.',
  'MFDS-170': '샘표 채소군만두는 고기 찐만두와 재료·조리법이 다름. 메뉴젠 만두 D032017도 찐 조리법 일치가 확인되지 않음.',
  'MFDS-192': '영양사협회 사과바나나셰이크는 사과 50g·바나나 100g·우유 100g, 배식량 250g·163kcal 제공. 바나나스무디 원본과 달리 사과가 주재료라 동일명 alias를 만들지 않음.',
  'MFDS-241': '영양사협회 청소년특선식단의 김치콩나물밥은 100인분 수치가 있으나 소금 사용량이 없고 완성 음식 자체 영양은 확보되지 않음.',
  'MFDS-256': '영양사협회 게재 도토리묵밥은 쑥갓 적당량·소금/깨 약간 등 정량 미확정. 메뉴젠 도토리묵비빔밥 D014081은 양념·비빔 조리법이 원본 묵/밥 담기와 다르므로 공식 kcal를 빌려오지 않음.',
  'MFDS-296': '메뉴젠 견과류 멸치볶음 D101008은 중멸치·아몬드·호박씨 배합. 원본 잔멸치 호두 조림의 멸치 크기와 조리법 일치를 보장하지 못함.',
  'MFDS-301': '샘표 사이트의 호두 멸치 요리 검색 결과 중 이용자 cooking 글은 제조사 공식 recipe-lab과 다르므로 공식 근거로 채택하지 않음. 원본 전 재료 단위 미기재도 그대로 유지.',
  'MFDS-308': '영양사협회 물미역초회(+초고추장)는 물미역·오이·당근·무가 있지만 양념 포함 재료별 양이 없음. 건미역 환산값으로 물미역을 임의 환산하지 않음.',
  'MFDS-321': '농식품정보누리 두릅초회는 한 줌/큰술 계량, 소비자원 두릅숙회는 두릅 중량 미기재. 당뇨병학회 두릅 70g/초고추장 10g 식단은 종·상태/전체 배합 근거가 불충분.',
  'MFDS-322': '냉이국 또는 콩나물국만 있는 참고 후보는 냉이·콩나물을 동시에 포함한 원본 identity가 아니므로 어느 한 주재료도 삭제하지 않음.',
  'MFDS-96': new[0]['identityEvidence'],
 }
 for r in q:
  s=byid[r['recipeId']]; old=r['priorEvidence']
  r.update(appComplete=s['appCompleteAvailable'],currentState=s['state'],currentFoodIds=s['targetFoodIds'],newSearchReceipts=[receipts[r['recipeId']]],wholeInternetExhausted=False)
  r['newSourceReviews']=[dict(url=d['url'],sha256=d.get('sha256'),pages=m['pages'],cacheReused=d.get('cacheReused',False),review='이름 일치 페이지 색인. 이름만으로 완전 구성으로 인정하지 않음. 기존 원문 검토와 이번 개별 판정에 따름.') for d in docs for m in d.get('matches',[]) if m['recipeId']==r['recipeId']]
  if r['name']=='백합죽': reviews[r['recipeId']]='수산물안전정보 FSIS 백합죽은 백합 150g을 껍질째 씻고 조개살을 떼는 조리법. 가식부 중량/쌀 종류와 참기름 1/2큰술·소금 1작은술 환산이 미확정. 어종 별칭은 확인했지만 전량 연결 근거는 부족.'
  r['currentCandidateDecision']=reviews.get(r['recipeId'],'기존 원문·후보 판정을 재사용. 이번 개별 정량/공식기관 검색과 공공 책자 색인에서 기존 실패 원인을 모두 해소한 동일 음식의 독립 전체 배합을 확보하지 못함.')
  r['missingInformation']=[dict(ingredient=i['ingredient'],raw=i['originalSpan'],status=i['status'],identityReason=i['identityReason'],unitReason=i['unitReason']) for i in r['missingIngredients']]
  if r['classification']=='FOOD_NUTRITION_MISSING':
   r['missingInformation']=[dict(type='Food-level nutrition',reason='정확한 완성 음식 자체 공식 kcal/basis 미확보. 기존 FoodItem.energyKcal 필수 Double 정책상 unknown Food 등록 불가. 재료 합계로 대체하지 않음.')]
  r['remainingReason']=old.get('remainingReason')
  r['additionalRealisticRoutes']=dict(publicRouteResult='이번 검색·색인 범위에서 완전한 동일 음식 참고 배합을 확보하지 못함. 인터넷 전체 또는 모든 기관을 소진했다고 주장하지 않음.',originalSource=old['originalSource']['url'],neededInformation=[i.get('ingredient',i.get('type')) for i in r['missingInformation']],institutionRequest='원본 발행기관에 해당 recipeId의 재료 규격·계량/영양 원표 요청. 공개되어 있지 않은 원표를 실제 확보해야 하며 임의 수량으로 대체할 수 없음.',existingNextAction=old.get('nextAction'))
 save('final-queue-91.json',q); remaining=[r for r in q if not r['appComplete']];save('remaining-90-foods.json',remaining)
 nutrition=[r for r in q if r['classification']=='FOOD_NUTRITION_MISSING'];save('food-nutrition-four-results.json',nutrition)
 # Preserve full detailed JSON; provide compact Korean review for every remaining food.
 lines=['# 잔여 90개 개별 근거','', '91개 검색 영수증과 이전 검토를 보존한 현재 판정입니다. 인터넷 전체 소진을 의미하지 않습니다.','']
 for r in remaining:
  lines += [f"## {r['recipeId']} · {r['name']}",'',f"- 상태: {r['classification']}; 선택 Food ID: {', '.join(r['currentFoodIds']) or '미연결'}",f"- 부족 정보: {json.dumps(r['missingInformation'],ensure_ascii=False)}",f"- 원문: {r['priorEvidence']['originalSource']['url']}",f"- 새 검색: {r['newSearchReceipts'][0]}",f"- 개별 후보 판정: {r['currentCandidateDecision']}",f"- 기존 후보/문서/미채택 이유: final-queue-91.json의 {r['recipeId']} priorEvidence 및 newSourceReviews",f"- 추가 경로: 원 발행기관에 위 부족 재료의 규격·정량·완성 음식 자체 영양 원표 요청. {r['additionalRealisticRoutes']['publicRouteResult']}",'']
 (OUT/'remaining-90-review.md').write_text('\n'.join(lines),encoding='utf-8')
 checks=[]
 def check(name,condition,detail):
  assert condition,(name,detail)
  checks.append(dict(name=name,status='PASS',detail=detail))
 current=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
 baseline=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(OUT/('baseline-'+n))]
 ids={r['id']:r for r in current}; f=ids[new[0]['foodId']]; ref=rows(AS/'official_recipe_reference_estimates.csv'); nr=[r for r in ref if r['foodId']==f['id']]
 original=rows(AS/'recipe_ingredient_estimates.csv');groups=collections.defaultdict(list)
 for r in ref+original: groups[(r['foodId'],r['recipeId'])].append(r)
 physical={(fid,rid) for (fid,rid),rs in groups.items() if fid in ids and all(r['recipeComplete']=='true' for r in rs)}
 complete=sum(any((fid,rid) in physical for fid in s['targetFoodIds'] for rid in (s['recipeId'],s.get('referenceId'))) for s in states)
 check('01 queue',len(q)==91 and len(receipts)==91 and len(remaining)==90,dict(before=91,after=90,searchCoverage=91))
 check('02 candidate matching',new[0]['originalRecipeId']=='MFDS-96' and new[0]['referenceId']=='MENUZEN-D051300',new[0]['identityEvidence'])
 check('03 alias canonical',[a for a in f['aliases'].split('|') if a]==['애호박젓국'] and f['name']=='애호박새우젓국' and not new[0]['duplicates'],'verified alias; no baseline exact duplicate')
 check('04 foodId mapping',len(ids)==len(current)==67357 and all(r['foodId']==f['id'] for r in nr),'67356 existing + 1 independently sourced Food')
 check('05 amount',len(nr)==7 and all(float(r['amountGrams'])>0 for r in nr) and math.isclose(sum(float(r['amountGrams']) for r in nr),64.5),'7 original official foodWgh inputs; total64.5g')
 check('06 unit',all(i['amountGrams']==i['originalRecord']['foodWgh'] for i in new[0]['inputs']) and f['unit']=='g','direct official grams; no spoon/cup guess')
 check('07 nutrition',all(i['nutrient']['sourceUrl'] and i['nutrient']['energyKcal']>=0 for i in new[0]['inputs']),[i['nutrient']['code'] for i in new[0]['inputs']])
 estimated=sum(float(r['amountGrams'])*float(r['kcalPer100g'])/100 for r in nr)
 check('08 kcal',float(f['energyKcal'])==76.9 and math.floor(float(f['energyKcal'])+.5)==77 and math.isfinite(estimated),dict(officialKcal=76.9,officialDisplayed=77,referenceIngredientSum=estimated,notRescaled=True))
 check('09 provenance',sha(ROOT/new[0]['rawFile'])==new[0]['sourceSha256'] and all(r['recipeSha256']==new[0]['sourceSha256'] and r['recipeUrl']==new[0]['sourceUrl'] for r in nr),new[0]['sourceSha256'])
 check('10 ORIGINAL_COMPLETE',sha(AS/'recipe_ingredient_estimates.csv')==sha(OUT/'baseline-recipe_ingredient_estimates.csv'),'original complete/partial rows unchanged; addedOriginal=0')
 check('11 REFERENCE_COMPLETE',complete==426 and all(r['compositionKind']=='REFERENCE_RECIPE' for r in nr),dict(complete=complete,ledger=sum(s['appCompleteAvailable'] for s in states)))
 check('12 PARTIAL',sum(r['classification']=='PARTIAL' for r in remaining)==80,'81→80')
 check('13 UNRESOLVED',sum(r['classification']=='UNRESOLVED' for r in remaining)==6,'6 unchanged')
 check('14 fake amount prevention',all(not i['conversionProvenance'] for i in new[0]['inputs']) if all('conversionProvenance' in i for i in new[0]['inputs']) else all(i['amountGrams']==i['originalRecord']['foodWgh'] for i in new[0]['inputs']),'no synthetic amount/ingredient cross-recipe merge')
 check('15 fake nutrition prevention',float(f['energyKcal'])==float(new[0]['reportedNutritionHeader']['totalEnergy']) and not new[0]['ingredientSumUsedAsOfficialNutrition'] and len(nutrition)==4,'institution header only; nutrition missing4 stay unlinked')
 check('16 wrong foodId prevention',all(r['foodId'] in ids for r in nr) and all(r['foodId']!=fid for fid in [b['id'] for b in baseline] for r in nr),'new rows attach only to verified new Food')
 check('17 existing official nutrition preservation',all(ids[b['id']]==b for b in baseline),'all67356 previous Food rows identical')
 protected=load(OUT/'protected-baseline.json');allowed={'app/src/main/assets/fooddata/'+n for n in ('food_items.csv','food_metadata.csv','official_recipe_reference_estimates.csv','food_data_manifest.properties')}
 changed=[r['path'] for r in protected if sha(ROOT/r['path'])!=r['sha256']]
 check('18 MealRecord and protected code',set(changed)==allowed,'all main source except4 allowlisted assets identical; Home/save8/DB/MealRecord code unchanged; physical Samsung preservation separate')
 check('19 metadata preservation',rows(AS/'food_metadata.csv')[:-1]==rows(OUT/'baseline-food_metadata.csv'),'all old metadata rows identical')
 check('20 reference preservation',ref[:-7]==rows(OUT/'baseline-official_recipe_reference_estimates.csv'),'7022 old + 7 new =7029 reference rows')
 apk=ROOT/'app/build/outputs/apk/qa/app-qa.apk'
 with zipfile.ZipFile(apk) as z:
  check('21 QA asset packaging',all(z.read('assets/fooddata/'+n)==(AS/n).read_bytes() for n in ('food_items.csv','food_metadata.csv','official_recipe_reference_estimates.csv','food_data_manifest.properties')),'compiled QA APK assets identical')
 check('22 physical state consistency',all(s['appCompleteAvailable']==any((fid,rid) in physical for fid in s['targetFoodIds'] for rid in (s['recipeId'],s.get('referenceId'))) for s in states),'516 ledger states independently matched to physical assets')
 save('data-verification.json',dict(status='PASS',checks=checks,counts=dict(total=516,beforeComplete=425,afterComplete=complete,remaining=len(remaining),classifications=dict(collections.Counter(r['classification'] for r in remaining))),protectedChangedFiles=changed))
 institutions=collections.defaultdict(lambda:dict(urls=[],captured=0,cacheReused=0,publishedReferences=0))
 for d in docs:
  host=urlparse(d['url']).netloc;x=institutions[host];x['urls'].append(d['url']);x['captured']+=d['status']=='CAPTURED';x['cacheReused']+=bool(d.get('cacheReused'))
 institutions['www.nics.go.kr']['publishedReferences']=1;institutions['www.nics.go.kr']['urls'].append(new[0]['sourceUrl']);institutions['www.nics.go.kr']['cacheReused']+=1
 save('institution-source-results.json',dict(institutions=institutions,note='URL captures include duplicate URL variants. Cached PDFs are not new references. Menuzen publication reuses a SHA-verified prior source; no newly captured external recipe was published. Search-only institutions and queries remain in route/search receipts.'))
 print(json.dumps(dict(status='PASS',checks=len(checks),complete=complete,remaining=len(remaining)),ensure_ascii=False))
if __name__=='__main__': run()
