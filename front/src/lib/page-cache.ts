import { buildScopedCacheKey, readCachedValue, writeCachedValue } from './cache';
import type { FileMetadata, UserProfile } from './types';

export interface OverviewCache {
  profile: UserProfile | null;
  recentFiles: FileMetadata[];
  rootFiles: FileMetadata[];
}

export function getOverviewCacheKey() {
  return buildScopedCacheKey('overview');
}

export function getFilesLastPathCacheKey() {
  return buildScopedCacheKey('files-last-path');
}

export function getFilesListCacheKey(path: string) {
  return buildScopedCacheKey('files-list', path || 'root');
}
