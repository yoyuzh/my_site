import test from 'node:test';
import assert from 'node:assert/strict';

type StorageMap = Map<string, string>;

function createLocalStorage(storage: StorageMap) {
  return {
    getItem(key: string) {
      return storage.has(key) ? storage.get(key)! : null;
    },
    setItem(key: string, value: string) {
      storage.set(key, value);
    },
    removeItem(key: string) {
      storage.delete(key);
    },
    clear() {
      storage.clear();
    },
  };
}

const storage = new Map<string, string>();
const windowMock = {
  localStorage: createLocalStorage(storage),
  addEventListener() {},
  removeEventListener() {},
  dispatchEvent() {
    return true;
  },
};

Object.defineProperty(globalThis, 'window', {
  value: windowMock,
  configurable: true,
});

if (typeof globalThis.CustomEvent === 'undefined') {
  class CustomEventMock<T = unknown> extends Event {
    detail: T;

    constructor(type: string, init?: CustomEventInit<T>) {
      super(type, init);
      this.detail = init?.detail as T;
    }
  }

  Object.defineProperty(globalThis, 'CustomEvent', {
    value: CustomEventMock,
    configurable: true,
  });
}

const transferModulePromise = import('@/src/transfer/api/transfer');

test('sanitizePickupCode keeps only uppercase letters and digits', async () => {
  const { sanitizePickupCode } = await transferModulePromise;
  assert.equal(sanitizePickupCode(' ab-12 cD*3 '), 'AB12CD');
});

test('toTransferFilePayload preserves relative paths and content types', async () => {
  const { toTransferFilePayload } = await transferModulePromise;
  const file = new File(['hello'], 'greeting.txt', {
    type: 'text/plain',
    lastModified: 1710000000000,
  });
  Object.defineProperty(file, 'webkitRelativePath', {
    value: 'docs/greeting.txt',
    configurable: true,
  });

  assert.deepEqual(toTransferFilePayload([file]), [
    {
      name: 'greeting.txt',
      relativePath: 'docs/greeting.txt',
      size: 5,
      contentType: 'text/plain',
    },
  ]);
});

test('buildOfflineTransferDownloadUrl uses the local api base', async () => {
  const { buildOfflineTransferDownloadUrl } = await transferModulePromise;
  assert.equal(
    buildOfflineTransferDownloadUrl('session-1', 'file-1'),
    '/api/transfer/sessions/session-1/files/file-1/download',
  );
});
