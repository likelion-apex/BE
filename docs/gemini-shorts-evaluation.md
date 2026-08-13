# Gemini Shorts 뷰티 루틴 분석 테스트

테스트 일자: 2026-08-06

## 결론

- 기본 모델은 `gemini-3.6-flash`로 선택했다. 동일 영상에서 `gemini-3.5-flash`보다 제품 OCR과 단계 재현율이 높았다.
- YouTube URL을 `video` 입력으로 먼저 배치하고, 보수적인 통합 프롬프트를 뒤에 배치한다.
- 응답은 비스트리밍 Interactions API와 compact JSON Schema를 사용한다. 처음 시도한 깊은 중첩 스키마는 `400 invalid_request`였고, 제품 필드를 단계 객체에 평탄화한 스키마는 정상 동작했다.
- 모델의 `confidence`만으로 상품명을 신뢰하지 않는다. 정확 상품에는 영상에서 읽은 원문인 `identityEvidenceText`를 함께 요구하고, 직접 근거가 없거나 확신도가 `0.85` 미만이면 서버에서 카테고리 전용으로 강등한다.
- `identityEvidenceText`도 모델이 잘못 읽을 수 있으므로 후속 OpenAI 단계에는 브랜드·상품명뿐 아니라 카테고리, 타임스탬프, 원문 근거, 근거 종류, 확신도를 모두 전달한다. 상품 카탈로그와 일치하지 않으면 정확 상품으로 노출하지 않는다.

## 샘플 결과

제공된 네 영상은 모두 메이크업보다는 스킨케어 루틴이었다. 구현은 두 종류를 모두 받을 수 있도록 `beauty routine`으로 일반화했다.

| 영상 | 길이 | 최종 단계 | 응답 시간 | 전체 토큰 | 주요 결과 |
|---|---:|---:|---:|---:|---|
| `-PC1SkLxtvo` | 54.7초 | 10 | 17.3초 | 8,495 | 토너, 바디크림, 오일, 립밤, 헤어 에센스, 세럼, 앰플, 디바이스, 수분크림, 시카크림 |
| `qDje7cL7iZ0` | 44.0초 | 7 | 12.3초 | 6,332 | 필오프·워시오프 마스크, 미스트, 세럼, 크림, 두피 앰플, 헤어 에센스 |
| `4iKgYvjXdac` | 57.2초 | 7 | 17.1초 | 7,742 | 립밤, 눈썹칼, 토너, 세럼, 마스크팩, 헤어 에센스, 수분크림 |
| `t1S24pgO2XQ` | 43.0초 | 4 | 7.1초 | 5,474 | 토너 패드, PDRN 앰플, 립 슬리핑 마스크, 콜라겐 마스크팩 |

최종 설정의 네 영상 중앙 응답 시간은 약 14.7초였다.

## 실제로 확인된 오류와 대응

### 상품명 환각

첫 영상 약 6초의 `ELSOL BOOSTING OIL`을 일부 3.5 응답은 웰라쥬 또는 이즈앤트리 제품으로 잘못 확정했다. 3.6 최종 응답도 짧은 영문 라벨 일부를 오독하는 사례가 있었다.

대응:

- 정확 상품에는 `identityEvidenceText`를 필수로 받는다.
- 라벨·화면 문구·음성·자막 중 직접 근거가 없으면 상품명을 모두 제거한다.
- OpenAI 또는 상품 검색 단계에서 원문과 실제 판매 상품을 대조하고, 일치하지 않으면 카테고리만 사용자에게 보여준다.

### 모델별 차이

세 번째 영상에서 3.5는 마지막 크림 브랜드를 잘못 읽고 헤어 에센스 단계를 누락했다. 3.6은 화면 문구와 일치하는 `구달 어성초 히알루론 수딩 크림`과 `모레모 프로 리페어 헤어 에센스`를 모두 반환했다.

### 할당량과 고수요

총 34회 이내로 인증·스키마·모델·프롬프트를 시험했다. 테스트 중 다음 동작을 확인했다.

- `gemini-3.5-flash`: 짧은 시간에 20회 한도에 도달하면 `429`와 약 30~57초 후 재시도 안내 반환
- 두 모델 모두 간헐적으로 고수요 `500` 또는 연결 종료 발생
- 성공 응답은 대체로 7~17초였지만 고수요 시 30초 이상 지연된 사례 존재

운영 대응:

- 동일한 영상 ID·모델·프롬프트 버전의 성공 결과를 30일 캐시한다.
- 동시에 같은 영상이 들어오면 한 번의 Gemini 호출 결과를 공유한다.
- API 서버 내부에서 장시간 반복 호출하지 않고 `429`와 `5xx`를 `503`으로 변환해 클라이언트가 잠시 후 재시도하도록 한다.
- API 키는 프로젝트 단위 할당량을 사용하므로 키를 여러 개 만들어 우회하지 않는다.

## 최종 요청 설정

```text
POST https://generativelanguage.googleapis.com/v1beta/interactions
model: gemini-3.6-flash
stream: false
store: false
generation_config.seed: 42
generation_config.thinking_level: low
generation_config.max_output_tokens: 4000
response_format.mime_type: application/json
```

프롬프트와 실제 JSON Schema는 다음 리소스를 단일 소스로 사용한다.

- `src/main/resources/gemini/beauty-routine-system-prompt.txt`
- `src/main/resources/gemini/beauty-routine-user-prompt.txt`
- `src/main/resources/gemini/beauty-routine-schema.json`

참고 문서:

- [Gemini 동영상 이해와 YouTube URL](https://ai.google.dev/gemini-api/docs/video-understanding?hl=ko#youtube)
- [Gemini 구조화 출력](https://ai.google.dev/gemini-api/docs/structured-output?hl=ko)
- [Gemini 비율 제한](https://ai.google.dev/gemini-api/docs/rate-limits?hl=ko)
