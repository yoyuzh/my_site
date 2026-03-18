import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';

import { apiRequest, shouldRetryRequest, toNetworkApiError } from './api';
import { clearStoredSession, saveStoredSession } from './session';

class MemoryStorage implements Storage {
  private store = new Map<string, string>();

  get length() {
    return this.store.size;
  }

  clear() {
    this.store.clear();
  }

  getItem(key: string) {
    return this.store.has(key) ? this.store.get(key)! : null;
  }

  key(index: number) {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string) {
    this.store.delete(key);
  }

  setItem(key: string, value: string) {
    this.store.set(key, value);
  }
}

const originalFetch = globalThis.fetch;
const originalStorage = globalThis.localStorage;

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: new MemoryStorage(),
  });
  clearStoredSession();
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: originalStorage,
  });
});

test('apiRequest attaches bearer token and unwraps response payload', async () => {
  let request: Request | URL | string | undefined;
  saveStoredSession({
    token: 'token-123',
    user: {
      id: 1,
      username: 'tester',
      email: 'tester@example.com',
      createdAt: '2026-03-14T10:00:00',
    },
  });

  globalThis.fetch = async (input, init) => {
    request =
      input instanceof Request
        ? input
        : new Request(new URL(String(input), 'http://localhost'), init);
    return new Response(
      JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          ok: true,
        },
      }),
      {
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  };

  const payload = await apiRequest<{ok: boolean}>('/files/recent');

  assert.deepEqual(payload, {ok: true});
  assert.ok(request instanceof Request);
  assert.equal(request.headers.get('Authorization'), 'Bearer token-123');
  assert.equal(request.url, 'http://localhost/api/files/recent');
});

test('apiRequest throws backend message on business error', async () => {
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        code: 40101,
        msg: 'login required',
        data: null,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );

  await assert.rejects(() => apiRequest('/user/profile'), /login required/);
});

test('network login failures are retried a limited number of times for auth login', () => {
  const error = new TypeError('Failed to fetch');

  assert.equal(shouldRetryRequest('/auth/login', {method: 'POST'}, error, 0), true);
  assert.equal(shouldRetryRequest('/auth/login', {method: 'POST'}, error, 1), true);
  assert.equal(shouldRetryRequest('/auth/login', {method: 'POST'}, error, 2), false);
});

test('network register failures are not retried automatically', () => {
  const error = new TypeError('Failed to fetch');

  assert.equal(shouldRetryRequest('/auth/register', {method: 'POST'}, error, 0), false);
});

test('network get failures are retried up to two times after the first attempt', () => {
  const error = new TypeError('Failed to fetch');

  assert.equal(shouldRetryRequest('/files/list', {method: 'GET'}, error, 0), true);
  assert.equal(shouldRetryRequest('/files/list', {method: 'GET'}, error, 1), true);
  assert.equal(shouldRetryRequest('/files/list', {method: 'GET'}, error, 2), true);
  assert.equal(shouldRetryRequest('/files/list', {method: 'GET'}, error, 3), false);
});

test('network fetch failures are converted to readable api errors', () => {
  const apiError = toNetworkApiError(new TypeError('Failed to fetch'));

  assert.equal(apiError.status, 0);
  assert.match(apiError.message, /网络连接异常|Failed to fetch/);
});
