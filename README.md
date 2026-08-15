# BE
# 🚀 Convention

우리 팀의 원활한 협업과 일관성 있는 코드 관리를 위한 규칙입니다. 모든 팀원은 개발 시작 전에 반드시 숙지해 주세요.

---

## 카카오 로그인·AI 루틴 로컬 테스트 프론트

테스트 화면은 백엔드 배포 파일에서 분리된 Vite 앱으로 실행합니다. 배포 서버는 테스트 HTML이나 SVG를 제공하지 않으며, 로컬 앱이 원격 백엔드 API를 호출합니다.

```bash
cd tools/kakao-login-test-frontend
cp .env.example .env.local
# .env.local의 VITE_KAKAO_CLIENT_ID를 실제 카카오 REST API 키로 변경
npm install
npm run dev
```

브라우저에서 `http://localhost:3000`을 엽니다. 카카오 개발자 콘솔과 로컬 `VITE_KAKAO_REDIRECT_URI`는 다음 주소로 설정합니다.

```text
http://localhost:3000/onboarding/kakaocallback
```

로컬 앱은 이 Redirect URI를 로그인 API 요청 본문에도 전달하므로 서버 `KAKAO_REDIRECT_URI`는 fallback 기본값으로만 사용됩니다. 배포 서버의 `CORS_ALLOWED_ORIGINS`에는 `http://localhost:3000`이 포함되어야 합니다. 상세한 환경변수와 실행 방법은 [`tools/kakao-login-test-frontend/README.md`](tools/kakao-login-test-frontend/README.md)를 참고하세요.

`KAKAO_CLIENT_SECRET`은 서버의 authorization code 교환에만 사용하며 Vite 환경변수에 넣지 않습니다. 로그인 성공 시 토큰은 화면에만 표시하고 브라우저 저장소에는 보관하지 않습니다.

---

## 🌿 Git 브랜치 전략

우리 프로젝트는 **Feature-Driven Workflow**를 따릅니다. **`main` 브랜치로의 직접적인 Push는 절대 금지**합니다.

### 📌 브랜치 명명 규칙
`종류/기능명` 형태로 생성하며, 단어 구문은 하이픈(`-`)을 사용합니다.

| 종류         | 설명                       | 예시                                        |
|:-----------|:-------------------------|:------------------------------------------|
| `feature`  | 새로운 기능 구현                | `feature/post-crud`, `feature/comment`    |
| `fix`      | 버그 및 에러 수정               | `fix/db-connection`, `fix/error-response` |
| `docs`     | 문서 수정 (README, API 명세 등) | `docs/readme-update`                      |
| `refactor` | 코드 리팩토링                  | `refactor/dto-separation`                 |
| `chore`    | 기타                       | `chore/unuse-file-delete`                 |

> ⚠️ **주의:** 브랜치를 생성하기 전에 항상 `main` 브랜치에서 `git pull`을 진행하여 최신 상태를 유지하세요.

---

## 💬 커밋 메시지 컨벤션 (Commit Convention)

커밋 메시지는 다른 팀원이 변경 사항을 쉽게 알아볼 수 있도록 아래 규칙을 준수합니다.

### 📌 메시지 구조
```text
태그: 변경 내용 요약 (한글로 간결하게)
```

---

## ✨ YouTube 뷰티 루틴 분석

공개 YouTube Shorts 또는 일반 영상 URL을 Gemini에 전달해 스킨케어·메이크업 제품과 적용 순서를 분석합니다.

### 환경변수

```text
GEMINI_API_KEY=Google AI Studio에서 발급한 서버용 API 키
GEMINI_MODEL=gemini-3.6-flash
```

- API 키는 프론트엔드나 Git에 포함하지 않고 서버 환경변수로만 주입합니다.
- `GEMINI_MODEL`을 생략하면 샘플 영상의 OCR 정확도와 단계 재현율이 더 높았던 `gemini-3.6-flash`를 사용합니다.
- 로컬 실행 시에는 기본 `local` 프로필이 활성화되며 `application-local.yml`의 더미 키와 localhost 더미 URL을 사용합니다. 실제 Kakao·식약처·OpenAI·Gemini API로 요청을 보내지 않고도 애플리케이션과 Swagger를 실행할 수 있습니다.
- 운영 환경은 `SPRING_PROFILES_ACTIVE=prod`를 사용하며 실제 비밀값을 서버 환경변수로 주입해야 합니다.

### API

```http
POST /api/v1/beauty-routines/analyze
Content-Type: application/json

{
  "youtubeUrl": "https://www.youtube.com/shorts/-PC1SkLxtvo"
}
```

응답에는 정규화된 YouTube URL, 사용 모델과 토큰 수, 루틴 유형, 타임스탬프순 단계가 포함됩니다. 정확한 브랜드·상품명은 영상의 라벨·화면 문구·음성·자막에서 원문을 확인할 수 있고 확신도가 `0.85` 이상일 때만 유지하며, 나머지는 제품 카테고리만 반환합니다.

- 허용 URL: `youtube.com/shorts/{id}`, `youtube.com/watch?v={id}`, `youtu.be/{id}`의 HTTPS 주소
- 성공한 분석은 모델·프롬프트 버전·영상 ID 기준으로 30일간 캐시합니다.
- Gemini의 분당 한도 또는 고수요 오류는 `503 Service Unavailable`로 반환합니다.
- Gemini에는 영상 URL만 전달하며 사용자 피부·알레르기·취향 정보는 후속 개인화 단계에서 처리합니다.
- 프롬프트·모델 비교와 네 샘플 영상의 실제 결과는 [`docs/gemini-shorts-evaluation.md`](docs/gemini-shorts-evaluation.md)에 정리했습니다.

---
