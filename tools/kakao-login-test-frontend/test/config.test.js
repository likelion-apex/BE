import assert from 'node:assert/strict';
import test from 'node:test';

import { buildKakaoAuthorizeUrl, normalizeKakaoClientId } from '../src/config.js';

const REST_API_KEY = '0123456789abcdef0123456789abcdef';

test('카카오 REST API 키 값만 입력하면 그대로 사용한다', () => {
  assert.deepEqual(normalizeKakaoClientId(REST_API_KEY), {
    clientId: REST_API_KEY,
    hadAssignmentPrefix: false,
    formatIsValid: true
  });
});

test('KAKAO_CLIENT_ID 접두어가 값에 포함되면 제거한다', () => {
  assert.deepEqual(normalizeKakaoClientId(`KAKAO_CLIENT_ID=${REST_API_KEY}`), {
    clientId: REST_API_KEY,
    hadAssignmentPrefix: true,
    formatIsValid: true
  });
});

test('VITE_KAKAO_CLIENT_ID 접두어가 중복되어도 제거한다', () => {
  assert.deepEqual(normalizeKakaoClientId(`VITE_KAKAO_CLIENT_ID=${REST_API_KEY}`), {
    clientId: REST_API_KEY,
    hadAssignmentPrefix: true,
    formatIsValid: true
  });
});

test('키가 아닌 환경변수 선언문은 잘못된 형식으로 판정한다', () => {
  assert.equal(normalizeKakaoClientId('KAKAO_CLIENT_ID=').formatIsValid, false);
});

test('카카오 인가 URL에는 접두어가 제거된 Client ID가 들어간다', () => {
  const normalized = normalizeKakaoClientId(`KAKAO_CLIENT_ID=${REST_API_KEY}`);
  const authorizeUrl = new URL(buildKakaoAuthorizeUrl({
    clientId: normalized.clientId,
    redirectUri: 'http://localhost:3000/onboarding/kakaocallback',
    state: 'test-state'
  }));

  assert.equal(authorizeUrl.searchParams.get('client_id'), REST_API_KEY);
  assert.equal(
    authorizeUrl.searchParams.get('redirect_uri'),
    'http://localhost:3000/onboarding/kakaocallback'
  );
});
