import { buildScopedCacheKey, readCachedValue, writeCachedValue } from './cache';
import type { CourseResponse, FileMetadata, GradeResponse, UserProfile } from './types';

export interface SchoolQueryCache {
  studentId: string;
  semester: string;
}

export interface SchoolResultsCache {
  queried: boolean;
  schedule: CourseResponse[];
  grades: GradeResponse[];
  studentId: string;
  semester: string;
}

export interface OverviewCache {
  profile: UserProfile | null;
  recentFiles: FileMetadata[];
  rootFiles: FileMetadata[];
  schedule: CourseResponse[];
  grades: GradeResponse[];
}

function getSchoolQueryCacheKey() {
  return buildScopedCacheKey('school-query');
}

export function readStoredSchoolQuery() {
  return readCachedValue<SchoolQueryCache>(getSchoolQueryCacheKey());
}

export function writeStoredSchoolQuery(query: SchoolQueryCache) {
  writeCachedValue(getSchoolQueryCacheKey(), query);
}

export function getSchoolResultsCacheKey(studentId: string, semester: string) {
  return buildScopedCacheKey('school-results', studentId, semester);
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
