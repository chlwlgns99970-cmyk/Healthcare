"""Write the final evidence report from generated audits and completed QA logs."""
import collections
import csv
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from urllib.parse import urlparse

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-full-reference'
QA=ROOT/'app/build/recipe-full-reference'
def load(p): return json.loads(p.read_text(encoding='utf-8'))
def save(name,value): (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def run():
    refs=load(OUT/'reference-composition-audit.json')
    publications=load(OUT/'reference-publication-audit.json')
    states=load(OUT/'recipe-final-states.json')
    progress=load(OUT/'reference-progress.json')
    residual=load(OUT/'residual-progress.json')
    preserved=load(QA/'preservation-verify.json')
    assert preserved['status']=='PASS' and preserved['allAllowlistedPrivateFileBytesIdentical']
    assert 'OK (10 tests)' in (QA/'samsung-final-targeted.log').read_text(encoding='utf-8-sig')
    assert 'OK (1 test)' in (QA/'samsung-unit-conversion.log').read_text(encoding='utf-8-sig')
    tests=[]
    for name in ['FullRecipeReferenceTest','RecipeMenuCompletionTest','SearchRecipeFollowupTest']:
        t=ET.parse(ROOT/f'app/build/test-results/testQaUnitTest/TEST-com.example.healthcare.{name}.xml').getroot()
        assert t.attrib['failures']=='0' and t.attrib['errors']=='0'
        tests.append(dict(name=name,count=int(t.attrib['tests']),status='PASS'))
    assert sum(t['count'] for t in tests)==12
    asset_hashes={}
    with zipfile.ZipFile(ROOT/'app/build/outputs/apk/qa/app-qa.apk') as apk:
        for name in ['recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv']:
            data=(ROOT/'app/src/main/assets/fooddata'/name).read_bytes()
            assert apk.read('assets/fooddata/'+name)==data
            asset_hashes[name]=hashlib.sha256(data).hexdigest()
    institutions=[]
    for name in sorted({r['sourceInstitution'] for r in refs}):
        acquired=[r for r in refs if r['sourceInstitution']==name]
        ids={r['recipeId'] for r in acquired}
        actual=[p for p in publications if p['recipeId'] in ids and p['foodIds']]
        institutions.append(dict(institution=name,acquiredCompositions=len(acquired),
            publishedCompositions=len(actual),publishedCompleteCompositions=sum(p['complete'] for p in actual),
            linkedIngredientsBeforeFoodIdExpansion=sum(p['linkedCount'] for p in actual)))
    save('source-institution-summary.json',institutions)
    captures=load(OUT/'source-captures.json')
    save('research-route-audit.json',dict(
        checkedAt='2026-10-05',residualRowsReviewed=1747,
        originalContext='모든 원본의 캐시 HTML·조리 문맥·원료 후보와 source hash를 residual-row-dispositions.json에 기록',
        bulkSources=['RDA 10.4 3366 식품','USDA SR Legacy 7793 추가 식품','MFDS 공개 bulk 3648 유효 식품 및 기존 4906 식품',
            'KDCA 613개 조사 평균 구성','메뉴젠 전체 3250 일반 음식 및 가공·제철 목록','MFDS 공식 조리책자 1–7 및 계량 감사'],
        additionalRoutes=[
            dict(source='교육기관 배포 MFDS 2017 외식 영양성분 자료집 제5권',
                 url='https://mhc.cbe.go.kr/upload/na_old/dc520bb5-50ad-48b0-94dc-cf1475f299ba/2018/15226582081.pdf',
                 sha256='fb3d941dfb4f5822f1525db3321bb9a6921636068ee5dc66e272f7b239da7a7b',
                 result='54쪽 전체 텍스트 확인. 완성 음식 44종 영양값만 있고 조리 배합 없음; 기존 kcal 교체 또는 역산에 사용하지 않음'),
            dict(source='한국전통지식포탈 농촌진흥청 닭찜',
                 url='https://koreantk.com/ktkp2014/food/food-view.view?foodCd=TF10001936&tempLang=ko',
                 result='전체 닭 1.3kg, 육수 ml, 간장 컵 등. 뼈 포함 식용량·정확 제품·g 환산 미확정. 원본/다른 레시피에 양을 혼합하지 않음'),
            dict(source='NCHFP USDA 잼 공식 안내·Guide 7 및 대학 학술 딸기잼',
                 result='논문 중량비 부분 구성 확보. 냉동 딸기 첨가물·펙틴 특정 제품 nutrition과 일반 딸기잼 foodId 미확정. crushed와 pureed의 컵 중량은 동일시하지 않음'),
            dict(source='일본 MEXT 식품 DB·FAO·먹물 학술 문헌',
                 result='주꾸미 먹물 자체의 종·에너지·기준량이 맞는 직접 값 미확보. 전체 개체/다른 종/단백질 범위로 대체하지 않음'),
            dict(source='제조사·국립국어원',result='임연수어/임연수 및 마늘종/마늘쫑 명칭 근거만 적용; 제품 kcal를 일반 음식에 복사하지 않음'),
        ],captureDomains=dict(collections.Counter(urlparse(c['url']).netloc for c in captures)),
        captureFailures=[c for c in captures if c['status']!='CAPTURED']))
    newly=[s for s in states if s['state']=='REFERENCE_COMPLETE']
    with (OUT/'newly-complete-recipes.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        fields=['recipeId','name','referenceId','referenceName','compositionKind','sourceInstitution','sourceUrl']
        writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader()
        byid={r['recipeId']:r for r in refs}
        for s in newly:
            r=byid[s['referenceId']]
            writer.writerow(dict(recipeId=s['recipeId'],name=s['name'],referenceId=r['recipeId'],referenceName=r['name'],
                compositionKind=r['compositionKind'],sourceInstitution=r['sourceInstitution'],sourceUrl=r['sourceUrl']))
    names=sorted({s['name'] for s in newly})
    gap=residual['primaryGapsAfter']
    lines=[
        '1. STATUS: PARTIAL',
        '2. Recipe 전체: 516개.',
        '3. Before: original complete 41 / partial 409 / unlinkable 66.',
        f"4. After: ORIGINAL_COMPLETE 41 / REFERENCE_COMPLETE {progress['states']['REFERENCE_COMPLETE']} / PARTIAL {progress['states']['PARTIAL']} / UNRESOLVED {progress['states']['UNRESOLVED']}.",
        '5. 데이터 완전 상태 41+233=274/516. 실제 앱 완전 구성 제공 262/516. 원본 complete 중 12개는 현재 정확한 일반 FoodItem 연결 대상이 없어 차이가 남는다. 기존 음식 식별자를 임의로 추가하거나 유사 음식에 연결하지 않았다.',
        '6. Ingredient: linked 2357→2382 / excluded 154→154 / unresolved 1747→1722. 새 원본 연결 25행 중 선택된 원본 composition asset에 실제 추가된 것은 3개 레시피·6개 foodId 행(모두 설탕)이다. 나머지 19행은 판정 기록에는 연결됐지만 현재 선택된 원본 composition으로 출판되지 않았다.',
        f"7. 원인별: identity 850→{gap['identity']} / unit 332→{gap['unit']} / amount 563→{gap['amount']} / nutrition 1→1 / recipe source 1→1. 기본 원인 분류이며 identity와 unit 같은 복수 축을 중복 집계하지 않았다.",
        '8. 공식 참고 구성: 확보 382 / 출판 136 / foodId 468 / asset 3785행. 미출판 246구성은 중복 후보·정확한 음식 대상 없음 등의 사유로 선택하지 않았다. 미출판을 전부 영양 연결 실패로 계산하지 않는다. 원래 미해결 1747행 가운데 928행은 원본 그대로 미해결일 수 있지만 별도 완전 참고 구성으로 해당 음식 설명이 제공된다.',
        '9. 기존 자료: 공식 중량 23/23행을 두 독립 카드의 전체 재료 문구에 반영, nutrition 9/9행 예상 kcal로 표시. 238개 충돌 전부 재검토; 설탕 스푼 25행 해결 / 213행 보류. 동일 재료의 1작은술 4.2g, 5mL/15mL 정의에 따른 큰술 12.6g 참고 환산이며 전역 spoon/cup 환산을 하지 않았다.',
        '10. 김밥: 원본 linked18 / excluded1 / unresolved18, original complete 아님. 메뉴젠 김밥(햄) 9재료·276g의 별도 REFERENCE_COMPLETE. 원본 부분 구성, 메뉴젠 완전 구성, MFDS 두 부분 구성을 카드별로 분리하고 변형명·기관·출처·전체 분량을 유지한다. 밥210g 약319kcal 등 재료별 예상값을 실제 표시한다.',
        f"11. 새 완전 상태 {len(newly)}개 원본 recipe / 중복 제거 음식명 {len(names)}개. 전체 목록: newly-complete-recipes.csv. 별도 원본 complete 증가는 0개.\n음식명: "+', '.join(names),
        '12. 기관별 수집 구성 / 실제 출판 구성 / 출판 완전 구성 / 중복 foodId 확장 전 linked 재료:',
    ]
    lines.extend(f"   {r['institution']}: {r['acquiredCompositions']} / {r['publishedCompositions']} / {r['publishedCompleteCompositions']} / {r['linkedIngredientsBeforeFoodIdExpansion']}" for r in institutions)
    lines += [
        '   영양·계량 source: RDA, MFDS/K-FIND, USDA. source-captures.json에 raw·SHA·URL·확인일, source-institution-summary.json에 구성 기관별 수치, research-route-audit.json에 추가 조사 결과를 기록했다. KDCA 조사 평균은 조리 레시피와 별도 표시한다.',
        '13. unresolved: remaining-unresolved-ingredients.json 1722행. 각 음식·재료·원문·문맥·후보 nutrition·계량 검토·확인 source·실패 원인·참고 구성 연결 여부를 저장했다. 516개 전체 판정은 recipe-final-states.json. 현재 확보 근거로 확정할 수 없는 원본 품종/상태/제품, 불명확한 단위·소량, 주꾸미 먹물, 딸기잼 원본 배합은 추정하지 않았다.',
        '14. 이번 변경: RecipeCaloriePolicy.kt(독립 composition 조회), FoodMetadataStore.kt(공통 asset 2개 로드), AddRecordScreen.kt의 RecipeReferenceCard(기관·original/reference/survey 구분), 공통 recipe asset 2개, FullRecipeReferenceTest.kt, FullRecipeReferenceSamsungTest.kt, tools/*full_recipe* 및 collect_menuzen_recipe_details.py와 생성 data-source/recipe-full-reference/*. 기존 dirty 작업은 유지했다. DB/Home/추천/음식 공식 nutrition 데이터는 변경하지 않았다.',
        '15. Data 16 PASS / 관련 Unit 12 PASS / Samsung 10+설탕 환산1=11 PASS. SM-S948N Android16/API36, 360dp·720dp·font1.30 참고 카드 확인. 실제 AddRecord 화면140kcal·in-memory Room 기록140kcal 보존 확인. 최종 QA APK 내부 두 asset은 현재 source 파일과 바이트 동일. screenshots 및 app/build/recipe-full-reference 로그에 증거 저장. 앞선 통화 방해 실행의 실패를 PASS로 재분류하지 않았고 최종 실행으로 확인했다.',
        '16. DB version8 / migration0. QA 개인 파일16개 원본 복원 후 전체 바이트 동일, DB 변경 tables0, QA firstInstallTime 유지, 제품 package identity 동일. 제품 private DB를 읽거나 수정하지 않았다.',
        '17. 기존 공식 kcal·탄단지·기준량: 보호된 3개 food/product/franchise asset SHA 동일, 기존 원본 asset 1295행의 gram/nutrition/kcal 기준값 동일. 재료 합으로 FoodItem/MealRecord/추천/통계 kcal를 덮어쓰지 않는다.',
        '18. QUESTION_REQUIRED: 없음. 정확한 추가 source가 필요한 항목은 unresolved 근거로 기록했으며 API key 없이 공개 자료를 사용했다.',
        '19. Production: versionCode7 / versionName1.0.6. 제품 설치0 / 초기화0 / 배포0 / Release build0. QA만 install -r -t.',
    ]
    (OUT/'FINAL_REPORT.md').write_text('\n\n'.join(lines)+'\n',encoding='utf-8')
    save('final-verification.json',dict(status='PARTIAL',recipeProgress=progress,residualProgress=residual,
        dataTests=16,unitTests=tests,samsungTests=11,samsungStatus='PASS',
        qaAssetSha256=asset_hashes,privateDataPreserved=True,productionActions=0))
    print(json.dumps(dict(status='PARTIAL',newCompleteRecipes=len(newly),uniqueNewFoodNames=len(names),appCoverage=262),ensure_ascii=False))

if __name__=='__main__':run()
