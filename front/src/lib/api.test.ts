import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';

import { apiRequest } from './api';
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
