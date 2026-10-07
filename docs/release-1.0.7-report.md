# 오늘 뭐먹지 1.0.7 정식 배포 결과

1. STATUS: RELEASED. 아이콘 적용부터 GitHub/Supabase/Vercel 공개 및 Samsung 안전 업데이트까지 완료.
2. 최종 버전: versionCode8 / versionName1.0.7.
3. Git: main, release commit `af7a2b3b29f3b7236ddc12124d8c498504171582`, tag v1.0.7. main/tag push 완료. Metadata와 본 보고서는 후속 문서 commit에 포함.
4. 아이콘: 사용자 원본 `뭘 먹을까_ 건강한 한 끼 고민.png` 사용. 원본과 1024x1024 master는 artifacts/app-icon. built-in image_gen으로 검은 프레임 제거·주방 배경 확장·구도 여유 확보. main adaptive/legacy 및 web/public/app-icon.png 공통 적용. 최종 프롬프트는 artifacts/app-icon/README.md.
5. Samsung: SM-S948N Android16/API36, QA 홈/앱서랍/앱정보 PASS. 최근 앱 새 아이콘 확인. 흰 외곽0/검은 모서리0/투명 외곽0. 얼굴·물음표·세 음식 표시.
6. DB: Room8, migration 추가0. QA DB8 및 기존 테이블/rows 복원 동일.
7. Food: 67,357. 이전 전체 상세 모델67,357/67,357 PASS 재사용. 최종 APK asset 모두 소스와 SHA 동일, Food총수 직접 확인.
8. Recipe: total516/complete426/known residual90 유지.
9. 테스트: 이전 전체 Unit593/Samsung365/23조건 PASS 재사용. 이번 업데이트 Unit38 PASS, Samsung targeted8 PASS. QA/test APK/Release 빌드 PASS. 전체 Lint/전체 instrumentation 반복 없음.
10. Release signer: 기존 signer 일치 YES. 385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8.
11. APK: `dist/v1.0.7/today-mwo-meokji-v1.0.7.apk`, 134168293bytes. SHA256 `C6B2919EE5B01DEBCE21B18AEE65EE8612D25BC8A9CA5805C0857E80384F5DA7`. applicationId com.example.healthcare, label 오늘 뭐먹지, minSdk24/targetSdk37. QA label 오늘 뭐먹지 QA.
12. GitHub: tag v1.0.7, Release/서명APK 공개 완료. https://github.com/chlwlgns99970-cmyk/Healthcare/releases/tag/v1.0.7
13. Supabase: latest1.0.6/code7→1.0.7/code8. 기존5row 유지, 새row1개 추가. 기존row의 is_latest외 변경0. schema/권한/RLS 변경0. 직접 조회검증 PASS.
14. Vercel: https://today-mwo-meokji.vercel.app 및 /api/releases/latest 1.0.7 표시, 기존 URL 유지. 다운로드 GitHub asset/SHA/크기 일치. 공개 웹 아이콘도 새 master와 SHA 동일.
15. 공개 APK: 인증 없이 실제 재다운로드, 8/1.0.7/applicationId/signer/SHA/크기 모두 PASS. 설치된 제품 APK도 같은 SHA.
16. Updater: 제품1.0.6 수동 확인에서 새1.0.7 인식 PASS. 1.0.7 cold start 정상 실행. 1.0.7 수동 확인에서 최신 버전 표시 PASS. cold-start/foreground 중복 방지는 공통 소스 및 targeted Unit으로 확인; 실제 요청 수를 별도로 계측하지 않음.
17. Production update: Android 시스템 설치 완료 확인, 1.0.6→1.0.7 PASS. firstInstallTime2026-09-21 00:10:35 유지. installer/initiating com.google.android.packageinstaller, originating com.example.healthcare. 제품 adb install/uninstall/pm clear/DB접근/테스트기록쓰기 모두0. 설치 확인 버튼 누른 순간은 캡처하지 못함. 기존10월4일 기록 표시 확인. 제품 개인 DB 전체 byte 비교는 미실행이며 Android update와 기존 기록 표시 확인 범위로 보고.
18. Smoke: 제품 startup/Home/search/spaghetti detail/back query+result retention/recommendation/statistics/existing record PASS. 실제 저장·양입력·저장 완료·history edit·몸무게 팝업은 QA targeted PASS. 사용자 기록 보호를 위해 제품 저장/편집은 미실행.
19. 수정 파일: 상세 release 파일 목록 docs/release-1.0.7-files.txt. 이번 준비 주요 파일 app/build.gradle.kts, main launcher PNG/XML, 새 drawable/ic_launcher_foreground.xml, web/public/app-icon.png, artifacts/app-icon/*, tools/qa_recipe_final_residual_preservation.py(QA releasefolder 허용), docs/release-1.0.7-validation.md, 본 보고서, Supabase20261007000100_publish_v1_0_7.sql. 이전 검증한 기능/데이터/test/tools/report 변경도 release commit 포함. 대용량 raw·임시 감사 파일은 로컬 보존, 삭제0.
20. 발견 문제: 최초 원본 미첨부로 중단했으나 사용자 원본 제공 후 해소. 원본 검은 둥근 프레임과 mask crop 여유를 image_gen 편집으로 해결. Windows 기본 문자 인코딩 오류를 UTF-8로 해결. 기존 EOF 빈줄6건은 기능 변경 없이 보존했고 별도 whitespace 옵션으로 다른 staged 오류0 확인.
21. Known limitations: Recipe426/516, residual90. 기존 Lint QA0errors/68warnings, Debug0/61. 일부 음식 미확인 영양/원재료는 확인 가능한 범위만 표시.
22. 남은 위험/검증 한계: 모든 제품 개인 DB row를 전후 byte 비교하지 않았음. 제품 저장/편집은 QA 검증으로 대체. 설치 확인 gesture 미캡처. 자동 업데이트 HTTP요청수 실계측 없음. QA 원본17파일은 전체 byte 동일, firstInstallTime 유지하며 마지막에 force-stop 상태로 복원.
23. 사용자 확인 순서: (1)제품 앱 정보에서1.0.7 확인 (2)Launcher 새 아이콘·홈 kcal 확인 (3)이전 날짜의 기존 기록/설정 확인 (4)필요한 실제 식사를 검색·저장하고 저장 완료/확인 확인 (5)앱 정보의 업데이트 확인에서 최신 버전 안내 확인.

상세 실행 증거는 Git에서 제외된 app/build/release-1.0.7에 보관. 비밀번호/키 출력 또는 commit 없음.
