"""Persist every remaining food, source route, and packaged asset verification."""
import collections,csv,hashlib,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-final-residual';PRIOR=ROOT/'data-source/recipe-full-reference';QA=ROOT/'app/build/recipe-final-residual'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,x):(OUT/n).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def run():
    progress=load(OUT/'progress.json')
    states=load(OUT/'recipe-final-states.json');queue={q['recipeId']:q for q in load(OUT/'food-work-queue.json')}
    refs=load(OUT/'validated-reference-compositions.json');byref={r['recipeId']:r for r in refs}
    decisions=load(OUT/'ingredient-decisions.json');byrecipe=collections.defaultdict(list)
    for d in decisions:byrecipe[d['recipeId']].append(d)
    routes=[dict(source='자연을 담은 학교 급식레시피 (2014)',url='https://dl.nanet.go.kr/detail/MONO1201738807',reviewedPages=[71,105,125,201,209],result='양상추샐러드 두 배합은 tsp·누락/불일치 양념, 주꾸미는 식용유 중량 누락, 떡갈비는 발효액/제품소스 또는 약간 단위 미확정. 다섯 해당 페이지 실제 열람. 260쪽 전체 열람으로 주장하지 않음'),
        dict(source='홍천군 46호 양상추샐러드&과일드레싱',url='https://www.hongcheon.go.kr/sciencecenter/selectBbsNttView.do?bbsNo=13&key=849&nttNo=107759',result='게시글 첨부파일 필드 비어 있음; 배합 없음'),
        dict(source='한국소비자원 두릅 안내',url='https://www.kca.go.kr/webzine/board/view?div=kca_2304&linkId=536&menuId=MENU00324',result='두릅 중량 없음, 큰술 양념의 정확 g 없음'),
        dict(source='국립수산물품질관리원 FSIS 주꾸미',url='https://www.fsis.go.kr/front/contents/cmsView.do?cate_id=0301&cnts_id=14818&select_list_no=118',sha256='dff31cc13ae317f6c62e1de0b7026bd30c4db2d559a33205c00ef9dd74b8c0d6',result='먹물 제거한 식용부 100g 52kcal; 먹물 자체에 적용 불가'),
        dict(source='농식품정보누리 이금기 제공 멍게비빔밥',url=load(OUT/'last-public-source-captures.json')[0]['url'],sha256=load(OUT/'last-public-source-captures.json')[0]['sha256'],result='멍게8개·청상추3장·두반장3큰술·맛간장1작은술. 정확 식용 g 및 해당 제품 영양 연결 부족'),
        dict(source='경기도교육청 자율선택급식 가이드',url=load(OUT/'last-public-source-captures.json')[1]['url'],sha256=load(OUT/'last-public-source-captures.json')[1]['sha256'],result='84쪽 전체 텍스트 검색 및 해당 48·63·80쪽 확인. 햄버거용 샐러드·달래국·건파래볶음으로 남은 양상추샐러드·달래오이무침·생 파래무침과 음식/방법 다름; 두 선택 메뉴 공동표의 양을 임의 분리하지 않음'),
        dict(source='KDCA 일반 소금 및 농촌진흥청 공공 영양 DB',url='https://www.data.go.kr/data/15100064/standard.do?colCondition=DATA_CD&searchKeyword1=R&limit=10000',sha256='8289abf0a64b317f76b37549e63200206a0fcb322ab6865c41830836e9c64572',result='소금 정확 generic 이름·100g·농촌진흥청 출처 R318-020000000-0000. 해당 소금 행만 영양 연결; 조미료/특정 제품/품종은 추측 금지')]
    save('research-route-audit.json',dict(checkedAt='2026-10-05',priorRoutes=load(PRIOR/'research-route-audit.json'),newRoutes=routes,acquiredReferences=len(refs),sourceKinds=dict(collections.Counter(r['compositionKind'] for r in refs)),surveyGroupsReviewed=613,menuzenCatalogCandidates=3250,additionalMenuzenCaptures=sum(r['recipeId'].startswith('MENUZEN-') for r in refs)-295))
    remaining=[]
    for s in states:
        if s['appCompleteAvailable']:continue
        ds=byrecipe[s['recipeId']];q=queue[s['recipeId']]
        issues=[dict(ingredient=d['ingredient'],originalSpan=d['originalSpan'],failureReason=d['identityReason'] if d['identityStatus']!='LINKED' else d['unitReason'],status=d['status'],amountGrams=d['amountGrams'],unitStatus=d['unitStatus'],candidates=d.get('candidates',[]),sourceUrl=d['sourceUrl'],sourceSha256=d['sourceSha256'],nutritionProvenance=d.get('nutritionProvenance')) for d in ds if d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')]
        cs=q['candidates']
        remaining.append(dict(recipeId=s['recipeId'],name=s['name'],state=s['state'],originalComplete=s['originalComplete'],targetFoodIds=s['targetFoodIds'],failureReason=q['reason'],unresolvedIngredients=issues,originalSource=dict(url=ds[0]['sourceUrl'],sha256=ds[0]['sourceSha256']) if ds else dict(url=q['sourceUrl']),referencesChecked=[c|dict(unresolvedIngredients=[dict(ingredient=i['ingredient'],amountGrams=i['amountGrams']) for i in byref[c['recipeId']]['inputs'] if not i.get('nutrient')]) for c in cs],additionalSourceStatus='현재 확인한 공개 자료에서 안전한 연결 근거 없음. 원자료의 특정 상태·제품 또는 동일 행 중량 증거가 추가되면 재검토 가능' if s['targetFoodIds'] else '보호 대상 67,354 식품에서 동일 음식 foodId 미확인. 다른 음식으로 대체하지 않음. 새 음식 추가는 이번 작업 범위 밖',furtherKnownCompleteMatchingSourceAvailable=False,researchRouteArtifact='research-route-audit.json',wholeInternetExhaustionClaim=False))
    assert len(remaining)==516-progress['appComplete']
    save('remaining-foods.json',remaining)
    new=[q for q in queue.values() if q['completeInApp']]
    assert len(new)==progress['newCompleteFoods']
    save('new-complete-foods.json',new)
    institutions=[]
    for institution in sorted({r['sourceInstitution'] for r in refs}|{'식품의약품안전처'}):
        rs=[r for r in refs if r['sourceInstitution']==institution]
        chosen=[q for q in new if not next(s for s in states if s['recipeId']==q['recipeId'])['originalComplete'] and byref.get(next(s.get('referenceId') for s in states if s['recipeId']==q['recipeId']),{}).get('sourceInstitution')==institution]
        if institution=='식품의약품안전처':chosen=[q for q in new if next(s for s in states if s['recipeId']==q['recipeId'])['originalComplete']]
        originalById={r['recipeId']:r for r in load(OUT/'additional-original-compositions.json')}
        count=sum(len(originalById[q['recipeId']]['inputs']) if institution=='식품의약품안전처' else len(byref[next(s['referenceId'] for s in states if s['recipeId']==q['recipeId'])]['inputs']) for q in chosen)
        institutions.append(dict(institution=institution,acquiredReferences=len(rs),newAppCompleteRecipeEntries=len(chosen),resolvedOriginalIngredients=0,ingredientCountInSelectedCompleteCompositions=count,note='음식 수는 원본 recipe entry 기준. 선택 구성 재료 수는 원본 새 해결 수가 아님'))
    institutions.append(dict(institution='농촌진흥청 농사로 원본',acquiredReferences=305,newAppCompleteRecipeEntries=0,resolvedOriginalIngredients=2,ingredientCountInSelectedCompleteCompositions=0,note='전체 원본은 MFDS211·RDA305 합계516. 새 직접 g 2행의 근거 기관은 농사로; 원본 complete 3개 foodId 연결은 위 MFDS에 집계'))
    save('source-institution-summary.json',institutions)
    checks={}
    with zipfile.ZipFile(ROOT/'app/build/outputs/apk/qa/app-qa.apk') as apk:
        for name in ['food_items.csv','product_items.csv','franchise_official_items.csv','recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv']:
            data=(ROOT/'app/src/main/assets/fooddata'/name).read_bytes();assert apk.read('assets/fooddata/'+name)==data
            checks[name]=hashlib.sha256(data).hexdigest()
    protected={'food_items.csv':'ccf6a9b2322ad5e75fe29f13ffb7c4bf719c2cca5cd3b459f104d659231261b4','product_items.csv':'3f659d78f279b65115e17d8194bd686560bca018dea701aaa1a9585261bbe18e','franchise_official_items.csv':'4a44591f8d8c6ce8276bee5449e90ced1ca513555c2920718ba9722e5ada06e3','recipe_ingredient_estimates.csv':'3dcb21e2339a2e3fd7defdbd814b946ac6c85eb8d9c9d5a3224cb22c86d72522'}
    assert all(checks[n]==h for n,h in protected.items())
    save('packaged-assets-verification.json',dict(status='PASS',commonSource='app/src/main/assets/fooddata',qaApplicationId='com.example.healthcare.qa',hashes=checks,protectedAssetsByteIdentical=True,productionOperations=0))
    print(json.dumps(dict(remaining=len(remaining),newComplete=len(new),institutions=institutions,packagedAssets='PASS'),ensure_ascii=False))
if __name__=='__main__':run()
