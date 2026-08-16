import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildKakaoAuthorizeUrl,
  buildRoutineApplyPayload,
  normalizeKakaoClientId,
} from '../src/config.js';

test('카카오 REST API 키의 잘못된 대입문 접두어를 제거한다', () => {
  assert.deepEqual(normalizeKakaoClientId('KAKAO_CLIENT_ID=abc123abc123abc1'), {
    clientId: 'abc123abc123abc1',
    hadAssignmentPrefix: true,
    formatIsValid: true,
  });
});

test('카카오 authorize URL에 필요한 파라미터를 모두 넣는다', () => {
  const url = new URL(buildKakaoAuthorizeUrl({
    clientId: 'client-id',
    redirectUri: 'http://localhost:3000/onboarding/kakaocallback',
    state: 'state-value',
  }));
  assert.equal(url.searchParams.get('response_type'), 'code');
  assert.equal(url.searchParams.get('redirect_uri'), 'http://localhost:3000/onboarding/kakaocallback');
  assert.equal(url.searchParams.get('state'), 'state-value');
});

test('루틴 타입을 선택한 경우에만 저장 요청에 포함한다', () => {
  assert.deepEqual(buildRoutineApplyPayload('TODAY', 'NIGHT'), {
    saveType: 'TODAY',
    routineType: 'NIGHT',
  });
  assert.deepEqual(buildRoutineApplyPayload('LIBRARY', ''), { saveType: 'LIBRARY' });
});
