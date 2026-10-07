"""Bounded public-evidence adjudication; never upgrades recipes from search hits."""
import collections,csv,hashlib,json,re,subprocess,sys,zipfile
from pathlib import Path
from urllib.parse import urlparse
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-residual-90'
AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,x):(OUT/n).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def run():
 q=load(OUT/'queue-90.json');docs=load(OUT/'source-captures.json');plan=load(OUT/'dish-new-route-plan.json')
 receipts={}
 for p in sorted(OUT.glob('dish-route-*.json')):
  for r in load(p)['dishes']:receipts[r['recipeId']]=p.relative_to(ROOT).as_posix()
 strategies={r['recipeId']:r for r in plan}
 decisions={
 'MFDS-302':'고양시 2026년2월 유치원형 XLSX file19039: 식빵30/단호박15/당근5/난황마요네즈5/백설탕1.5/소금0.5와 134.69kcal 유아 1인분 영양표 확보. 수량 열에 g 단위 명시 없음. 숫자를 g로 해석한 임시 반영을 작업 전 자산으로 전부 복원하고 보류. 신규 공동 레시피북의 개별 g 표기는 이 별도 XLSX의 단위를 증명하지 않음.',
 'MFDS-256':'충남농업기술원 향토음식 특성화 연구에 도토리묵밥 45.6kcal/100g 확보. 지역 시료의 개별 재료·육수·밥량 미공개. 원본은 묵200g/밥280g만 명시되어 동일 배합 확인 불가. 관광공사 가이드의 묵밥=도토리묵밥 이름 근거도 국물 배합 동일성을 증명하지 못함.',
 'MFDS-239':'딸기롤샌드위치 공식 음식 자체 영양을 우선 검색했으나 원본 딸기·식빵 롤의 동일 배합 영양표를 확보하지 못함. 롤빵·딸기잼 샌드위치·브랜드 제품 영양을 대체하지 않음.',
 'MFDS-255':'연근치즈구이 공식 영양표를 우선 조사. 연근전·연근칩·피자/샐러드와 다른 구이 조리·치즈 배합에 해당하는 독립 Food 영양 원표 미확보.',
 'MFDS-306':'잣스프/잣수프 학술·급식 영양표 검색. 잣죽·크림수프를 동일 음식으로 보지 않음. 원본 수프 전체 배합과 일치하는 음식 자체 영양표 미확보.',
 'MFDS-167':'아워홈 공식 1인분 취나물비빔밥 308 확보. 조리법의 느타리버섯이 정량표에 없고 사용 후추/깨가0으로 기재되어 실제 양 불명. 그 재료를 생략하거나0으로 처리하지 않음.',
 'MFDS-192':'고양시 file16015 블루베리바나나스무디는 블루베리가 주재료인 별도 배합. 공동 레시피북 배요거트스무디도 바나나스무디와 다름. 과일 qualifier를 삭제해 연결하지 않음.',
 'MFDS-223':'고양시 XLSX 46건 이름 적중은 딸기잼을 재료로 쓰는 토스트/샌드위치. 딸기잼 자체를 제조하는 전체 배합으로 인정하지 않음.',
 'MFDS-170':'고양시 찐만두 정량표는 기성품 돼지고기 만두 투입량과 찌는 단계. 피·속의 전체 구성 없음. 원본 배합과 합치지 않음.',
 'RDA-91134':'고양시 메밀전병은 기성품 메밀전병45와 기름2. 피/속 구성, 단위, 전체 제조 배합이 없어 총떡 원본의 완전 참고 배합으로 채택하지 않음.',
 'RDA-90925':'고양시 바람떡은 구매 완제품 양만 제공. 서울시 연구 부록172/173/176/177쪽도 식단명 목록이며 피·소 재료 정량 없음.',
 'MFDS-340':'고양시 닭봉구이 5개 재료 수량 후보 확인. 수량 열 단위 및 뼈 포함 닭봉의 가식부 기준 불명. 닭봉100을 순살100g으로 바꾸지 않음.',
 'MFDS-269':'고양시 file20739 새우살달걀찜에 달걀49/새우5 등과 달걀 양만큼 물 안내. 수량 단위와 새우 종류/가식 상태 영양 기준 미확정. 새우종을 임의 선택하지 않음.',
 'MFDS-279':'고양시 고등어김치찜 후보 중 file18625 조리법의 후추 사용량이 재료표에 없음. 생선 뼈/가식량과 수량 열 단위도 불명. 후추 생략으로 완전 처리하지 않음.',
 }
 shared_docs=[d['url'] for d in docs if d.get('pageCount',0)>30 and not d.get('sheetNames')]
 for r in q:
  rid=r['recipeId'];s=strategies[rid]
  hits=[]
  for d in docs:
   for m in d.get('matches',[]):
    if m['recipeId']==rid:
     t=(ROOT/d['textFile']).read_text(encoding='utf-8') if d.get('textFile') else ''
     normalized=re.sub(r'\s+','',t);term=re.sub(r'\s+','',r['name'].split('(')[0]);n=normalized.find(term)
     hits.append(dict(url=d['url'],institution=d['institution'],sha256=d.get('sha256'),rawFile=d.get('rawFile'),pages=m['pages'],context=normalized[max(0,n-100):n+650] if n>=0 else '',decision='NAME_HIT_ONLY_NOT_PUBLISHED',review=decisions.get(rid,'단순 메뉴명·부분 재료 또는 다른 배합 적중. 원문의 부족 정보가 모두 해소되는 단일 전체 정량 구성으로 검증되지 않음.')))
  r['newEvidence']=hits
  r['newSourceStrategy']=s
  r['newSearchReceipts']=[receipts[rid]]
  r['sharedPublicDocumentRoutes']=shared_docs
  r['currentCandidateDecision']=decisions.get(rid,r['priorCurrentCandidateDecision']+' 이번 신규 기관 경로 검색 및 전체 공개문서 색인에서 이 부족 사항을 모두 해소한 같은 음식의 전체 정량 배합을 확보하지 못함.')
  r['finalStatus']='UNRESOLVED_WITH_EXHAUSTED_EVIDENCE' if r['currentStatus']=='UNRESOLVED' else 'PARTIAL_WITH_EXHAUSTED_EVIDENCE'
  r['exhaustionScope']='이번에 실제 확인한 검색 영수증·공개 원문 범위의 판정. 모든 인터넷/기관을 소진했다는 뜻 아님.'
  r['wholeInternetExhausted']=False
  r['additionalRealisticRoutesRemain']=True
  r['additionalRealisticRoutes']={'originalInstitution':r['originalRecipe']['url'],'requiredOriginalInformation':r['missingElements'],'institutionRoute':'원 발행기관의 해당 recipeId 재료 규격·계량·완성 음식 영양 원표 또는 누락 없는 독립 전체 배합이 실제 공개되어야 연결 가능. 요청 메시지는 발송하지 않음.','specificNextRoute':s['route']+'의 새 원표/첨부가 확보되면 기존 실패 요소와 먼저 대조; 같은 URL·검색어·방법 반복 금지.'}
 save('final-queue-90.json',q);save('remaining-90-foods.json',q)
 save('food-nutrition-four-results.json',[r for r in q if r['currentStatus']=='FOOD_NUTRITION_MISSING'])
 save('new-app-complete-foods.json',[])
 # Preserve unverified quantities as raw source cells; remove tentative grams/calorie calculations.
 c=load(OUT/'sandwich-candidate-withheld.json')
 for x in c:
  for i in x['inputs']:
   if 'amountGrams' in i:i['rawSourceAmount']=i.pop('amountGrams')
   i.pop('calculatedKcal',None);i['unit']=None;i['quantityUnitVerified']=False
  x['publishedRows']=0;x['foodIdStatus']='PROPOSED_NOT_REGISTERED';x['published']=False
 save('sandwich-candidate-withheld.json',c)
 lines=['# 남은90개 개별 판정','', '516/426/잔여90. 신규 연결0. 범위가 한정된 공개 근거 판정이며 전체 인터넷 소진을 의미하지 않습니다.','']
 for r in q:
  lines += [f"## {r['recipeId']} · {r['name']}",'',f"- 상태: {r['finalStatus']} ({r['currentStatus']})",f"- 부족 정보: {json.dumps(r['missingElements'],ensure_ascii=False)}",f"- 기존 원문: {r['originalRecipe']['url']}",f"- 기존 source/후보/미채택: final-queue-90.json의 previousSources, previousCandidates, priorCurrentCandidateDecision",f"- 이번 경로: {r['newSourceStrategy']['route']}",f"- 새 검색 영수증: {r['newSearchReceipts'][0]}",f"- 새 원문 적중: {json.dumps(r['newEvidence'],ensure_ascii=False)}",f"- 후보 판정: {r['currentCandidateDecision']}",f"- 추가 현실적 경로: 있음. {r['additionalRealisticRoutes']['institutionRoute']}",'']
 (OUT/'remaining-90-review.md').write_text('\n'.join(lines),encoding='utf-8')
 groups=collections.defaultdict(list);foods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)];ids={r['id'] for r in foods}
 for n in ('recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv'):
  for r in rows(AS/n):groups[(r['foodId'],r['recipeId'])].append(r)
 physical={k for k,rs in groups.items() if k[0] in ids and all(r['recipeComplete']=='true' for r in rs)}
 states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')
 actual=lambda s:any((fid,rid) in physical for fid in s['targetFoodIds'] for rid in (s['recipeId'],s.get('referenceId')))
 protected=load(OUT/'protected-baseline.json');changed=[r['path'] for r in protected if sha(ROOT/r['path'])!=r['sha256']]
 assets=[p for p in OUT.glob('baseline-*') if p.suffix=='.csv' or p.name=='baseline-food_data_manifest.properties']
 checks=[]
 def check(n,ok,detail):
  assert ok,(n,detail)
  checks.append(dict(name=n,status='PASS',detail=detail))
 check('01 all90 adjudicated',len(q)==len({r['recipeId'] for r in q})==90 and all(r['finalStatus']!='PENDING' for r in q),'90 unique dishes')
 check('02 search evidence coverage',len(receipts)==90 and all((ROOT/r['newSearchReceipts'][0]).exists() for r in q),'90 dish-specific routes/receipts')
 check('03 prior failures preserved',all(r['previousSources'] and 'priorCurrentCandidateDecision' in r for r in q),'no discarded prior rejection ledger')
 check('04 recipe516',len(states)==516,'unchanged')
 check('05 complete426 physical',sum(actual(s) for s in states)==426,'CSV rows independently verify426')
 check('06 remaining90',sum(not actual(s) for s in states)==90,'unchanged')
 check('07 physical/ledger agreement',all(actual(s)==s['appCompleteAvailable'] for s in states),'516 states')
 check('08 PARTIAL80',sum(r['currentStatus']=='PARTIAL' for r in q)==80,'unchanged')
 check('09 UNRESOLVED6',sum(r['currentStatus']=='UNRESOLVED' for r in q)==6,'unchanged')
 check('10 nutrition4',sum(r['currentStatus']=='FOOD_NUTRITION_MISSING' for r in q)==4,'each adjudicated; no ingredient-sum official kcal')
 check('11 Food67357 unique',len(foods)==len(ids)==67357,'no new foodId')
 check('12 baseline assets byte preservation',all(sha(p)==sha(AS/p.name.removeprefix('baseline-')) for p in assets),'all7 baseline assets identical')
 check('13 all main code protected',not changed,'DB/MealRecord/popup/Home/calculation/preferences all same SHA')
 check('14 zero publication',load(OUT/'new-app-complete-foods.json')==[],'0 ORIGINAL /0 REFERENCE additions')
 check('15 unverified unit withheld',all(i['unit'] is None and not i['quantityUnitVerified'] for i in c[0]['inputs']) and c[0]['published'] is False,'6 source numbers retained without g assumption')
 result=subprocess.run([sys.executable,str(ROOT/'tools/publish_recipe_residual_90.py')],capture_output=True,text=True)
 check('16 publisher fails closed',result.returncode!=0 and 'Publication withheld' in result.stderr,'no unit approval => no mutations')
 check('17 captured raw provenance',all(sha(ROOT/d['rawFile'])==d['sha256'] for d in docs if d.get('rawFile')),'all captured original bytes SHA checked, inaccessible responses separated')
 with zipfile.ZipFile(ROOT/'app/build/outputs/apk/qa/app-qa.apk') as z:
  check('18 QA packages current assets',all(z.read('assets/fooddata/'+p.name.removeprefix('baseline-'))==(AS/p.name.removeprefix('baseline-')).read_bytes() for p in assets),'QA APK all7 assets byte-identical')
 counts=dict(total=516,beforeComplete=426,afterComplete=426,beforeRemaining=90,afterRemaining=90,PARTIAL=80,UNRESOLVED=6,FOOD_NUTRITION_MISSING=4,newOriginal=0,newReference=0,newFood=0)
 save('data-verification.json',dict(status='PASS',checks=checks,counts=counts,protectedChangedFiles=changed))
 unit=[]
 for p in sorted((ROOT/'app/build/test-results/testQaUnitTest').glob('TEST-*.xml')):
  e=ET.parse(p).getroot();unit.append(dict(name=e.attrib['name'],tests=int(e.attrib['tests']),failures=int(e.attrib['failures']),errors=int(e.attrib['errors']),skipped=int(e.attrib['skipped'])))
 assert len(unit)==5 and sum(x['tests'] for x in unit)==26 and not sum(x['failures']+x['errors'] for x in unit)
 save('unit-verification.json',dict(status='PASS',tests=26,suites=unit,execution='Gradle UP-TO-DATE: unchanged code/assets reuse existing26 passing results; not26 newly executed tests',log='app/build/recipe-residual-90-build.log'))
 institutions=collections.defaultdict(lambda:dict(investigated=0,captured=0,xlsx=0,pdf=0,uniqueDocuments=0,accessRequired=0,published=0))
 for d in docs:
  x=institutions[d['institution']];x['investigated']+=1;x['captured']+=d['status']=='CAPTURED';x['xlsx']+=bool(d.get('sheetNames'));x['pdf']+=bool(d.get('rawFile') and (ROOT/d['rawFile']).read_bytes().startswith(b'%PDF'));x['accessRequired']+=d['status']=='ACCESS_REQUIRED_NO_DOCUMENT'
 for k,x in institutions.items():x['uniqueDocuments']=len({d['sha256'] for d in docs if d['institution']==k and d['status']=='CAPTURED'})
 save('institution-source-results.json',dict(institutions=institutions,allPublished=0,note='search-only institutions in per-dish receipts; URL count differs from unique document SHA count; HTML index/ebook shells are not full recipes'))
 querycounts=collections.Counter(r['q'] for r in plan)
 save('search-dedup-audit.json',dict(plannedDishes=90,uniqueDishQueries=len(querycounts),duplicateQueries=[dict(query=k,count=v,reason='3 original 백합죽 recipes share one dish query; this turn accidentally repeated the query2 extra times. Future queries must use cached shared receipt.') for k,v in querycounts.items() if v>1],scope='Does not claim zero duplicate executions; old raw evidence/recipes were reused without new network fetch.'))
 save('final-summary.json',dict(status='PARTIAL',counts=counts,dataTests=18,unitTests=26,unitCached=True,qaBuild='PASS',samsung='NOT_RUN: no newly published foods, no QA installation',dbVersion=8,migrations=0,userData='No device mutation this turn; main source bytes preserved',productVersionCode=7,productVersionName='1.0.6',productInstalls=0,clears=0,overwrites=0,deployments=0,questionRequired=False,publicEvidenceScopeBounded=True))
 print(json.dumps(dict(status='PARTIAL',dataTests='18 PASS',complete=426,remaining=90),ensure_ascii=False))
if __name__=='__main__':run()
