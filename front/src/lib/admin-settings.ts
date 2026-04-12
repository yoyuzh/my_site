import { fetchApi } from './api';

export type AdminSettings = {
  site: {
    supported: boolean;
    writeSupported: boolean;
  };
  registration: {
    inviteCodeRequired: boolean;
    currentInviteCode: string;
    managementRoles: string[];
    writeSupported: boolean;
  };
  userSession: {
    accessExpirationSeconds: number;
    refreshExpirationSeconds: number;
    tokenBlacklistEnabled: boolean;
    tokenBlacklistTtlBufferSeconds: number;
    writeSupported: boolean;
  };
  transfer: {
    offlineTransferStorageLimitBytes: number;
    writeSupported: boolean;
  };
  mediaProcessing: {
    metadataExtractionEnabled: boolean;
    thumbnailGenerationEnabled: boolean;
    videoPosterEnabled: boolean;
    writeSupported: boolean;
  };
  queue: {
    backend: string;
    mediaMetadataFixedDelayMs: number;
    mediaMetadataInitialDelayMs: number;
    writeSupported: boolean;
  };
  appearance: {
    supported: boolean;
    writeSupported: boolean;
  };
  server: {
    storageProvider: string;
    redisEnabled: boolean;
    writeSupported: boolean;
  };
};

export type AdminRegistrationInviteCodeResponse = {
  currentInviteCode: string;
};

export type AdminOfflineTransferStorageLimitResponse = {
  offlineTransferStorageLimitBytes: number;
};

export async function getAdminSettings() {
  return fetchApi<AdminSettings>('/admin/settings');
}

export async function updateAdminRegistrationInviteCode(inviteCode: string) {
  return fetchApi<AdminRegistrationInviteCodeResponse>('/admin/settings/registration/invite-code', {
    method: 'PATCH',
    body: JSON.stringify({ inviteCode }),
  });
}

export async function rotateAdminRegistrationInviteCode() {
  return fetchApi<AdminRegistrationInviteCodeResponse>('/admin/settings/registration/invite-code/rotate', {
    method: 'POST',
  });
}

export async function updateAdminOfflineTransferStorageLimit(offlineTransferStorageLimitBytes: number) {
  return fetchApi<AdminOfflineTransferStorageLimitResponse>('/admin/settings/offline-transfer-storage-limit', {
    method: 'PATCH',
    body: JSON.stringify({ offlineTransferStorageLimitBytes }),
  });
}
