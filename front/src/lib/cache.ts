import { readStoredSession } from './session';

interface CacheEnvelope<T> {
  value: T;
  updatedAt: number;
}

const CACHE_PREFIX = 'portal-cache';

function getCacheScope() {
  const session = readStoredSession();
  if (session?.user?.id != null) {
    return `user:${session.user.id}`;
  }

  return 'guest';
}

export function buildScopedCacheKey(namespace: string, ...parts: Array<string | number>) {
  const normalizedParts = parts.map((part) => String(part).replace(/:/g, '_'));
  return [CACHE_PREFIX, getCacheScope(), namespace, ...normalizedParts].join(':');
}

export function readCachedValue<T>(key: string): T | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }

  const rawValue = localStorage.getItem(key);
  if (!rawValue) {
    return null;
  }

  try {
    const parsed = JSON.parse(rawValue) as CacheEnvelope<T>;
    return parsed.value;
  } catch {
    localStorage.removeItem(key);
    return null;
  }
}

export function writeCachedValue<T>(key: string, value: T) {
  if (typeof localStorage === 'undefined') {
    return;
  }

  const payload: CacheEnvelope<T> = {
    value,
    updatedAt: Date.now(),
  };
  localStorage.setItem(key, JSON.stringify(payload));
}

export function removeCachedValue(key: string) {
  if (typeof localStorage === 'undefined') {
    return;
  }

  localStorage.removeItem(key);
}
