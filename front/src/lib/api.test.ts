import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';

import { apiBinaryUploadRequest, apiRequest, apiUploadRequest, shouldRetryRequest, toNetworkApiError } from './api';
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
const originalXMLHttpRequest = globalThis.XMLHttpRequest;

class FakeXMLHttpRequest {
  static latest: FakeXMLHttpRequest | null = null;

  method = '';
  url = '';
  requestBody: Document | XMLHttpRequestBodyInit | null = null;
  responseText = '';
  status = 200;
  headers = new Map<string, string>();
  responseHeaders = new Map<string, string>();
  onload: null | (() => void) = null;
  onerror: null | (() => void) = null;

  upload = {
    addEventListener: (type: string, listener: EventListenerOrEventListenerObject) => {
      if (type !== 'progress') {
        return;
      }

      this.progressListeners.push(listener);
    },
  };

  private progressListeners: EventListenerOrEventListenerObject[] = [];

  constructor() {
    FakeXMLHttpRequest.latest = this;
  }

  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(name: string, value: string) {
    this.headers.set(name.toLowerCase(), value);
  }

  getResponseHeader(name: string) {
    return this.responseHeaders.get(name) ?? null;
  }

  send(body: Document | XMLHttpRequestBodyInit | null) {
    this.requestBody = body;
  }

  triggerProgress(loaded: number, total: number) {
    const event = {
      lengthComputable: true,
      loaded,
      total,
    } as ProgressEvent<EventTarget>;

    for (const listener of this.progressListeners) {
      if (typeof listener === 'function') {
        listener(event);
      } else {
        listener.handleEvent(event);
      }
    }
  }

  respond(body: unknown, status = 200, contentType = 'application/json') {
    this.status = status;
    this.responseText = typeof body === 'string' ? body : JSON.stringify(body);
    this.responseHeaders.set('content-type', contentType);
    this.onload?.();
  }
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: new MemoryStorage(),
  });
  Object.defineProperty(globalThis, 'XMLHttpRequest', {
    configurable: true,
    value: FakeXMLHttpRequest,
  });
  FakeXMLHttpRequest.latest = null;
  clearStoredSession();
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: originalStorage,
  });
  Object.defineProperty(globalThis, 'XMLHttpRequest', {
    configurable: true,
    value: originalXMLHttpRequest,
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

test('network rename failures are retried once for idempotent file rename requests', () => {
  const error = new TypeError('Failed to fetch');

  assert.equal(shouldRetryRequest('/files/32/rename', {method: 'PATCH'}, error, 0), true);
  assert.equal(shouldRetryRequest('/files/32/rename', {method: 'PATCH'}, error, 1), false);
});

test('network fetch failures are converted to readable api errors', () => {
  const apiError = toNetworkApiError(new TypeError('Failed to fetch'));

  assert.equal(apiError.status, 0);
  assert.match(apiError.message, /网络连接异常|Failed to fetch/);
});

test('apiUploadRequest attaches auth header and forwards upload progress', async () => {
  saveStoredSession({
    token: 'token-456',
    user: {
      id: 2,
      username: 'uploader',
      email: 'uploader@example.com',
      createdAt: '2026-03-18T10:00:00',
    },
  });

  const progressCalls: Array<{loaded: number; total: number}> = [];
  const formData = new FormData();
  formData.append('file', new Blob(['hello']), 'hello.txt');

  const uploadPromise = apiUploadRequest<{id: number}>('/files/upload?path=%2F', {
    body: formData,
    onProgress: (progress) => {
      progressCalls.push(progress);
    },
  });

  const request = FakeXMLHttpRequest.latest;
  assert.ok(request);
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/files/upload?path=%2F');
  assert.equal(request.headers.get('authorization'), 'Bearer token-456');
  assert.equal(request.headers.get('accept'), 'application/json');
  assert.equal(request.requestBody, formData);

  request.triggerProgress(128, 512);
  request.triggerProgress(512, 512);
  request.respond({
    code: 0,
    msg: 'success',
    data: {
      id: 7,
    },
  });

  const payload = await uploadPromise;
  assert.deepEqual(payload, {id: 7});
  assert.deepEqual(progressCalls, [
    {loaded: 128, total: 512},
    {loaded: 512, total: 512},
  ]);
});

test('apiBinaryUploadRequest sends raw file body to signed upload url', async () => {
  const progressCalls: Array<{loaded: number; total: number}> = [];
  const fileBody = new Blob(['hello-oss']);

  const uploadPromise = apiBinaryUploadRequest('https://upload.example.com/object', {
    method: 'PUT',
    headers: {
      'Content-Type': 'text/plain',
      'x-oss-meta-test': '1',
    },
    body: fileBody,
    onProgress: (progress) => {
      progressCalls.push(progress);
    },
  });

  const request = FakeXMLHttpRequest.latest;
  assert.ok(request);
  assert.equal(request.method, 'PUT');
  assert.equal(request.url, 'https://upload.example.com/object');
  assert.equal(request.headers.get('content-type'), 'text/plain');
  assert.equal(request.headers.get('x-oss-meta-test'), '1');
  assert.equal(request.requestBody, fileBody);

  request.triggerProgress(64, 128);
  request.triggerProgress(128, 128);
  request.respond('', 200, 'text/plain');

  await uploadPromise;
  assert.deepEqual(progressCalls, [
    {loaded: 64, total: 128},
    {loaded: 128, total: 128},
  ]);
});
