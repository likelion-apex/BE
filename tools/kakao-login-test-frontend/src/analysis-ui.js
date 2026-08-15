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

export function hasInternalProcessingCopy(value) {
  return /AI가|AI는|AI의|AI 분석|추정|식별|대표 처방|서버 보정|NORMALIZED|ESTIMATED/i.test(String(value || ''));
}
