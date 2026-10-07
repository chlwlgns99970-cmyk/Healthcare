"""Current followup report: distinguish completed code work from device checks."""
import collections,json,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-final-residual-followup';FINAL=ROOT/'data-source/recipe-final-residual';QA=ROOT/'app/build/recipe-final-residual-followup'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,x):(OUT/n).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def run():
    current=load(FINAL/'progress.json');before=load(OUT/'before-progress.json');new=load(OUT/'newly-reviewed-mappings.json')
    refs=load(FINAL/'validated-reference-compositions.json');states=load(FINAL/'recipe-final-states.json')
    remaining=load(FINAL/'remaining-foods.json');ingredients=load(FINAL/'original-ingredient-progress.json')
    assert current['appComplete']-before['appComplete']==len(new)==20
    assert len(remaining)==516-current['appComplete']
    units=[]
    for name in ['FullRecipeReferenceTest','RecipeMenuCompletionTest','SearchRecipeFollowupTest']:
        t=ET.parse(ROOT/f'app/build/test-results/testQaUnitTest/TEST-com.example.healthcare.{name}.xml').getroot()
        assert t.attrib['failures']=='0' and t.attrib['errors']=='0';units.append(dict(name=name,tests=int(t.attrib['tests'])))
    assert sum(t['tests'] for t in units)==12
    assert 'Ran 25 tests' in (QA/'data-tests.log').read_text(encoding='utf-8-sig') and '\nOK' in (QA/'data-tests.log').read_text(encoding='utf-8-sig')
    ui=QA/'samsung-followup.log';preservation=QA/'preservation-verify.json'
    uiPass=ui.exists() and 'OK (20 tests)' in ui.read_text(encoding='utf-8-sig')
    preserved=preservation.exists() and load(preservation)['status']=='PASS'
    assert not uiPass or preserved, 'Final report requires restoring QA private data after tests'
    status='PARTIAL';device='PASS: 추가20개 targeted Samsung 검증 및 사용자데이터 복원 완료' if uiPass else 'PENDING: adb 기기 없음. 이번 추가20개의 Samsung 설치·화면 검증 미실행'
    summary=dict(status=status,beforeAppComplete=before['appComplete'],appComplete=current['appComplete'],total=516,added=20,states=current['states'],newTotalFromOriginalRequest=current['newCompleteFoods'],remaining=len(remaining),dataTests=25,unitTests=units,samsungFollowup='PASS' if uiPass else 'PENDING',samsungTestCount=20 if uiPass else 0,privateDataRestored=preserved,DBVersion=8,migrations=0,productionOperations=0)
    save('verification-summary.json',summary)
    sourceRules=[dict(ingredient='오이, 개량종, 생것',code='R106-148010001-0000',name='오이_개량종_생것',basis='같은 재료·개량종·생것·100g. 다다기 품종 추정 없음',sourceArtifact='data-source/recipe-linkage-strategy/public-raw-nutrients.json'),dict(ingredient='키위, 생것',code='MFDS-LEGACY-2096',name='키위, 생것',basis='식약처 공개 기존 DB exact generic 이름·생것·100g. 골드/그린 추정 없음',sourceArtifact='data-source/recipe-linkage-strategy/mfds-legacy-nutrients-rows.json'),dict(ingredient='조미료',code='MFDS-LEGACY-4574',name='조미료',basis='식약처 공개 기존 DB 동일 generic 이름·조미료류·100g. 특정 제조사/MSG/추출액으로 대체 없음',sourceArtifact='data-source/recipe-linkage-strategy/mfds-legacy-nutrients-rows.json')]
    save('nutrition-fallback-evidence.json',sourceRules)
    names='\n'.join(f"- {m['originalName']} → {m['selectedName']} ({m['selectedId']})" for m in new)
    pending=sum(bool(r['targetFoodIds']) for r in remaining)
    report=f'''# 연속 작업 결과 · 2026-10-05

1. STATUS: **{status}**. 이번 추가20개 반영. 전체516개 완전 해결로 보고하지 않음.

2. 전체516개. 이번 연속 작업은 직전 잔여155개를 재검토.

3. Before(직전): ORIGINAL41 / REFERENCE329 / PARTIAL140 / UNRESOLVED6. 실제앱361/516.

4. After: ORIGINAL41 / REFERENCE349 / PARTIAL120 / UNRESOLVED6. 실제앱**381/516**. 원본complete중foodId없는9개는 실제앱분모에서 제외.

5. 이번20개 추가. 최초요청262개 대비누적119개 추가 =원본foodId연결3개+전체reference116개. 최종reference asset5797행.

6. 원본complete foodId미연결9개 유지. 새 음식생성·보호67354식품변경 없음.

7. 최초미출판246개 현재분류: A중복151 / B출판21 / CfoodId없음27 / D불완전47. 전체[분류](../recipe-final-residual/unpublished-reference-dispositions.json).

8. Ingredient linked2384 / excluded154 / unresolved1720. 이번연속작업에서 원본ingredient의 양이나nutrition을 새로 덮어쓰지 않음.

9. 원본잔여원인: identity850 / unit305 / amount563 / nutrition1 / source1. 별도공식참고와 원본누락해결을 혼동하지 않음.

10. 기존계량충돌213건: 이전해결1 / 미해결212 유지. 이번에 평균·다수결·새 생활단위환산 추가 없음.

11. 김밥 기존원본부분/햄김밥전체참고/공식140kcal 구분 유지. 관련Unit테스트 재실행PASS.

12. 이번새 ORIGINAL_COMPLETE: 0개. 직전원본foodId연결3개 유지.

13. 이번새 REFERENCE_COMPLETE20개 전체목록:

{names}

정확foodIds·배합종류·원문hash·연결이유는 [20개검토결정](newly-reviewed-mappings.json).

14. 현재미완료**135개**: foodId미확인{len(remaining)-pending}개, foodId있지만안전한전체배합없음{pending}개. [음식별원문·미해결재료·실패이유·추가근거상태](../recipe-final-residual/remaining-foods.json). 새로운공식배합자료나 정확원료상태가 확보되면 추가검토 가능하며 모든인터넷자료소진을 주장하지 않음.

15. 메뉴젠공식12개조회중 기존1개캐시재사용, 신규11개capture. 전체확보reference974개=메뉴젠358+KDCA613+MFDS2+학술1. 개피떡두자료는 완성떡단일행으로 재료배합분해가 없어불완전 처리. 조사평균의 오이개량종/생키위/일반조미료는 정확같은 qualified/generic 이름의공개100g nutrition만 적용. [영양근거](nutrition-fallback-evidence.json), [기관별누적집계](../recipe-final-residual/source-institution-summary.json).

16. 이번실제수정: 공통`official_recipe_reference_estimates.csv`, `build_full_recipe_references.py`, `finish_recipe_reference_mapping.py`, `test_final_recipe_residuals.py`, `qa_recipe_final_residual_preservation.py`, `final_recipe_evidence_artifacts.py`. 추가`prepare_recipe_followup_qa.py`, `write_recipe_followup_report.py`, `FollowupRecipeResidualSamsungTest.kt`, followup자료·테스트artifact. Home/검색/추천/DB/기록/프랜차이즈/updater 제품코드 수정0.

17. Data25 PASS, 관련Unit12 PASS, QA빌드PASS. 이전5106행완전보존·이번20개실제완전배합행출판·최종QA APK공통asset일치 확인. [데이터로그]({(QA/'data-tests.log').as_posix()}), [Unit로그]({(QA/'unit-tests.log').as_posix()}), [패키지검증](../recipe-final-residual/packaged-assets-verification.json).

18. Samsung: **{device}**. 직전361개APK관련22PASS는 이번381개APK의새20개실행결과로 재사용하여 주장하지 않음. 새test는360dp/font1.30에서각음식의qualified명·재료g/kcal·조사평균안내 확인.

19. DB8 / migration0. {'새QA백업 복원·검증PASS, 기존16개privatefile byte동일·table변경0·제품identity동일.' if preserved else '이번연속작업에서는 Samsung 연결이 없어 설치/사용자데이터접근0. 이전작업복원16파일PASS 결과는 유지. 연결후새백업이 먼저필요.'}

20. 기존food_items/product_items/franchise_official_items/primaryrecipeasset hash동일. 공식kcal계산식/67354음식값변경0.

21. QUESTION_REQUIRED: {'없음' if uiPass else 'Samsung USB재연결/디버깅허용 답변 대기. 코드작업과데이터검증은 완료; 기기화면검증은 미완료'}.

22. Production versionCode7/versionName1.0.6. 제품설치0/초기화0/배포0. Release build0/버전변경0/Supabase·Vercel변경0.
'''
    (OUT/'FINAL_REPORT.md').write_text(report,encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))
if __name__=='__main__':run()
