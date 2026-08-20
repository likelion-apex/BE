export const conditionOptions = [
  { value: '트러블있고예민해요', short: '트러블이 있고\n예민해요', icon: 'condition-troubled.svg' },
  { value: '건조하고푸석해요', short: '건조하고\n푸석해요', icon: 'condition-dry.svg' },
  { value: '평범하고무난해요', short: '평범하고\n무난해요', icon: 'condition-normal.svg' },
  { value: '촉촉하고편안해요', short: '촉촉하고\n편안해요', icon: 'condition-moist.svg' },
  { value: '컨디션최고예요', short: '컨디션\n최고예요', icon: 'condition-best.svg' },
];

const normalizeCondition = (value) => String(value || '').replaceAll(/\s/g, '');

export function conditionPresentation(value) {
  const normalized = normalizeCondition(value);
  return conditionOptions.find((option) => normalizeCondition(option.value) === normalized) || null;
}

export function formatRoutineRecordDate(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ''));
  if (!match) return `${String(value || '')} 기록`.trim();

  const [, year, month, day] = match;
  const date = new Date(Number(year), Number(month) - 1, Number(day));
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
  return `${Number(month)}월 ${Number(day)}일 (${weekdays[date.getDay()]}) 기록`;
}

export function routineCompletion(routine) {
  const steps = Array.isArray(routine?.steps) ? routine.steps : [];
  const completedCount = steps.filter((step) => Boolean(step.completed)).length;
  const totalCount = steps.length;
  const rate = totalCount ? Math.round((completedCount * 100) / totalCount) : 0;
  return { completedCount, totalCount, rate };
}

export function sortedRoutineSteps(routine) {
  return [...(Array.isArray(routine?.steps) ? routine.steps : [])]
    .sort((left, right) => Number(left.order || 0) - Number(right.order || 0));
}

export function sortedRoutineLogs(routineLogs) {
  const typeOrder = { DAY: 0, NIGHT: 1 };
  return [...(Array.isArray(routineLogs) ? routineLogs : [])]
    .sort((left, right) => {
      const typeDifference = (typeOrder[left.routineType] ?? 2) - (typeOrder[right.routineType] ?? 2);
      if (typeDifference) return typeDifference;
      return Number(left.routineId || 0) - Number(right.routineId || 0);
    });
}

export function routineRecordView(routineLogs, selectedRoutineId = null) {
  const logs = sortedRoutineLogs(routineLogs);
  if (!logs.length) return { mode: 'empty', logs, routine: null };
  if (logs.length === 1) return { mode: 'detail', logs, routine: logs[0] };

  const selected = selectedRoutineId == null
    ? null
    : logs.find((routine) => String(routine.routineId) === String(selectedRoutineId));
  return selected
    ? { mode: 'detail', logs, routine: selected }
    : { mode: 'selection', logs, routine: null };
}

export function routineTypeLabel(routineType) {
  if (routineType === 'DAY') return '데이';
  if (routineType === 'NIGHT') return '나이트';
  return String(routineType || '루틴');
}
