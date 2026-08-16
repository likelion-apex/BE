import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildGeneratedRoutineCreatePayload,
  buildKakaoAuthorizeUrl,
  buildRoutineApplyPayload,
  buildRoutineLogsPath,
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

test('캘린더와 특정 날짜 루틴 기록 조회 경로를 만든다', () => {
  assert.equal(
    buildRoutineLogsPath({ year: 2026, month: 8 }),
    '/api/v1/routines/logs?year=2026&month=8',
  );
  assert.equal(
    buildRoutineLogsPath({ date: '2026-08-16' }),
    '/api/v1/routines/logs?date=2026-08-16',
  );
  assert.throws(() => buildRoutineLogsPath({ year: 2026 }), /year와 month/);
});

test('AI 자동생성 미리보기를 루틴 생성 요청으로 변환한다', () => {
  assert.deepEqual(buildGeneratedRoutineCreatePayload({
    generated: {
      suggestedName: 'AI 추천 나이트 루틴',
      routineType: 'NIGHT',
      steps: [
        { order: 1, inventoryId: 101, productName: '토너' },
        { order: 2, inventoryId: 102, productName: '크림' },
      ],
    },
    name: '  진정 루틴  ',
    saveType: 'TODAY',
  }), {
    name: '진정 루틴',
    routineType: 'NIGHT',
    saveType: 'TODAY',
    steps: [
      { order: 1, inventoryId: 101 },
      { order: 2, inventoryId: 102 },
    ],
  });
});
