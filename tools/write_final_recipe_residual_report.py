"""Write the requested 22-item final report only after preservation passes."""
import collections,json,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-final-residual';QA=ROOT/'app/build/recipe-final-residual'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def run():
    p=load(OUT/'progress.json');i=load(OUT/'original-ingredient-progress.json');v=load(QA/'preservation-verify.json')
    assert v['status']=='PASS' and v['allAllowlistedPrivateFileBytesIdentical'] and v['productIdentityUnchanged']
    assert 'OK (22 tests)' in (QA/'samsung-final-current-assets.log').read_text(encoding='utf-8-sig')
    assert 'Ran 23 tests' in (QA/'data-tests.log').read_text(encoding='utf-8-sig') and '\nOK' in (QA/'data-tests.log').read_text(encoding='utf-8-sig')
    unit=[]
    for name in ['FullRecipeReferenceTest','RecipeMenuCompletionTest','SearchRecipeFollowupTest']:
        t=ET.parse(ROOT/f'app/build/test-results/testQaUnitTest/TEST-com.example.healthcare.{name}.xml').getroot()
        assert t.attrib['failures']=='0' and t.attrib['errors']=='0'
        unit.append(dict(name=name,tests=int(t.attrib['tests'])))
    assert sum(t['tests'] for t in unit)==12
    new=load(OUT/'new-complete-foods.json');states={s['recipeId']:s for s in load(OUT/'recipe-final-states.json')}
    original=[q for q in new if states[q['recipeId']]['originalComplete']];ref=[q for q in new if not states[q['recipeId']]['originalComplete']]
    assert len(original)==3 and len(ref)==96
    originalList='、'.join(q['name'] for q in original)
    referenceList='\n'.join(f"- {q['name']} ({q['recipeId']}) → {states[q['recipeId']]['referenceId']}" for q in ref)
    institutions=load(OUT/'source-institution-summary.json')
    inst='\n'.join(f"- {x['institution']}: 확보 {x['acquiredReferences']} 구성/원본, 새 앱 연결 {x['newAppCompleteRecipeEntries']}개, 원본 재료 새 해결 {x['resolvedOriginalIngredients']}행, 선택 완전 구성 재료 {x['ingredientCountInSelectedCompleteCompositions']}행" for x in institutions)
    text=f'''# 최종 잔여 레시피 작업 보고 · 2026-10-05

1. STATUS: **PARTIAL**. 254개 전체 조사·재평가 후 안전하게 앱 완전 구성 99개 추가. 155개는 근거 또는 동일 foodId가 부족하여 완전 상태로 올리지 않음.

2. 전체 recipe: **516개**. recipe entry 기준이며 같은 음식명 여러 원본은 따로 집계.

3. Before: ORIGINAL_COMPLETE41 / REFERENCE_COMPLETE233 / PARTIAL233 / UNRESOLVED9. 실제 앱 완전 구성 **262/516**.

4. After: ORIGINAL_COMPLETE41 / REFERENCE_COMPLETE329 / PARTIAL140 / UNRESOLVED6. 데이터 완전370개 중 foodId 없는 원본9개를 제외하면 실제 앱 **361/516**.

5. 새 실제 앱 완전 연결: **99개** = 기존 원본 complete에 정확 foodId 연결3개 + 별도 전체 reference 연결96개. 기존 ORIGINAL_COMPLETE 총계는 증가시키지 않음.

6. foodId 미연결 원본 complete: **12→9**. 딸기바나나연두부쉐이크, 배오이무침, 감자채튀김, 딸기롤샌드위치, 연근치즈구이, 도토리묵밥, 잣스프, 봄동겉절이, 송이버섯구이. 보호 대상 food 데이터에서 다른 음식으로 대체하지 않음.

7. 기존 미출판 reference246개 전수 분류: A중복/불필요150, B실제출판16, C완전하지만 검증된foodId없음32, D불완전48. [전체246건](unpublished-reference-dispositions.json). 새로 확보한 참고 자료는 별도로 감사하며 이 분모에 섞지 않음.

8. Ingredient4258행: linked **2382→2384**, excluded **154→154**, unresolved **1722→1720**. 원본의 직접 g: 청국장찌개 두부1/2모(250g), 더덕8뿌리(100g). 해당 원본 행에만 적용; 모·뿌리 공통 환산 변경 없음.

9. 원인별 Before→After: identity850→850 / unit307→305 / amount563→563 / nutrition1→1 / recipe source1→1. 기존1722행 모두 문맥·양·단위·nutrition·전체reference 연결을 재평가하여 [행별근거](residual-row-reassessments.json)에 보존.

10. 기존 계량 충돌213건 전수 비교: 해결1 / 미해결212. 다른 레시피의 크기·다짐·측정법·serving 정의를 같다고 가정하지 않음. [전체비교](portion-conflict-reassessments.json).

11. 김밥: 원본18linked/1제외/18미해결의 부분 구성 유지. MENUZEN-D016004 햄김밥9재료·276g의 완전 참고 구성을 별도 표시. 공식 음식 kcal140 유지. 원본 상태·참고 배합·기록 kcal를 혼합하지 않음. 실제 입력 및 in-memory Room 저장 테스트 통과.

12. 새 앱 연결 ORIGINAL_COMPLETE 목록: **{originalList}**. 이미 완전하던 원본41개 중3개의 동일 음식 foodId만 새 연결. 원본 재료량을 재작성하지 않음.

13. 새 REFERENCE_COMPLETE 전체96개 목록 (qualified reference 이름·foodId·source는 [99개연결artifact](new-complete-foods.json), [연결결정](food-mapping-decisions.json)):

{referenceList}

14. 미완료 전체 **155개**: [음식별155건](remaining-foods.json)에 음식명·각미해결재료·원문량·실패이유·sourceURL/hash·참고후보·추가근거상태 저장. 145개는 동일 음식foodId가 미확인, 10개는 foodId가 있으나 완전한 안전 배합이 없음. 후자는 묵밥, 두부김치, 양상추샐러드, 주꾸미볶음, 달래오이무침, 두부김치국, 멍게비빔밥, 백합죽3개 원본. PARTIAL140+UNRESOLVED6 외에 원본complete미연결9개도 포함. 인터넷의 모든 자료를 소진했다고 주장하지 않음.

15. 공식·공공·학술 자료: 963개 확보참고구성 = MENUZEN347 + KDCA613 + MFDS2 + 학술1. 메뉴젠은 이번 요청에서52건 추가 capture. RDA10.4의3366영양항목, USDA7793, MFDS공개3648/기존4906도 재료후보로 검토. KDCA는 조사평균으로만 표시. [기관별집계](source-institution-summary.json), [자료별판정](research-route-audit.json), [마지막공개자료capture](last-public-source-captures.json).

{inst}

원본은 MFDS211/RDA305 합계516. source기관별 음식 수 합계99이며 선택구성 재료수와 새 원본 ingredient해결 수를 구분함. 일반 소금은 공공 농촌진흥청 DB의 정확한 같은 이름·100g 항목만 사용. 파래무침은 무 포함 조사평균 명칭을 보존.

16. 실제 수정/추가 파일 (이전310개 working tree 변경은 보존):

- `app/src/main/assets/fooddata/official_recipe_reference_estimates.csv`: 기존3785행 모두보존, 최종5106행. 원본완전3개와 독립전체참고 및 원본직접g부분구성2개 추가.
- `tools/build_full_recipe_references.py`: KDCA613개 전수검토, 정확generic소금 공공nutrition연결.
- `tools/finish_recipe_reference_mapping.py`, `tools/audit_final_recipe_residuals.py`: 전체254/1722/213 검토·연결·출판.
- `tools/test_final_recipe_residuals.py`: 근거·배합·공식kcal·기존행보존23검증.
- `tools/qa_recipe_final_residual_preservation.py`, `tools/pull_final_recipe_qa_images.py`: QA백업/복원/검증 및 지정cache화면읽기.
- `tools/check_final_public_sources.py`, `tools/final_recipe_evidence_artifacts.py`, `tools/write_final_recipe_residual_report.py`: 공개자료capture 및 최종artifact.
- `app/src/androidTest/java/com/example/healthcare/FinalRecipeResidualSamsungTest.kt`, `FinalRecipeSearchSamsungTest.kt`:20구성+2실제검색 targeted 검증.
- `data-source/recipe-final-residual/`: immutablebaseline, 판단·잔여목록·집계·sourcecapture·최종보고.
- `data-source/recipe-full-reference/`: 새 공식capture 및 파이프라인 중간 생성audit. 최종앱출판 판정은 `recipe-final-residual`과 실제asset이 기준.

제품 공통 Kotlin UI/계산/DB 코드, Home·검색·프랜차이즈·추천·updater·기록·통계는 수정하지 않음.

17. 테스트: 최종data23 PASS, 관련Unit12 PASS, QA빌드 PASS. 기존자료와 현재APK asset byte일치 PASS. 예전검색test는 현재 접힌동일음식자료 및 오래된조건 때문에 실패기록을 남겼고, 현재 실제검색흐름으로 수정한test는 PASS. 전체회귀/Lint/Release build 미실행.

18. Samsung SM-S948N Android16/API36: 최종APK **22/22 PASS** (재료구성20 + 현재MainActivity검색2). 360dp·font1.30 참고카드, 원본/참고/부분/조사평균, 신규연결, 공식kcal기록·저장, 신라면수량2 및 참치김밥검색 검증. 테스트는 개별대표구성 targeted검증이며516화면 전수기기검증으로 주장하지 않음. [최종로그]({(QA/'samsung-final-current-assets.log').as_posix()}), [캡처폴더]({(QA/'screenshots').as_posix()}).

19. DB: version8 / migration0. QA백업16개기존privatefile 복원후 **전부 byte동일**, 모든table변경0, QAfirstInstallTime동일. 제품은package정보만읽기조회·identity동일. [복원검증]({(QA/'preservation-verify.json').as_posix()}).

20. 공식 kcal보존: food_items/product_items/franchise_official_items/primaryrecipeasset 작업전hash와동일. 총67354식품·공식영양값 불변. 재료예상합계는 기록열량에 쓰지 않음. [패키지asset검증](packaged-assets-verification.json).

21. QUESTION_REQUIRED: 없음. 원문이 없는 양·품종·특정제품을 사용자 승인으로 추정하지 않음. 추가로 특정 원자료가 확보되면 잔여artifact의 조건에 따라 재평가 가능.

22. Production: **versionCode7 / versionName1.0.6 / 제품설치0 / 초기화0 / 배포0**. 버전변경·GitHubRelease·Supabase·Vercel 변경0. QA비파괴install-r-t만 수행하고 사용자데이터 복원확인까지 완료.
'''
    (OUT/'FINAL_REPORT.md').write_text(text,encoding='utf-8')
    (OUT/'verification-summary.json').write_text(json.dumps(dict(status='PARTIAL',appComplete=361,total=516,newComplete=99,dataTests=23,unitTests=unit,samsungTests=22,DBVersion=8,migrations=0,privateFilesPreserved=16,productionOperations=0),ensure_ascii=False,indent=2),encoding='utf-8')
    print('Final 22-item report written; restoration and final Samsung assets verified.')
if __name__=='__main__':run()
