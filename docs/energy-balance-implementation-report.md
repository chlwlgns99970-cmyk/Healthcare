# Healthcare 에너지 기준·체중 환산 기능 구현 보고서

상태: **PARTIAL — 구현·단위 테스트·Lint·APK 빌드는 완료, 실기기 미연결로 QA 계측 실행은 보류**

1. **실제 수정한 파일**
   - 신규 9개: `ActivityLevel.kt`, `TargetMode.kt`, `EnergyProfileHistory.kt`, `EnergyProfileDao.kt`, `EnergyProfileRepository.kt`, `EnergyBalanceCalculator.kt`, `EnergyBalanceCalculatorTest.kt`, `EnergyViewModelTest.kt`, `EnergyUiTest.kt`
   - 수정 12개: `Converters.kt`, `AppDatabase.kt`, `HealthcareApplication.kt`, `DashboardViewModel.kt`, `SettingsViewModel.kt`, `HistoryViewModel.kt`, `ViewModelFactory.kt`, `HealthcareApp.kt`, `DashboardScreen.kt`, `SettingsScreen.kt`, `Color.kt`, `AppDatabaseMigrationTest.kt`
   - 삭제 파일 없음

2. **파일별 수정 내용**
   - 데이터 계층: 날짜별 에너지 프로필 Entity/DAO/Repository와 enum converter 추가
   - 도메인 계층: 유지 칼로리, 유지 기준 초과량, 7일·30일 단순 환산을 한 계산기로 통합
   - ViewModel: 설정 입력·검증·저장 상태와 Dashboard/History 유효 목표 계산 추가
   - UI: Settings 에너지 기준 입력, Dashboard 안내·에너지 기준·체중 환산 카드 추가
   - 테스트: 계산 15개, ViewModel 7개 및 Room/migration/UI 계측 시나리오 추가

3. **사용한 데이터 저장 방식**
   - Room의 `energy_profile_history` 테이블에 적용 시작일별로 저장한다.
   - `effectiveFromDate`에 unique index를 두어 같은 날짜 연속 저장은 한 행을 갱신한다.
   - 단순 체중 환산값은 저장하지 않고 Dashboard 상태에서만 계산한다.

4. **Room 버전 및 migration 변경 여부**
   - 실제 확인 버전 v2에서 v3으로 상승했다.
   - `MIGRATION_2_3`은 에너지 이력 테이블과 unique index만 추가한다.
   - destructive migration은 사용하지 않았고 기존 MealRecord, CalorieGoal, FrequentFood를 변경하지 않았다.

5. **기초대사량 입력 방식**
   - Settings의 `기초대사량(BMR)` 숫자 필드에 사용자가 직접 입력한다.
   - 0 이하·공백·숫자 변환 실패를 차단하고 최대 5자리로 제한한다.
   - 1~2자리 또는 5자리 입력은 차단하지 않고 재확인 안내를 표시한다.

6. **활동 수준과 PAL 값**
   - LIGHT 1.55, MODERATE 1.75, HIGH 2.20, CUSTOM 1.40~2.40.
   - CUSTOM은 소수점 둘째 자리까지 입력할 수 있고 범위 밖이면 저장하지 않는다.

7. **예상 유지 칼로리 계산식**
   - `BMR × PAL`을 Double 정밀도로 계산하고 화면·일일 목표에서는 반올림한 정수 kcal를 사용한다.
   - NaN, Infinity, 음수 및 Int 범위 초과 결과를 거부한다.

8. **목표 모드 처리 방식**
   - MANUAL: 기존 CalorieGoal을 사용한다.
   - BMR: 입력한 BMR을 유효 목표로 사용한다.
   - MAINTENANCE: 반올림한 예상 유지 칼로리를 유효 목표로 사용한다.
   - Dashboard와 History는 선택 날짜에 적용되는 프로필을 조회해 같은 기준을 사용한다.

9. **기존 목표 보호 방식**
   - EnergyProfile 저장 시 기존 `calorie_goals`를 쓰거나 덮어쓰지 않는다.
   - 프로필이 없으면 기존 직접 목표가 그대로 적용된다.
   - 기존 사용자의 초기 목표 모드는 자동 변경되지 않고 MANUAL 동작을 유지한다.

10. **체중 환산 계산식**
    - 오늘 초과량: `max(0, 오늘 섭취량 - 예상 유지 칼로리)`.
    - 단순 환산: `오늘 초과량 × 일수 ÷ 7700.0`.
    - BMR보다 많이 섭취해도 유지 칼로리 이하라면 환산을 표시하지 않는다.

11. **7일·30일 결과 반올림 방식**
    - 소수점 첫째 자리로 표시한다.
    - 0보다 크고 0.05kg 미만이면 `약 0.1 kg 미만`으로 표시한다.
    - 초과량이 0이면 환산 카드를 숨긴다.

12. **실제 체중 증가와 구분한 UI 문구**
    - `오늘과 같은 초과가 매일 이어질 경우`, `7일/30일 단순 환산`, `실제 체중 변화와 다를 수 있습니다`를 사용한다.
    - 의료 진단·개인 영양 처방이 아니라는 안내와 전문가 상담 안내를 Settings에 표시한다.

13. **Dashboard 변경 내용**
    - 미설정 안내 카드와 Settings 진입 버튼 추가.
    - BMR, 예상 유지 칼로리, 현재 목표, 목표 기준, 오늘 섭취, 유지 기준 남음/초과 표시.
    - 초과 시 7일·30일 단순 환산 카드 표시.
    - 30일 환산 단계에 따라 amber/orange/coral 테마 토큰을 사용하며 문구·아이콘을 함께 제공한다.

14. **Settings 변경 내용**
    - 에너지 기준 섹션, BMR 입력, 활동 수준 선택, CUSTOM PAL, 즉시 유지 칼로리 미리보기, 목표 모드, 저장 버튼을 추가했다.
    - 기존 직접 목표 편집은 별도 카드로 유지했다.
    - 저장 중 연속 클릭을 차단하고 성공·실패 상태를 구분한다.

15. **기존 기능 보호 결과**
    - MealRecord/사진/CameraX/백엔드/자동 분석 비활성 상태를 변경하지 않았다.
    - 제품 `com.example.healthcare`, QA `com.example.healthcare.qa`, QA 표시명 `Healthcare QA`를 APK에서 재확인했다.
    - 신규 dependency 없음.

16. **실행한 단위 테스트와 결과**
    - `:app:testQaUnitTest`: 48/48 통과, failure/error/skipped 0.
    - 계산 테스트 15개는 BMR×PAL, 반올림, 초과 기준, 7/30일, 경계·오버플로·목표 모드를 포함한다.
    - 현재 AGP task 목록에는 `testDebugUnitTest`가 생성되지 않고 `testQaUnitTest`만 생성된다.

17. **ViewModel 테스트와 결과**
    - 7/7 통과.
    - 미설정 기존 목표 유지, 정상 계산, BMR 초과지만 유지 이내, 미리보기, CUSTOM 오류, 같은 날 중복 방지, 저장 오류를 검증했다.

18. **UI 및 instrumentation 테스트 결과**
    - `assembleQaAndroidTest` 성공으로 기존/신규 계측 테스트 컴파일과 테스트 APK 생성을 확인했다.
    - 연결 기기가 없어 `connectedQaAndroidTest` 실제 실행은 보류했다.

19. **migration 테스트 결과**
    - v1→v2→v3 기존 MealRecord/CalorieGoal 보존 및 v3 테이블 생성 검증 코드를 추가했다.
    - 날짜별 적용과 같은 날짜 저장 중복 방지 Room 테스트를 추가했다.
    - 테스트 APK 컴파일 성공. 실제 실행은 기기 미연결로 보류했다.

20. **Lint 결과**
    - `lintDebug`: 오류 0, 경고 48.
    - `lintQa`: 오류 0, 경고 48.
    - 작업 전 보고된 기존 경고 48개와 동일해 신규 경고 증가는 없다.

21. **Debug 및 QA 빌드 결과**
    - `assembleDebug`, `assembleQa`, `assembleQaAndroidTest` 모두 성공.

22. **최종 APK 경로**
    - Debug: `app/build/outputs/apk/debug/app-debug.apk`
    - QA: `app/build/outputs/apk/qa/app-qa.apk`
    - QA test: `app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk`

23. **최종 APK SHA-256**
    - Debug: `6DE151E5B1F8CAB210D2A6AFAEA104EBD1B790F3426FAEF47B25A735EF31F571`
    - QA: `DB5E392ADAD12BF931633744C23DA60737DBF205CA6BC386541074357900557D`
    - QA test: `9DF3058F9AB737E13BE889D99BCDAB23657238B88DD97B5F123149621DD07BD9`

24. **테스트하지 못한 항목과 이유**
    - `connectedQaAndroidTest`, migration 실제 실행, force-stop 재실행, 실기기 작은 화면·큰 글자·TalkBack: ADB 연결 기기 없음.
    - backend pytest: 사용 가능한 Python 환경에 pytest 모듈이 없어 실행하지 못했다. 백엔드 파일은 변경하지 않았다.
    - Debug unit task: 현재 실제 Gradle task에 존재하지 않는다.

25. **남아 있는 정확도 또는 사용자 오해 위험**
    - PAL과 7,700kcal/kg 환산은 개인별 대사·수분·활동 변화를 반영하지 않는 단순 추정이다.
    - 앱은 이 값을 실제 체중이나 건강 기록으로 저장하지 않으며 UI에 한계를 명시한다.

26. **사용자가 직접 확인할 검수 순서**
    1. 기존 앱을 업데이트하고 기존 식사 기록·직접 목표가 남아 있는지 확인한다.
    2. Dashboard에서 BMR 미설정 안내 카드를 누른다.
    3. BMR 1500, 가벼운 활동을 선택해 유지 칼로리 2,325kcal를 확인한다.
    4. MANUAL/BMR/MAINTENANCE 모드를 각각 선택해 Dashboard 목표가 2,000/1,500/2,325로 바뀌는지 확인한다.
    5. 오늘 섭취 2,000kcal에서는 `유지 기준 이내`이고 환산 카드가 없는지 확인한다.
    6. 오늘 섭취 2,625kcal에서는 초과 300kcal, 7일 약 +0.3kg, 30일 약 +1.2kg인지 확인한다.
    7. 앱을 force-stop 후 다시 열어 설정과 계산 결과가 유지되는지 확인한다.
    8. 기록 추가·수정·삭제, 사진 참고 기록, CameraX 진입이 기존대로 동작하는지 확인한다.
