import test from 'node:test';
import assert from 'node:assert/strict';
import {
  assessmentLabel,
  benefitSummary,
  hasInternalProcessingCopy,
  userFacingApiError
} from '../src/analysis-ui.js';

test('Figma 안전도 분류 이름을 반환한다', () => {
  assert.equal(assessmentLabel('SAFE'), '성분이 안전함');
  assert.equal(assessmentLabel('BENEFICIAL'), '피부에 좋음');
  assert.equal(assessmentLabel('CAUTION'), '아쉬움·애매');
  assert.equal(assessmentLabel('WARNING'), '경고·위험');
});

test('단계 효능을 짧은 명사구로 조합한다', () => {
  assert.equal(benefitSummary({ keyBenefits: ['피부 진정', '수분 공급'] }), '피부 진정 및 수분 공급');
  assert.equal(benefitSummary({ matchSummary: '장벽 보호' }), '장벽 보호');
});

test('네트워크 오류 원문을 사용자 문구로 변환한다', () => {
  const error = new TypeError('Failed to fetch');
  assert.equal(
    userFacingApiError(error),
    '서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.'
  );
});

test('내부 처리 표현을 검출한다', () => {
  assert.equal(hasInternalProcessingCopy('AI가 추정한 대표 처방입니다.'), true);
  assert.equal(hasInternalProcessingCopy('피부 진정과 수분 공급에 도움을 줍니다.'), false);
});
