import test from 'node:test';
import assert from 'node:assert/strict';
import { optimizationPresentation, optimizationScorePresentation } from '../src/optimization.js';

test('대체 제품은 영상 원본 제품명을 함께 표시한다', () => {
  assert.deepEqual(optimizationPresentation({
    status: 'REPLACED',
    replaceName: '영상 수분 앰플',
  }), {
    status: 'replaced',
    label: '대체',
    reasonTitle: 'AI 대체 이유',
    sourceLabel: '영상 속 루틴: 영상 수분 앰플',
  });
});

test('영상 제품 유지 상태는 대체품 없음으로 표시한다', () => {
  assert.deepEqual(optimizationPresentation({ status: 'VIDEO_PRODUCT', replaceName: null }), {
    status: 'video-product',
    label: '영상 속 제품',
    reasonTitle: '대체품 없음',
    sourceLabel: '영상 속 제품',
  });
});

test('최종 루틴 점수와 최대 두 개의 하이라이트를 표시한다', () => {
  assert.deepEqual(optimizationScorePresentation({
    overallScore: 88.4,
    highlights: ['수부지 맞춤 성분 4개 매칭', '', '알레르기 유발 성분 0개', '무시'],
  }), {
    score: 88,
    highlights: ['수부지 맞춤 성분 4개 매칭', '알레르기 유발 성분 0개'],
  });
});
