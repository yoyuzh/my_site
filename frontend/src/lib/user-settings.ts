import { apiRequest } from '../api/client';
import type { UserCapacity, UserSettings } from '../api/types';
export { formatBytes } from './format';

export function getUserCapacity(signal?: AbortSignal) {
  return apiRequest<UserCapacity>({
    url: '/user/capacity',
    method: 'GET',
    signal,
  });
}

export function getUserSettings(signal?: AbortSignal) {
  return apiRequest<UserSettings>({
    url: '/user/settings',
    method: 'GET',
    signal,
  });
}
