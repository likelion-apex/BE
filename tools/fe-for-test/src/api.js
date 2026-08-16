export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message, options);
    this.name = 'ApiError';
    this.status = options.status;
    this.code = options.code;
    this.payload = options.payload;
    this.isNetworkError = Boolean(options.isNetworkError);
  }
}

export class ApiClient {
  constructor({ baseUrl, getAccessToken }) {
    this.baseUrl = String(baseUrl || '').replace(/\/$/, '');
    this.getAccessToken = getAccessToken;
  }

  setBaseUrl(baseUrl) {
    this.baseUrl = String(baseUrl || '').trim().replace(/\/$/, '');
  }

  async request(path, options = {}) {
    const token = options.auth === false ? '' : this.getAccessToken?.();
    const headers = new Headers(options.headers || {});
    if (options.body != null && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
    if (token) headers.set('Authorization', `Bearer ${token}`);

    let response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        ...options,
        auth: undefined,
        cache: 'no-store',
        headers,
      });
    } catch (cause) {
      if (cause?.name === 'AbortError') throw cause;
      throw new ApiError('백엔드 서버에 연결하지 못했습니다.', {
        cause,
        isNetworkError: true,
      });
    }

    const payload = await response.json().catch(() => null);
    if (!response.ok || payload?.success === false) {
      throw new ApiError(payload?.message || `요청이 실패했습니다. (HTTP ${response.status})`, {
        status: response.status,
        code: payload?.code,
        payload,
      });
    }
    return payload;
  }

  async data(path, options = {}) {
    const payload = await this.request(path, options);
    return payload && Object.hasOwn(payload, 'data') ? payload.data : payload;
  }
}
