# K-FIND 단위 안전성 / TalkBack QA 결과

검수일: 2026-09-20  
최종 상태: **ACCESSIBILITY_REVIEW_REQUIRED** — 데이터 전수 감사 및 QA 계측 테스트 완료. TalkBack 실제 읽기 순서에 대한 사용자 확인이 없어 전체 흐름은 미완료.

## 데이터 감사

- 원본: `data-source/kfind/kfind-food-db-2026-08-28.xlsx` (`음식(19,617건)`); 작업 전후 SHA-256 `1EF3551F9A1D0EE87891D6306FA22BBD6A7FFCC90F2C70A4A184DBFE3FCE6EA6`로 동일.
- 앱 자산: `app/src/main/assets/fooddata/food_items.csv`; 원본/앱 각각 19,617행. 식품코드로 이름·대분류·기준량·단위·kcal를 대조했으며 불일치 0건, 확인된 가져오기 오류 0건, 확인된 원본 값 오류 0건.
- 고형 범주 ml 2,382행: 동일 이름 g 대안 728, 부피 기준만 있어 미해결 1,623, 카테고리 수동 검토 31, 적합한 부피 분량 확정 0, 검증된 생활 단위 대안 0, 미분류 0.
- 의심 ml 항목 직접 기록 차단 2,382, 검색 순위만 낮추고 직접 기록 허용한 항목 0. 같은 이름의 g 행이 있어도 다른 영양값을 ml 행에 복사하지 않는다.
- `김밥_참치`의 원본 `100ml`/`128kcal` 보존. 별도 `100g`/`174kcal` 후보가 `참치김밥`과 `참치 김밥` 검색에서 먼저 나온다.
- 18개 한글 검색어 모두 후보가 있으며, 안전하지 않은 ml 후보가 안전 후보보다 앞선 검색은 0건. 각 검색어의 첫 음식명·단위·분류·기록 가능 여부는 `kfind-search-regression.md`에 기록.
- 재현 도구: `tools/audit_kfind_serving_units.py`, `tools/audit_kfind_search_results.py`; 항목별 CSV `kfind-serving-unit-audit.csv`, 불일치 CSV `kfind-serving-unit-discrepancies.csv`, 요약 `kfind-serving-unit-audit-summary.md`.
- 데이터 감사 자동 테스트 5/5 통과. QA 앱의 전체 ml 행 정책 계측 테스트 1/1 통과. 원본 Excel, 원본 kcal, Room 스키마, 제품 앱 데이터는 수정하지 않음.

## 코드 및 자동 검증

- 변경: `AddRecordViewModel.selectVerifiedFood`에 고형 ml 행 직접 선택 방어선을 추가. 검색 UI에는 확인 필요 설명과 음식 직접 등록 경로를 추가. 해당 ViewModel 단위 테스트와 전수 정책 계측 테스트 추가.
- `testQaUnitTest`: 80/80 통과.
- `lintQa`, `lintDebug`: 성공, 각 오류 0/기존 경고 46.
- `assembleQa`, `assembleDebug`, `assembleQaAndroidTest`: 성공.
- `connectedQaAndroidTest`: 첫 실행은 기기가 `Dozing`이고 전면이 NotificationShade/런처여서 Compose 화면 테스트 21개가 화면 계층을 찾지 못했다. 사용자가 잠금을 풀고 QA 앱을 전면에 놓은 뒤 같은 테스트를 재실행해 **33/33 통과**, 실패 0개. 4개 `BundledFoodDataTest`와 4개 migration 테스트도 포함된다.
- 최종 QA APK: `app/build/outputs/apk/qa/app-qa.apk`; SHA-256 `781A37979F6A93215BB9163B8AEE1805DC6392D2DBAAAFBA34F157163C252C82`. 계측 테스트 후 QA 앱만 `adb install -r`로 다시 설치함.

## 실기기 및 개인정보 보호

- 기기: Samsung SM-S948N, Android 16, serial `R5KL20HFPAK`, ADB `device`.
- 제품 앱 `com.example.healthcare`: versionCode 1, versionName 1.0, firstInstallTime 전후 `2026-09-15 21:34:39`, lastUpdateTime 전후 `2026-09-15 21:34:39`. 제품 앱 실행·DB 조회·초기화·계측 테스트를 하지 않았다.
- QA 패키지: `com.example.healthcare.qa` 설치됨.
- 이전 시도의 TalkBack 원래 설정은 `enabled_accessibility_services=null`, `accessibility_enabled=0`이었고, 이전 시도 종료 시 복구했다. 이번 검수는 사용자가 직접 Samsung TalkBack을 켰고, 이후 사용자가 직접 껐다. 종료 시 `accessibility_enabled=0`, QA 앱 전면 및 ADB `device` 상태를 확인했다. 에이전트는 시스템 설정을 조작하지 않았다.
- 다른 앱 또는 잠금 화면의 UI를 캡처하지 않았다. 모든 QA UI 조회·입력 전에 `com.example.healthcare.qa` 전면 여부를 확인했다.

## TalkBack 활성 상태의 QA 앱 구조 검사 (이번 검수)

이 표는 활성 TalkBack 환경에서 QA 앱의 접근성 노드와 화면 이동을 자동 확인한 결과다. **실제로 재생된 음성을 청취하거나 사용자가 들은 순서를 확인한 결과가 아니므로 음성 QA 통과 판정이 아니다.**

| 화면/흐름 | 접근성 노드 및 동작 확인 | 남은 실제 음성 검수 |
| --- | --- | --- |
| Dashboard | 인사 heading, 오늘 섭취·남은 양·목표·스마트 기록, 아래쪽 오늘 코치의 목표·섭취·남은 양·다음 식사·추천 보기 노출 | 음성 순서와 그룹 읽기 미확인. 상단 요약 노드에서는 남은 양이 목표보다 먼저 나타남 |
| 스마트 기록 | 사진·바코드·영양성분표·음식 검색·직접 입력의 제목과 설명 노출 | 실제 읽기 순서·역할 미확인 |
| 음식 검색 | 검색 입력명·결과 카드의 음식명·기준량·kcal·분류·출처 노출. 영문 검색어 `labbq`로 별도 g 후보 확인 | 한글 음성 발음과 고형 ml 확인 필요 상태의 실제 읽기 미확인. ADB 한글 입력 명령 오류 |
| 검색 결과 없음 | `zzzz` 검색에서 빈 상태 제목·설명·직접 입력 경로 노출 | 오류/빈 상태 자동 음성 안내 미확인 |
| 안전한 음식 선택·분량 | g 후보 선택 후 조금·보통·많이, `보통` 선택 시 checked 상태와 `약 308 kcal`·‘일반적인 1인분이 아님’ 설명 노출 | TalkBack이 ‘선택됨’으로 말하는지 미확인 |
| 직접 양 입력 | `더 정확히 입력하기` 후 100 g 입력란과 설명 노출 | 입력란 실제 읽기·키보드 탐색 미확인 |
| 직접 기록·오류 | 빈 저장 시 음식명·칼로리 필드 오류 문구 노출. QA 임시 기록 저장 후 Dashboard 200 kcal/1건 갱신, 이전 입력 폼 노드 사라짐 | 오류 자동 읽기, 저장 완료 발화, Dashboard 초점 이동 미확인 |
| 기록 목록·상세·편집 | QA 임시 기록의 이름·시간·kcal, 상세의 날짜·시간·편집·삭제, 편집 필드·분량·저장 노출. 임시 기록 삭제 후 0건/0 kcal 복귀 | 실제 음성 순서·편집 상태 미확인 |
| Settings·에너지 | BMR·활동 수준·유지 칼로리·목표 모드의 제목·설명 노출 | 실제 읽기·선택 상태·오류 미확인 |
| 추천 | Dashboard 추천 보기에서 `조건에 맞는 검증 식단이 없어요` 빈 상태 노출 | 추천 상세·교체·먹은 비율·최종 저장을 진행할 후보가 없어 미검증 |
| Bottom Navigation | 홈·내역·추가·설정 이동 및 각 항목 노출 | 실제 선택 발화 순서 미확인 |

QA 임시 기록 이름은 `QATalkBackTest`였으며, 검수 중 만든 이 기록만 앱의 삭제 동작으로 제거했다. 제품 앱과 기존 데이터는 변경하지 않았다. 코드·K-FIND 자산·Room·추천 계산·CameraX에는 이번 검수에서 변경이 없다.
기기 공유 저장소에 이번 검사 중 생성한 `/sdcard/qa-*.xml` UI 계층 임시 파일을 목록 확인 후 모두 제거했다. 이 임시 사본은 복구되지 않으며 앱 기록과는 별개다. 사용자가 TalkBack을 직접 끈 후에도 QA 앱이 전면에 있음을 확인했다.

## 남은 검수 및 위험

1. 실제 음성을 들을 수 있는 감독자가 20개 핵심 화면의 읽기 순서, 선택 상태, 오류 안내 및 저장 후 Dashboard 초점을 확인한다. 이번 자동 구조 검사만으로 통과로 기록하지 않는다.
2. `CATEGORY_REVIEW_REQUIRED` 31개 및 `VOLUME_ONLY_UNRESOLVED` 1,623개에 대해 원본 측정 규약을 별도 조사한다. 현재 직접 기록 차단은 유지한다.
3. 부분 일치 검색의 음식 적합성(예: `사과` 첫 후보가 사과차)은 단위 안전성과 별개로 후속 품질 검토가 필요하다.

실제 TalkBack 음성 검수 전에는 `COMPLETE`가 아니다. 사용자의 선택에 따라 음성 검수는 중단했으며, 사용자가 TalkBack을 직접 끈 상태를 확인했다.
