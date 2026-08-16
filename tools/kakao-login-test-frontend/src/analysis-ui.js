const ASSESSMENT_LABELS = Object.freeze({
  SAFE: '성분이 안전함',
  BENEFICIAL: '피부에 좋음',
  CAUTION: '아쉬움·애매',
  WARNING: '경고·위험'
});

export function assessmentLabel(category) {
  return ASSESSMENT_LABELS[String(category || '').toUpperCase()] || ASSESSMENT_LABELS.CAUTION;
}

export function benefitSummary(step = {}) {
  const benefits = Array.isArray(step.keyBenefits)
    ? step.keyBenefits.map((value) => String(value || '').trim()).filter(Boolean).slice(0, 2)
    : [];
  return benefits.length ? benefits.join(' 및 ') : String(step.matchSummary || '피부 컨디션 관리');
}

export function userFacingApiError(error) {
  if (error?.isNetworkError || error instanceof TypeError) {
    return '서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.';
  }
  if (Number.isInteger(error?.status)) {
    return `요청을 처리하지 못했습니다. (HTTP ${error.status}) ${error.message || ''}`.trim();
  }
  return error?.message || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

export function buildRoutineApplyPayload(saveType, routineType) {
  const normalizedSaveType = String(saveType || '').toUpperCase();
  const normalizedRoutineType = String(routineType || '').toUpperCase();
  if (!['TODAY', 'LIBRARY'].includes(normalizedSaveType)) {
    throw new Error('유효하지 않은 루틴 저장 방식입니다.');
  }
  if (!['DAY', 'NIGHT'].includes(normalizedRoutineType)) {
    throw new Error('유효하지 않은 루틴 사용 시간대입니다.');
  }
  return { saveType: normalizedSaveType, routineType: normalizedRoutineType };
}

export function safeHttpImageUrl(value) {
  if (!value) return '';
  try {
    const parsed = new URL(value);
    return ['http:', 'https:'].includes(parsed.protocol) ? parsed.toString() : '';
  } catch (error) {
    return '';
  }
}

export function createVideoPreviewState() {
  let revision = 0;
  let currentUrl = '';
  let previewUrl = '';
  let preview = null;

  return Object.freeze({
    start(value) {
      revision += 1;
      currentUrl = String(value || '').trim();
      previewUrl = '';
      preview = null;
      return Object.freeze({ revision, url: currentUrl });
    },
    isCurrent(ticket) {
      return ticket?.revision === revision && ticket?.url === currentUrl;
    },
    accept(ticket, value) {
      if (!this.isCurrent(ticket)) return false;
      previewUrl = ticket.url;
      preview = value || null;
      return Boolean(preview);
    },
    canAnalyze(value) {
      return Boolean(preview) && previewUrl === String(value || '').trim();
    },
    value() {
      return preview;
    }
  });
}

export function hasInternalProcessingCopy(value) {
  return /AI가|AI는|AI의|AI 분석|추정|식별|대표 처방|서버 보정|NORMALIZED|ESTIMATED/i.test(String(value || ''));
}
