import { useCallback, useEffect, useState } from 'react';
import { listFiles, type FileItem } from '../lib/files';
import { filesCache, type DirectoryCacheEntry } from '../lib/files-cache';

export function useDirectoryData(path: string, page = 0, size = 100) {
  const [data, setData] = useState<DirectoryCacheEntry | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const key = filesCache.getCacheKey(path, page, size);

  const fetchLatest = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    setError('');
    try {
      const result = await listFiles(path, page, size);
      filesCache.set(key, result.items, result.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取目录数据失败');
    } finally {
      if (!isSilent) setLoading(false);
    }
  }, [key, path, page, size]);

  useEffect(() => {
    const entry = filesCache.get(key);
    if (entry) {
      setData(entry);
      if (entry.isStale) {
        void fetchLatest(true);
      }
    } else {
      void fetchLatest();
    }

    const unsubscribe = filesCache.subscribe(() => {
      setData(filesCache.get(key));
    });

    return unsubscribe;
  }, [key, fetchLatest]);

  return {
    items: data?.items ?? [],
    total: data?.total ?? 0,
    loading,
    error,
    refresh: () => fetchLatest(),
    isStale: data?.isStale ?? false,
  };
}
