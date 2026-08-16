import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiClient } from '../src/api.js';

test('ApiResponse의 명시적인 null data를 응답 객체로 바꾸지 않는다', async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  globalThis.fetch = async () => new Response(JSON.stringify({
    success: true,
    data: null,
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });

  const client = new ApiClient({ baseUrl: 'https://example.com', getAccessToken: () => '' });
  assert.equal(await client.data('/api/v1/routines/daily'), null);
});
