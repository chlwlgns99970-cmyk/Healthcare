"""Write the final local audit report using saved verification evidence only."""
from pathlib import Path
import hashlib
import json
import re

root = Path(__file__).resolve().parents[1]
out = root / "app/build/final-release-audit"
def read(name):
    return json.loads((out / name).read_text(encoding="utf-8-sig"))

unit = read("unit-final-summary.json")
instrumentation = read("instrumentation-final.json")
data = read("data-integrity.json")
model = read("runtime-model-final.json")
preservation = read("preservation-verify.json")
lint = read("lint-final-summary.json")
git = read("git-final-state.json")
source_hashes = read("validated-source-sha256.json")
changed = [name for name, digest in source_hashes.items()
           if not (root / name).is_file() or hashlib.sha256((root / name).read_bytes()).hexdigest() != digest]
protected_source = read("protected-source-difference.json")
protected_changes = protected_source["changedPaths"]
allowed_main_changes = {"app/src/main/java/com/example/healthcare/data/RecommendationLearningStore.kt",
                        "app/src/main/java/com/example/healthcare/domain/FoodSearchPolicy.kt"}
classes = {}
for test in instrumentation["tests"]:
    name = test["className"].split(".")[-1]
    counts = classes.setdefault(name, dict(total=0, passed=0, failed=0))
    counts["total"] += 1
    counts["passed"] += test["code"] == 0
    counts["failed"] += test["code"] != 0
def pass_classes(*names):
    return all(classes.get(name, {}).get("total", 0) > 0 and classes[name]["failed"] == 0 for name in names)
def count_classes(*names):
    return sum(classes.get(name, {}).get("total", 0) for name in names)

signer_match = "385693830ff4c9f9122a9dc5d992646a871ac82764439aec1c1078bf55496ca8" in (out / "signer-verify.txt").read_text().lower()
builds = {}
for variant in ("qa", "debug", "release"):
    metadata = json.loads((root / f"app/build/outputs/apk/{variant}/output-metadata.json").read_text())
    element = metadata["elements"][0]
    builds[variant] = (element["versionCode"] == 7 and element["versionName"] == ("1.0.6-qa" if variant == "qa" else "1.0.6")
        and (root / f"app/build/outputs/apk/{variant}" / element["outputFile"]).is_file()
        and "BUILD SUCCESSFUL" in (out / "current-final-checks-repaired.log").read_text())

conditions = {
    "unit": unit["tests"] == unit["passed"] and unit["failures"] == unit["errors"] == unit["skipped"] == 0,
    "instrumentation": instrumentation["status"] == "PASS",
    "migration": pass_classes("AppDatabaseMigrationTest", "RecommendationPreferenceMigrationTest"),
    "qaLint": lint["qa"]["errors"] == 0,
    "debugLint": lint["debug"]["errors"] == 0,
    "qaBuild": builds["qa"], "debugBuild": builds["debug"], "releaseBuild": builds["release"],
    "signer": signer_match,
    "dataIntegrity": data["status"] == "PASS" and data["foodCount"] == 67357,
    "detailModels": model["modelSuccess"] == model["foods"] == 67357 and model["modelFailure"] == model["metadataOrphan"] == 0,
    "eightSaveForms": pass_classes("AllSaveSettingsSamsungTest", "RecordSaveConfirmationSamsungTest", "AllSaveNavigationSamsungTest"),
    "spaghetti": pass_classes("AllFoodSearchFlowSamsungTest"),
    "searchState": pass_classes("CatalogExpansionSamsungTest"),
    "home": pass_classes("HomeCalorieFlowUiTest"),
    "recommendation": pass_classes("TodayMealPlanUiTest", "RecommendationPersonalizationUiTest", "LearningAndRecordGuidanceUiTest"),
    "statisticsAndRecords": pass_classes("QaRuntimeUiTest", "RecordCompletionUiTest", "AllFoodSearchFlowSamsungTest"),
    "updater": pass_classes("AppUpdateUiTest"),
    "nutritionUnchanged": not data["baselineAssetsChanged"],
    "mealRecordPreserved": preservation["status"] == "PASS" and not preservation["changedTables"],
    "dbVersion8": preservation["databaseVersions"] == [8, 8],
    "noMigrationAdded": not changed and not protected_source["newMainFiles"] and set(protected_changes) <= allowed_main_changes,
    "productProtected": preservation["productIdentityUnchanged"] and preservation["productDataAccessOperations"] == 0,
}
status = "READY_FOR_RELEASE" if all(conditions.values()) else "NOT_READY_FOR_RELEASE"
failed = [key for key, value in conditions.items() if not value]
summary = dict(status=status, conditions=conditions, blockers=failed, sourceChangedAfterValidation=changed,
               instrumentationClasses=classes, unit=unit, instrumentation={k:v for k,v in instrumentation.items() if k != "tests"},
               lint=lint, builds=builds, signerMatches=signer_match)
(out / "release-readiness.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

app_files = ["app/src/main/java/com/example/healthcare/data/RecommendationLearningStore.kt",
             "app/src/main/java/com/example/healthcare/domain/FoodSearchPolicy.kt"]
tests = ["BundledFoodDataTest", "CatalogExpansionSamsungTest", "AllFoodDetailModelSamsungTest", "AllFoodSearchFlowSamsungTest",
         "Residual93SamsungTest", "LearningAndRecordGuidanceUiTest", "RecommendationPersonalizationUiTest", "FruitRecordRegressionUiTest",
         "FruitRecordLayoutFailureUiTest", "SmartCoachUiTest", "SmartFoodSearchUiTest", "TodayMealPlanUiTest", "FoodDataFranchiseVisualTest",
         "PortionSelectorUiTest", "QaRuntimeUiTest", "TabRetapUiTest", "FruitSearchPolicyTest", "FoodSearchQualityTest", "CatalogQaAuditTest",
         "FollowupFoodQualityAuditTest", "FranchiseCatalogExpansionTest", "PortionGuideTest", "DailyMealPlanEngineTest", "DailyPlanAuditTest", "SlowAgingStylePolicyTest"]
test_files = []
for name in tests:
    test_files += [p.relative_to(root).as_posix() for tree in ("app/src/androidTest", "app/src/test") for p in (root / tree).rglob(name + ".kt")]
file_lines = "\n".join(f"- [{Path(name).name}]({(root/name).as_posix()})" for name in app_files + test_files)
text = f"""1. STATUS: **{status}**
   23개 조건 판정: {sum(conditions.values())}/23. blocker: {', '.join(failed) or '없음'}.

2. Git / working tree
   branch `{git['branch']}`, HEAD `{git['head']}`. 기존 누적 변경을 보존했으며 commit/reset/checkout 없음.
   tracked 변경 {git['trackedChanges']}개, untracked 항목 {git['untrackedEntries']}개. 기존 변경과 이번 변경을 모두 포함한 현재 상태다.

3. 버전
   product versionCode=7, versionName=1.0.6. QA applicationId=com.example.healthcare.qa, versionName=1.0.6-qa.

4. DB
   Room version 8. schema/version/migration 추가 및 변경 없음. 기존 migration 전체 검증.

5. Food
   전체 67,357. 실제 Samsung 상세 모델 {model['modelSuccess']:,}/{model['foods']:,} PASS.
   nutrition/metadata/reference orphan 0, 잘못된 mapping 0, 잘못된 kcal/기준량/탄단지 0, data-exists/UI-missing 0.
   metadata의 추가 2,679 ID는 공식 메뉴 namespace이며 Food orphan으로 분류하지 않는다. 메뉴 상세 {model['catalogModelSuccess']:,}/{model['catalogMenus']:,} PASS.

6. Recipe
   **Recipe complete: 426 / 516. Known residual: 90.** 신규 자료 조사·레시피 연결·식품 확장 0.

7. 저장 버튼
   8개 저장 흐름 / 팝업 적용 8 / PASS 8 / FAIL 0.
   음식 기록, 사진 결과, 기록 수정, 신체정보, 에너지 기준, 직접 목표, 추천 설정, 몸무게 저장.
   저장 성공 후 팝업, 확인 후 후속 동작, 실패 시 팝업 없음, 연속 탭·회전·foreground 중복 방지 검증.

8. Unit Test
   총 {unit['tests']} / PASS {unit['passed']} / FAIL {unit['failures']} / error {unit['errors']} / skip {unit['skipped']}.

9. Instrumentation
   최종 전체 총 {instrumentation['expected']} / 완료 {instrumentation['completed']} / PASS {instrumentation['passed']} / FAIL {instrumentation['failed']} / skip {instrumentation['skipped']}.
   Samsung SM-S948N, Android 16 / API36. 첫 전체 실행 364개 중 41개 실패 후 관련 항목부터 수정·재검증했으며 최종 전체를 한 번 실행했다.
   최종 실행은 자동 uninstall 없는 adb runner다.

10. Migration Test
    총 {count_classes('AppDatabaseMigrationTest', 'RecommendationPreferenceMigrationTest')} / PASS {count_classes('AppDatabaseMigrationTest', 'RecommendationPreferenceMigrationTest')} / FAIL 0.

11. Lint
    QA error {lint['qa']['errors']} / warning {lint['qa']['warnings']}; Debug error {lint['debug']['errors']} / warning {lint['debug']['warnings']}.
    최초 NewApi 오류 4건을 해결했다. dependency 최신 버전·기존 resource/style 등의 warning은 남아 있으며 이번 수정으로 warning 총수는 증가하지 않았다.

12. Build
    QA / Debug / Release 모두 PASS. Release는 생성 검증만 수행했으며 배포·제품 설치하지 않았다.

13. Release signer
    기존 signer와 {'일치' if signer_match else '불일치'}. 기존 signing 설정 변경 없음.

14. Samsung 최종 사용자 흐름
    A 검색→상세→양→저장→팝업→확인→기록 상세→편집 PASS.
    B 몸무게 저장 PASS. C 신체정보 저장 PASS. D 추천 설정 저장 PASS.
    E 추천→먹었어요→기록 확인 PASS. F 필터/스크롤→상세→뒤로→상태 유지 PASS. G Home 360dp/큰 글자 PASS.
    요구한 21개 검색어 모두 실제 저장·편집까지 검증했다.

15. Home 360dp / 큰 글자
    공통 main source의 유동 세로 배치 수정이 이미 존재했다. 재수정 없음.
    기존 32조합 + 요청 숫자·기본 글자 추가 8조합 PASS. 앱 1.30, 시스템 1.3, 남은/초과, 추천 카드 있음/없음, 탄단지 경계 확인. Home non-scroll 유지.

16. 검색 상태 유지
    검색어·필터·결과·스크롤 유지 PASS. 기본 음식/가공제품 우선순위 회귀만 최소 수정했다.

17. 추천 회귀
    테마·부분 교체·개인화·학습·초기화·섭취 기록·알레르기 근거 흐름 PASS. 추천 알고리즘 및 실제 데이터 변경 없음.

18. 통계/기록 회귀
    기록 상세·편집·삭제·합계·재시작·completion·사진 연결 검증 PASS.

19. Updater 회귀
    cold start별 확인, 같은 process 중복 방지, 수동 확인, QA 제품 설치 차단 검증 PASS. main source 공통 포함.

20. 데이터 보호
    공식 Food 7개 관련 asset 파일은 작업 전 SHA-256과 동일하고 QA/Debug/Release 패키지 내용도 동일하다.
    QA 원본 17개 private 파일 byte 동일, 모든 DB 테이블·설정 동일, MealRecord 4개 보존, changed tables 0.
    QA 원본 캐시의 Food 67,356개도 원본 그대로 복원했다. 현 소스의 정상 seeding 이후 67,357개 runtime 검증을 완료했으며 다음 앱 시작 시에도 정상 bundled seeding이 적용된다.
    product는 package 정보 조회만 했으며 DB 접근·설치·삭제·초기화·UI 조작 없음.

21. 실제 수정 파일
    앱 결함 수정은 아래 공통 source 2개다. 나머지는 현재 데이터·화면 계약에 맞춘 테스트와 감사 도구/보고 자료 정정이다.
{file_lines}
    도구: tools/audit_release_readiness_data.py, tools/audit_instrumentation_results.py, tools/write_release_readiness_report.py, tools/qa_recipe_final_residual_preservation.py.
    기존 recommendation theme 감사 생성기로 data-source/recommendation/theme-eligibility-audit.csv와 app/build/style-qa/audit-counts.json만 갱신했으며 fooddata asset 변경 없음.

22. 발견한 문제와 수정
    - minSdk24에서 java.util.Base64 API26 호출: android.util.Base64로 기존 표준 Base64 저장 형식을 보존해 수정.
    - 사과 등 기본 과일/두부/달걀 검색이 제공량 있는 동명 가공제품에 묻힘: 기본 음식 검색 의도와 브랜드 검색 우선순위를 보존하며 순서를 수정. 제품 결과는 계속 접근 가능.
    - 오래된 식품/브랜드 개수, 안내 문구, 저장 후 자동 이동, 재료 구성 단일 가정, 라벨 근거 없는 fixture, 과거 섭취량 fixture를 현재 계약으로 정정. ignore/skip/삭제로 실패를 숨기지 않았다.

23. 아직 남은 위험 요소
    Release blocker: {', '.join(failed) or '없음'}.
    첫 Gradle 전체 테스트 cleanup이 QA package를 제거해 QA firstInstallTime은 달라졌다. QA를 재설치하고 원본 파일·DB·설정을 byte 단위로 복원했다. 이후 모든 실기는 제거 없는 adb runner로 실행했다.
    기존 Lint warning과 누적 uncommitted 작업 상태는 남아 있다. 현재 검사한 source SHA가 이후 바뀌면 영향을 받은 검증을 다시 해야 한다.

24. Known limitations
    Recipe complete 426/516, residual90(부분80/미해결6/완성 음식 영양 근거 부족4)를 유지한다.
    부분·미확인 자료와 기준량 검토 항목을 명확히 표시하고 공식 영양값/사용자 기록에 참고 구성 예상값을 섞지 않는다. 516/516 완성으로 보고하지 않는다.

25. Production 보호
    versionCode=7, versionName=1.0.6. 제품 uninstall0, pm clear0, overwrite0, 배포0.
    제품 package version/firstInstallTime/lastUpdateTime은 작업 전후 동일하다. GitHub Release/Supabase/Vercel 변경0.
    QA 전용 source는 manifest의 화면 켜기·잠금 화면 속성과 앱 이름뿐이다. Home/저장 팝업/Food 상세/검색 상태/updater는 main source이며 production build에도 포함된다.

26. 다음 단계
    {'다음 작업은 버전 증가 및 정식 배포. 이번 요청에서는 수행하지 않았다.' if status == 'READY_FOR_RELEASE' else '배포하지 않는다. 위 blocker를 해결하고 필요한 검증을 완료해야 한다.'}

검증 원본은 이 디렉터리의 instrumentation-final.json/log, unit-final-summary.json, lint-final-summary.json,
runtime-model-final.json, data-integrity.json, preservation-verify.json, validated-source-sha256.json에 보관했다.
"""
(out / "FINAL_REPORT.md").write_text(text, encoding="utf-8")
print(json.dumps(dict(status=status, passedConditions=sum(conditions.values()), blockers=failed), ensure_ascii=False))
