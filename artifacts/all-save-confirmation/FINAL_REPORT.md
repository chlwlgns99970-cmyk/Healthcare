# 모든 저장 버튼 완료 보고

1. STATUS: COMPLETE. 아래 실제 저장 버튼 구현 위치 8개 모두 연결·Samsung 표시 검증 완료.

2. 전수 조사: 전체 버튼 8, 화면/저장 양식 8, 팝업 적용 8, 검증 8, 누락 0. screen source 파일은 6개이며 설정 양식 3개를 각각 셌다. 동적 문구는 같은 버튼으로 집계. [전수 인벤토리](INVENTORY.md).

3. 버튼 목록: 수동 음식·사진 결과·식사 수정·신체정보·에너지 기준·직접 목표·추천 설정·운동 몸무게. 인벤토리에 실제 파일·저장 대상·함수·성공 판단·이전/최종 동작을 각각 기록했다.

4. 원인: 기존 4개 파일 구현은 실제로 존재했지만 MealRecord 완료 경로만 처리했다. 설정 3개, 추천 설정, 운동 몸무게에는 연결이 없었고 사진 결과 저장은 onBack을 바로 호출했다. 수정 저장 제목은 수정 완료였다. 따라서 이전 단일 기록 경로 COMPLETE는 모든 저장 버튼 완료의 증거가 아니었다. Samsung 제품 패키지는 실제로 code 7/name 1.0.6이며 lastUpdateTime 2026-10-02 그대로다. 이번 새 수정은 QA에만 설치했다. 사용자 피드백이 발생한 특정 버튼/설치 빌드는 알려지지 않았으므로 그 한 건의 원인을 특정 빌드로 단정하지 않는다.

5. 공통 동작: 저장 → 실제 persistence 성공 → 현재 route에서 저장 완료 Dialog → 확인 → 원래 후속 동작. 사진 결과와 목표 입력창도 확인 전에 유지한다.

6. 실제 수정 파일:
- [HealthcareApp.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/HealthcareApp.kt)
- [RecordSavedDialog.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/components/RecordSavedDialog.kt)
- [SaveAcknowledgement.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/SaveAcknowledgement.kt)
- [SettingsViewModel.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/SettingsViewModel.kt)
- [MealPreferenceViewModel.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/MealPreferenceViewModel.kt)
- [AddRecordViewModel.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/AddRecordViewModel.kt)
- [SettingsScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/SettingsScreen.kt)
- [MealPreferenceScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/MealPreferenceScreen.kt)
- [ExerciseCoachScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/ExerciseCoachScreen.kt)
- [AddRecordScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt)
- [BodyProfileStore.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/data/BodyProfileStore.kt)
- [SaveAcknowledgementTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/test/java/com/example/healthcare/SaveAcknowledgementTest.kt)
- [EnergyViewModelTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/test/java/com/example/healthcare/EnergyViewModelTest.kt)
- [MealPreferenceViewModelTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/test/java/com/example/healthcare/MealPreferenceViewModelTest.kt)
- [AllSaveSettingsSamsungTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/androidTest/java/com/example/healthcare/AllSaveSettingsSamsungTest.kt)
- [AllSaveNavigationSamsungTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/androidTest/java/com/example/healthcare/AllSaveNavigationSamsungTest.kt)
- [RecordSavedDialogUiTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/androidTest/java/com/example/healthcare/RecordSavedDialogUiTest.kt)
- [RecordSaveConfirmationSamsungTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/androidTest/java/com/example/healthcare/RecordSaveConfirmationSamsungTest.kt)
- [AllFoodSearchFlowSamsungTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/androidTest/java/com/example/healthcare/AllFoodSearchFlowSamsungTest.kt)
- [qa_recipe_final_residual_preservation.py](C:/Users/young/AndroidStudioProjects/Healthcare/tools/qa_recipe_final_residual_preservation.py)

7. 재사용: RecordSavedDialog와 기존 RecordSaveConfirmationViewModel을 유지한다. Dialog 제목은 신규/수정 모두 저장 완료. 비기록 화면 본문은 저장되었습니다. 추가 SaveAcknowledgement는 저장 ViewModel이 소유한 StateFlow와 원자적인 consume이며 navigation lambda를 ViewModel에 보관하지 않는다.

8. 중복: 기존 isSaving guard 유지; 설정·취향·사진은 pending 중 저장도 거부한다. 몸무게는 동기 commit 뒤 pending으로 재탭을 막는다. 확인은 consume 후 callback을 실행한다. 실제 빠른 두 번 탭·원자적 확인·회전·foreground·recomposition 검증 PASS. 한 저장 작업이 원래 여러 저장 대상을 쓰는 구조는 그대로이며, SQL 전체를 한 문장으로 바꾼 것은 아니다.

9. 실패: repository 실패 시 기존 입력·오류·재시도 유지. 성공 gate가 생기지 않는다. BodyProfileStore의 commit 반환값을 check하여 디스크 저장 실패를 성공으로 처리하지 않는다. 운동 화면은 실패 메시지와 재시도 유지. 실제 UI의 격리 Room 첫 실패→재시도 성공 검증, 에너지 실패 callback 비실행, 추천 설정 실패/재시도 검증 PASS.

10. navigation: 신규/수정은 확인 후 기존 RecordCompletionRoute. 사진은 확인 후 기존 onBack. 직접 목표는 확인 후 입력창 닫기. 신체정보는 확인 후 기존 body-weight callback. 에너지·추천·몸무게는 원래처럼 같은 화면. 확인 연타로 두 번째 후속 실행 없음.

11. 테스트: 관련 Unit 94 PASS/실패 0. 최종 source Samsung instrumentation 10 PASS; 캡처 안정화 재검증 6 PASS; 최종 기본 기록 저장 경로 2 PASS. 고유 테스트는 10개이며 재실행을 더해 개수를 부풀리지 않았다. QA assembleQa/assembleQaAndroidTest PASS. 전체 Release/Lint/회귀 실행 없음. 초기 테스트 API/노드 탐색/단계형 입력 경로 오류는 수정 후 해당 테스트 PASS로 확인했다.

12. Samsung QA: SM-S948N/API36. 신규·수정은 실제 MainActivity 검색/Room/기록 경로. 다른 6개는 실제 화면 Composable과 QA persistence의 실제 버튼. 각 경로 저장값·저장 완료 표시·확인·원래 후속 동작 PASS. 설정은 실제 MainActivity에서도 회전/foreground 후 단일 Dialog 유지 PASS. 안정화한 팝업 6개 화면 캡처 모두 시각 확인. 사진 분석 결과 생성만 고정 fixture이며 저장 버튼·repository·Room·팝업·onBack은 실제 구현이다.

13. 글자: Samsung system fontScale 1.3에서 저장 경로들 PASS. 360dp effective fontScale 1.30 및 앱1.30×system1.3=1.69에서 제목/본문/확인 표시·간격 PASS. 실제 캡처 확인. 시스템 설정 1.0으로 원복.

14. DB: 8→8. schema/entity 변경 0, migration 0.

15. 사용자 데이터: 테스트 전 QA DB/shared_prefs/files 17개 백업. 마지막 restore/verify PASS, 모든 파일 바이트 동일, 기존 테이블 변경 없음, 추가/누락 파일 없음, QA firstInstallTime 동일. QA는 복원 후 정지 상태로 둔다. 제품 식별 정보 동일.

16. kcal/MealRecord: 계산식과 데이터 구조 변경 없음. 실제 짜파게티 기록 140g/610kcal/탄96·단9·지20 유지. 기존 MealRecord 동일 검증 및 최종 복원 PASS. 음식/상세/메타데이터/recipe 보호 대상 11개 파일 SHA256 일치. AddRecordScreen/ViewModel의 전체 hash는 저장 UX 변경 때문에 달라졌으므로 동일하다고 주장하지 않는다. Food/영양 계산·Home·추천 알고리즘·통계·updater 수정 없음.

17. 남은 저장 버튼: 0.

18. 미검증: 누락된 저장 버튼 경로 없음. 사진 외부 분석 서버 자체는 이 작업 대상이 아니어서 fixture 사용. 같은 수동 저장 버튼의 바코드 표시문구 분기별 별도 실기기 촬영은 반복하지 않았으며 동일 onClick/saveRecord 연결을 소스로 추적했다. 전체 기기·OS 전수 테스트/프로세스 강제 종료는 실행하지 않았다.

19. 위험: 강제 프로세스 종료 후 미확인 Dialog 복원은 보장 범위 밖이다. 회전·foreground 재진입은 검증했다. 기존 SharedPreferences와 Room을 함께 저장하는 작업의 다중 저장소 atomic transaction 구조는 변경하지 않았다. 제품 사용자는 새 배포 전 이 수정본을 받지 않는다.

20. Production: versionCode=7, versionName=1.0.6 유지. 제품 설치=0/초기화=0/overwrite=0/배포=0. main 공통 소스이므로 다음 production build에 포함되지만 이번에는 Release build/배포하지 않았다.

증거 위치: app/build/all-save-confirmation (instrumentation-final.txt, instrumentation-capture-final.txt, instrumentation-manual-final.txt, unit-results.json, protected-assets.json, preservation-verify.json, screenshots), app/build/all-save-build-verified.log.
