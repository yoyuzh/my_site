import { type FileItem } from './files';

export interface DirectoryCacheEntry {
  items: FileItem[];
  total: number;
  timestamp: number;
  isStale: boolean;
}

const CACHE_TTL = 1000 * 60 * 5; // 5 分钟有效

class FilesCache {
  private cache: Map<string, DirectoryCacheEntry> = new Map();
  private listeners: Set<() => void> = new Set();

  getCacheKey(path: string, page: number, size: number, sort?: string) {
    return `${path}:${page}:${size}:${sort || 'default'}`;
  }

  get(key: string): DirectoryCacheEntry | null {
    const entry = this.cache.get(key);
    if (!entry) return null;

    const isStale = Date.now() - entry.timestamp > CACHE_TTL;
    return { ...entry, isStale };
  }

  set(key: string, items: FileItem[], total: number) {
    this.cache.set(key, {
      items,
      total,
      timestamp: Date.now(),
      isStale: false,
    });
    this.notify();
  }

  invalidate(pathPrefix: string) {
    const keysToDELETE = Array.from(this.cache.keys()).filter((key) => key.startsWith(pathPrefix));
    keysToDELETE.forEach((key) => this.cache.delete(key));
    this.notify();
  }

  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach((l) => l());
  }
}

export const filesCache = new FilesCache();
