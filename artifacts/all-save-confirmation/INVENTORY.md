# 저장 버튼 인벤토리

현재 실행 가능한 저장 버튼 구현 위치 8개, 저장 양식 8개(6개 screen source 파일). 같은 버튼의 동적 표시문구는 별도 버튼으로 중복 집계하지 않는다.

|화면|표시문구|파일|저장 대상|저장 함수|성공 판단|기존 후속 동작|작업 전 팝업|최종 처리|Samsung|
|---|---|---|---|---|---|---|---|---|---|
|음식 수동 기록|기록 저장 / 상품 정보와 기록 저장 (식사 지정 시 ○○에 기록)|[WellnessManualRecordScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/WellnessManualRecordScreen.kt)|MealRecord; 선택 시 FrequentFood/로컬 상품|AddRecordViewModel.saveRecord|DAO/repository 정상 반환 뒤 onRecordSaved|기록 완료 화면|기존 연결 있음|연결 유지|PASS|
|사진 분석 결과|확인한 내용으로 기록 저장|[AddRecordScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt)|선택한 PHOTO_AI MealRecord 목록|AddRecordViewModel.savePhotoAnalysisRecords|insertPhotoMeals 완료 뒤 photoSaveAcknowledgement|기존 onBack|없음; 바로 onBack|현재 결과 유지 → 팝업 확인 → 기존 onBack|PASS|
|식사 기록 수정|수정 저장|[HistoryScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/HistoryScreen.kt)|기존 MealRecord|HistoryViewModel.saveMealEdit|repository.updateMeal 정상 반환 뒤 onRecordEdited|기록 완료 화면|수정 완료 제목|저장 완료로 통일|PASS|
|내 신체정보|저장하기|[SettingsScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/SettingsScreen.kt)|BodyProfile/운동 몸무게; 기존 에너지 동기화|SettingsViewModel.saveBodyProfile|checked SharedPreferences.commit + 기존 repository 반환 후 callback|설정 화면 유지; body weight callback|없음|공통 Dialog + ViewModel 보류|PASS|
|에너지 목표|에너지 기준 저장|[SettingsScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/SettingsScreen.kt)|EnergyProfileHistory|SettingsViewModel.saveEnergyProfile|repository.saveProfile 반환 후 callback|설정 화면 유지|없음|공통 Dialog + ViewModel 보류|PASS|
|직접 목표 설정|저장|[SettingsScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/SettingsScreen.kt)|CalorieGoal; 기존 MANUAL 에너지 동기화|SettingsViewModel.updateGoal|persistManualGoal 반환 후 callback|목표 입력창 닫기|없음; 바로 닫힘|입력창 유지; 팝업 확인 후 닫기|PASS|
|식사 추천 설정|추천 설정 저장|[MealPreferenceScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/MealPreferenceScreen.kt)|UserMealPreference|MealPreferenceViewModel.save|repository 저장 및 재조회 정상 반환|화면 유지|없음|공통 Dialog + ViewModel 보류|PASS|
|오늘의 움직임|몸무게 저장|[ExerciseCoachScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/ExerciseCoachScreen.kt)|운동 몸무게/기존 BodyProfile 동기화|BodyProfileStore.saveExerciseWeight (root callback)|checked commit 정상 반환|화면 유지|없음|성공 후 saveable pending; 실패 메시지|PASS|

시작하기·이 취향으로 시작하기·건너뛰기·삭제·확인·적용·자동 저장·즐겨찾기 체크박스는 실제 저장 버튼이 아니므로 제외했다. MealPlan 확인/먹었어요 경로의 기존 완료 팝업은 유지한다.

AddRecordScreen의 private ManualRecordScreen(저장하기)은 호출 지점이 없는 과거 선언으로 실행 가능한 버튼 수에 포함하지 않는다. Settings의 반복 배치는 동일 form과 동일 callback이므로 중복 집계하지 않는다.

추적: UI 저장 onClick → ViewModel → repository/DAO 또는 BodyProfileStore → 성공 callback/상태 → Dialog → 명시적인 confirm에서 기존 callback. QA source set에만 둔 구현은 없으며 모두 main 공통 소스다.
