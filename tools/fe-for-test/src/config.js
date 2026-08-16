const CLIENT_ID_ASSIGNMENT_PATTERN = /^(?:VITE_)?KAKAO_CLIENT_ID\s*=\s*/i;
const CLIENT_ID_VALUE_PATTERN = /^[a-zA-Z0-9_-]{16,128}$/;

export function normalizeKakaoClientId(value) {
  const rawValue = String(value || '').trim();
  const hadAssignmentPrefix = CLIENT_ID_ASSIGNMENT_PATTERN.test(rawValue);
  const clientId = rawValue.replace(CLIENT_ID_ASSIGNMENT_PATTERN, '').trim();

  return {
    clientId,
    hadAssignmentPrefix,
    formatIsValid: CLIENT_ID_VALUE_PATTERN.test(clientId),
  };
}

export function buildKakaoAuthorizeUrl({ clientId, redirectUri, state }) {
  const authorizeUrl = new URL('https://kauth.kakao.com/oauth/authorize');
  authorizeUrl.searchParams.set('client_id', clientId);
  authorizeUrl.searchParams.set('redirect_uri', redirectUri);
  authorizeUrl.searchParams.set('response_type', 'code');
  authorizeUrl.searchParams.set('state', state);
  return authorizeUrl.toString();
}

export function buildRoutineApplyPayload(saveType, routineType) {
  const payload = { saveType };
  if (routineType) payload.routineType = routineType;
  return payload;
}

export function buildRoutineLogsPath({ date, year, month } = {}) {
  const params = new URLSearchParams();
  if (date) {
    params.set('date', String(date));
  } else {
    if (!Number.isInteger(year) || !Number.isInteger(month)) {
      throw new TypeError('루틴 캘린더 조회에는 year와 month가 필요합니다.');
    }
    params.set('year', String(year));
    params.set('month', String(month));
  }
  return `/api/v1/routines/logs?${params.toString()}`;
}

export function buildGeneratedRoutineCreatePayload({ generated, name, saveType }) {
  return {
    name: String(name || generated?.suggestedName || '').trim(),
    routineType: generated?.routineType,
    saveType,
    steps: (generated?.steps || []).map((step, index) => ({
      order: Number(step.order || index + 1),
      inventoryId: Number(step.inventoryId),
    })),
  };
}

export function createOAuthState(cryptoObject = globalThis.crypto) {
  const bytes = new Uint8Array(24);
  cryptoObject.getRandomValues(bytes);
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
}
