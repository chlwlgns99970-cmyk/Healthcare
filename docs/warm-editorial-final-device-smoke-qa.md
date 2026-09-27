# Healthcare Warm Editorial Food Coach — 최종 실기기 스모크 QA

검수일: 2026-09-20 · 상태: **COMPLETE** (지정된 CameraX·식사 선호 두 흐름 완료)

## 환경과 보호 범위

- Samsung SM-S948N, Android 16/API 36, ADB `R5KL20HFPAK device`.
- QA 앱 `com.example.healthcare.qa`만 실행·조작했다. 설치된 QA `base.apk`와 로컬 `app/build/outputs/apk/qa/app-qa.apk`의 SHA-256이 모두 `208E8922F96C3AC964E49D11249FB31C335A3CFCC77DC28AD8A5B792434A0FE9`로 일치했다.
- 제품 앱 `com.example.healthcare`는 실행·계측·삭제·초기화·DB 조회·권한 변경하지 않았다. 허용된 패키지 메타데이터만 확인했다. 검수 후에도 설치 상태, versionCode 1, versionName 1.0, firstInstallTime 및 lastUpdateTime `2026-09-15 21:34:39`가 유지됐다.
- 모든 자동 탭·입력 및 화면 조회 직전에 전면 창이 QA 패키지인지 확인했다. 개인정보가 없는 단색 표면을 촬영했다. 다른 앱 전면 전환이나 기기 연결 해제는 없었다.
- 실기기 기본 물리 크기 1440×3120, 밀도 600dpi(384dp). 확인을 위해 잠시 적용한 640dpi(360dp)와 라이트 모드는 검수 후 각각 물리 밀도 600dpi, 원래 다크 모드로 복원했다.

## CameraX → 사진 참고 기록

| 검수 항목 | 실기기 결과 |
| --- | --- |
| 카메라 진입/표시 | QA 기록 허브의 “사진으로 기록”에서 CameraX가 열렸다. 실시간 미리보기가 움직였고 검은 화면·정지·시스템 바와 버튼의 겹침이 없었다. 닫기, 플래시, 안내, 중앙 셔터가 보였다. 자동 분석·자동 무게 측정 문구는 없었다. |
| 첫 촬영/미리보기 | 셔터 한 번으로 임시 사진 1개와 큰 미리보기가 나타났다. 방향·비율이 자연스러웠고 “이 사진 사용”·“다시 촬영”이 보였다. |
| 재촬영 | “다시 촬영”으로 CameraX에 복귀했고 첫 임시 사진이 삭제됐다. 두 번째 촬영·미리보기·“이 사진 사용”까지 성공했다. |
| 수동 기록 | 큰 사진 아래 음식명, 먹은 양, 식사·시간, 최종 kcal 입력의 순서로 진행했다. 사진만으로 자동 분석한다는 표현은 없었다. |
| 저장 | 기존 오늘 0 kcal·0건에서 QA 시험 기록 `QA_Photo_Check` 1 cup, 120 kcal 한 건을 저장했다. 홈 도크가 선택된 Dashboard로 이동해 120 kcal·1건, 남은 목표 1,880 kcal로 갱신됐다. |
| 재실행/초기화 | QA 앱만 force-stop 후 재실행해도 120 kcal·1건이 유지됐다. 새 직접 기록 폼은 비어 있었고 이전 사진은 남지 않았다. 뒤로 가기로 저장 완료 폼에 재진입하지 않았다. |
| 생활 분량 추정 | 위 QA 시험 기록을 “최근 음식”에서 선택한 별도의 **저장하지 않은** 사진 흐름에서 “조금” 약 60 kcal → “보통” 약 120 kcal로 즉시 바뀌는 것을 확인했다. 선택 카드에는 색상 외 체크 표시와 “저장된 기준량에 대한 대략적인 비율” 안내가 있었다. 이는 QA 수동 기준 120 kcal에 대한 UI/비율 검증이지 실제 식품의 영양 정확도 검증은 아니다. |
| 임시 파일 | 재촬영, 저장, 미리보기 취소, 카메라 닫기, 저장하지 않은 두 번째 사진 흐름 완전 종료 뒤 QA `cache/food_photo_captures`가 비어 있었다. MealRecord에 사진 파일을 영구 저장하지 않았다. |

증거 화면: [카메라](../app/build/reports/healthcare-qa-camera-preview.png), [첫 미리보기](../app/build/reports/healthcare-qa-photo-preview-1.png), [저장 후 홈](../app/build/reports/healthcare-qa-photo-saved-home.png), [분량 60 kcal](../app/build/reports/healthcare-qa-photo-portion-estimate.png), [분량 120 kcal](../app/build/reports/healthcare-qa-photo-portion-estimate-updated.png), [최종 홈](../app/build/reports/healthcare-qa-final-home.png).

한 번은 촬영 직후 미리보기 전환이 완료되기 전에 매우 빠르게 뒤로가기를 누르자 “사진을 촬영하지 못했습니다” 안내가 표시됐다. 정상적으로 “사진 확인” 화면이 표시된 뒤 취소한 동일 사용자 흐름에서는 재현되지 않았고 사진은 정리됐다. 두 번 재현되고 기능 완료를 막는 결함이라는 수정 기준을 충족하지 않아 코드를 변경하지 않았다.

## 식사 선호 → 저장·추천·복원

실제 화면의 모든 그룹을 확인했다. 하루 식사 구성(아침·점심·저녁·간식 켜기 및 각 비율, 원래 25/35/30/10·합계 100%), 식단 성향(일반/채식), 최대 조리 시간(10/20/30/60분), 식사 방식(모두/조리/외식/편의점), 예산(상관없음/낮음/보통/여유), 추천 다양성(익숙한 음식/균형/다양하게), 알레르기 음식·먹지 않는 음식 추가, 선호 음식 입력, 저장 버튼 및 의료적 안내가 존재했다. 원래 값은 일반·30분·모두·예산 상관없음·균형, 제외/선호 입력 없음이었다.

최대 조리 시간 **30→10분**, 식사 방식 **모두→편의점**만 변경해 “식사 추천 설정을 저장했습니다”를 확인했다. QA 앱을 force-stop·재실행한 뒤 두 칩의 선택이 유지됐다. 저녁 추천 화면은 조건에 맞는 검증 식단이 없다는 빈 상태와 조리 시간·칼로리 범위를 넓히라는 안내를 보여 줬으며 제한을 자동 완화하지 않았다. 후보가 없어 변경 전후 추천 카드의 실제 차이는 확인할 수 없었다. 별도의 기존 단위 테스트 `MealRecommendationEngineTest`에는 조리 시간 필터 코드와 편의점/예산 필터 확인이 있다. 이 결과를 실기기 후보 비교 성공으로 표현하지 않는다.

두 값을 **30분·모두**로 복원해 저장 안내를 확인했고, 다시 force-stop·재실행하여 원래 칩 상태가 유지됨을 확인했다. 알레르기·제외 음식에는 허위 값을 추가하지 않았으며, 하루 식사 비율·기타 설정도 변경하지 않았다.

384dp 다크·라이트, 360dp 다크 화면에서 제목·그룹·칩·도크의 가로 넘침이나 읽기 어려운 대비는 없었다. 선호 음식 입력창에 키보드가 정상 열렸지만 키보드가 열린 동안 하단 저장 버튼은 보이지 않아 키보드를 닫아야 했다. 실제 저장은 정상이다. 잘못된 입력 오류 및 실제 알레르기 추가/삭제는 데이터 보호를 위해 수행하지 않았다. 설정 칩은 채움/외곽선과 Compose 선택 상태로 구분되지만 선택 아이콘은 별도로 보이지 않았다.

증거 화면: [원래 조건](../app/build/reports/healthcare-qa-preferences-conditions.png), [변경 저장](../app/build/reports/healthcare-qa-pref-saved.png), [재실행 후 변경 유지](../app/build/reports/healthcare-qa-pref-persisted.png), [추천 빈 상태](../app/build/reports/healthcare-qa-recommend-changed-pref.png), [원래 값 재복원](../app/build/reports/healthcare-qa-pref-restored-after-restart-2.png), [라이트](../app/build/reports/healthcare-qa-pref-light.png), [360dp](../app/build/reports/healthcare-qa-pref-dark-360dp.png).

## 코드·자동 검증·남은 위험

- 이번 스모크 QA에서 제품/UI 코드, 테스트 코드, Room, CameraX, 백엔드, 의존성, 패키지 ID를 수정하지 않았다. 추가·수정한 것은 이 결과 문서뿐이다. 재현된 출시 차단 결함은 0건이다.
- 자동 검증은 **이번 회차 재실행한 결과가 아니다**. 직전 리디자인 검수의 `testQaUnitTest` 80/80, `connectedQaAndroidTest` 36/36, `lintQa`/`lintDebug` 오류 0·경고 각 46, `assembleQa`/`assembleDebug`/`assembleQaAndroidTest` 성공 결과를 인용한다. 코드 변경이 없어 전체 자동 테스트를 다시 실행하지 않았다.
- 최종 QA APK: `C:/Users/young/AndroidStudioProjects/Healthcare/app/build/outputs/apk/qa/app-qa.apk`; SHA-256은 위 설치본 확인 값과 같다.
- 접근성 semantics 및 360dp/384dp와 라이트·다크 화면 검수는 확인했다. **실제 TalkBack 전체 음성 읽기 순서는 이번 출시 범위에서 수동 검수하지 않았다.**
- 남은 위험: 검증 식단 부족으로 선호 조건별 실제 추천 후보 비교 불가, 빠른 촬영 직후 취소 때의 일회성 오류 안내, 키보드가 열린 채 저장 버튼 미노출, QA 임의 식품 기준량의 실제 영양 정확도 미검증. 이 중 반복 재현된 핵심 기능 실패는 없다.
