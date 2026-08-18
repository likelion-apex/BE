# AI 전체 스킨케어 루틴 분석

## 범위

- 구현: YouTube URL 입력, 비동기 분석, 상태 조회/취소, 전체 루틴 AI 브리핑, 단계별 상세, 인벤토리 최적화, TODAY/LIBRARY 저장, 최근 분석 목록
- 제외: 별도 분석 모드인 `핵심 제품 분석`
- 기존 `POST /api/v1/beauty-routines/analyze`는 Gemini 원본 추출 및 호환성 API로 유지한다.

## 환경변수

```text
GEMINI_API_KEY=...
OPENAI_API_KEY=...
YOUTUBE_API_KEY=...
MFDS_SERVICE_KEY=...
KAKAO_IMAGE_SEARCH_KEY=... # 선택
SHORTFORM_PRODUCT_CACHE_ENABLED=true # false면 제품·전성분 캐시 조회/저장 모두 우회
SHORTFORM_GEMINI_FALLBACK_ENABLED=true # 숏폼 OpenAI 장애 및 미확인 제품의 Gemini 폴백
```

비밀값은 저장소에 커밋하거나 API 응답·로그에 출력하지 않는다. `YOUTUBE_API_KEY`는 Gemini 호출 전에 공개 여부와 5분 제한을 확인하는 데 필요하다.

## 호출 순서

1. `POST /api/shortform-analyses/preview`로 썸네일, 제목, 게시자, 조회수, 영상 길이 확인
2. `POST /api/shortform-analyses`로 분석 시작
3. `GET /api/shortform-analyses/{analysisId}/status`를 `COMPLETED`까지 polling
4. `GET /api/shortform-analyses/{analysisId}`
5. 필요 시 `GET /api/shortform-analyses/{analysisId}/results/{resultId}`
6. `POST /api/shortform-analyses/{analysisId}/optimize`
7. `POST /api/shortform-analyses/{analysisId}/apply` with `saveType=TODAY|LIBRARY`, `routineType=DAY|NIGHT`
   - `routineType`을 생략한 기존 요청은 서울 시간 기준 06:00~17:59 `DAY`, 그 외 `NIGHT`로 저장됩니다.

분석 상태는 `PENDING → EXTRACTING_VIDEO → MATCHING_PRODUCTS → PERSONALIZING → OPTIMIZING → COMPLETED` 순서다. 사용자는 진행 중 `POST /cancel`로 취소할 수 있다.

미리보기 API는 분석 데이터를 생성하지 않는다. 미리보기와 분석 시작 시점 사이에 영상의 공개 상태나 길이가 바뀔 수 있으므로 분석 요청에서도 YouTube 정보를 다시 검증한다. 게시자는 YouTube 채널 제목이며 채널 핸들은 아니다.

분석 생성 시 YouTube 응답의 썸네일 URL을 분석 레코드에 저장한다. 최근 분석 목록은 저장된 URL만 조회하므로 목록을 열거나 새로고침할 때 YouTube Data API를 호출하지 않는다. 기존 분석은 `videoId` 기반 정적 썸네일 URL로 보정한다.

## AI와 데이터 근거

- 영상 단계·제품 식별: `gemini-3.6-flash`, 기존 프롬프트/검증기/캐시 재사용
- 개인화·점수·조합·인벤토리 추천: `gpt-4o-mini` 우선, 실패 시 동일 프롬프트·스키마로 Gemini 폴백
- 기존 분석의 optimize 맞춤 이유 생성도 OpenAI 실패 시 Gemini로 전환하며, 두 공급자가 모두 실패하면 확인된 제품·성분 기반 서버 문구를 사용한다.
- Gemini 영상 추출은 DB에도 모델·프롬프트 버전 기준으로 저장해 서버 재시작 후 재사용한다.
- OpenAI가 추천한 단계 번호와 inventory ID는 서버 입력 집합으로 다시 제한한다.
- 인벤토리 대체 추천은 영상 제품과 동일한 DB `ProductCategory`에서만 허용하며, 서버가 AI 응답을 다시 검증한다. `ETC` 또는 카테고리 불일치 추천은 영상 제품 유지로 처리한다.
- 같은 카테고리의 인벤토리 후보는 DB 성분과 제품 보강 캐시를 우선 사용하고, 근거가 없을 때만 제품 보강 API를 호출한다. 확인된 성분·효능만 개인화 입력과 대체 이유에 사용한다.
- 최적화 응답은 `REPLACED`와 `VIDEO_PRODUCT`만 반환한다. 대체 시 `productName`은 인벤토리 제품명, `replaceName`은 영상 제품명이며, 대체품이 없으면 `replaceName`은 `null`이다.
- 최적화 응답의 `overallScore`는 서버가 확정한 최종 제품 구성으로 다시 계산한다. 단계별 피부 타입 적합도 40점, 피부 고민·효능 적합도 35점, 성분 안전도 25점의 평균이며 원본 점수보다 낮아질 수도 있다.
- 최적화 응답의 `highlights`는 AI가 입력 성분 중 실제 점수 근거로 선택한 성분명을 서버가 검증·중복 제거한 맞춤 성분 수와, 최종 제품들의 알레르기 유발 성분 수를 최대 두 문장으로 제공한다.
- 제품 상세 이유의 공식 4단계는 `assessmentCategory`의 `SAFE`, `BENEFICIAL`, `CAUTION`, `WARNING`이다. `tone`은 하위 호환용 표현 분류로 SAFE/BENEFICIAL은 `POSITIVE`, CAUTION은 `CAUTION`, WARNING은 `WARNING`으로 반환한다.
- 단계 대표 판정이 CAUTION 또는 WARNING이면 상세 이유에도 같은 단계의 근거 카드가 반드시 포함된다. 기존 완료 분석도 조회 시 저장된 성분 통계와 성분 정보만으로 정규화하며 추가 AI 호출은 하지 않는다.
- `ingredientMarketOrVariant`는 기존 필드명을 유지하지만 `100ml`, `50g`처럼 확인된 단일 용량만 반환한다. 국가·판매처 문구는 제거하며 용량이 없거나 서로 충돌하면 `null`이다.
- 최적화의 제품 선택과 점수는 분석 시점 인벤토리 스냅샷을 유지한다. 신규 분석은 저장된 맞춤 이유와 재계산 점수를 추가 AI 호출 없이 반환한다. 최적화 결과 버전이 `3.5`보다 오래된 기존 분석만 최초 `POST /optimize`에서 현재 피부 프로필로 문구와 점수를 한 번 갱신하고 저장한다. 갱신 실패 시 확인된 제품·성분 기반 서버 문구와 보수적인 점수로 API 응답을 유지한다.
- 제품·전성분은 OpenAI 웹 검색을 먼저 사용한다. OpenAI 호출 자체가 실패하면 다른 OpenAI 모델을 재시도하지 않고 Gemini Google Search로 바로 전환하며, OpenAI 정상 응답의 미확인 제품도 Gemini로 재조사한다.
- Gemini Search 쿼터까지 사용할 수 없으면 MVP 폴백으로 Gemini 모델 지식에서 대표 처방을 생성하되 `ESTIMATED`로 구분하고 안전도를 강제로 `UNKNOWN`으로 낮춘다.
- 웹 출처가 검증된 성분은 `OFFICIAL/CORROBORATED/THIRD_PARTY`, 출처 없는 최선 추정은 `ESTIMATED`로 응답한다.
- 테스트 중 응답을 매번 새로 받고 싶으면 서버에서 `SHORTFORM_PRODUCT_CACHE_ENABLED=false`로 설정한 뒤 재시작한다. 운영 기본값은 비용 절감을 위해 `true`다.
- MFDS 규제 캐시에 같은 성분명이 있으면 규제 메타데이터만 덧붙인다. 이것이 해당 제품에 그 성분이 실제 포함됐다는 의미는 아니다.
- 분석 결과는 의학적 진단이 아니며 실제 제품 라벨과 패치 테스트가 우선한다.

## 테스트 화면

로컬 프로필에서 `/kakao-login-test`를 연다. 카카오 로그인 후 아래 기능이 추가로 표시된다.

- 테스트 피부 타입/피부 고민 저장
- 기본 Shorts 샘플 `https://www.youtube.com/shorts/t1S24pgO2XQ`
- URL 입력 후 자동으로 조회되는 YouTube 영상 정보 카드
- 4단계 진행률, 취소, AI 브리핑과 제품 상세 모달
- 최종 루틴 AI 매칭 점수가 포함된 인벤토리 최적화와 TODAY/LIBRARY 저장
- 모델·토큰·원시 JSON 디버그 패널

JWT는 기존 페이지와 동일하게 브라우저 메모리에만 유지하며 localStorage에 저장하지 않는다.
