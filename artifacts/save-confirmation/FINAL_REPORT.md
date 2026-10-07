# 저장 성공 팝업 완료 보고

검증일: 2026-10-06. 프로젝트: `C:\Users\young\AndroidStudioProjects\Healthcare`.

1. **STATUS: COMPLETE.** 요청한 저장 성공 안내와 확인 후 기존 이동 흐름을 실제 앱에 반영했다.

2. **원인:** 기존 `saveRecord`와 `saveMealEdit`는 repository/DAO 호출이 성공한 다음 callback을 실행하고 있었다. 하지만 `HealthcareApp.completeRecord`가 이 callback을 받자마자 완료 화면으로 이동하여 AlertDialog를 표시하는 단계가 없었다. 기록 수정 화면 내부의 즉시 뒤로 이동도 제거했다.

3. **최종 동작:** 저장 버튼 → 기존 저장 중 상태/버튼 비활성화 → 실제 repository/DAO 저장 성공 callback → Activity ViewModel의 확인 대기 상태 → `저장 완료 / 기록이 저장되었습니다. / 확인` → 확인 대기 상태를 먼저 소비 → 기존 RecordCompletionRoute로 한 번 이동. 팝업을 버튼 클릭 자체에서 띄우지 않는다.

4. **실제 수정 파일:**
   - `app/src/main/java/com/example/healthcare/ui/HealthcareApp.kt`: 저장 성공 callback을 팝업 대기로 연결하고 확인 후 기존 navigation 실행.
   - `app/src/main/java/com/example/healthcare/ui/screens/HistoryScreen.kt`: 수정 성공 직후 내부 navigation 제거, 동일 확인 흐름 사용.
   - `app/src/main/java/com/example/healthcare/ui/viewmodel/RecordSaveConfirmationViewModel.kt`: 확인 대기/소비/완료 표시 상태, 중복 확인 차단. Room entity가 아닌 UI 상태다.
   - `app/src/main/java/com/example/healthcare/ui/components/RecordSavedDialog.kt`: 기존 Material3 AlertDialog 스타일 재사용, 48dp 이상 확인 버튼.
   - `app/src/test/java/com/example/healthcare/RecordSaveConfirmationTest.kt`: 성공 1회 소비·확인 1회·완료 화면을 떠난 뒤 재수집 방지 등 7개.
   - `app/src/test/java/com/example/healthcare/PhotoManualRecordViewModelTest.kt`: 실제 실패/재시도 callback에 확인 상태 검증 추가. 기존 다중 기록 테스트 3곳에 화면과 동일한 독립 기록 세션 시작을 추가했다. 계산 코드 변경은 하지 않았다.
   - `app/src/androidTest/java/com/example/healthcare/RecordSaveConfirmationSamsungTest.kt`: 실제 검색/저장/두 번 탭/회전/foreground/뒤로가기/확인/기록 상세/수정 검증.
   - `app/src/androidTest/java/com/example/healthcare/RecordSavedDialogUiTest.kt`: 360dp·글자 1.30, 실패 주입/입력 유지/재시도 UI 검증.
   - `app/src/androidTest/java/com/example/healthcare/AllFoodSearchFlowSamsungTest.kt`: 기존 저장 smoke가 새 확인 버튼을 거쳐 진행하도록 조정. 전체 Food 테스트는 다시 실행하지 않았다.
   - `tools/qa_recipe_final_residual_preservation.py`: 이번 작업 QA 보존 폴더 허용 목록만 추가.
   기존 dirty working tree의 나머지 변경은 이 작업 변경으로 집계하지 않았다.

5. **중복 저장 방지:** 기존 신규 기록 ViewModel의 `isSaving` 및 저장 성공 이후 `repeatCompleted` 방어를 그대로 유지했다. 수정 저장도 기존 `isSaving`/편집 상태 검사를 유지한다. 팝업은 modal이라 뒤의 버튼을 누를 수 없다. 확인 상태는 compareAndSet으로 navigation보다 먼저 소비하므로 빠른 확인 연타가 navigation을 두 번 실행하지 않는다. 성공 callback 재전달도 대기 중/소비한 성공에 대해 새 팝업을 만들지 않는다. 회전·foreground에도 Activity ViewModel의 대기 상태가 유지된다.

6. **저장 실패:** 기존 실패 안내를 그대로 사용한다. 성공 callback이 호출되지 않아 팝업 대기 상태도 생성되지 않는다. 음식·양·칼로리 입력을 유지하고 저장 버튼을 다시 사용할 수 있다. 격리 Room 연결 repository에서 첫 쓰기에 실패를 주입했고, 실패 시 0행/팝업 없음, 재시도 시 1행/팝업 표시를 확인했다.

7. **신규 기록 저장 검증:** Samsung의 실제 음식 검색에서 공식 짜파게티 Food를 선택하고 1봉을 기록했다. 팝업이 보이는 시점에 실제 DB에서 1건을 확인했으며 확인 전 완료 화면은 없었다. 저장값은 140g / 610kcal / 탄수화물 96g / 단백질 9g / 지방 20g 및 기존 정확한 foodItemId를 유지했다. 저장·확인 버튼 빠른 두 번 탭, 팝업 표시 중 회전·foreground, 뒤로가기도 검증했다.

8. **기록 수정 적용:** 같은 저장 UX이므로 `수정 완료 / 기록이 수정되었습니다. / 확인`으로 구분했다. 삭제에는 적용하지 않았다. 기존 수정 성공 callback 후 팝업을 표시하고 확인 후 기존 완료 화면으로 이동한다. 실제 기록 상세 → 수정 → 저장에서도 610kcal 보존 PASS.

9. **테스트:**
   - Unit: 71 / 71 PASS — RecordSaveConfirmationTest 7, PhotoManualRecordViewModelTest 44, HistoryEditViewModelTest 12, TabAndCompletionTest 8.
   - UI/instrumentation: 관련 고유 4개 최종 PASS — 실제 저장/회전/수정 경로, 360dp 큰 글자 dialog, 실제 저장 ViewModel에 실패 주입/재시도, 기존 스파게티 저장·상세·편집 경로.
   - QA build: `:app:assembleQa`, `:app:assembleQaAndroidTest` PASS.
   - Release build, 전체 Lint, 전체 회귀테스트 실행 0.
   - 진단 중 기존 Unit fixture의 세션 누락과 실패 UI fixture의 섭취량 미입력을 확인해 테스트를 보정했다. 마지막 실패 재시도 검증은 정상 입력에서 DB 쓰기 호출을 실제로 확인했다.

10. **Samsung QA:** SM-S948N / Android 16 API36. 저장 완료 팝업, 중복 저장 1건, 확인 전 navigation 없음, 확인 후 완료 화면, 기록 상세, 수정 완료 팝업, kcal 보존, 회전/foreground, dialog 표시 중 뒤로가기, 360dp·앱 글자 1.30 모두 PASS. 큰 글자 캡처를 직접 확인해 제목·본문·확인 버튼 잘림이 없음을 확인했다. 기존 앱의 blocking dialog 방식처럼 팝업은 뒤로가기/외부 탭으로 닫지 않고 확인 버튼으로 진행한다.

11. **DB:** version 8 유지, migration 추가 0. QA 테스트 전에 immutable snapshot을 확보했다. 테스트 후 복원 및 별도 verify PASS: 기존 private file 17개 byte 동일, 변경 DB 테이블 0, QA 최초 설치 시각 유지, 제품 package identity 동일. 제품 앱 데이터 접근은 하지 않았다.

12. **기존 kcal / MealRecord 보호:** 저장 계산·단위·음식 ID·날짜/시간·정렬 코드 변경 0. 실제 저장·수정 테스트에서 기존 모든 MealRecord가 변하지 않았음을 비교했다. 최종 QA DB 전체를 복원했다. 이전 전체 Food 작업의 source/asset SHA를 다시 비교하여 음식 67,356개 데이터와 상세 모델/metadata 파일 변경 0을 확인했다.

13. **테스트하지 않은 항목:** 제품 앱 설치/실험 및 실제 사용자 DB 손상·저장장치 고장 유발은 하지 않았다. 저장 실패는 격리 Room 연결 repository에 오류를 주입해 검증했다. 강제 프로세스 종료 후 UI 복원은 이번 회전/foreground 검증 범위에 포함하지 않았다.

14. **남아 있는 위험:** 현재 요구 범위의 최종 실패 없음. 강제 프로세스 종료 후 확인 대기 팝업 복원은 보장하지 않는다. 정상 화면 회전·foreground 전환 및 상태 재수집은 검증했다. DB 저장 성공 자체와 사용자 기록 보존은 확인했다.

15. **Production:** versionCode 7 / versionName 1.0.6 유지. 제품 설치 0 / 제품 초기화 0 / 제품 overwrite 0 / 배포 0. 공통 main source 수정이므로 이후 production build에도 포함된다. 이번에는 QA 앱만 기존 QA 위에 설치했다.

## 검증 근거

- `app/build/save-confirmation/unit-results.json`, `gradle-output.txt`
- `app/build/save-confirmation/samsung-output.txt`: 실제 저장/회전/foreground/수정 및 큰 글자 PASS.
- `app/build/save-confirmation/final-ui-output.txt`: 스파게티 경로와 큰 글자 PASS. 초기 실패 fixture 진단 로그도 남아 있다.
- `app/build/save-confirmation/failure-retry-ui-output.txt`: 보정 후 실제 DB 실패/재시도 최종 PASS.
- `app/build/save-confirmation/samsung-result.json`, `dialog-360-large.png`
- `app/build/save-confirmation/preservation-verify.json`, `food-protection.json`

QA 데이터가 포함된 tar 보존본은 외부로 배포하지 않았다.
