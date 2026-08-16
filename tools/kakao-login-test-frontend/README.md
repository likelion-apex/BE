# APEX 백엔드 로컬 테스트 프론트

배포된 백엔드 API를 로컬 브라우저에서 카카오 로그인부터 AI 전체 루틴 분석까지 검증하는 Vite 앱입니다. 백엔드 서버는 테스트 HTML이나 SVG를 제공하지 않습니다.

## 최초 설정

```bash
cd tools/kakao-login-test-frontend
cp .env.example .env.local
npm install
```

`.env.local`에서 `VITE_KAKAO_CLIENT_ID`를 실제 카카오 REST API 키로 바꿉니다. 등호 오른쪽에는 키 값만 입력해야 합니다.

```dotenv
# 올바름
VITE_KAKAO_CLIENT_ID=0123456789abcdef0123456789abcdef

# 잘못됨: KAKAO_CLIENT_ID= 접두어까지 값에 포함됨
VITE_KAKAO_CLIENT_ID=KAKAO_CLIENT_ID=0123456789abcdef0123456789abcdef
```

다음 두 위치의 Redirect URI는 반드시 완전히 같아야 합니다.

```text
http://localhost:3000/onboarding/kakaocallback
```

1. 카카오 개발자 콘솔 Redirect URI
2. 로컬 앱 `VITE_KAKAO_REDIRECT_URI`

이 앱은 `redirectUri`를 `/api/auth/kakao/login` 요청 본문에도 명시적으로 전달합니다. 서버 `KAKAO_REDIRECT_URI`는 이 값이 생략됐을 때 사용하는 기본값입니다.

배포 서버의 `CORS_ALLOWED_ORIGINS`에도 `http://localhost:3000`이 포함되어 있어야 합니다.

## 실행

```bash
npm run dev
```

브라우저에서 `http://localhost:3000`을 열고 카카오 로그인을 진행합니다. 카카오가 `/onboarding/kakaocallback`으로 돌려보내면 Vite가 같은 앱을 제공하고, 앱이 authorization code를 배포 백엔드의 `/api/auth/kakao/login`으로 전달합니다.

로그인 후 YouTube URL을 입력하면 영상 정보 미리보기를 자동으로 확인합니다. 썸네일, 제목, 게시자, 조회수와 영상 길이가 표시되고, 공개 상태이며 5분 이하인 영상의 미리보기가 성공한 경우에만 AI 전체 루틴 분석을 요청할 수 있습니다.

`VITE_` 환경변수는 브라우저에 공개됩니다. Client Secret, JWT Secret, OpenAI·Gemini·YouTube·MFDS API 키는 이 폴더의 환경변수에 넣지 마세요.

## 배포 결과만 확인

```bash
npm run build
npm run preview
```

`preview`도 카카오 Redirect URI와 일치하도록 `localhost:3000`에서 실행됩니다.
