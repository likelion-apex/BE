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
```

비밀값은 저장소에 커밋하거나 API 응답·로그에 출력하지 않는다. `YOUTUBE_API_KEY`는 Gemini 호출 전에 공개 여부와 5분 제한을 확인하는 데 필요하다.

## 호출 순서

1. `POST /api/shortform-analyses`
2. `GET /api/shortform-analyses/{analysisId}/status`를 `COMPLETED`까지 polling
3. `GET /api/shortform-analyses/{analysisId}`
4. 필요 시 `GET /api/shortform-analyses/{analysisId}/results/{resultId}`
5. `POST /api/shortform-analyses/{analysisId}/optimize`
6. `POST /api/shortform-analyses/{analysisId}/apply` with `TODAY` 또는 `LIBRARY`

분석 상태는 `PENDING → EXTRACTING_VIDEO → MATCHING_PRODUCTS → PERSONALIZING → OPTIMIZING → COMPLETED` 순서다. 사용자는 진행 중 `POST /cancel`로 취소할 수 있다.

## AI와 데이터 근거

- 영상 단계·제품 식별: `gemini-3.6-flash`, 기존 프롬프트/검증기/캐시 재사용
- 개인화·점수·조합·인벤토리 추천: `gpt-4o-mini`, 전체 루틴당 Chat Completions 1회, strict JSON Schema
- Gemini 영상 추출은 DB에도 모델·프롬프트 버전 기준으로 저장해 서버 재시작 후 재사용한다.
- OpenAI가 추천한 단계 번호와 inventory ID는 서버 입력 집합으로 다시 제한한다.
- 제품-전성분의 공식 매핑이 현재 DB에 없으므로 반환 성분은 `AI_ESTIMATED`로 표시한다. 정확 제품이 아닌 단계는 안전도를 강제로 `UNKNOWN`으로 낮춘다.
- MFDS 규제 캐시에 같은 성분명이 있으면 규제 메타데이터만 덧붙인다. 이것이 해당 제품에 그 성분이 실제 포함됐다는 의미는 아니다.
- 분석 결과는 의학적 진단이 아니며 실제 제품 라벨과 패치 테스트가 우선한다.

## 테스트 화면

로컬 프로필에서 `/kakao-login-test`를 연다. 카카오 로그인 후 아래 기능이 추가로 표시된다.

- 테스트 피부 타입/피부 고민 저장
- 기본 Shorts 샘플 `https://www.youtube.com/shorts/t1S24pgO2XQ`
- 4단계 진행률, 취소, AI 브리핑과 제품 상세 모달
- 인벤토리 최적화와 TODAY/LIBRARY 저장
- 모델·토큰·원시 JSON 디버그 패널

JWT는 기존 페이지와 동일하게 브라우저 메모리에만 유지하며 localStorage에 저장하지 않는다.
