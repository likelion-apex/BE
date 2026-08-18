import test from 'node:test';
import assert from 'node:assert/strict';
import { reasonPresentation } from '../src/product-detail.js';

test('Figma 4단계 분석 카드를 서로 다른 스타일과 아이콘으로 매핑한다', () => {
  assert.deepEqual(reasonPresentation({ assessmentCategory: 'SAFE' }), {
    category: 'safe',
    iconUrl: '/assets/reason-safe.svg',
  });
  assert.deepEqual(reasonPresentation({ assessmentCategory: 'BENEFICIAL' }), {
    category: 'beneficial',
    iconUrl: '/assets/reason-beneficial.svg',
  });
  assert.deepEqual(reasonPresentation({ assessmentCategory: 'CAUTION' }), {
    category: 'caution',
    iconUrl: '/assets/reason-caution.svg',
  });
  assert.deepEqual(reasonPresentation({ assessmentCategory: 'WARNING' }), {
    category: 'warning',
    iconUrl: '/assets/reason-warning.svg',
  });
});

test('분류가 없거나 잘못되면 보수적으로 주의 스타일을 사용한다', () => {
  assert.deepEqual(reasonPresentation({ assessmentCategory: 'UNKNOWN' }), {
    category: 'caution',
    iconUrl: '/assets/reason-caution.svg',
  });
});
