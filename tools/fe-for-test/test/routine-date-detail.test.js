import test from 'node:test';
import assert from 'node:assert/strict';
import {
  conditionPresentation,
  formatRoutineRecordDate,
  routineCompletion,
  routineRecordView,
  sortedRoutineSteps,
} from '../src/routine-date-detail.js';

const routine = (routineId, routineType, steps = []) => ({ routineId, routineType, steps });

test('API 컨디션 라벨의 공백 여부와 관계없이 아이콘 정보를 찾는다', () => {
  assert.deepEqual(conditionPresentation('촉촉하고편안해요'), {
    value: '촉촉하고편안해요',
    short: '촉촉하고\n편안해요',
    icon: 'condition-moist.svg',
  });
  assert.equal(conditionPresentation('촉촉하고 편안해요')?.icon, 'condition-moist.svg');
  assert.equal(conditionPresentation(null), null);
});

test('날짜를 월 일 요일이 포함된 기록 제목으로 표시한다', () => {
  assert.equal(formatRoutineRecordDate('2026-08-04'), '8월 4일 (화) 기록');
  assert.equal(formatRoutineRecordDate('2026-08-19'), '8월 19일 (수) 기록');
});

test('단계 완료 수와 실천도를 실제 완료 상태에서 계산한다', () => {
  assert.deepEqual(routineCompletion({
    completionRate: 99,
    steps: [{ completed: true }, { completed: false }, { completed: true }],
  }), {
    completedCount: 2,
    totalCount: 3,
    rate: 67,
  });
});

test('루틴 단계는 원본을 바꾸지 않고 order 순서로 정렬한다', () => {
  const source = { steps: [{ order: 3 }, { order: 1 }, { order: 2 }] };
  assert.deepEqual(sortedRoutineSteps(source).map((step) => step.order), [1, 2, 3]);
  assert.deepEqual(source.steps.map((step) => step.order), [3, 1, 2]);
});

test('루틴 개수와 선택 여부에 따라 빈 화면, 상세, 선택 목록을 결정한다', () => {
  const day = routine(10, 'DAY', [{ completed: true }]);
  const night = routine(20, 'NIGHT', [{ completed: false }]);

  assert.equal(routineRecordView([]).mode, 'empty');
  assert.equal(routineRecordView([night]).routine, night);
  assert.deepEqual(routineRecordView([night, day]).logs, [day, night]);
  assert.equal(routineRecordView([night, day]).mode, 'selection');
  assert.equal(routineRecordView([night, day], 20).routine, night);
  assert.equal(routineRecordView([night, day], 999).mode, 'selection');
});
