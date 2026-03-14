import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';

import { clearStoredSession, saveStoredSession } from './session';
import { buildScopedCacheKey, readCachedValue, writeCachedValue } from './cache';

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

const originalStorage = globalThis.localStorage;

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: new MemoryStorage(),
  });
  clearStoredSession();
});

afterEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: originalStorage,
  });
});

test('scoped cache key includes current user identity', () => {
  saveStoredSession({
    token: 'token-1',
    user: {
      id: 7,
      username: 'alice',
      email: 'alice@example.com',
      createdAt: '2026-03-14T12:00:00',
    },
  });

  assert.equal(buildScopedCacheKey('school', '2023123456', '2025-spring'), 'portal-cache:user:7:school:2023123456:2025-spring');
});

test('cached values are isolated between users', () => {
  saveStoredSession({
    token: 'token-1',
    user: {
      id: 7,
      username: 'alice',
      email: 'alice@example.com',
      createdAt: '2026-03-14T12:00:00',
    },
  });
  writeCachedValue(buildScopedCacheKey('school', '2023123456', '2025-spring'), {
    queried: true,
    grades: [95],
  });

  saveStoredSession({
    token: 'token-2',
    user: {
      id: 8,
      username: 'bob',
      email: 'bob@example.com',
      createdAt: '2026-03-14T12:00:00',
    },
  });

  assert.equal(readCachedValue(buildScopedCacheKey('school', '2023123456', '2025-spring')), null);
});

test('invalid cached json is ignored safely', () => {
  localStorage.setItem('portal-cache:user:7:school:2023123456:2025-spring', '{broken-json');

  assert.equal(readCachedValue('portal-cache:user:7:school:2023123456:2025-spring'), null);
  assert.equal(localStorage.getItem('portal-cache:user:7:school:2023123456:2025-spring'), null);
});
