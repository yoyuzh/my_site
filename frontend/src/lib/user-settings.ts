import { apiRequest } from '../api/client';
import type { UpdateUserSettingsPayload, UserCapacity, UserSettings } from '../api/types';
export { formatBytes } from './format';

let pendingUserSettingsRequest: Promise<UserSettings> | null = null;

export function getUserCapacity(signal?: AbortSignal) {
  return apiRequest<UserCapacity>({
    url: '/user/capacity',
    method: 'GET',
    signal,
  });
}

export function getUserSettings(signal?: AbortSignal) {
  if (!signal && pendingUserSettingsRequest) {
    return pendingUserSettingsRequest;
  }

  const request = apiRequest<UserSettings>({
    url: '/user/settings',
    method: 'GET',
    signal,
  });

  if (!signal) {
    pendingUserSettingsRequest = request.finally(() => {
      pendingUserSettingsRequest = null;
    });
    return pendingUserSettingsRequest;
  }

  return request;
}

export function updateUserSettings(payload: UpdateUserSettingsPayload) {
  return apiRequest<UserSettings>({
    url: '/user/settings',
    method: 'PUT',
    data: payload,
  });
}
