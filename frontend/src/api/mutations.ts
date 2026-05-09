import { apiRequest } from './client';
import type { AdminSettings, AdminStoragePolicy, AdminUser, StoragePolicyCapabilities } from './types';

export type AdminUserRole = AdminUser['role'];

export interface AdminStoragePolicyPayload {
  name: string;
  type: string;
  bucketName: string | null;
  endpoint: string | null;
  region: string | null;
  privateBucket: boolean;
  prefix: string | null;
  credentialMode: string;
  maxSizeBytes: number;
  capabilities: StoragePolicyCapabilities;
  enabled: boolean;
}

export interface AdminSettingsUpdatePayload {
  registration?: {
    inviteCodeRequired: boolean;
    managementRoles: string[];
  };
  transfer?: {
    offlineTransferStorageLimitBytes: number;
  };
}

export function updateAdminUserRole(userId: number, role: AdminUserRole) {
  return apiRequest<AdminUser>({
    url: `/admin/users/${userId}/role`,
    method: 'PATCH',
    data: { role },
  });
}

export function updateAdminUserBanned(userId: number, banned: boolean) {
  return apiRequest<AdminUser>({
    url: `/admin/users/${userId}/status`,
    method: 'PATCH',
    data: { banned },
  });
}

export function updateAdminUserPassword(userId: number, newPassword: string) {
  return apiRequest<AdminUser>({
    url: `/admin/users/${userId}/password`,
    method: 'PUT',
    data: { newPassword },
  });
}

export function resetAdminUserPassword(userId: number) {
  return apiRequest<{ userId: number; username: string; newPassword: string }>({
    url: `/admin/users/${userId}/password/reset`,
    method: 'POST',
  });
}

export function updateAdminUserStorageQuota(userId: number, storageQuotaBytes: number) {
  return apiRequest<AdminUser>({
    url: `/admin/users/${userId}/storage-quota`,
    method: 'PATCH',
    data: { storageQuotaBytes },
  });
}

export function updateAdminUserMaxUploadSize(userId: number, maxUploadSizeBytes: number) {
  return apiRequest<AdminUser>({
    url: `/admin/users/${userId}/max-upload-size`,
    method: 'PATCH',
    data: { maxUploadSizeBytes },
  });
}

export function deleteAdminFile(fileId: number) {
  return apiRequest<void>({
    url: `/admin/files/${fileId}`,
    method: 'DELETE',
  });
}

export function deleteAdminShare(shareId: number) {
  return apiRequest<void>({
    url: `/admin/shares/${shareId}`,
    method: 'DELETE',
  });
}

export function createAdminStoragePolicy(payload: AdminStoragePolicyPayload) {
  return apiRequest<AdminStoragePolicy>({
    url: '/admin/storage-policies',
    method: 'POST',
    data: payload,
  });
}

export function updateAdminStoragePolicy(policyId: number, payload: AdminStoragePolicyPayload) {
  return apiRequest<AdminStoragePolicy>({
    url: `/admin/storage-policies/${policyId}`,
    method: 'PUT',
    data: payload,
  });
}

export function updateAdminStoragePolicyStatus(policyId: number, enabled: boolean) {
  return apiRequest<AdminStoragePolicy>({
    url: `/admin/storage-policies/${policyId}/status`,
    method: 'PATCH',
    data: { enabled },
  });
}

export function updateAdminSettings(payload: AdminSettingsUpdatePayload) {
  return apiRequest<AdminSettings>({
    url: '/admin/settings',
    method: 'PUT',
    data: payload,
  });
}

export function updateAdminInviteCode(inviteCode: string) {
  return apiRequest<{ inviteCode: string }>({
    url: '/admin/settings/registration/invite-code',
    method: 'PATCH',
    data: { inviteCode },
  });
}

export function rotateAdminInviteCode() {
  return apiRequest<{ inviteCode: string }>({
    url: '/admin/settings/registration/invite-code/rotate',
    method: 'POST',
  });
}

export function updateOfflineTransferStorageLimit(offlineTransferStorageLimitBytes: number) {
  return apiRequest<{ offlineTransferStorageLimitBytes: number }>({
    url: '/admin/settings/offline-transfer-storage-limit',
    method: 'PATCH',
    data: { offlineTransferStorageLimitBytes },
  });
}
