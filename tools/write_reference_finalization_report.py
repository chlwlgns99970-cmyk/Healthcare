"""One final 21-point report for the user's 361/516 baseline."""
import collections,hashlib,json,re,shutil,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-reference-finalization';FINAL=ROOT/'data-source/recipe-final-residual';QA=ROOT/'app/build/recipe-reference-finalization-ui'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,x):(OUT/n).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def link(label,p):return f'[{label}]({p.as_posix()})'
def run():
    progress=load(FINAL/'progress.json');new=load(OUT/'new-complete-foods-from-361.json');remaining=load(OUT/'remaining-97-foods.json')
    assert len(new)==progress['appComplete']-361 and len(remaining)==516-progress['appComplete']
    samsung=(QA/'samsung-final.log').read_text(encoding='utf-8-sig');assert 'OK (60 tests)' in samsung
    data=(ROOT/'app/build/reference-finalization-data-tests.log').read_text(encoding='utf-8-sig');assert '\nOK' in data
    count=int(re.search(r'Ran (\d+) tests',data)[1])
    units=[]
    for n in ['FullRecipeReferenceTest','RecipeMenuCompletionTest','SearchRecipeFollowupTest']:
        result=ET.parse(ROOT/f'app/build/test-results/testQaUnitTest/TEST-com.example.healthcare.{n}.xml').getroot()
        assert result.attrib['failures']=='0' and result.attrib['errors']=='0'
        units.append(dict(name=n,tests=int(result.attrib['tests']),failures=0))
    preservation=load(QA/'preservation-verify.json');assert preservation['status']=='PASS' and preservation['allAllowlistedPrivateFileBytesIdentical'] and preservation['productIdentityUnchanged']
    protected=load(FINAL/'packaged-assets-verification.json');assert protected['protectedAssetsByteIdentical']
    assert 'BUILD SUCCESSFUL' in (ROOT/'app/build/reference-finalization-ui-gradle.log').read_text(encoding='utf-8-sig')
    status='COMPLETE' if progress['appComplete']==516 else 'PARTIAL'
    source=load(OUT/'source-institution-finalization-summary.json')
    changed=['app/src/main/assets/fooddata/official_recipe_reference_estimates.csv','app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt',
        'tools/build_full_recipe_references.py','tools/finish_recipe_reference_mapping.py','tools/audit_reference_first_candidates.py','tools/collect_reference_finalization_sources.py',
        'tools/index_reference_finalization_documents.py','tools/write_reference_finalization_ledger.py','tools/prepare_reference_finalization_qa.py','tools/qa_recipe_final_residual_preservation.py',
        'tools/pull_reference_finalization_images.py','tools/test_final_recipe_residuals.py','tools/write_reference_finalization_report.py',
        'app/src/androidTest/java/com/example/healthcare/ReferenceFinalizationSamsungTest.kt','app/src/androidTest/java/com/example/healthcare/FinalRecipeResidualSamsungTest.kt',
        'app/src/androidTest/java/com/example/healthcare/FullRecipeReferenceSamsungTest.kt','data-source/recipe-full-reference/source-captures.json','data-source/recipe-full-reference/reference-composition-audit.json',
        'data-source/recipe-final-residual/validated-reference-compositions.json','data-source/recipe-final-residual/food-mapping-decisions.json','data-source/recipe-final-residual/additional-original-compositions.json',
        'data-source/recipe-final-residual/recipe-final-states.json','data-source/recipe-final-residual/progress.json','data-source/recipe-final-residual/residual-row-reassessments.json',
        'data-source/recipe-final-residual/portion-conflict-reassessments.json','data-source/recipe-final-residual/packaged-assets-verification.json']
    changed.extend(str(p.relative_to(ROOT)).replace('\\','/') for p in OUT.iterdir() if p.is_file())
    save('modified-files.json',dict(sourceAndEvidenceFiles=sorted(set(changed)),generatedVerificationDirectory=str(QA),preexistingWorkingTreeChangesPreserved=True,branch='main',head='320543ea8126455d32df3c7ceb2332ffbf4a50b6',note='작업 범위의 코드·asset·근거 파일. 전체 기존 Git dirty 파일을 이번 변경으로 귀속하지 않음. 원본 food/product/franchise/primary ingredient asset은 변경하지 않음.'))
    summary=dict(status=status,total=516,beforeAppComplete=361,appComplete=progress['appComplete'],added=len(new),remaining=len(remaining),states=progress['states'],originalCompleteNoFoodId=progress['originalCompleteNoApp'],
        originalIngredients=dict(total=4258,linked=2384,excluded=154,unresolved=1720,identity=850,unit=305,amount=563,nutrition=1,source=1),
        conflicts=dict(before=212,newResolved=0,unresolved=212),dataTests=dict(status='PASS',tests=count),unitTests=units,qaBuild='PASS',
        samsung=dict(status='PASS',tests=60,log=str(QA/'samsung-final.log'),device='SM-S948N',android=16,api=36,newMappingsVerified=38,previous20MappingChecksReused=True),
        privateDataPreservation=dict(status='PASS',databaseVersion=8,privateFiles=16,changedTables=[],byteIdentical=True,productIdentityUnchanged=True,report=str(QA/'preservation-verify.json')),
        protectedAssets=protected,qaApkSha256=hashlib.sha256((ROOT/'app/build/outputs/apk/qa/app-qa.apk').read_bytes()).hexdigest(),
        questionRequired=False,production=dict(versionCode=7,versionName='1.0.6',installs=0,clears=0,uninstalls=0,deployments=0,releaseBuilds=0),
        research=dict(all155Reviewed=True,remainingWithNoSafeFoodId=sum(not r['verifiedFoodIds'] for r in remaining),remainingWithFoodIdButNoCompleteReference=sum(bool(r['verifiedFoodIds']) for r in remaining),documentIndexCount=19,wholeInternetExhausted=False),
        latestReport=str(OUT/'FINAL_REPORT.md'))
    save('verification-summary.json',summary)
    institutionText='; '.join(f"{r['institution']}: 조사{r['foodsSearched']} / 보유 참고{r['acquiredReferences']} / 새 앱 연결{r['newAppCompleteFoods']}" for r in source['publishedInstitutions'])
    parts=[
      f'1. STATUS: **{status}**. 155개 전체를 조사·판정했으나 안전한 foodId 또는 완전 정량 근거가 없는97개가 남아 516/516 목표는 달성하지 못했습니다.',
      '2. 전체: **516개**.',
      f"3. 앱 완전 구성: **361 → {progress['appComplete']}/516**. 공통 asset6671행, 실제 QA APK 포함 확인.",
      f'4. 남은 음식: **155 → {len(remaining)}**. foodId 미확정90개, foodId는 있으나 완전 구성 부족7개.',
      '5. 상태: ORIGINAL_COMPLETE **41 → 41**, REFERENCE_COMPLETE **329 → 382**, PARTIAL **140 → 87**, UNRESOLVED **6 → 6**. 데이터상 완전423개 중 foodId 없는4개를 제외한 앱 완전419개.',
      '6. complete foodId 미연결: **9 → 4**. 5개를 안전하게 연결. 남은 딸기롤샌드위치·연근치즈구이·도토리묵밥·잣스프는 다른 주재료/조리법 음식에 대입하지 않았습니다. '+link('9개 전체 비교',OUT/'priority-nine-final-review.json'),
      f'7. 새 완전 연결: **{len(new)}개**(전체 참고53개 + 기존 완전 원본 foodId 연결5개). '+link('전체 음식·foodId·출처 목록',OUT/'new-complete-foods-from-361.json'),
      '8. Ingredient: 총4258행. linked **2384 → 2384**, excluded **154 → 154**, unresolved **1720 → 1720**. reference 연결을 원본 재료 해결로 계산하지 않았습니다.',
      '9. 미해결 원인 유지: identity850 / unit305 / amount563 / nutrition1(주꾸미 먹물) / recipe source1(MFDS223 딸기잼). 타 종 먹물·먹물 제거 식용부·완제품 잼 자기 참조를 배합으로 대체하지 않았습니다.',
      '10. reference source: '+institutionText+'. 학회·대학·서울의료원·지자체·제조사·해외 연구기관까지 추가 조사하고19개 원문을 색인했습니다. 정량이 빠진 새 책자 후보는 완전 reference로 집계하지 않았습니다. '+link('기관별 조사·확보·적용',OUT/'source-institution-finalization-summary.json'),
      '11. 계량 충돌: 기존212건 전부 원문 상태·단위·측정/serving 문맥으로 재검토. 이번 해결 **0**, 미해결 **212**. 새 책자에도 동일 재료 행에 적용 가능한 추가 g 근거가 없어 평균·전역 큰술값을 만들지 않았습니다. '+link('212건 비교',OUT/'current-212-conflict-review.json'),
      '12. 김밥: 원본 linked18/excluded1/unresolved18 유지. 햄김밥 전체 참고는 실제 일반 김밥 배합과 분리 표시. 일반·야채 김밥 추가 조사에서 학회 PDF의 미정량 식용유, 경기도 조사책자의 개/단위, 공식 경연 레시피의 약간·범위·누락량 때문에 새 완전 구성으로 채택하지 않았습니다. Samsung 구분 표시 PASS.',
      '13. 남은97개마다 원문·부족한 재료·기관/검색 기록·전체 배합 후보·foodId 후보·미채택 이유를 저장했습니다. 근거 소진 판정은 확인한 자료/검색 범위에 한정하며 인터넷 전체 부재를 주장하지 않습니다. '+link('97개 상세 근거',OUT/'remaining-97-foods.json')+' / '+link('155개 전체 판정',OUT/'all-155-food-evidence-ledger.json'),
      '14. 실제 수정: 공통 reference asset, 음식별 검증 mapping·source validation·후보10개 기준 ranking, 참고 카드의 원본/부분/공공 평균 제목, 관련 데이터/기기 테스트와 조사 근거 도구. '+link('수정 파일 목록',OUT/'modified-files.json'),
      f'15. 테스트: Data **{count}/{count} PASS**; 관련 Unit **12/12 PASS**; QA APK·QA AndroidTest build **PASS**; 최종 Samsung **60/60 PASS**. 전체 Release/Lint/배포 작업 없음.',
      '16. Samsung SM-S948N Android16/API36: 신규38개 연결을 모든 재료 행까지 실제 표시 확인(360dp·fontScale1.30). 기존 원본/부분/김밥/공공 평균, 검색→상세→수량, 메모리 Room 저장 kcal까지 확인. 앞선20개 추가 연결 검증도 PASS. '+link('최종60개 로그',QA/'samsung-final.log'),
      '17. DB: **version8 / migration0**. QA만 비파괴 설치. 각 설치 전 백업 후 복원·검증: 기존16개 private 파일 byte 동일, 변경 테이블0, 제품 설치 정보와 QA 최초 설치 시각 동일. '+link('최종 데이터 보존',QA/'preservation-verify.json'),
      '18. 공식 kcal·탄단지·food/product/franchise 및 원본 ingredient asset: **바이트 동일**. 재료 합계로 공식 kcal를 보정하지 않았습니다. '+link('공통 asset/QA APK 비교',FINAL/'packaged-assets-verification.json'),
      '19. MealRecord kcal: **보존 PASS**. 메모리 DB 저장 테스트의 공식140kcal가 참고 재료 합계로 바뀌지 않음을 확인하고 실제 QA 기록·설정·파일을 완전히 복원했습니다.',
      '20. QUESTION_REQUIRED: **없음**. DB schema 변경이나 임의 foodId/수치 생성 없음.',
      '21. Production: **versionCode7 / versionName1.0.6**. 제품 설치0 / 초기화0 / 삭제0 / 배포0 / Release build0.'
    ]
    (OUT/'FINAL_REPORT.md').write_text('# 최종 잔여155개 reference-first 작업 · 2026-10-05\n\n'+'\n\n'.join(parts)+'\n',encoding='utf-8')
    # Replace stale pending-USB entry points while preserving their exact history.
    for name in ['FINAL_REPORT.md','verification-summary.json']:
        historical=OUT/('prior-entrypoint-'+name)
        if not historical.exists():shutil.copyfile(FINAL/name,historical)
    (FINAL/'FINAL_REPORT.md').write_text(f'# 최신 작업 결과\n\nSTATUS {status}. 실제 앱361 → {progress["appComplete"]}/516, 신규58개, 잔여97개. Data31·Unit12·QA빌드·최종 Samsung60 PASS. QA16개 private 파일과 모든 DB 테이블 복원 검증 PASS. 제품 작업0.\n\n'+link('최신21항목 보고',OUT/'FINAL_REPORT.md')+'\n',encoding='utf-8')
    (FINAL/'verification-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(status=status,complete=progress['appComplete'],remaining=len(remaining),report=str(OUT/'FINAL_REPORT.md')),ensure_ascii=False))
if __name__=='__main__':run()
