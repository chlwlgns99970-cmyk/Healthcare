# Healthcare — Warm Editorial Food Coach UI/UX 리디자인 결과

검수일: 2026-09-20 · 실기기: Samsung SM-S948N, Android 16 · QA 패키지: `com.example.healthcare.qa`

## 실제 화면 목록과 적용 범위

| 실제 경로/화면 | 주요 변경 | 확인 |
| --- | --- | --- |
| 홈 / `DashboardScreen` | 날짜·상태 중심 헤더, 큰 오늘 섭취 카드, 빠른 기록, 다음 식사, 오늘 기록 | QA 실기기 라이트·다크 |
| 추천 / `MealPlanScreen` | 식사 예산과 추천 카드를 에디토리얼 카드로 재구성, 기존 추천·기록 계산 유지 | QA 실기기 다크, 계측 테스트 |
| 기록 허브 / `SmartFoodInputScreen` | 음식 검색 우선, 사진·바코드·영양성분표·직접 입력의 행동 위계 재정리 | QA 실기기 다크, 검색 계측 테스트 |
| 음식 검색·분량 선택 / `SmartFoodInputScreen`, `PortionSelector` | 검색 결과 카드와 시각적 분량 카드, 선택 상태·예상 kcal 표시 | 360dp 계측 테스트 |
| 직접 기록 / `WellnessManualRecordScreen` | 음식 → 양 → 식사·시간 → 최종 확인의 4단계 안내; 기존 한 화면 상세 입력 유지 | QA 실기기 다크, 계측 테스트 |
| 사진 기록 미리보기 / `WellnessManualRecordScreen` | 큰 둥근 사진, 재촬영·사진 사용 행동 정리; 수동 입력 흐름 유지 | Compose Preview·빌드, 실물 촬영은 이번 검수에서 미실행 |
| CameraX / `FoodCameraScreen` | 몰입형 다크 UI와 셔터·안내 표시 조정 | 빌드, 실제 촬영은 이번 검수에서 미실행 |
| 내역 목록·상세·편집 / `HistoryScreen` | 날짜 스트립, 식사 유형 묶음, 영수증형 상세; 기존 편집 폼 테마 통일 | QA 실기기 목록, 상세·편집·삭제 계측 테스트 |
| 설정·목표 / `SettingsScreen` | 프로필형 헤더, 에너지·목표 설명과 섹션 정리 | QA 실기기 다크, 에너지 계측 테스트 |
| 식사 선호 / `MealPreferenceScreen` | 톤 카드와 섹션 구분 | Compose Preview·빌드, 직접 실기기 탐색 미실행 |

하단 도크는 홈·추천·기록·내역·설정의 5개 항목으로 통일했고, 가운데 기록을 주요 행동으로 강조했다. 기존 화면 간 실제 경로와 저장·검색·추천 동작은 유지했다. 사용하지 않는 레거시 `ManualRecordScreen`을 새 경로로 노출하지 않았으며, 일반 흐름에서 비활성인 사진 자동 분석 UI는 기능을 다시 연결하지 않았다.

## 디자인 시스템

- 밝은 종이색 배경, 잉크 네이비 정보 카드, 코럴 행동, 올리브 보조 상태를 Theme 색상 토큰으로 통합했다. 다크 모드는 동일 위계를 어두운 배경으로 대응한다.
- 큰 핵심 숫자, 화면·섹션·본문·보조 설명의 타이포그래피 계층, 둥근 카드/필드/버튼, 공통 간격과 저고도 표면을 적용했다.
- 데이터가 없을 때 빈 카드를 유지하고, 계산되지 않은 추천을 임의로 만들지 않는다. 분량 추정은 계속 대략값으로 설명한다.
- 선택된 분량·도크 상태는 색상 외 텍스트/아이콘/선택 semantics도 사용한다. 터치 영역은 최소 48dp를 기준으로 유지했다.

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| `testQaUnitTest` | 80/80 통과 |
| `connectedQaAndroidTest` | SM-S948N에서 36/36 통과, 실패·건너뜀 0 |
| `lintQa`, `lintDebug` | 각각 오류 0, 경고 46개. 기존 경고 수준이며 신규 치명 경고 없음 |
| `assembleQa`, `assembleDebug`, `assembleQaAndroidTest` | 성공 |
| `testDebugUnitTest` | Gradle에 해당 task가 없어 실행 불가. 로컬 단위 테스트는 QA 변형으로 실행 |
| QA 앱 실기기 설치·실행 | 성공. 제품 앱 실행·계측·데이터 접근 미실행 |
| 실기기 화면 폭 | 기본 약 384dp, 임시 밀도 360/390/약 412dp 홈 렌더링 확인. 가로 넘침·하단 탭 잘림 없음 |
| 시스템 테마 | 라이트·다크 홈 렌더링 확인. 원래 다크 모드와 물리 밀도 600으로 복원 |
| 360dp 큰 글꼴 | `EditorialUiTest`에서 1.3배 글꼴의 단계 입력 흐름 통과 |

계측 과정에서 `HistoryScreen`의 적응형 상세 탐색 키에 Room `MealRecord` 객체 전체를 저장하면 Activity 종료 시 예외가 발생함을 발견했다. 저장 가능한 기록 ID만 탐색 키로 보유하도록 고쳐 재검증했다. 기존 QA 테스트는 동일 이름의 과거 시험 기록과 상세 화면 하단 삭제 버튼을 올바르게 구분·스크롤하도록 보강했다. 분량 카드와 최종 kcal의 동일한 텍스트도 구분했다. 데이터 모델, DB 스키마, 계산식, CameraX 촬영 로직, 백엔드, 패키지 ID, 의존성은 변경하지 않았다.

## 산출물

- QA APK: `app/build/outputs/apk/qa/app-qa.apk`
- QA APK SHA-256: `208E8922F96C3AC964E49D11249FB31C335A3CFCC77DC28AD8A5B792434A0FE9`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Debug APK SHA-256: `AF82B50BA17E05FAA6A3B50EBBD73AECCEC22845E471FE157FB99F17F3F4153A`
- 실기기 화면: `app/build/reports/healthcare-qa-redesign-{home,light-home,recommendations,record,record-lower,guided,history,settings,360dp,390dp,412dp}.png`

## 제한과 남은 검수

- 이번 회차에는 실제 카메라 촬영/사진 저장, 식사 선호의 수동 탐색, 전체 화면의 라이트·다크 양쪽 조합, 실제 TalkBack 음성 순서를 확인하지 않았다. 이전 TalkBack 사용성 검수와 이 회차의 Compose semantics 테스트를 혼동하지 않는다.
- 360/390/412dp는 홈의 실기기 화면과 360dp 계측 테스트를 확인했다. 모든 화면을 세 폭에서 일일이 촬영한 결과는 아니다.
- 빌드는 제품(Debug) APK까지 성공했지만 제품 앱을 설치·실행하지 않았다. 제품 데이터 보존을 위해 QA 패키지만 실기기 검수했다.
