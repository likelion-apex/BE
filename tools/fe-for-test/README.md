# SOAK 기능 테스트 프론트

Figma의 `UI디자인` 페이지를 바탕으로 만든 독립형 Vite 테스트 앱입니다. 백엔드 소스와 기존 `tools/kakao-login-test-frontend`는 변경하지 않습니다. 이 폴더만 삭제하면 앱 전체가 제거됩니다.

## 실행

```bash
cd tools/fe-for-test
cp .env.example .env.local
npm install
npm run dev
```

브라우저에서 `http://localhost:3000`을 엽니다. 기존 테스트 프론트와 같은 포트를 사용하므로 두 앱을 동시에 실행하지는 마세요. 기존 백엔드 CORS 설정을 그대로 재사용하기 위한 선택입니다.

카카오 로그인을 실제로 테스트하려면 다음 값을 맞춰야 합니다.

- `.env.local`의 `VITE_KAKAO_CLIENT_ID`: 카카오 REST API 키
- 카카오 개발자 콘솔 Redirect URI: `http://localhost:3000/onboarding/kakaocallback`
- `.env.local`의 `VITE_KAKAO_REDIRECT_URI`: 위와 완전히 같은 주소
- 백엔드 `CORS_ALLOWED_ORIGINS`: `http://localhost:3000` 허용

`VITE_` 환경변수는 브라우저에 공개됩니다. Client Secret, JWT Secret, OpenAI·Gemini·YouTube·MFDS API 키는 넣지 마세요.

## 테스트 모드

- **카카오로 시작하기**: 실제 OAuth 로그인 후 백엔드 JWT를 받아 API를 호출합니다.
- **UI 미리보기**: 서버 없이 Figma 기반 화면과 상호작용을 확인합니다. 쓰기 동작은 메모리의 샘플 데이터에만 반영됩니다.
- `http://localhost:3000/?onboarding=1`: UI 미리보기 데이터로 온보딩 첫 화면부터 확인합니다.
- **테스트 토큰으로 연결**: 이미 발급받은 Access Token과 백엔드 URL을 직접 넣어 API를 테스트합니다. 토큰은 `localStorage`에 저장하지 않으며 새로고침하면 사라집니다.

## 포함된 기능

- 카카오 로그인 및 신규 사용자 온보딩
- 피부 타입·피부 고민·닉네임 조회/수정
- 홈 요약, 오늘 컨디션 기록, 오늘 루틴, 즐겨찾기
- YouTube 영상 미리보기, 비동기 루틴 분석, 상태 조회/취소
- 분석 결과, 단계별 제품 상세, 인벤토리 최적화, TODAY/LIBRARY 저장
- 오늘의 데일리 루틴 조회, 단계별 완료 토글, 전체 체크 및 최종 완료
- 루틴 보관함 목록/상세/삭제/오늘 적용, 월별 캘린더와 날짜별 기록
- 인벤토리 기반 AI 루틴 미리보기 및 TODAY/LIBRARY 생성
- 인벤토리 조회/검색/등록/즐겨찾기/삭제, 제품 AI·성분 분석
- 프로필, 인증 상태, Swagger 링크, 마지막 API 응답 확인

## 검증

```bash
npm test
npm run build
```
