# 디자인 참조 이미지 보관

`low-resolution-originals/`에는 승인 시안에서 추출한 저해상도 참조 이미지 17개를
원본 파일명으로 보관합니다. 이 파일들은 역할·크롭·색감 비교용이며 Android resource나
최종 APK에 포함되지 않습니다.

앱은 `app/src/main/res/drawable-nodpi/ref_*_hq.png`만 사용합니다. 최종 자산은 단순
Lanczos 확대본이 아니라 EDSR x4 super-resolution 처리 후 목표 크기로 정리했으며,
온보딩 이미지는 래스터 글자를 제거한 뒤 Compose의 네이티브 텍스트를 겹쳐 렌더링합니다.

재생성 도구:

- `tools/enhance_reference_assets.py`
- `tools/clean_onboarding_reference.py`

저해상도 참조 파일을 다시 `res/`에 넣지 않습니다. 앱 리소스에 함께 두면 사용되지 않는
자산이 APK에 포함될 수 있고 Android Lint의 `UnusedResources` 경고가 증가합니다.
