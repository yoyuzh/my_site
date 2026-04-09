import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';

import {
  uploadFileToNetdiskViaSession,
} from './upload-session';

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
  static instances: FakeXMLHttpRequest[] = [];

  method = '';
  url = '';
  requestBody: Document | XMLHttpRequestBodyInit | null = null;
  responseText = '';
  status = 200;
  headers = new Map<string, string>();
  responseHeaders = new Map<string, string>();
  onload: null | (() => void) = null;
  onerror: null | (() => void) = null;
  onabort: null | (() => void) = null;
  aborted = false;

  upload = {
    addEventListener: () => {},
  };

  constructor() {
    FakeXMLHttpRequest.instances.push(this);
  }

  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(name: string, value: string) {
    this.headers.set(name.toLowerCase(), value);
  }

  getResponseHeader(name: string) {
    return this.responseHeaders.get(name.toLowerCase()) ?? this.responseHeaders.get(name) ?? null;
  }

  send(body: Document | XMLHttpRequestBodyInit | null) {
    this.requestBody = body;
  }

  abort() {
    this.aborted = true;
    this.onabort?.();
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
  FakeXMLHttpRequest.instances = [];
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

async function waitFor(predicate: () => boolean, timeoutMs = 100) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    if (predicate()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
  throw new Error('timed out waiting for async upload work');
}

test('uploadFileToNetdiskViaSession completes a direct single upload session', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input, init) => {
    const request = input instanceof Request ? input : new Request(new URL(String(input), 'http://localhost'), init);
    calls.push(`${request.method} ${request.url}`);

    if (request.url.endsWith('/api/v2/files/upload-sessions')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          sessionId: 'session-1',
          objectKey: 'blobs/session-1',
          directUpload: true,
          multipartUpload: false,
          uploadMode: 'DIRECT_SINGLE',
          path: '/docs',
          filename: 'movie.mp4',
          contentType: 'video/mp4',
          size: 9,
          storagePolicyId: 1,
          status: 'CREATED',
          chunkSize: 8388608,
          chunkCount: 1,
          expiresAt: '2026-04-09T00:00:00',
          createdAt: '2026-04-09T00:00:00',
          updatedAt: '2026-04-09T00:00:00',
          strategy: {
            prepareUrl: '/api/v2/files/upload-sessions/session-1/prepare',
            proxyContentUrl: null,
            partPrepareUrlTemplate: null,
            partRecordUrlTemplate: null,
            completeUrl: '/api/v2/files/upload-sessions/session-1/complete',
            proxyFormField: null,
          },
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    if (request.url.endsWith('/api/v2/files/upload-sessions/session-1/prepare')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          direct: true,
          uploadUrl: 'https://upload.example.com/single',
          method: 'PUT',
          headers: {'Content-Type': 'video/mp4'},
          storageName: 'blobs/session-1',
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    if (request.url.endsWith('/api/v2/files/upload-sessions/session-1/complete')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          sessionId: 'session-1',
          status: 'COMPLETED',
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    throw new Error(`unexpected fetch ${request.method} ${request.url}`);
  };

  const uploadPromise = uploadFileToNetdiskViaSession(
    new File([new Blob(['123456789'])], 'movie.mp4', {type: 'video/mp4'}),
    '/docs',
  );

  await waitFor(() => FakeXMLHttpRequest.instances.length === 1);
  assert.equal(FakeXMLHttpRequest.instances.length, 1);
  const uploadRequest = FakeXMLHttpRequest.instances[0];
  assert.equal(uploadRequest.method, 'PUT');
  assert.equal(uploadRequest.url, 'https://upload.example.com/single');
  uploadRequest.respond('', 200, 'text/plain');

  const result = await uploadPromise;
  assert.deepEqual(result, {
    sessionId: 'session-1',
    filename: 'movie.mp4',
    path: '/docs',
  });
  assert.deepEqual(calls, [
    'POST http://localhost/api/v2/files/upload-sessions',
    'GET http://localhost/api/v2/files/upload-sessions/session-1/prepare',
    'POST http://localhost/api/v2/files/upload-sessions/session-1/complete',
  ]);
});

test('uploadFileToNetdiskViaSession completes a proxy upload session', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input, init) => {
    const request = input instanceof Request ? input : new Request(new URL(String(input), 'http://localhost'), init);
    calls.push(`${request.method} ${request.url}`);

    if (request.url.endsWith('/api/v2/files/upload-sessions')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          sessionId: 'session-2',
          objectKey: 'blobs/session-2',
          directUpload: false,
          multipartUpload: false,
          uploadMode: 'PROXY',
          path: '/docs',
          filename: 'notes.txt',
          contentType: 'text/plain',
          size: 5,
          storagePolicyId: 1,
          status: 'CREATED',
          chunkSize: 8388608,
          chunkCount: 1,
          expiresAt: '2026-04-09T00:00:00',
          createdAt: '2026-04-09T00:00:00',
          updatedAt: '2026-04-09T00:00:00',
          strategy: {
            prepareUrl: null,
            proxyContentUrl: '/api/v2/files/upload-sessions/session-2/content',
            partPrepareUrlTemplate: null,
            partRecordUrlTemplate: null,
            completeUrl: '/api/v2/files/upload-sessions/session-2/complete',
            proxyFormField: 'file',
          },
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    if (request.url.endsWith('/api/v2/files/upload-sessions/session-2/complete')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          sessionId: 'session-2',
          status: 'COMPLETED',
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    throw new Error(`unexpected fetch ${request.method} ${request.url}`);
  };

  const uploadPromise = uploadFileToNetdiskViaSession(
    new File([new Blob(['hello'])], 'notes.txt', {type: 'text/plain'}),
    '/docs',
  );

  await waitFor(() => FakeXMLHttpRequest.instances.length === 1);
  assert.equal(FakeXMLHttpRequest.instances.length, 1);
  const uploadRequest = FakeXMLHttpRequest.instances[0];
  assert.equal(uploadRequest.method, 'POST');
  assert.equal(uploadRequest.url, '/api/v2/files/upload-sessions/session-2/content');
  uploadRequest.respond({
    code: 0,
    msg: 'success',
    data: {
      sessionId: 'session-2',
      status: 'UPLOADING',
    },
  });

  const result = await uploadPromise;
  assert.deepEqual(result, {
    sessionId: 'session-2',
    filename: 'notes.txt',
    path: '/docs',
  });
  assert.deepEqual(calls, [
    'POST http://localhost/api/v2/files/upload-sessions',
    'POST http://localhost/api/v2/files/upload-sessions/session-2/complete',
  ]);
});

test('uploadFileToNetdiskViaSession completes a multipart upload session', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input, init) => {
    const request = input instanceof Request ? input : new Request(new URL(String(input), 'http://localhost'), init);
    calls.push(`${request.method} ${request.url}`);

    if (request.url.endsWith('/api/v2/files/upload-sessions')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          sessionId: 'session-3',
          objectKey: 'blobs/session-3',
          directUpload: true,
          multipartUpload: true,
          uploadMode: 'DIRECT_MULTIPART',
          path: '/docs',
          filename: 'archive.zip',
          contentType: 'application/zip',
          size: 10,
          storagePolicyId: 1,
          status: 'CREATED',
          chunkSize: 5,
          chunkCount: 2,
          expiresAt: '2026-04-09T00:00:00',
          createdAt: '2026-04-09T00:00:00',
          updatedAt: '2026-04-09T00:00:00',
          strategy: {
            prepareUrl: null,
            proxyContentUrl: null,
            partPrepareUrlTemplate: '/api/v2/files/upload-sessions/session-3/parts/{partIndex}/prepare',
            partRecordUrlTemplate: '/api/v2/files/upload-sessions/session-3/parts/{partIndex}',
            completeUrl: '/api/v2/files/upload-sessions/session-3/complete',
            proxyFormField: null,
          },
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    if (request.url.endsWith('/api/v2/files/upload-sessions/session-3/parts/0/prepare')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          direct: true,
          uploadUrl: 'https://upload.example.com/part-1',
          method: 'PUT',
          headers: {'Content-Type': 'application/zip'},
          storageName: 'blobs/session-3',
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    if (request.url.endsWith('/api/v2/files/upload-sessions/session-3/parts/1/prepare')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          direct: true,
          uploadUrl: 'https://upload.example.com/part-2',
          method: 'PUT',
          headers: {'Content-Type': 'application/zip'},
          storageName: 'blobs/session-3',
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    if (request.url.endsWith('/api/v2/files/upload-sessions/session-3/parts/0')
        || request.url.endsWith('/api/v2/files/upload-sessions/session-3/parts/1')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          sessionId: 'session-3',
          status: 'UPLOADING',
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    if (request.url.endsWith('/api/v2/files/upload-sessions/session-3/complete')) {
      return new Response(JSON.stringify({
        code: 0,
        msg: 'success',
        data: {
          sessionId: 'session-3',
          status: 'COMPLETED',
        },
      }), {headers: {'Content-Type': 'application/json'}});
    }

    throw new Error(`unexpected fetch ${request.method} ${request.url}`);
  };

  const uploadPromise = uploadFileToNetdiskViaSession(
    new File([new Blob(['abcdefghij'])], 'archive.zip', {type: 'application/zip'}),
    '/docs',
  );

  await waitFor(() => FakeXMLHttpRequest.instances.length === 1);
  assert.equal(FakeXMLHttpRequest.instances.length, 1);
  FakeXMLHttpRequest.instances[0].responseHeaders.set('etag', '"part-1"');
  FakeXMLHttpRequest.instances[0].respond('', 200, 'text/plain');
  await waitFor(() => FakeXMLHttpRequest.instances.length === 2);
  assert.equal(FakeXMLHttpRequest.instances.length, 2);
  FakeXMLHttpRequest.instances[1].responseHeaders.set('etag', '"part-2"');
  FakeXMLHttpRequest.instances[1].respond('', 200, 'text/plain');

  const result = await uploadPromise;
  assert.deepEqual(result, {
    sessionId: 'session-3',
    filename: 'archive.zip',
    path: '/docs',
  });
  assert.deepEqual(calls, [
    'POST http://localhost/api/v2/files/upload-sessions',
    'GET http://localhost/api/v2/files/upload-sessions/session-3/parts/0/prepare',
    'PUT http://localhost/api/v2/files/upload-sessions/session-3/parts/0',
    'GET http://localhost/api/v2/files/upload-sessions/session-3/parts/1/prepare',
    'PUT http://localhost/api/v2/files/upload-sessions/session-3/parts/1',
    'POST http://localhost/api/v2/files/upload-sessions/session-3/complete',
  ]);
});
