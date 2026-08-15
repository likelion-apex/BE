const CLIENT_ID_ASSIGNMENT_PATTERN = /^(?:VITE_)?KAKAO_CLIENT_ID\s*=\s*/i;
const CLIENT_ID_VALUE_PATTERN = /^[a-zA-Z0-9_-]{16,128}$/;

export function normalizeKakaoClientId(value) {
  const rawValue = String(value || '').trim();
  const hadAssignmentPrefix = CLIENT_ID_ASSIGNMENT_PATTERN.test(rawValue);
  const clientId = rawValue.replace(CLIENT_ID_ASSIGNMENT_PATTERN, '').trim();

  return {
    clientId,
    hadAssignmentPrefix,
    formatIsValid: CLIENT_ID_VALUE_PATTERN.test(clientId)
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
