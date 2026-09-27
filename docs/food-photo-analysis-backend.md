# 음식 사진 분석 백엔드 연결

> 현재 앱의 일반 사용자 흐름에서는 자동 사진 분석을 사용하지 않는다. 촬영 사진은
> 기기 안에서 수동 음식 기록의 참고 자료로만 표시되고 저장·취소 후 삭제되며, 이
> 백엔드로 전송되지 않는다. 아래 내용은 향후 명시적으로 자동 분석 기능을 다시 활성화할
> 경우를 위해 보존한 서버 계약과 운영 지침이다.

이 저장소에는 `backend`의 FastAPI 서버와 Android Retrofit 클라이언트가 함께 있다.
자동 분석 기능을 활성화한 Android 앱은 자체 백엔드의 `POST /food-photo/analyze`만 호출하며 OpenAI API 키를
APK, BuildConfig, 리소스 또는 `local.properties`에 저장하지 않는다.

## 실행과 배포

로컬 실행 및 Docker 명령은 `backend/README.md`를 따른다. 서버는 플랫폼의 `PORT`
환경변수(기본값 8000)를 읽고 `0.0.0.0`에 바인딩한다. 실제 기기 앱이 접근할
주소는 반드시 신뢰할 수 있는 HTTPS 도메인이어야 한다. 배포한 뒤 환경변수 또는
Gradle 속성 `FOOD_ANALYSIS_BASE_URL=https://api.example.com/`을 주입해 빌드한다.

```powershell
$env:FOOD_ANALYSIS_BASE_URL='https://api.example.com/'
.\gradlew.bat assembleDebug
```

Debug 빌드는 주소가 비어 있으면 명시적인 미설정 상태를 유지한다. Release 빌드는
유효한 HTTPS URL이 없거나 localhost, `.local`, 루프백·링크 로컬·사설 IP 주소이면
`validateFoodAnalysisReleaseConfig`에서 실패한다.

## 서버 환경변수

필수:

- `OPENAI_API_KEY`: 서버 비밀 저장소에서 주입하는 OpenAI 프로젝트 키
- `OPENAI_MODEL`: 이미지 입력과 구조화 출력을 지원하는 모델 ID
- `PORT`: 호스팅 플랫폼이 주입하는 수신 포트(기본 8000)
- `FOOD_ANALYSIS_FORWARDED_ALLOW_IPS`: 신뢰하는 리버스 프록시 IP 목록; `*` 금지

제한/운영 설정:

- `FOOD_ANALYSIS_REQUEST_TIMEOUT_SECONDS`
- `FOOD_ANALYSIS_MAX_IMAGE_BYTES`
- `FOOD_ANALYSIS_MAX_IMAGE_DIMENSION`
- `FOOD_ANALYSIS_MAX_IMAGE_PIXELS`
- `FOOD_ANALYSIS_RATE_LIMIT` (`20/minute` 형식)
- `FOOD_ANALYSIS_DAILY_REQUEST_LIMIT`
- `FOOD_ANALYSIS_ENVIRONMENT`
- `FOOD_ANALYSIS_IDEMPOTENCY_TTL_SECONDS`
- `FOOD_ANALYSIS_PROVIDER_MAX_RETRIES`
- `FOOD_ANALYSIS_MAX_OUTPUT_TOKENS`
- `FOOD_ANALYSIS_MAX_ITEMS`

## API 계약과 보호 장치

`GET /health`는 프로세스의 생존 상태를, `GET /ready`는 공급자 자격증명과 모델이
구성됐는지를 반환한다. 두 응답 모두 키와 모델 ID 전문을 반환하지 않는다. 설정이
없으면 readiness는 `503 not_ready`이다.

`POST /food-photo/analyze`는 `multipart/form-data`의 `image`, `requestId`, `locale`,
`timezone`, `capturedAt`을 받는다. 응답에는 `analysisId`, 원래 `requestId`,
`SUCCESS|NO_FOOD|UNCERTAIN` 상태, 음식 항목, 서버가 다시 계산한 총칼로리,
경고, `modelVersion`, `analyzedAt`이 포함된다.

서버는 선언된 MIME 타입뿐 아니라 Pillow 디코딩 결과와 실제 이미지 형식을 확인한다.
JPEG/PNG만 허용하고 크기·픽셀 수를 제한하며, RGB JPEG로 축소 재인코딩해 EXIF와
위치정보를 제거한다. 원본과 Base64, 음식명, 칼로리는 로그에 기록하지 않는다.
FastAPI 업로드 파일은 성공·실패와 관계없이 `finally`에서 닫히며 별도 원본 파일을
장기 보관하지 않는다.

OpenAI 호출은 Responses API 이미지 입력과 엄격한 JSON Schema 출력을 사용하고
`store=false`로 전송한다. 공급자 결과는 Pydantic과 서버 비즈니스 규칙으로 다시
검증한다. 숫자 신뢰도는 만들지 않고 `confidenceLevel`만 받는다. 총합은 개별 항목에서
서버가 계산하며 일부 범위가 없으면 총 최소/최대값도 만들지 않는다.

`store=false`는 Responses API 응답 객체의 저장을 요청하지 않는 설정이지 Zero Data
Retention을 보장하는 문구가 아니다. 실제 공급자 측 처리·보관 기간은 배포에 사용하는
OpenAI 조직/프로젝트의 데이터 제어 설정과 적용 정책을 별도로 확인해야 한다.

같은 `requestId`와 같은 원본 이미지 해시는 짧은 TTL 동안 같은 결과를 반환한다.
같은 ID에 다른 이미지가 오면 `409 REQUEST_ID_CONFLICT`를 반환한다. 현재 구현은
단일 프로세스 메모리 저장소이므로 다중 인스턴스 운영 전 Redis/DB 구현으로 교체한다.

표준 오류 본문은 다음과 같다.

```json
{
  "error": {
    "code": "IMAGE_TOO_LARGE",
    "message": "업로드한 이미지가 허용 크기를 초과했습니다.",
    "retryable": false,
    "requestId": "client-request-id"
  }
}
```

로그에는 마스킹된 requestId, 결과 상태, 처리 시간, 오류 코드만 남긴다. 리버스
프록시를 사용하는 경우 `FOOD_ANALYSIS_FORWARDED_ALLOW_IPS`에는 실제 신뢰 프록시만
지정해야 한다. 잘못 지정하면 모든 사용자가 같은 IP로 제한되거나 전달 헤더를 위조할
수 있다. 현재 앱에는
계정 인증이 없으므로 APK 고정 비밀값을 추가하지 않았다. 운영 전 공유 속도 제한,
일일 예산·경보, WAF, 모니터링, Play Integrity 검증을 추가해야 한다. 공급자 측 데이터
보관 조건은 사용하는 OpenAI 조직/프로젝트의 최신 데이터 제어 설정과 정책을 배포 시
확인한다.
