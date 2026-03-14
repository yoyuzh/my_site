import { apiRequest } from './api';
import { writeCachedValue } from './cache';
import { getSchoolResultsCacheKey, writeStoredSchoolQuery } from './page-cache';
import type { LatestSchoolDataResponse } from './types';

export async function fetchLatestSchoolData() {
  return apiRequest<LatestSchoolDataResponse | null>('/cqu/latest');
}

export function cacheLatestSchoolData(latest: LatestSchoolDataResponse) {
  writeStoredSchoolQuery({
    studentId: latest.studentId,
    semester: latest.semester,
  });
  writeCachedValue(getSchoolResultsCacheKey(latest.studentId, latest.semester), {
    queried: true,
    studentId: latest.studentId,
    semester: latest.semester,
    schedule: latest.schedule,
    grades: latest.grades,
  });
}
